package cz.vsb.autarepo.autorepo.feature.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReservationForm(
        @NotNull(message = "Please select a vehicle")
        Long vehicleId,

        @NotNull(message = "Please select a user")
        Long userId,

        @NotNull(message = "Start date is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,

        @NotNull(message = "Start hour is required")
        @Min(value = 0, message = "Hour must be between 0 and 23")
        @Max(value = 23, message = "Hour must be between 0 and 23")
        Integer startHour,

        @NotNull(message = "End date is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate,

        @NotNull(message = "End hour is required")
        @Min(value = 0, message = "Hour must be between 0 and 23")
        @Max(value = 23, message = "Hour must be between 0 and 23")
        Integer endHour
) {
    public LocalDateTime toStartDateTime() {
        if (startDate == null || startHour == null) {
            return null;
        }
        return startDate.atTime(startHour, 0);
    }

    public LocalDateTime toEndDateTime() {
        if (endDate == null || endHour == null) {
            return null;
        }
        return endDate.atTime(endHour, 0);
    }
}