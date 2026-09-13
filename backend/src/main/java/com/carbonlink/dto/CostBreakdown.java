package com.carbonlink.dto;

import com.carbonlink.entity.DistanceSource;

/**
 * A quote. {@code volumeTons} and {@code distanceKm} are the two inputs every figure below is
 * derived from, and they travel with the money deliberately: a client showing ₹74,000 next to a
 * listing's 2,500 t supply reads as a price for 2,500 t, when it is really the price for the
 * volume quoted here. Before a match is accepted that is the buyer's ask; afterwards it is the
 * volume actually transacted.
 */
public record CostBreakdown(
        double basePrice,
        double transportCost,
        double totalCost,
        double volumeTons,
        double distanceKm,
        DistanceSource distanceSource
) {
}
