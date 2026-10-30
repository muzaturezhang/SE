package com.holidayresort.booking.web.stay;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class StayForm {
    @NotBlank(message = "请选择房型。")
    private String roomType;
    @NotNull(message = "请选择入住日期。")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate arrival;
    @NotNull(message = "请选择退房日期。")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate departure;
    @NotNull(message = "请输入房间数量。")
    @Min(value = 1, message = "房间数量必须为正整数。")
    private Integer rooms;
    @NotBlank(message = "请输入入住人姓名。")
    @Size(max = 80, message = "入住人姓名最多 80 个字符。")
    private String guestName;
    @NotBlank(message = "请输入联系邮箱。")
    @Email(message = "请输入有效的联系邮箱。")
    @Size(max = 254, message = "联系邮箱最多 254 个字符。")
    private String guestEmail;

    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public LocalDate getArrival() { return arrival; }
    public void setArrival(LocalDate arrival) { this.arrival = arrival; }
    public LocalDate getDeparture() { return departure; }
    public void setDeparture(LocalDate departure) { this.departure = departure; }
    public Integer getRooms() { return rooms; }
    public void setRooms(Integer rooms) { this.rooms = rooms; }
    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public String getGuestEmail() { return guestEmail; }
    public void setGuestEmail(String guestEmail) { this.guestEmail = guestEmail; }

    public StayPageGateway.Selection selection() {
        return new StayPageGateway.Selection(roomType, arrival, departure, rooms);
    }
}
