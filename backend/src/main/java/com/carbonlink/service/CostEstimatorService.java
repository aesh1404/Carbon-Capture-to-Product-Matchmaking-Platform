package com.carbonlink.service;

import com.carbonlink.dto.CostBreakdown;
import com.carbonlink.entity.DistanceSource;
import com.carbonlink.entity.Listing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CostEstimatorService {

    private final double ratePerKmPerTon;

    public CostEstimatorService(
            @Value("${carbonlink.transport.rate-per-km-per-ton:8.0}") double ratePerKmPerTon) {
        this.ratePerKmPerTon = ratePerKmPerTon;
    }

    public CostBreakdown estimateCost(Listing listing, double volumeTons, double distanceKm,
                                       DistanceSource distanceSource) {
        double basePrice = listing.getPricePerTon() * volumeTons;
        double transportCost = distanceKm * ratePerKmPerTon * volumeTons;
        double totalCost = basePrice + transportCost;

        return new CostBreakdown(
                round2(basePrice),
                round2(transportCost),
                round2(totalCost),
                round2(volumeTons),
                round2(distanceKm),
                distanceSource);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
