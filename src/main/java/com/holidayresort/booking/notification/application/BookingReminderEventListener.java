package com.holidayresort.booking.notification.application;

import com.holidayresort.booking.booking.application.BookingLifecycleEvent;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.room.application.RoomQueryService;
import com.holidayresort.booking.room.domain.RoomType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * D-owned adapter between C's booking lifecycle and D's reminder application
 * service. Booking and reminder writes therefore participate in the same
 * transaction when the event is published from BookingService.
 */
@Component
public class BookingReminderEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(BookingReminderEventListener.class);

    private final NotificationService notifications;
    private final RoomQueryService roomTypes;

    public BookingReminderEventListener(NotificationService notifications, RoomQueryService roomTypes) {
        this.notifications = notifications;
        this.roomTypes = roomTypes;
    }

    @EventListener
    public void onBookingLifecycle(BookingLifecycleEvent event) {
        Booking booking = event.booking();
        if (event.type() == BookingLifecycleEvent.Type.CREATED
                || event.type() == BookingLifecycleEvent.Type.CHANGED
                || booking.status() == BookingStatus.PAID) {
            schedule(booking, event.roomTypeName());
            return;
        }
        if (booking.status() == BookingStatus.FAILED
                || booking.status() == BookingStatus.EXPIRED
                || booking.status() == BookingStatus.CANCELLED) {
            notifications.cancelOneDayReminder(booking.reference());
        }
    }

    private void schedule(Booking booking, String roomTypeName) {
        String name = roomTypeName;
        if (name == null || name.isBlank()) {
            name = roomTypes.listRoomTypes().stream()
                    .filter(type -> type.id().equals(booking.roomTypeId()))
                    .map(RoomType::name)
                    .findFirst()
                    .orElse(booking.roomTypeId());
        }
        try {
            notifications.scheduleOneDayReminder(new ReminderRequest(
                    booking.reference(), booking.guestEmail(), booking.stay().arrival(),
                    booking.stay().departure(), name));
        } catch (NotificationException exception) {
            // Same-day arrivals cannot receive a one-day-before reminder. The
            // booking remains valid; no recipient or message content is logged.
            if (exception.kind() == NotificationException.Kind.INVALID_REQUEST
                    && "checkInDate".equals(exception.field())) {
                LOG.info("Reminder skipped for same-day booking: bookingReference={}",
                        booking.reference());
                return;
            }
            throw exception;
        }
    }
}
