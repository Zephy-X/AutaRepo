package cz.vsb.autarepo.autorepo.feature.reservation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ReservationForm(
    @NotNull(message = "Please select an option")
    Long vehicleId,

    @NotNull(message = "Please select an option")
    Long userId,

    @NotNull(message = "Start time is required")
    LocalDateTime startTime,

    @NotNull(message = "End time is required")
    LocalDateTime endTime
) {
}
