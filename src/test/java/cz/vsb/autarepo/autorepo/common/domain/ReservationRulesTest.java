package cz.vsb.autarepo.autorepo.common.domain;

import cz.vsb.autarepo.autorepo.feature.reservation.ReservationState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationRulesTest {
    private final ReservationRules rules = new ReservationRules();

    @Test
    void halfOpenIntervalDoesNotOverlapAdjacentSlots() {
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        Instant middle = Instant.parse("2026-01-01T12:00:00Z");
        Interval first = new Interval(start, middle);
        Interval second = new Interval(middle, middle.plus(2, ChronoUnit.HOURS));

        assertThat(first.overlaps(second)).isFalse();
        assertThat(rules.overlaps(first, second)).isFalse();
    }

    @Test
    void reservationWindowMustBeInFutureAndWithinMaximumDuration() {
        Instant now = Instant.parse("2026-01-01T09:00:00Z");
        Instant validStart = now.plus(3, ChronoUnit.HOURS);
        Instant validEnd = validStart.plus(2, ChronoUnit.HOURS);
        Instant tooSoonStart = now.plus(1, ChronoUnit.HOURS);

        assertThat(rules.isValidReservationWindow(validStart, validEnd, now)).isTrue();
        assertThat(rules.isValidReservationWindow(tooSoonStart, tooSoonStart.plus(1, ChronoUnit.HOURS), now)).isFalse();
    }

    @Test
    void blockingStatesAndExpirationAreDetected() {
        Instant now = Instant.parse("2026-01-01T08:00:00Z");
        Instant start = now.plus(1, ChronoUnit.DAYS);
        Instant deadline = now.plus(2, ChronoUnit.HOURS);

        assertThat(rules.isBlockingState(ReservationState.CONFIRMED)).isTrue();
        assertThat(rules.isBlockingState(ReservationState.PENDING_APPROVAL)).isTrue();
        assertThat(rules.shouldExpire(ReservationState.PENDING_APPROVAL, deadline, deadline, start)).isTrue();
        assertThat(rules.canCancel(ReservationState.DRAFT, now.plusSeconds(1), start)).isTrue();
    }
}
