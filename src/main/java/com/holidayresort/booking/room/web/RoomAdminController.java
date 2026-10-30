package com.holidayresort.booking.room.web;

import com.holidayresort.booking.room.application.ManagedRoom;
import com.holidayresort.booking.room.application.RoomManagementException;
import com.holidayresort.booking.room.application.RoomManagementService;
import com.holidayresort.booking.room.application.RoomQueryService;
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.web.StaffNavigationAdvice;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RoomAdminController {
    private final RoomManagementService management;
    private final RoomQueryService queries;
    private final StaffNavigationAdvice navigation;

    public RoomAdminController(RoomManagementService management, RoomQueryService queries, StaffNavigationAdvice navigation) {
        this.management = management;
        this.queries = queries;
        this.navigation = navigation;
    }

    @InitBinder("roomForm")
    void bindRoom(WebDataBinder binder) {
        binder.setAllowedFields("roomId", "roomTypeId", "enabled");
    }

    @InitBinder("rateForm")
    void bindRate(WebDataBinder binder) {
        binder.setAllowedFields("nightlyRate");
    }

    @GetMapping("/admin/rooms")
    String list(@RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "all") String state, Model model) {
        model.addAttribute("managedRooms", management.list(type, state));
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedState", state);
        List<RoomType> types = queries.listRoomTypes();
        model.addAttribute("roomTypes", types);
        model.addAttribute("rateCards", types.stream().map(typeInfo -> RoomCardView.of(typeInfo, null)).toList());
        return "room/admin/index";
    }

    @GetMapping("/admin/rooms/new")
    String newRoom(Model model) {
        return roomEditor(new RoomEditForm(), null, model);
    }

    @PostMapping("/admin/rooms")
    String create(@ModelAttribute("roomForm") RoomEditForm form, BindingResult errors,
            HttpServletResponse response, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                management.create(form.getRoomId(), form.getRoomTypeId(), form.isEnabled());
                redirect.addFlashAttribute("successMessage", "房间已新增，查询库存已同步更新。");
                return "redirect:/admin/rooms";
            } catch (RoomManagementException exception) {
                reject(exception, errors, response);
            }
        } else response.setStatus(400);
        addFormErrors(errors, model);
        return roomEditor(form, null, model);
    }

    @GetMapping("/admin/rooms/{roomId}/edit")
    String edit(@PathVariable String roomId, Model model) {
        ManagedRoom item = management.get(roomId);
        RoomEditForm form = new RoomEditForm();
        form.setRoomId(item.room().id());
        form.setRoomTypeId(item.room().roomTypeId());
        form.setEnabled(item.room().enabled());
        return roomEditor(form, item, model);
    }

    @PostMapping("/admin/rooms/{roomId}")
    String update(@PathVariable String roomId, @ModelAttribute("roomForm") RoomEditForm form,
            BindingResult errors, HttpServletResponse response, Model model, RedirectAttributes redirect) {
        ManagedRoom item = management.get(roomId);
        // The URL is the stable identity; a forged roomId parameter cannot rename a room.
        form.setRoomId(roomId);
        if (!errors.hasErrors()) {
            try {
                management.update(roomId, form.getRoomTypeId(), form.isEnabled());
                redirect.addFlashAttribute("successMessage", "房间设置已保存，查询库存已同步更新。");
                return "redirect:/admin/rooms";
            } catch (RoomManagementException exception) {
                reject(exception, errors, response);
            }
        } else response.setStatus(400);
        addFormErrors(errors, model);
        return roomEditor(form, item, model);
    }

    @PostMapping("/admin/rooms/{roomId}/disable")
    String disable(@PathVariable String roomId, RedirectAttributes redirect) {
        management.disable(roomId);
        redirect.addFlashAttribute("successMessage", "房间已停用，历史占用记录已保留。");
        return "redirect:/admin/rooms";
    }

    @GetMapping("/admin/room-types/{typeId}/rate")
    String rate(@PathVariable String typeId, Model model) {
        var type = requireType(typeId);
        RoomRateForm form = new RoomRateForm();
        form.setNightlyRate(BigDecimal.valueOf(type.nightlyRateMinor(), 2));
        model.addAttribute("rateForm", form);
        model.addAttribute("roomType", type);
        return "room/admin/rate";
    }

    @PostMapping("/admin/room-types/{typeId}/rate")
    String saveRate(@PathVariable String typeId, @ModelAttribute("rateForm") RoomRateForm form,
            BindingResult errors, HttpServletResponse response, Model model, RedirectAttributes redirect) {
        model.addAttribute("roomType", requireType(typeId));
        if (!errors.hasErrors()) {
            try {
                management.updateRate(typeId, form.getNightlyRate());
                redirect.addFlashAttribute("successMessage", "房型每晚基础价已更新。");
                return "redirect:/admin/rooms";
            } catch (RoomManagementException exception) {
                reject(exception, errors, response);
            }
        } else response.setStatus(400);
        addFormErrors(errors, model);
        return "room/admin/rate";
    }

    private String roomEditor(RoomEditForm form, ManagedRoom item, Model model) {
        model.addAttribute("roomForm", form);
        model.addAttribute("editing", item != null);
        model.addAttribute("managedRoom", item);
        model.addAttribute("roomTypes", queries.listRoomTypes());
        return "room/admin/edit";
    }

    private RoomType requireType(String typeId) {
        return queries.listRoomTypes().stream().filter(type -> type.id().equals(typeId)).findFirst()
                .orElseThrow(() -> new RoomManagementException(RoomManagementException.Kind.NOT_FOUND,
                        "roomTypeId", "找不到这个房型，请返回列表重新选择。"));
    }

    private void reject(RoomManagementException exception, BindingResult errors, HttpServletResponse response) {
        errors.rejectValue(exception.field(), "room.management", exception.getMessage());
        response.setStatus(status(exception));
    }

    private void addFormErrors(BindingResult errors, Model model) {
        model.addAttribute("formErrors", errors.getFieldErrors().stream().map(error -> error.isBindingFailure()
                ? (error.getField().equals("nightlyRate") ? "请输入有效的 CNY 金额，最多两位小数。" : "请选择有效的房间状态。")
                : error.getDefaultMessage()).distinct().toList());
    }

    @ExceptionHandler(RoomManagementException.class)
    String managementError(RoomManagementException exception, HttpServletResponse response, Model model, Authentication authentication) {
        response.setStatus(status(exception));
        navigation.addToErrorModel(model, authentication);
        model.addAttribute("managementError", exception.getMessage());
        return "room/admin/error";
    }

    private int status(RoomManagementException exception) {
        return switch (exception.kind()) {
            case INVALID -> 400;
            case CONFLICT -> 409;
            case NOT_FOUND -> 404;
        };
    }
}
