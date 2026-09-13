package com.carbonlink.service;

import com.carbonlink.dto.DistanceResult;
import com.carbonlink.dto.MatchResult;
import com.carbonlink.dto.ScoreBreakdown;
import com.carbonlink.entity.CarbonRequest;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import com.carbonlink.entity.User;
import com.carbonlink.exception.ResourceNotFoundException;
import com.carbonlink.repository.CarbonRequestRepository;
import com.carbonlink.repository.ListingRepository;
import com.carbonlink.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MatchingService {

    static final double MIN_COMPATIBILITY_SCORE = 50.0;

    private static final double WEIGHT_PURITY = 0.3;
    private static final double WEIGHT_VOLUME = 0.3;
    private static final double WEIGHT_DISTANCE = 0.2;
    private static final double WEIGHT_PRICE = 0.2;

    private final CarbonRequestRepository requestRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final DistanceService distanceService;

    public MatchingService(CarbonRequestRepository requestRepository,
                            ListingRepository listingRepository,
                            UserRepository userRepository,
                            DistanceService distanceService) {
        this.requestRepository = requestRepository;
        this.listingRepository = listingRepository;
        this.userRepository = userRepository;
        this.distanceService = distanceService;
    }

    @Transactional(readOnly = true)
    public List<MatchResult> findMatches(Long requestId) {
        CarbonRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id: " + requestId));

        User buyer = userRepository.findById(request.getBuyerId())
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found with id: " + request.getBuyerId()));

        List<Listing> activeListings = listingRepository.findByStatus(ListingStatus.ACTIVE);

        List<MatchResult> matches = new ArrayList<>();
        for (Listing listing : activeListings) {
            DistanceResult distanceResult = distanceService.getDistance(
                    listing.getLocationLat(), listing.getLocationLng(),
                    buyer.getLocationLat(), buyer.getLocationLng());

            ScoreBreakdown breakdown = scoreListing(listing, request, distanceResult.distanceKm());
            double compatibilityScore = weightedScore(breakdown);

            if (compatibilityScore >= MIN_COMPATIBILITY_SCORE) {
                matches.add(new MatchResult(
                        listing.getId(),
                        request.getId(),
                        round2(compatibilityScore),
                        round2(distanceResult.distanceKm()),
                        distanceResult.source(),
                        breakdown));
            }
        }

        matches.sort(Comparator.comparingDouble(MatchResult::compatibilityScore).reversed());
        return matches;
    }

    static ScoreBreakdown scoreListing(Listing listing, CarbonRequest request, double distanceKm) {
        double purityScore = scorePurity(listing.getPurityPercent(), request.getMinPurityRequired());
        double volumeScore = scoreVolume(listing.getRemainingVolumeTons(), request.getMinVolumeNeeded());
        double distanceScore = scoreDistance(distanceKm, request.getMaxDistanceKm());
        double priceScore = scorePrice(listing.getPricePerTon(), request.getMaxBudgetPerTon());
        return new ScoreBreakdown(round2(purityScore), round2(volumeScore), round2(distanceScore), round2(priceScore));
    }

    static double weightedScore(ScoreBreakdown breakdown) {
        return (breakdown.purityScore() * WEIGHT_PURITY)
                + (breakdown.volumeScore() * WEIGHT_VOLUME)
                + (breakdown.distanceScore() * WEIGHT_DISTANCE)
                + (breakdown.priceScore() * WEIGHT_PRICE);
    }

    static double scorePurity(double purityPercent, double minPurityRequired) {
        if (purityPercent >= minPurityRequired) {
            return 100.0;
        }
        return (purityPercent / minPurityRequired) * 100.0;
    }

    static double scoreVolume(double remainingVolumeTons, double minVolumeNeeded) {
        if (remainingVolumeTons >= minVolumeNeeded) {
            return 100.0;
        }
        return (remainingVolumeTons / minVolumeNeeded) * 100.0;
    }

    static double scoreDistance(double distanceKm, double maxDistanceKm) {
        if (distanceKm <= maxDistanceKm) {
            return 100.0;
        }
        return Math.max(0.0, 100.0 - (distanceKm - maxDistanceKm));
    }

    static double scorePrice(double pricePerTon, double maxBudgetPerTon) {
        if (pricePerTon <= maxBudgetPerTon) {
            return 100.0;
        }
        return Math.max(0.0, 100.0 - (pricePerTon - maxBudgetPerTon) * 2);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
