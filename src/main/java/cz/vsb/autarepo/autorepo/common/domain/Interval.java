package cz.vsb.autarepo.autorepo.common.domain;

import java.time.Instant;
import java.util.Objects;

public record Interval(Instant start, Instant end) {
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

    public boolean contains(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(start) && instant.isBefore(end);
    }
}
