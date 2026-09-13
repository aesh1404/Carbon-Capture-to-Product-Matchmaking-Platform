package com.carbonlink.dto;

public record ScoreBreakdown(
        double purityScore,
        double volumeScore,
        double distanceScore,
        double priceScore
) {
}
