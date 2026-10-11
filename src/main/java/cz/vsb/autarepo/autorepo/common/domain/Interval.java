package cz.vsb.autarepo.autorepo.common.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public record Interval(LocalDateTime start, LocalDateTime end) {
    public Interval {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Interval start must be strictly before end");
        }
    }

    public boolean overlaps(Interval other) {
        Objects.requireNonNull(other, "other");
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    public boolean contains(LocalDateTime instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(start) && instant.isBefore(end);
    }
}
