package edu.cmu.cs214.campusreserve.core.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What came of a modification. A successful modification reports the booking
 * as it was, the booking as it is now, and the bookings that were promoted off
 * the waitlist into any time it freed.
 */
public sealed interface ModificationResult {

    /**
     * The modification went through. When nothing changed, {@code previous} and
     * {@code booking} are equal and {@code promotions} is empty.
     */
    record Modified(Booking previous, Booking booking, List<CancellationResult.Promotion> promotions)
        implements ModificationResult {
        public Modified {
            Objects.requireNonNull(previous, "previous");
            Objects.requireNonNull(booking, "booking");
            promotions = List.copyOf(promotions);
        }

        /** Did the modification change the interval or the attendee count? */
        public boolean changedAnything() {
            return !previous.equals(booking);
        }
    }

    /** The modification was refused. Nothing changed. */
    record Rejected(RejectionReason reason, String detail) implements ModificationResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
            detail = detail == null ? reason.message() : detail;
        }
    }

    /** Builds a rejection carrying the reason's own message. */
    static ModificationResult rejected(RejectionReason reason) {
        return new Rejected(reason, reason.message());
    }

    /** Did the modification go through? */
    default boolean isModified() {
        return this instanceof Modified;
    }

    /** The booking as it now stands, when the modification went through. */
    default Optional<Booking> modifiedBooking() {
        return this instanceof Modified modified ? Optional.of(modified.booking()) : Optional.empty();
    }

    /** Promotions triggered by the modification, empty when it was refused. */
    default List<CancellationResult.Promotion> promotions() {
        return this instanceof Modified modified ? modified.promotions() : List.of();
    }

    /** The reason, when the modification was refused. */
    default Optional<RejectionReason> rejectionReason() {
        return this instanceof Rejected rejected ? Optional.of(rejected.reason()) : Optional.empty();
    }
}
