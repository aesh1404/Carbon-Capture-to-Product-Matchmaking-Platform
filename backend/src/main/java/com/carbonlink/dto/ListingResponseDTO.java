package com.carbonlink.dto;

import com.carbonlink.entity.ListingStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

// locationLat/locationLng are deliberately NOT exposed: they're an internal derivation of
// `city` used only for distance scoring, and every client renders the city name instead.
// Distances reach the client pre-computed (MatchResponseDTO.distanceKm,
// QuickBrowseResultDTO.distanceKm), so raw coordinates have no consumer.
public record ListingResponseDTO(
        Long id,

        // This listing's 1-based position among its OWN emitter's listings, in creation order.
        //
        // Populated ONLY when the caller is scoped to the owning emitter (their own "My
        // Listings" view, or the response to creating a listing). In every buyer-facing
        // response it is null and, thanks to NON_NULL, absent from the JSON entirely - not
        // merely hidden by the client. That matters because the number is meaningless outside
        // one emitter's set: two emitters both have a "Listing #1", so exposing it across the
        // marketplace would produce duplicate-looking ids a buyer could mistake for a real one.
        //
        // Cosmetic label only. `id` above is the sole identifier used for matching, linking
        // orders, lookups - everything.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer personalListingNumber,
        Long emitterId,
        Double totalVolumeTons,
        Double remainingVolumeTons,
        Double purityPercent,
        String captureMethod,
        Double pricePerTon,
        String city,
        String address,
        ListingStatus status,
        LocalDateTime createdAt
) {
}
