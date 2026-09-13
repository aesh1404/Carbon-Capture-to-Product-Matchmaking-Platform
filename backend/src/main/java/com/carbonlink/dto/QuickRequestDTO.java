package com.carbonlink.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// "Request This Listing" from Quick Browse - the buyer only supplies who they are and how much
// they need; permissive defaults for purity/distance/budget are filled in server-side (see
// MatchService.quickRequest), since the buyer already picked this exact listing deliberately.
public record QuickRequestDTO(
        @NotNull(message = "buyerId is required") Long buyerId,

        @NotNull(message = "minVolumeNeeded is required")
        @Positive(message = "minVolumeNeeded must be greater than 0")
        Double minVolumeNeeded
) {
}
