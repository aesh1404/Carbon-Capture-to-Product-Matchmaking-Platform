package com.carbonlink.dto;

import com.carbonlink.entity.DistanceSource;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.carbonlink.entity.MatchStatus;

import java.time.LocalDateTime;

public record MatchResponseDTO(
        Long id,
        Long listingId,
        Long requestId,

        // At most ONE of these is ever populated, decided by which side is looking:
        //   - an emitter's view carries personalListingNumber (their listing) and omits the
        //     buyer's request number entirely
        //   - a buyer's view carries personalRequestNumber (their request) and omits the
        //     emitter's listing number entirely
        // The counterparty's entity is always identified by its global id, which is the only
        // id the two sides can meaningfully quote at each other. Match.id stays global for the
        // same reason - it is the shared reference.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer personalListingNumber,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer personalRequestNumber,
        Double compatibilityScore,
        Double distanceKm,
        DistanceSource distanceSource,
        MatchStatus status,
        ScoreBreakdown scoreBreakdown,
        CostBreakdown costBreakdown,
        Double transactedVolumeTons,
        LocalDateTime createdAt,
        String statusMessage,
        boolean viewed,
        String emitterCity,
        String buyerCity,
        Long orderId
) {
}
