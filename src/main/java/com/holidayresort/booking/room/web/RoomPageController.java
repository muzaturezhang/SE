package com.holidayresort.booking.room.web;

import com.holidayresort.booking.room.application.InvalidRoomSearchException;
import com.holidayresort.booking.room.application.RoomQueryService;
import com.holidayresort.booking.room.application.RoomSearchQuery;
import com.holidayresort.booking.room.application.RoomSearchResult;
import com.holidayresort.booking.room.domain.RoomType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class RoomPageController {

    private final RoomQueryService service;

    public RoomPageController(RoomQueryService service) {
        this.service = service;
    }

    @InitBinder("staySearch")
    void bindSearchFields(WebDataBinder binder) {
        binder.setAllowedFields("arrival", "departure", "rooms", "roomType");
    }

    @GetMapping("/rooms")
    String rooms(@ModelAttribute("staySearch") RoomSearchForm form, BindingResult errors,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        List<RoomType> types = service.listRoomTypes();
        model.addAttribute("roomTypes", types);
        String typeId = form.getRoomType();
        if (typeId == null || typeId.isBlank() || typeId.equals("all")) {
            typeId = null;
            form.setRoomType("all");
        }
        final String selectedType = typeId;
        if (selectedType != null && types.stream().noneMatch(type -> type.id().equals(selectedType))) {
            errors.rejectValue("roomType", "room.search.invalid", "找不到所选房型，请重新选择。");
        }
        boolean requested = request.getParameterMap().containsKey("arrival")
                || request.getParameterMap().containsKey("departure")
                || request.getParameterMap().containsKey("rooms");
        List<RoomCardView> cards = new ArrayList<>();
        boolean performed = false;
        if (requested && !errors.hasErrors()) {
            if (form.getRooms() == null) {
                errors.rejectValue("rooms", "room.search.invalid", "请输入房间数量。");
            } else {
                try {
                    RoomSearchResult result = service.search(new RoomSearchQuery(
                            form.getArrival(), form.getDeparture(), form.getRooms(), selectedType));
                    cards = result.options().stream()
                            .map(option -> RoomCardView.of(option.roomType(), option.availableRooms()))
                            .toList();
                    model.addAttribute("stayNights", result.stay().nights());
                    performed = true;
                } catch (InvalidRoomSearchException exception) {
                    errors.rejectValue(exception.field(), "room.search.invalid", exception.getMessage());
                }
            }
        } else if (!requested && !errors.hasErrors()) {
            cards = types.stream()
                    .filter(type -> selectedType == null || type.id().equals(selectedType))
                    .map(type -> RoomCardView.of(type, null))
                    .toList();
        }
        if (errors.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        model.addAttribute("roomCards", cards);
        model.addAttribute("searchPerformed", performed);
        model.addAttribute("searchErrors", errors.getFieldErrors().stream()
                .map(this::friendlyMessage).distinct().toList());
        return "room/index";
    }

    private String friendlyMessage(FieldError error) {
        if (!error.isBindingFailure()) {
            return error.getDefaultMessage();
        }
        return switch (error.getField()) {
            case "arrival" -> "请输入有效的入住日期（年-月-日）。";
            case "departure" -> "请输入有效的退房日期（年-月-日）。";
            case "rooms" -> "房间数量必须为有效的正整数。";
            default -> "请检查查询条件。";
        };
    }
}
