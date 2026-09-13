package com.carbonlink.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ListingRequestDTO(
        @NotNull(message = "emitterId is required") Long emitterId,

        @NotNull(message = "totalVolumeTons is required")
        @Positive(message = "totalVolumeTons must be greater than 0")
        Double totalVolumeTons,

        @NotNull(message = "purityPercent is required")
        @DecimalMin(value = "0.0", message = "purityPercent must be between 0 and 100")
        @DecimalMax(value = "100.0", message = "purityPercent must be between 0 and 100")
        Double purityPercent,

        @NotBlank(message = "captureMethod is required") String captureMethod,

        @NotNull(message = "pricePerTon is required")
        @Positive(message = "pricePerTon must be greater than 0")
        Double pricePerTon,

        @NotBlank(message = "city is required") String city,

        // Optional free-text display address - never used for distance/coordinate calculations.
        String address
) {
}
