package com.carbonlink.dto;

import com.carbonlink.entity.DistanceSource;

public record DistanceResult(double distanceKm, DistanceSource source) {
}
