package cz.vsb.autarepo.autorepo.common.domain;

import cz.vsb.autarepo.autorepo.feature.reservation.ReservationState;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
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

    public void validateReservationWindow(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (!isValidInterval(start, end)) {
            throw new IllegalArgumentException("INVALID_INTERVAL");
        }
        if (start.isBefore(now.plus(MIN_LEAD_TIME))) {
            throw new IllegalArgumentException("INSUFFICIENT_LEAD_TIME");
        }
        if (Duration.between(start, end).compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("MAXIMUM_DURATION_EXCEEDED");
        }
    }

    public boolean isValidInterval(LocalDateTime start, LocalDateTime end) {
        return start != null && end != null && start.isBefore(end);
    }

    public boolean isValidReservationWindow(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        try {
            validateReservationWindow(start, end, now);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean overlaps(Interval left, Interval right) {
        return left != null && right != null && left.overlaps(right);
    }

    public boolean overlaps(LocalDateTime leftStart, LocalDateTime leftEnd, LocalDateTime rightStart, LocalDateTime rightEnd) {
        return overlaps(new Interval(leftStart, leftEnd), new Interval(rightStart, rightEnd));
    }

    public static boolean isBlocking(ReservationState state) {
        return state != null && BLOCKING_STATES.contains(state);
    }

    public boolean isBlockingState(ReservationState state) {
        return isBlocking(state);
    }

    public boolean blocksAvailability(ReservationState state) {
        return isBlocking(state);
    }

    public static boolean canCancel(ReservationState state, LocalDateTime now, LocalDateTime startTime) {
        return state != null
                && state != ReservationState.CANCELLED
                && CANCELLABLE_STATES.contains(state)
                && now != null
                && startTime != null
                && now.isBefore(startTime);
    }

    public boolean canCancelState(ReservationState state, LocalDateTime now, LocalDateTime startTime) {
        return canCancel(state, now, startTime);
    }

    public boolean shouldExpire(ReservationState state, LocalDateTime now, LocalDateTime reviewDeadline, LocalDateTime startTime) {
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

    public static LocalDateTime calculateReviewDeadline(LocalDateTime submittedAt, LocalDateTime startTime) {
        if (submittedAt == null || startTime == null) {
            return startTime;
        }
        LocalDateTime reviewWindowDeadline = submittedAt.plus(REVIEW_SLA);
        return reviewWindowDeadline.compareTo(startTime) <= 0 ? reviewWindowDeadline : startTime;
    }

    public LocalDateTime evaluateReviewDeadline(LocalDateTime submittedAt, LocalDateTime startTime) {
        return calculateReviewDeadline(submittedAt, startTime);
    }
}
