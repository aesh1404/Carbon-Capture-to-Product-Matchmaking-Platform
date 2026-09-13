package com.carbonlink.dto;

import com.carbonlink.entity.DistanceSource;

public record QuickBrowseResultDTO(
        Long listingId,
        Long emitterId,
        Double totalVolumeTons,
        Double remainingVolumeTons,
        Double purityPercent,
        String captureMethod,
        Double pricePerTon,
        String city,
        String address,
        // Both null when the buyer searched without a city - there is no origin to measure
        // from, so the distance is genuinely unknown rather than zero.
        Double distanceKm,
        DistanceSource distanceSource
) {
}
