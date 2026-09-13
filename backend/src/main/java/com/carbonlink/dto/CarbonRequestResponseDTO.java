package com.carbonlink.dto;

import com.carbonlink.entity.RequestStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

public record CarbonRequestResponseDTO(
        Long id,

        // 1-based position among this request's OWN buyer's requests, in creation order.
        // Populated only for the owning buyer's own views; absent from the JSON for everyone
        // else (an emitter looking at an incoming request only ever sees the global id).
        // See ListingResponseDTO.personalListingNumber for the full reasoning.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer personalRequestNumber,
        Long buyerId,
        Double minVolumeNeeded,
        Double minPurityRequired,
        Double maxDistanceKm,
        Double maxBudgetPerTon,
        String intendedUse,
        RequestStatus status,
        LocalDateTime createdAt
) {
}
