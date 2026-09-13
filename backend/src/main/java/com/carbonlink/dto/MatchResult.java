package com.carbonlink.dto;

import com.carbonlink.entity.DistanceSource;

public record MatchResult(
        Long listingId,
        Long requestId,
        double compatibilityScore,
        double distanceKm,
        DistanceSource distanceSource,
        ScoreBreakdown scoreBreakdown
) {
}
