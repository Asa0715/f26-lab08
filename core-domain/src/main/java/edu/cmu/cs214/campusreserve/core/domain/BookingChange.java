package edu.cmu.cs214.campusreserve.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One entry in a booking's modification history: what the booking was, what it
 * became, and when the owner changed it.
 */
public record BookingChange(
    TimeInterval previousInterval,
    int previousAttendees,
    TimeInterval newInterval,
    int newAttendees,
    Instant changedAt) {

    public BookingChange {
        Objects.requireNonNull(previousInterval, "previousInterval");
        Objects.requireNonNull(newInterval, "newInterval");
        Objects.requireNonNull(changedAt, "changedAt");
        if (previousAttendees <= 0 || newAttendees <= 0) {
            throw new IllegalArgumentException(
                "Attendees must be positive: " + previousAttendees + " to " + newAttendees);
        }
    }

    /** Did this change move or resize the interval? */
    public boolean changedInterval() {
        return !previousInterval.equals(newInterval);
    }
}
