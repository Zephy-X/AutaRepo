package cz.vsb.autarepo.autorepo.common.domain;

import cz.vsb.autarepo.autorepo.feature.reservation.ReservationState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationRulesTest {
    private final ReservationRules rules = new ReservationRules();

    @Test
    void halfOpenIntervalDoesNotOverlapAdjacentSlots() {
        LocalDateTime start = LocalDateTime.parse("2026-01-01T10:00:00");
        LocalDateTime middle = LocalDateTime.parse("2026-01-01T12:00:00");
        Interval first = new Interval(start, middle);
        Interval second = new Interval(middle, middle.plus(2, ChronoUnit.HOURS));

        assertThat(first.overlaps(second)).isFalse();
        assertThat(rules.overlaps(first, second)).isFalse();
    }

    @Test
    void reservationWindowMustBeInFutureAndWithinMaximumDuration() {
        LocalDateTime now = LocalDateTime.parse("2026-01-01T09:00:00");
        LocalDateTime validStart = now.plus(3, ChronoUnit.HOURS);
        LocalDateTime validEnd = validStart.plus(2, ChronoUnit.HOURS);
        LocalDateTime tooSoonStart = now.plus(1, ChronoUnit.HOURS);

        assertThat(rules.isValidReservationWindow(validStart, validEnd, now)).isTrue();
        assertThat(rules.isValidReservationWindow(tooSoonStart, tooSoonStart.plus(1, ChronoUnit.HOURS), now)).isFalse();
    }

    @Test
    void blockingStatesAndExpirationAreDetected() {
        LocalDateTime now = LocalDateTime.parse("2026-01-01T08:00:00");
        LocalDateTime start = now.plus(1, ChronoUnit.DAYS);
        LocalDateTime deadline = now.plus(2, ChronoUnit.HOURS);

        assertThat(rules.isBlockingState(ReservationState.CONFIRMED)).isTrue();
        assertThat(rules.isBlockingState(ReservationState.PENDING_APPROVAL)).isTrue();
        assertThat(rules.shouldExpire(ReservationState.PENDING_APPROVAL, deadline, deadline, start)).isTrue();
        assertThat(ReservationRules.canCancel(ReservationState.DRAFT, now.plusSeconds(1), start)).isTrue();
    }
}
