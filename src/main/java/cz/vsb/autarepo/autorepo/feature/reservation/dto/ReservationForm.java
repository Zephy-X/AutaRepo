package cz.vsb.autarepo.autorepo.feature.reservation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ReservationForm(
    @NotNull(message = "Vehicle is required")
    Long vehicleId,

    @NotNull(message = "User is required")
    Long userId,

    @NotNull(message = "Start time is required")
    LocalDateTime startTime,

    @NotNull(message = "End time is required")
    LocalDateTime endTime
) {
}
