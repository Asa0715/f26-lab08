package edu.cmu.cs214.campusreserve.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.BookingChange;
import edu.cmu.cs214.campusreserve.core.domain.ModificationResult;
import edu.cmu.cs214.campusreserve.core.domain.RejectionReason;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingServiceModifyTest extends BookingServiceTestBase {

    private static final TimeInterval SLOT = Fixtures.interval("10:00", "11:00");

    @Test
    void aFifteenMinuteShiftSucceeds() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        TimeInterval shifted = Fixtures.interval("10:15", "11:15");

        ModificationResult result = service.modifyBooking(id, "u-1", shifted, 4);

        assertTrue(result.isModified());
        Booking stored = service.findBooking(id).orElseThrow();
        assertEquals(shifted, stored.interval());
        assertEquals(4, stored.attendees());
        assertTrue(stored.holdsRoom());
    }

    @Test
    void aSuccessRecordsTheOldAndNewValuesAndWhen() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        clock.setTo("2026-10-19T09:30:00Z");
        TimeInterval moved = Fixtures.interval("13:00", "14:30");

        service.modifyBooking(id, "u-1", moved, 6);

        List<BookingChange> history = service.findBooking(id).orElseThrow().history();
        assertEquals(1, history.size());
        BookingChange change = history.get(0);
        assertEquals(SLOT, change.previousInterval());
        assertEquals(4, change.previousAttendees());
        assertEquals(moved, change.newInterval());
        assertEquals(6, change.newAttendees());
        assertEquals(Instant.parse("2026-10-19T09:30:00Z"), change.changedAt());
    }

    @Test
    void historyAccumulatesOldestFirst() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        service.modifyBooking(id, "u-1", Fixtures.interval("10:15", "11:15"), 4);
        service.modifyBooking(id, "u-1", Fixtures.interval("10:15", "11:15"), 8);

        List<BookingChange> history = service.findBooking(id).orElseThrow().history();
        assertEquals(2, history.size());
        assertEquals(SLOT, history.get(0).previousInterval());
        assertEquals(8, history.get(1).newAttendees());
    }

    @Test
    void aMoveIntoATakenSlotIsRefusedAndChangesNothing() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        bookAndGetId("r-1", "u-2", Fixtures.interval("12:00", "13:00"));
        Booking original = service.findBooking(id).orElseThrow();
        int waitingBefore = waitlist.count();

        ModificationResult result = service.modifyBooking(id, "u-1", Fixtures.interval("12:00", "13:00"), 4);

        assertEquals(Optional.of(RejectionReason.ROOM_UNAVAILABLE), result.rejectionReason());
        assertEquals(original, service.findBooking(id).orElseThrow());
        assertEquals(waitingBefore, waitlist.count());
        assertTrue(service.waitlistForUser("u-1").isEmpty());
    }

    @Test
    void aPartialOverlapWithAnotherBookingIsRefused() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        bookAndGetId("r-1", "u-2", Fixtures.interval("11:00", "12:00"));

        ModificationResult result = service.modifyBooking(id, "u-1", Fixtures.interval("10:00", "11:15"), 4);

        assertEquals(Optional.of(RejectionReason.ROOM_UNAVAILABLE), result.rejectionReason());
        assertEquals(SLOT, service.findBooking(id).orElseThrow().interval());
    }

    @Test
    void aBookerAtTheCapCanStillModify() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("08:00", "09:00"));
        bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "11:00"));
        bookAndGetId("r-1", "u-1", Fixtures.interval("12:00", "13:00"));
        bookAndGetId("r-1", "u-1", Fixtures.interval("14:00", "15:00"));
        bookAndGetId("r-1", "u-1", Fixtures.interval("16:00", "17:00"));
        assertEquals(5, service.activeBookingsForUser("u-1").size());
        assertEquals(Optional.of(RejectionReason.USER_BOOKING_LIMIT_REACHED),
            book("r-1", "u-1", Fixtures.interval("18:00", "19:00")).rejectionReason());

        ModificationResult result = service.modifyBooking(id, "u-1", Fixtures.interval("08:15", "09:15"), 4);

        assertTrue(result.isModified());
        assertEquals(Fixtures.interval("08:15", "09:15"), service.findBooking(id).orElseThrow().interval());
        assertEquals(5, service.activeBookingsForUser("u-1").size());
    }

    @Test
    void lengtheningOverItsOwnTimeIsAllowed() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        ModificationResult result = service.modifyBooking(id, "u-1", Fixtures.interval("10:00", "12:00"), 4);
        assertTrue(result.isModified());
        assertEquals(Fixtures.interval("10:00", "12:00"), service.findBooking(id).orElseThrow().interval());
    }

    @Test
    void theOwnersOtherBookingsStillBlockAMove() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        bookAndGetId("r-2", "u-1", Fixtures.interval("12:00", "13:00"));

        ModificationResult result = service.modifyBooking(id, "u-1", Fixtures.interval("12:00", "13:00"), 4);

        assertEquals(Optional.of(RejectionReason.USER_DOUBLE_BOOKED), result.rejectionReason());
        assertEquals(SLOT, service.findBooking(id).orElseThrow().interval());
    }

    @Test
    void theRequestRulesApplyToTheNewValues() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        assertEquals(Optional.of(RejectionReason.ATTENDEES_EXCEED_CAPACITY),
            service.modifyBooking(id, "u-1", SLOT, 11).rejectionReason());
        assertEquals(Optional.of(RejectionReason.INTERVAL_NOT_ALIGNED),
            service.modifyBooking(id, "u-1", Fixtures.interval("10:05", "11:05"), 4).rejectionReason());
        assertEquals(Optional.of(RejectionReason.INTERVAL_TOO_SHORT),
            service.modifyBooking(id, "u-1", Fixtures.interval("10:00", "10:15"), 4).rejectionReason());
        assertEquals(Optional.of(RejectionReason.OUTSIDE_OPENING_HOURS),
            service.modifyBooking(id, "u-1", Fixtures.interval("21:30", "22:30"), 4).rejectionReason());
        assertEquals(Optional.of(RejectionReason.INTERVAL_IN_PAST),
            service.modifyBooking(id, "u-1", Fixtures.intervalOn("2026-10-18", "10:00", "11:00"), 4)
                .rejectionReason());
        Booking stored = service.findBooking(id).orElseThrow();
        assertEquals(SLOT, stored.interval());
        assertEquals(4, stored.attendees());
        assertTrue(stored.history().isEmpty());
    }

    @Test
    void rejectsAnUnknownBooking() {
        assertEquals(Optional.of(RejectionReason.BOOKING_NOT_FOUND),
            service.modifyBooking("bk-missing", "u-1", SLOT, 4).rejectionReason());
    }

    @Test
    void rejectsSomeoneElsesBooking() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        assertEquals(Optional.of(RejectionReason.NOT_BOOKING_OWNER),
            service.modifyBooking(id, "u-2", Fixtures.interval("10:15", "11:15"), 4).rejectionReason());
        assertEquals(SLOT, service.findBooking(id).orElseThrow().interval());
    }

    @Test
    void rejectsACancelledBooking() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        service.cancelBooking(id, "u-1");
        assertEquals(Optional.of(RejectionReason.BOOKING_ALREADY_CANCELLED),
            service.modifyBooking(id, "u-1", Fixtures.interval("10:15", "11:15"), 4).rejectionReason());
        assertFalse(service.findBooking(id).orElseThrow().holdsRoom());
    }

    @Test
    void rejectsABookingThatHasStarted() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        clock.setTo("2026-10-20T10:30:00Z");
        assertEquals(Optional.of(RejectionReason.BOOKING_ALREADY_STARTED),
            service.modifyBooking(id, "u-1", Fixtures.interval("11:00", "12:00"), 4).rejectionReason());
        assertEquals(SLOT, service.findBooking(id).orElseThrow().interval());
    }

    @Test
    void anIdenticalModificationSucceedsAndRecordsNothing() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        Booking original = service.findBooking(id).orElseThrow();

        ModificationResult result = service.modifyBooking(id, "u-1", SLOT, 4);

        assertTrue(result.isModified());
        assertFalse(((ModificationResult.Modified) result).changedAnything());
        assertEquals(original, service.findBooking(id).orElseThrow());
        assertTrue(service.findBooking(id).orElseThrow().history().isEmpty());
    }

    @Test
    void aModificationPublishesNoEventYet() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        service.modifyBooking(id, "u-1", Fixtures.interval("10:15", "11:15"), 4);
        assertEquals(List.of("booking-created"), publisher.types());
    }

    @Test
    void freedTimeGoesToTheFirstWaiter() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        assertTrue(book("r-1", "u-2", SLOT).isWaitlisted());
        assertTrue(book("r-1", "u-3", SLOT).isWaitlisted());

        ModificationResult result = service.modifyBooking(id, "u-1", Fixtures.interval("14:00", "15:00"), 4);

        assertEquals(1, result.promotions().size());
        Booking promoted = result.promotions().get(0).booking();
        assertEquals("u-2", promoted.userId());
        assertEquals(SLOT, promoted.interval());
        assertEquals(1, service.waitlistForRoom("r-1").size());
        assertEquals(List.of("booking-created", "waitlist-promoted"), publisher.types());
    }

    @Test
    void shorteningFreesTheTailForAWaiter() {
        String id = bookAndGetId("r-1", "u-1", Fixtures.interval("10:00", "12:00"));
        assertTrue(book("r-1", "u-2", Fixtures.interval("11:00", "12:00")).isWaitlisted());

        ModificationResult result = service.modifyBooking(id, "u-1", SLOT, 4);

        assertEquals(1, result.promotions().size());
        assertEquals("u-2", result.promotions().get(0).booking().userId());
    }

    @Test
    void aWaiterWhoseSlotIsStillTakenIsNotPromoted() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        assertTrue(book("r-1", "u-2", SLOT).isWaitlisted());

        ModificationResult result = service.modifyBooking(id, "u-1", Fixtures.interval("10:00", "12:00"), 4);

        assertTrue(result.isModified());
        assertTrue(result.promotions().isEmpty());
        assertEquals(1, service.waitlistForRoom("r-1").size());
    }

    @Test
    void anAttendeeOnlyChangePromotesNobody() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        assertTrue(book("r-1", "u-2", SLOT).isWaitlisted());

        ModificationResult result = service.modifyBooking(id, "u-1", SLOT, 7);

        assertTrue(result.isModified());
        assertTrue(result.promotions().isEmpty());
        assertEquals(7, service.findBooking(id).orElseThrow().attendees());
        assertEquals(1, service.waitlistForRoom("r-1").size());
    }

    @Test
    void cancellingKeepsTheHistory() {
        String id = bookAndGetId("r-1", "u-1", SLOT);
        service.modifyBooking(id, "u-1", Fixtures.interval("10:15", "11:15"), 4);
        service.cancelBooking(id, "u-1");
        assertEquals(1, service.findBooking(id).orElseThrow().history().size());
    }
}
