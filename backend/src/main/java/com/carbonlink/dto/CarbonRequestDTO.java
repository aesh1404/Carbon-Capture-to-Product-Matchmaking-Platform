package com.carbonlink.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CarbonRequestDTO(
        @NotNull(message = "buyerId is required") Long buyerId,

        @NotNull(message = "minVolumeNeeded is required")
        @Positive(message = "minVolumeNeeded must be greater than 0")
        Double minVolumeNeeded,

        @NotNull(message = "minPurityRequired is required")
        @DecimalMin(value = "0.0", message = "minPurityRequired must be between 0 and 100")
        @DecimalMax(value = "100.0", message = "minPurityRequired must be between 0 and 100")
        Double minPurityRequired,

        @NotNull(message = "maxDistanceKm is required")
        @Positive(message = "maxDistanceKm must be greater than 0")
        Double maxDistanceKm,

        @NotNull(message = "maxBudgetPerTon is required")
        @Positive(message = "maxBudgetPerTon must be greater than 0")
        Double maxBudgetPerTon,

        @NotBlank(message = "intendedUse is required") String intendedUse
) {
}
