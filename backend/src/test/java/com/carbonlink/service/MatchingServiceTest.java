package com.carbonlink.service;

import com.carbonlink.dto.DistanceResult;
import com.carbonlink.dto.MatchResult;
import com.carbonlink.entity.CarbonRequest;
import com.carbonlink.entity.DistanceSource;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import com.carbonlink.entity.RequestStatus;
import com.carbonlink.entity.Role;
import com.carbonlink.entity.User;
import com.carbonlink.exception.ResourceNotFoundException;
import com.carbonlink.repository.CarbonRequestRepository;
import com.carbonlink.repository.ListingRepository;
import com.carbonlink.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private CarbonRequestRepository requestRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DistanceService distanceService;

    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingService = new MatchingService(requestRepository, listingRepository, userRepository, distanceService);
    }

    // ---- pure scoring formula boundary tests ----

    @Test
    void scorePurity_exactBoundary_returns100() {
        assertThat(MatchingService.scorePurity(90.0, 90.0)).isEqualTo(100.0);
    }

    @Test
    void scorePurity_aboveBoundary_returns100() {
        assertThat(MatchingService.scorePurity(99.0, 90.0)).isEqualTo(100.0);
    }

    @Test
    void scorePurity_belowBoundary_returnsProportional() {
        assertThat(MatchingService.scorePurity(45.0, 90.0)).isEqualTo(50.0);
    }

    @Test
    void scoreVolume_exactBoundary_returns100() {
        assertThat(MatchingService.scoreVolume(100.0, 100.0)).isEqualTo(100.0);
    }

    @Test
    void scoreVolume_zeroListingVolume_returnsZero() {
        assertThat(MatchingService.scoreVolume(0.0, 100.0)).isEqualTo(0.0);
    }

    @Test
    void scoreVolume_zeroVolumeNeeded_alwaysSatisfied() {
        assertThat(MatchingService.scoreVolume(50.0, 0.0)).isEqualTo(100.0);
        assertThat(MatchingService.scoreVolume(0.0, 0.0)).isEqualTo(100.0);
    }

    @Test
    void scoreDistance_exactBoundary_returns100() {
        assertThat(MatchingService.scoreDistance(50.0, 50.0)).isEqualTo(100.0);
    }

    @Test
    void scoreDistance_beyondBoundary_decreasesLinearly() {
        assertThat(MatchingService.scoreDistance(60.0, 50.0)).isEqualTo(90.0);
    }

    @Test
    void scoreDistance_farBeyondBoundary_neverGoesNegative() {
        assertThat(MatchingService.scoreDistance(600.0, 50.0)).isEqualTo(0.0);
    }

    @Test
    void scorePrice_exactBoundary_returns100() {
        assertThat(MatchingService.scorePrice(1000.0, 1000.0)).isEqualTo(100.0);
    }

    @Test
    void scorePrice_aboveBoundary_decreasesTwiceAsFast() {
        assertThat(MatchingService.scorePrice(1010.0, 1000.0)).isEqualTo(80.0);
    }

    @Test
    void scorePrice_farAboveBoundary_neverGoesNegative() {
        assertThat(MatchingService.scorePrice(2000.0, 1000.0)).isEqualTo(0.0);
    }

    // ---- findMatches tests (mocked repos + distance service) ----

    @Test
    void findMatches_requestNotFound_throwsResourceNotFoundException() {
        when(requestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchingService.findMatches(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findMatches_noListingMeetsThreshold_returnsEmptyList() {
        CarbonRequest request = buyerRequest();
        User buyer = buyerUser();
        Listing poorListing = Listing.builder()
                .id(1L).emitterId(10L)
                .totalVolumeTons(1.0)
                .remainingVolumeTons(1.0)
                .purityPercent(10.0)
                .captureMethod("Direct Air Capture")
                .pricePerTon(5000.0)
                .locationLat(5.0).locationLng(5.0)
                .status(ListingStatus.ACTIVE)
                .build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findById(request.getBuyerId())).thenReturn(Optional.of(buyer));
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(poorListing));
        when(distanceService.getDistance(eq(5.0), eq(5.0), eq(0.0), eq(0.0)))
                .thenReturn(new DistanceResult(2000.0, DistanceSource.HAVERSINE_FALLBACK));

        List<MatchResult> matches = matchingService.findMatches(1L);

        assertThat(matches).isEmpty();
    }

    @Test
    void findMatches_ranksDescendingAndOnlyIncludesScoresAtOrAbove50() {
        CarbonRequest request = buyerRequest();
        User buyer = buyerUser();

        // Meets every criterion comfortably -> compatibilityScore should be 100
        Listing strongListing = Listing.builder()
                .id(1L).emitterId(10L)
                .totalVolumeTons(100.0)
                .remainingVolumeTons(100.0)
                .purityPercent(95.0)
                .captureMethod("Direct Air Capture")
                .pricePerTon(900.0)
                .locationLat(0.0).locationLng(0.0)
                .status(ListingStatus.ACTIVE)
                .build();

        // Meets purity/volume/price but is far outside maxDistanceKm -> distanceScore drags it down, still >= 50
        Listing distantListing = Listing.builder()
                .id(2L).emitterId(11L)
                .totalVolumeTons(100.0)
                .remainingVolumeTons(100.0)
                .purityPercent(91.0)
                .captureMethod("Direct Air Capture")
                .pricePerTon(950.0)
                .locationLat(1.0).locationLng(1.0)
                .status(ListingStatus.ACTIVE)
                .build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findById(request.getBuyerId())).thenReturn(Optional.of(buyer));
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(distantListing, strongListing));
        when(distanceService.getDistance(eq(0.0), eq(0.0), eq(0.0), eq(0.0)))
                .thenReturn(new DistanceResult(10.0, DistanceSource.HAVERSINE_FALLBACK));
        when(distanceService.getDistance(eq(1.0), eq(1.0), eq(0.0), eq(0.0)))
                .thenReturn(new DistanceResult(200.0, DistanceSource.HAVERSINE_FALLBACK));

        List<MatchResult> matches = matchingService.findMatches(1L);

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).listingId()).isEqualTo(1L);
        assertThat(matches.get(0).compatibilityScore()).isEqualTo(100.0);
        assertThat(matches.get(1).listingId()).isEqualTo(2L);
        assertThat(matches.get(1).compatibilityScore()).isLessThan(matches.get(0).compatibilityScore());
        assertThat(matches).allMatch(m -> m.compatibilityScore() >= MatchingService.MIN_COMPATIBILITY_SCORE);
    }

    @Test
    void findMatches_usesRemainingVolumeNotTotalVolume_forVolumeScoring() {
        CarbonRequest request = buyerRequest(); // minVolumeNeeded = 100.0
        User buyer = buyerUser();

        // Originally a 1000-ton listing, but 920 tons already sold off to other buyers -
        // only 80 remain, which is less than this request's 100-ton need.
        Listing partiallyDepletedListing = Listing.builder()
                .id(1L).emitterId(10L)
                .totalVolumeTons(1000.0)
                .remainingVolumeTons(80.0)
                .purityPercent(95.0)
                .captureMethod("Direct Air Capture")
                .pricePerTon(900.0)
                .locationLat(0.0).locationLng(0.0)
                .status(ListingStatus.ACTIVE)
                .build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findById(request.getBuyerId())).thenReturn(Optional.of(buyer));
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(partiallyDepletedListing));
        when(distanceService.getDistance(eq(0.0), eq(0.0), eq(0.0), eq(0.0)))
                .thenReturn(new DistanceResult(10.0, DistanceSource.HAVERSINE_FALLBACK));

        List<MatchResult> matches = matchingService.findMatches(1L);

        // Still appears (not excluded just because it can't fully satisfy the ask)...
        assertThat(matches).hasSize(1);
        // ...but volumeScore reflects the 80 remaining, not the 1000 total (80/100*100=80,
        // not 100 as it would be if totalVolumeTons were used instead).
        assertThat(matches.get(0).scoreBreakdown().volumeScore()).isEqualTo(80.0);
    }

    @Test
    void findMatches_remainingVolumeFarBelowNeed_combinesWithOtherWeaknessesToExclude() {
        CarbonRequest request = buyerRequest(); // minPurity=90, maxDistance=50, maxBudget=1000, minVolume=100
        User buyer = buyerUser();

        // Only 2 tons left of a listing that once had 1000, and it's also weak on purity/price/distance -
        // combined, this should fall below the 50 cutoff and be excluded entirely.
        Listing nearlyDepletedListing = Listing.builder()
                .id(1L).emitterId(10L)
                .totalVolumeTons(1000.0)
                .remainingVolumeTons(2.0)
                .purityPercent(45.0)
                .captureMethod("Direct Air Capture")
                .pricePerTon(2000.0)
                .locationLat(9.0).locationLng(9.0)
                .status(ListingStatus.ACTIVE)
                .build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findById(request.getBuyerId())).thenReturn(Optional.of(buyer));
        when(listingRepository.findByStatus(ListingStatus.ACTIVE)).thenReturn(List.of(nearlyDepletedListing));
        when(distanceService.getDistance(eq(9.0), eq(9.0), eq(0.0), eq(0.0)))
                .thenReturn(new DistanceResult(300.0, DistanceSource.HAVERSINE_FALLBACK));

        List<MatchResult> matches = matchingService.findMatches(1L);

        // purityScore=45/90*100=50, volumeScore=2/100*100=2, distanceScore=max(0,100-250)=0,
        // priceScore=max(0,100-2000)=0 -> compatibilityScore=50*.3+2*.3+0*.2+0*.2=15.6 < 50.
        assertThat(matches).isEmpty();
    }

    private CarbonRequest buyerRequest() {
        return CarbonRequest.builder()
                .id(1L)
                .buyerId(20L)
                .minVolumeNeeded(100.0)
                .minPurityRequired(90.0)
                .maxDistanceKm(50.0)
                .maxBudgetPerTon(1000.0)
                .intendedUse("Synthetic fuel")
                .status(RequestStatus.OPEN)
                .build();
    }

    private User buyerUser() {
        return User.builder()
                .id(20L)
                .name("Buyer Co Contact")
                .companyName("Buyer Co Pvt Ltd")
                .role(Role.BUYER)
                .locationLat(0.0)
                .locationLng(0.0)
                .build();
    }
}
