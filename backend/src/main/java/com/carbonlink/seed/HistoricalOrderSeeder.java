package com.carbonlink.seed;

import com.carbonlink.constants.CityCoordinates;
import com.carbonlink.entity.CarbonRequest;
import com.carbonlink.entity.DeliveryStatus;
import com.carbonlink.entity.DistanceSource;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import com.carbonlink.entity.Match;
import com.carbonlink.entity.MatchStatus;
import com.carbonlink.entity.Order;
import com.carbonlink.entity.OrderStatus;
import com.carbonlink.entity.PaymentStatus;
import com.carbonlink.entity.RequestStatus;
import com.carbonlink.entity.User;
import com.carbonlink.repository.CarbonRequestRepository;
import com.carbonlink.repository.ListingRepository;
import com.carbonlink.repository.MatchRepository;
import com.carbonlink.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds the trading history behind the Impact Dashboard: ~30 settled orders spread over the
 * last five calendar months.
 *
 * <p><b>The shape is deliberately uneven.</b> A clean upward line reads as fabricated, so the
 * months go strong → quiet → spike → dip → strongest. The period still trends up overall, but
 * it looks like a business rather than a growth chart someone drew.
 *
 * <p><b>Stock isolation.</b> Historical orders draw exclusively from their own archive of
 * already-consumed listings, never from the fresh listings the live demo clicks on. Nothing
 * seeded here reduces the remaining volume of anything a presenter will touch.
 *
 * <p>Rows are written straight through the repositories rather than driven through the
 * request → accept API flow, because that flow would stamp everything with today's date and
 * deduct from live stock. Each order still gets a complete, self-consistent chain behind it
 * (listing → request → accepted match → confirmed order) so receipts and order history open
 * correctly.
 *
 * <p>All randomness is seeded from a fixed value: the demo shows identical numbers every run.
 */
@Component
public class HistoricalOrderSeeder {

    private static final Logger log = LoggerFactory.getLogger(HistoricalOrderSeeder.class);

    // monthsAgo, orderCount, minTons, maxTons - the deliberate up/down/up shape.
    private static final int[][] MONTH_PLAN = {
            {4, 6, 100, 250},   // a solid opening month
            {3, 4,  50, 150},   // quiet: fewer, smaller deals
            {2, 9, 150, 350},   // rebound spike
            {1, 5, 100, 300},   // dips again - growth isn't linear
            {0, 6, 200, 450},   // strongest month, so the latest point lands healthy
    };

    // Flat per-order haulage estimates. Historical rows never call the routing API - there is
    // no value in spending real network calls to geocode invented history - so each order gets
    // a plausible route length and is honestly labelled as an estimate.
    private static final double MIN_ESTIMATED_KM = 180.0;
    private static final double MAX_ESTIMATED_KM = 1450.0;
    private static final double TRANSPORT_RATE_PER_KM_PER_TON = 1.0;

    private static final String[] ARCHIVE_CAPTURE_METHODS = {
            "Post-combustion", "Pre-combustion", "Oxy-fuel", "Direct Air Capture",
    };
    private static final String[] ARCHIVE_INTENDED_USES = {
            "Fuel Synthesis", "Building Materials", "Greenhouse", "Bioplastics",
            "Beverage Carbonation", "Algae Farming", "Concrete Curing",
    };

    private final ListingRepository listingRepository;
    private final CarbonRequestRepository carbonRequestRepository;
    private final MatchRepository matchRepository;
    private final OrderRepository orderRepository;

    public HistoricalOrderSeeder(ListingRepository listingRepository,
                                  CarbonRequestRepository carbonRequestRepository,
                                  MatchRepository matchRepository,
                                  OrderRepository orderRepository) {
        this.listingRepository = listingRepository;
        this.carbonRequestRepository = carbonRequestRepository;
        this.matchRepository = matchRepository;
        this.orderRepository = orderRepository;
    }

    public int seed(List<User> emitters, List<User> buyers) {
        Random rng = new Random(20260912L);
        List<Listing> archive = createArchiveListings(emitters, rng);

        Map<Integer, Integer> sequenceByYear = new HashMap<>();
        int created = 0;

        for (int[] month : MONTH_PLAN) {
            int monthsAgo = month[0];
            int orderCount = month[1];

            for (int k = 0; k < orderCount; k++) {
                // Rotating the two sides at different rates keeps pairings varied - no month
                // is just the same emitter selling to the same buyer over and over.
                Listing listing = archive.get(created % archive.size());
                User buyer = buyers.get((created * 3 + monthsAgo) % buyers.size());

                double tons = roundTo(month[2] + rng.nextDouble() * (month[3] - month[2]), 1);
                LocalDateTime placedAt = timestampWithin(monthsAgo, k, orderCount, rng);

                CarbonRequest request = saveHistoricalRequest(buyer, tons, listing, placedAt, rng);
                Match match = saveHistoricalMatch(listing, request, tons, placedAt, rng);
                saveHistoricalOrder(listing, request, match, tons, placedAt, sequenceByYear, rng);

                // Consume from the ARCHIVE listing only - live demo stock is never touched.
                listing.setRemainingVolumeTons(roundTo(
                        Math.max(0.0, listing.getRemainingVolumeTons() - tons), 2));
                created++;
            }
        }

        // Close out the archive: these represent supply that has already been sold through.
        archive.forEach(listing -> {
            listing.setStatus(ListingStatus.CLOSED);
            listingRepository.save(listing);
        });

        log.info("Historical seed: {} settled orders across {} months, drawn from {} archived listings",
                created, MONTH_PLAN.length, archive.size());
        return created;
    }

    // One archived listing per emitter, sized to cover everything drawn from it. These are
    // CLOSED, so they never appear in marketplace browse, Quick Browse or live matching.
    private List<Listing> createArchiveListings(List<User> emitters, Random rng) {
        List<Listing> archive = new ArrayList<>();
        LocalDateTime listedAt = LocalDate.now().minusMonths(5).atTime(9, 0);

        for (int i = 0; i < emitters.size(); i++) {
            User emitter = emitters.get(i);
            CityCoordinates.LatLng coordinates = CityCoordinates.lookup(emitter.getCity())
                    .orElseThrow(() -> new IllegalStateException(
                            "Historical seed uses city '" + emitter.getCity() + "', not in CityCoordinates"));
            double volume = 2500 + rng.nextInt(2000);

            archive.add(listingRepository.save(Listing.builder()
                    .emitterId(emitter.getId())
                    .totalVolumeTons(volume)
                    .remainingVolumeTons(volume)
                    .purityPercent(roundTo(86 + rng.nextDouble() * 13, 1))
                    .captureMethod(ARCHIVE_CAPTURE_METHODS[i % ARCHIVE_CAPTURE_METHODS.length])
                    .pricePerTon(roundTo(760 + rng.nextInt(1500), 0))
                    .city(emitter.getCity())
                    .address(null)
                    .locationLat(coordinates.lat())
                    .locationLng(coordinates.lng())
                    .status(ListingStatus.ACTIVE)
                    .createdAt(listedAt.plusDays(i))
                    .build()));
        }
        return archive;
    }

    private CarbonRequest saveHistoricalRequest(User buyer, double tons, Listing listing,
                                                 LocalDateTime placedAt, Random rng) {
        return carbonRequestRepository.save(CarbonRequest.builder()
                .buyerId(buyer.getId())
                .minVolumeNeeded(tons)
                // Thresholds that this listing comfortably satisfies - these requests were
                // fulfilled, so they should read as though they were always going to be.
                .minPurityRequired(roundTo(Math.max(80.0, listing.getPurityPercent() - 4), 1))
                .maxDistanceKm(3000.0)
                .maxBudgetPerTon(roundTo(listing.getPricePerTon() + 200, 0))
                .intendedUse(ARCHIVE_INTENDED_USES[rng.nextInt(ARCHIVE_INTENDED_USES.length)])
                .status(RequestStatus.MATCHED)
                .createdAt(placedAt.minusDays(2))
                .build());
    }

    private Match saveHistoricalMatch(Listing listing, CarbonRequest request, double tons,
                                       LocalDateTime placedAt, Random rng) {
        double distanceKm = estimatedDistanceKm(rng);
        return matchRepository.save(Match.builder()
                .listingId(listing.getId())
                .requestId(request.getId())
                .compatibilityScore(roundTo(78 + rng.nextDouble() * 22, 2))
                .distanceKm(distanceKm)
                .distanceSource(DistanceSource.HAVERSINE_FALLBACK)
                .transportCost(roundTo(distanceKm * TRANSPORT_RATE_PER_KM_PER_TON * tons, 2))
                .totalEstimatedCost(roundTo(listing.getPricePerTon() * tons
                        + distanceKm * TRANSPORT_RATE_PER_KM_PER_TON * tons, 2))
                .transactedVolumeTons(tons)
                .status(MatchStatus.ACCEPTED)
                .viewed(true)
                .createdAt(placedAt.minusDays(1))
                .build());
    }

    private void saveHistoricalOrder(Listing listing, CarbonRequest request, Match match, double tons,
                                      LocalDateTime placedAt, Map<Integer, Integer> sequenceByYear,
                                      Random rng) {
        double pricePerTon = listing.getPricePerTon();
        double basePrice = roundTo(pricePerTon * tons, 2);
        double distanceKm = match.getDistanceKm();
        double transportCost = roundTo(distanceKm * TRANSPORT_RATE_PER_KM_PER_TON * tons, 2);

        orderRepository.save(Order.builder()
                .orderNumber(nextOrderNumber(placedAt.getYear(), sequenceByYear))
                .matchId(match.getId())
                .emitterId(listing.getEmitterId())
                .buyerId(request.getBuyerId())
                .listingId(listing.getId())
                .requestId(request.getId())
                .transactedVolumeTons(tons)
                .pricePerTon(pricePerTon)
                .basePrice(basePrice)
                .transportCost(transportCost)
                .totalCost(roundTo(basePrice + transportCost, 2))
                .distanceKm(distanceKm)
                .distanceSource(DistanceSource.HAVERSINE_FALLBACK)
                .status(OrderStatus.CONFIRMED)
                // Historical business is finished business: settled and handed over. These must
                // be PAID - an order reading DELIVERED while payment is outstanding would
                // contradict the rule the live flow now enforces.
                .paymentStatus(PaymentStatus.PAID)
                .deliveryStatus(DeliveryStatus.DELIVERED)
                .createdAt(placedAt)
                .build());
    }

    // Matches OrderNumberGenerator's format. Numbering from 1 within each year means the
    // generator - which re-seeds itself from the persisted count per year - carries straight on
    // from here for anything created live, with no collision.
    private String nextOrderNumber(int year, Map<Integer, Integer> sequenceByYear) {
        int next = sequenceByYear.merge(year, 1, Integer::sum);
        return "CL-" + year + "-" + String.format("%04d", next);
    }

    // Spreads a month's orders across its days instead of stacking them on one date. The
    // current month only runs up to today - a demo must never show orders dated in the future.
    private LocalDateTime timestampWithin(int monthsAgo, int index, int count, Random rng) {
        LocalDate monthStart = LocalDate.now().minusMonths(monthsAgo).withDayOfMonth(1);
        int lastSelectableDay = monthsAgo == 0
                ? Math.max(1, LocalDate.now().getDayOfMonth())
                : monthStart.lengthOfMonth();

        int span = Math.max(1, lastSelectableDay - 1);
        int day = Math.min(lastSelectableDay, 1 + (index * span) / Math.max(1, count));
        return monthStart.withDayOfMonth(day).atTime(9 + rng.nextInt(9), rng.nextInt(60));
    }

    private double estimatedDistanceKm(Random rng) {
        return roundTo(MIN_ESTIMATED_KM + rng.nextDouble() * (MAX_ESTIMATED_KM - MIN_ESTIMATED_KM), 2);
    }

    private static double roundTo(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }
}
