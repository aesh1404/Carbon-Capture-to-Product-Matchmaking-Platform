package com.carbonlink.service;

import com.carbonlink.dto.CostBreakdown;
import com.carbonlink.entity.DistanceSource;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CostEstimatorServiceTest {

    private static final double RATE_PER_KM_PER_TON = 8.0;

    private CostEstimatorService costEstimatorService;

    @BeforeEach
    void setUp() {
        costEstimatorService = new CostEstimatorService(RATE_PER_KM_PER_TON);
    }

    @Test
    void estimateCost_computesBaseTransportAndTotal() {
        Listing listing = listing(1000.0);

        CostBreakdown breakdown = costEstimatorService.estimateCost(
                listing, 50.0, 20.0, DistanceSource.HAVERSINE_FALLBACK);

        assertThat(breakdown.basePrice()).isEqualTo(50000.0);       // 1000 * 50
        assertThat(breakdown.transportCost()).isEqualTo(8000.0);    // 20 * 8.0 * 50
        assertThat(breakdown.totalCost()).isEqualTo(58000.0);
        assertThat(breakdown.distanceKm()).isEqualTo(20.0);
        assertThat(breakdown.distanceSource()).isEqualTo(DistanceSource.HAVERSINE_FALLBACK);
    }

    @Test
    void estimateCost_reportsTheVolumeItPricedNotTheListingSize() {
        // The listing holds 200 t; this quote is for 50 of them. Without the volume travelling
        // alongside the money, a client has nothing to show but the listing's size, and ₹50,000
        // next to "200t" reads as the price of the whole listing.
        Listing listing = listing(1000.0);

        CostBreakdown breakdown = costEstimatorService.estimateCost(
                listing, 50.0, 20.0, DistanceSource.HAVERSINE_FALLBACK);

        assertThat(breakdown.volumeTons()).isEqualTo(50.0);
        assertThat(listing.getTotalVolumeTons()).isEqualTo(200.0);
    }

    @Test
    void estimateCost_transportCost_matchesDistanceTimesRateTimesVolume_exactly() {
        // Regression guard for a reported 10x-too-high transport cost: 150 tons, 182.1 km,
        // rate ₹8/km/ton must come out to exactly 182.1 * 8 * 150 = 218520, not 2185200.
        Listing listing = listing(900.0);

        CostBreakdown breakdown = costEstimatorService.estimateCost(
                listing, 150.0, 182.1, DistanceSource.HAVERSINE_FALLBACK);

        assertThat(breakdown.transportCost()).isEqualTo(218520.0);
    }

    @Test
    void estimateCost_zeroDistance_transportCostIsZero() {
        Listing listing = listing(1000.0);

        CostBreakdown breakdown = costEstimatorService.estimateCost(
                listing, 50.0, 0.0, DistanceSource.OPENROUTESERVICE);

        assertThat(breakdown.transportCost()).isEqualTo(0.0);
        assertThat(breakdown.totalCost()).isEqualTo(breakdown.basePrice());
    }

    @Test
    void estimateCost_zeroVolume_allCostsAreZero() {
        Listing listing = listing(1000.0);

        CostBreakdown breakdown = costEstimatorService.estimateCost(
                listing, 0.0, 20.0, DistanceSource.HAVERSINE_FALLBACK);

        assertThat(breakdown.basePrice()).isEqualTo(0.0);
        assertThat(breakdown.transportCost()).isEqualTo(0.0);
        assertThat(breakdown.totalCost()).isEqualTo(0.0);
    }

    private Listing listing(double pricePerTon) {
        return Listing.builder()
                .id(1L).emitterId(10L)
                .totalVolumeTons(200.0)
                .remainingVolumeTons(200.0)
                .purityPercent(95.0)
                .captureMethod("Direct Air Capture")
                .pricePerTon(pricePerTon)
                .locationLat(0.0).locationLng(0.0)
                .status(ListingStatus.ACTIVE)
                .build();
    }
}
