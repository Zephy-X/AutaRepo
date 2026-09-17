package cz.vsb.autarepo.autorepo.feature.vehicle.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VehicleForm(
    @NotBlank(message = "Brand is required")
    String brand,

    @NotBlank(message = "Model is required")
    String model,

    @Min(value = 1900, message = "Year must be 1900 or later")
    @Max(value = 2100, message = "Year must be 2100 or earlier")
    int year,

    @NotBlank(message = "VIN is required")
    @Size(min = 17, max = 17, message = "VIN must be exactly 17 characters")
    String vin,

    @NotBlank(message = "License plate is required")
    String licensePlate
) {
}
