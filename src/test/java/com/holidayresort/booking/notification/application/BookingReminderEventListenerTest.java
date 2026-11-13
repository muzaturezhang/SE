package com.holidayresort.booking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.holidayresort.booking.booking.application.BookingLifecycleEvent;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.booking.domain.PriceSnapshot;
import com.holidayresort.booking.room.application.RoomQueryService;
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.room.domain.StayPeriod;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BookingReminderEventListenerTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final RoomQueryService roomTypes = mock(RoomQueryService.class);
    private final BookingReminderEventListener listener =
            new BookingReminderEventListener(notifications, roomTypes);

    @Test
    void createdBookingSchedulesReminderWithRoomTypeName() {
        Booking booking = booking(BookingStatus.PENDING, LocalDate.now().plusDays(3));

        listener.onBookingLifecycle(new BookingLifecycleEvent(
                BookingLifecycleEvent.Type.CREATED, booking, "庭院客房"));

        ArgumentCaptor<ReminderRequest> request = ArgumentCaptor.forClass(ReminderRequest.class);
        verify(notifications).scheduleOneDayReminder(request.capture());
        assertThat(request.getValue().bookingReference()).isEqualTo("BOOK-TEST");
        assertThat(request.getValue().roomTypeName()).isEqualTo("庭院客房");
        assertThat(request.getValue().recipientEmail()).isEqualTo("guest@example.com");
    }

    @Test
    void failedBookingCancelsReminder() {
        Booking booking = booking(BookingStatus.FAILED, LocalDate.now().plusDays(3));

        listener.onBookingLifecycle(new BookingLifecycleEvent(
                BookingLifecycleEvent.Type.STATUS_CHANGED, booking, null));

        verify(notifications).cancelOneDayReminder("BOOK-TEST");
        verify(notifications, never()).scheduleOneDayReminder(any());
    }

    @Test
    void sameDayArrivalDoesNotFailTheBooking() {
        Booking booking = booking(BookingStatus.PENDING, LocalDate.now());
        when(notifications.scheduleOneDayReminder(any())).thenThrow(
                new NotificationException(NotificationException.Kind.INVALID_REQUEST,
                        "checkInDate", "too late"));

        listener.onBookingLifecycle(new BookingLifecycleEvent(
                BookingLifecycleEvent.Type.CREATED, booking, "庭院客房"));

        verify(notifications).scheduleOneDayReminder(any());
    }

    @Test
    void nonDateNotificationErrorIsNotSwallowed() {
        Booking booking = booking(BookingStatus.PENDING, LocalDate.now().plusDays(3));
        when(notifications.scheduleOneDayReminder(any())).thenThrow(
                new NotificationException(NotificationException.Kind.CONFLICT,
                        "bookingReference", "conflict"));

        assertThatThrownBy(() -> listener.onBookingLifecycle(new BookingLifecycleEvent(
                BookingLifecycleEvent.Type.CREATED, booking, "庭院客房")))
                .isInstanceOf(NotificationException.class);
    }

    private static Booking booking(BookingStatus status, LocalDate arrival) {
        return new Booking("BOOK-TEST", "idem-test", "CUST-1", "Guest", "guest@example.com",
                "courtyard", new StayPeriod(arrival, arrival.plusDays(2)), 1,
                List.of("C101"), new PriceSnapshot(98000, 2, 1, 0, 196000, 196000),
                status, LocalDateTime.now(), LocalDateTime.now().plusMinutes(15));
    }
}
