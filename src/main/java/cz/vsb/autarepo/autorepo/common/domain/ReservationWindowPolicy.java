package cz.vsb.autarepo.autorepo.common.domain;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ReservationWindowPolicy {
    private final ReservationRules rules;

    public ReservationWindowPolicy(ReservationRules rules) {
        this.rules = rules;
    }

    public void validate(LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now) {
        if (startTime == null || endTime == null || now == null) {
            throw new IllegalArgumentException("INVALID_INTERVAL");
        }
        var startInstant = startTime.toInstant(java.time.ZoneOffset.UTC);
        var endInstant = endTime.toInstant(java.time.ZoneOffset.UTC);
        var nowInstant = now.toInstant(java.time.ZoneOffset.UTC);

        if (!rules.isValidReservationWindow(startInstant, endInstant, nowInstant)) {
            if (!rules.isValidInterval(startInstant, endInstant)) {
                throw new IllegalArgumentException("INVALID_INTERVAL");
            }
            if (startTime.isBefore(now.plus(ReservationRules.MIN_LEAD_TIME))) {
                throw new IllegalArgumentException("INSUFFICIENT_LEAD_TIME");
            }
            if (java.time.Duration.between(startTime, endTime).compareTo(ReservationRules.MAX_DURATION) > 0) {
                throw new IllegalArgumentException("MAXIMUM_DURATION_EXCEEDED");
            }
            throw new IllegalArgumentException("INVALID_INTERVAL");
        }
    }
}
