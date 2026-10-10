package cz.vsb.autarepo.autorepo.common.domain;

import cz.vsb.autarepo.autorepo.feature.reservation.ReservationState;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Component
public class ReservationRules {
    public static final Duration MIN_LEAD_TIME = Duration.ofHours(2);
    public static final Duration MAX_DURATION = Duration.ofDays(14);
    public static final Duration REVIEW_SLA = Duration.ofHours(24);

    private static final Set<ReservationState> BLOCKING_STATES = EnumSet.of(
            ReservationState.CONFIRMED,
            ReservationState.PENDING_APPROVAL
    );

    private static final Set<ReservationState> CANCELLABLE_STATES = EnumSet.of(
            ReservationState.DRAFT,
            ReservationState.PENDING_APPROVAL,
            ReservationState.CONFIRMED
    );

    public boolean isValidInterval(Instant start, Instant end) {
        return start != null && end != null && start.isBefore(end);
    }

    public boolean isValidReservationWindow(Instant start, Instant end, Instant now) {
        if (!isValidInterval(start, end)) {
            return false;
        }
        if (!start.isAfter(now)) {
            return false;
        }
        if (start.isBefore(now.plus(MIN_LEAD_TIME))) {
            return false;
        }
        return Duration.between(start, end).compareTo(MAX_DURATION) <= 0;
    }

    public boolean overlaps(Interval left, Interval right) {
        return left != null && right != null && left.overlaps(right);
    }

    public boolean overlaps(Instant leftStart, Instant leftEnd, Instant rightStart, Instant rightEnd) {
        return overlaps(new Interval(leftStart, leftEnd), new Interval(rightStart, rightEnd));
    }

    public boolean isBlockingState(ReservationState state) {
        return state != null && BLOCKING_STATES.contains(state);
    }

    public boolean blocksAvailability(ReservationState state) {
        return isBlockingState(state);
    }

    public boolean canCancel(ReservationState state, Instant now, Instant startTime) {
        return CANCELLABLE_STATES.contains(state) && now != null && startTime != null && now.isBefore(startTime);
    }

    public boolean shouldExpire(ReservationState state, Instant now, Instant reviewDeadline, Instant startTime) {
        if (state != ReservationState.PENDING_APPROVAL) {
            return false;
        }
        if (now == null) {
            return false;
        }
        if (reviewDeadline != null && !now.isBefore(reviewDeadline)) {
            return true;
        }
        return startTime != null && !now.isBefore(startTime);
    }

    public Instant calculateReviewDeadline(Instant submittedAt, Instant startTime) {
        if (submittedAt == null || startTime == null) {
            return startTime;
        }
        Instant reviewWindowDeadline = submittedAt.plus(REVIEW_SLA);
        return reviewWindowDeadline.compareTo(startTime) <= 0 ? reviewWindowDeadline : startTime;
    }

    public Instant evaluateReviewDeadline(Instant submittedAt, Instant startTime) {
        return calculateReviewDeadline(submittedAt, startTime);
    }
}
