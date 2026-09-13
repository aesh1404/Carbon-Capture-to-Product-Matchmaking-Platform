package com.carbonlink.service;

import com.carbonlink.dto.CostBreakdown;
import com.carbonlink.dto.DistanceResult;
import com.carbonlink.dto.MatchResponseDTO;
import com.carbonlink.dto.MatchResult;
import com.carbonlink.dto.QuickRequestDTO;
import com.carbonlink.dto.ScoreBreakdown;
import com.carbonlink.dto.UnviewedCountDTO;
import com.carbonlink.entity.CarbonRequest;
import com.carbonlink.entity.DeliveryStatus;
import com.carbonlink.entity.DistanceSource;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import com.carbonlink.entity.Match;
import com.carbonlink.entity.MatchStatus;
import com.carbonlink.entity.RequestStatus;
import com.carbonlink.entity.Role;
import com.carbonlink.entity.User;
import com.carbonlink.exception.BadRequestException;
import com.carbonlink.exception.ConflictException;
import com.carbonlink.exception.ResourceNotFoundException;
import com.carbonlink.repository.CarbonRequestRepository;
import com.carbonlink.repository.ListingRepository;
import com.carbonlink.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private ListingRepository listingRepository;
    @Mock
    private CarbonRequestRepository carbonRequestRepository;
    @Mock
    private MatchingService matchingService; // unused by accept()/reject() - only findMatches() needs it
    @Mock
    private UserService userService;
    @Mock
    private OrderService orderService;
    @Mock
    private DistanceService distanceService;
    @Mock
    private DisplayIdService displayIdService;

    private MatchService matchService;

    @BeforeEach
    void setUp() {
        matchService = new MatchService(
                matchRepository, listingRepository, carbonRequestRepository,
                matchingService, new CostEstimatorService(8.0), userService, orderService, distanceService,
                displayIdService);
    }

    @Test
    void accept_reducesRemainingVolume_byTheTransactedAmount() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        MatchResponseDTO response = matchService.accept(match.getId());

        assertThat(listing.getRemainingVolumeTons()).isEqualTo(700.0);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(response.transactedVolumeTons()).isEqualTo(300.0);
        assertThat(response.status()).isEqualTo(MatchStatus.ACCEPTED);
    }

    @Test
    void accept_transactedVolumeIsRequestNeed_whenRemainingStockIsPlentiful() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        MatchResponseDTO response = matchService.accept(match.getId());

        assertThat(response.transactedVolumeTons()).isEqualTo(300.0);
    }

    @Test
    void accept_refusesAndChangesNothing_whenStockCannotCoverTheFullAsk() {
        // This used to fill partially - 200 of the 500 asked for - which rewrote the deal after
        // both sides had agreed to it: the buyer's quote said 500 tons and their order said 200.
        // Accepting is the moment of commitment, so it honours the ask in full or refuses.
        Listing listing = listing(1000.0, 200.0); // only 200 tons left
        CarbonRequest request = request(500.0);    // buyer wants 500
        Match match = match(listing.getId(), request.getId());
        stubFindOnly(match, listing, request);

        assertThatThrownBy(() -> matchService.accept(match.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("200")
                .hasMessageContaining("500");

        // Nothing moved: no stock deducted, no status flipped, no order created.
        assertThat(listing.getRemainingVolumeTons()).isEqualTo(200.0);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(match.getStatus()).isNotEqualTo(MatchStatus.ACCEPTED);
        assertThat(match.getTransactedVolumeTons()).isNull();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
        verifyNoInteractions(orderService);
    }

    @Test
    void accept_succeedsWhenStockExactlyCoversTheAsk() {
        // The boundary the refusal above turns on: equal is enough, only short is refused.
        Listing listing = listing(1000.0, 500.0);
        CarbonRequest request = request(500.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        MatchResponseDTO response = matchService.accept(match.getId());

        assertThat(response.transactedVolumeTons()).isEqualTo(500.0);
        assertThat(listing.getRemainingVolumeTons()).isEqualTo(0.0);
        assertThat(response.costBreakdown().basePrice()).isEqualTo(listing.getPricePerTon() * 500.0);
    }

    @Test
    void accept_closesListingWhenRemainingVolumeHitsExactlyZero() {
        Listing listing = listing(1000.0, 300.0);
        CarbonRequest request = request(300.0); // exactly depletes it
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        matchService.accept(match.getId());

        assertThat(listing.getRemainingVolumeTons()).isEqualTo(0.0);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.CLOSED);
    }

    @Test
    void accept_closesListingWhenRemainingVolumeFallsBelowOneTonThreshold() {
        Listing listing = listing(1000.0, 300.5);
        CarbonRequest request = request(300.0); // leaves 0.5 tons - under the 1-ton close threshold
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        matchService.accept(match.getId());

        assertThat(listing.getRemainingVolumeTons()).isEqualTo(0.5);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.CLOSED);
    }

    @Test
    void accept_leavesListingActiveWhenMeaningfulStockRemains() {
        Listing listing = listing(1000.0, 500.0);
        CarbonRequest request = request(100.0); // leaves 400 tons - well above the threshold
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        matchService.accept(match.getId());

        assertThat(listing.getRemainingVolumeTons()).isEqualTo(400.0);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void stockDepletesCumulativelyAcrossAccepts_andRevertRestoresExactlyOneAcceptsWorth() {
        // The full demo arc: one listing sold down by two separate buyers, then one of those
        // deals reversed. Each individual step was covered; the cumulative arithmetic across
        // steps - that a revert gives back exactly its own transacted amount and not the
        // listing's whole history - was not.
        Listing listing = listing(1000.0, 1000.0);

        CarbonRequest firstRequest = request(300.0);
        Match firstMatch = match(listing.getId(), firstRequest.getId());
        stubLookups(firstMatch, listing, firstRequest);
        matchService.accept(firstMatch.getId());
        assertThat(listing.getRemainingVolumeTons()).isEqualTo(700.0);

        CarbonRequest secondRequest = request(250.0);
        secondRequest.setId(2L);
        Match secondMatch = match(listing.getId(), secondRequest.getId());
        secondMatch.setId(2L);
        when(matchRepository.findById(2L)).thenReturn(Optional.of(secondMatch));
        when(carbonRequestRepository.findById(2L)).thenReturn(Optional.of(secondRequest));
        matchService.accept(secondMatch.getId());
        assertThat(listing.getRemainingVolumeTons()).isEqualTo(450.0);

        // Reversing the FIRST deal restores 300, not 550 - the second deal still stands.
        when(orderService.findDeliveryStatusByMatchId(firstMatch.getId())).thenReturn(DeliveryStatus.CONFIRMED);
        matchService.revert(firstMatch.getId());

        assertThat(listing.getRemainingVolumeTons()).isEqualTo(750.0);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(firstRequest.getStatus()).isEqualTo(RequestStatus.OPEN);
        assertThat(secondMatch.getStatus()).isEqualTo(MatchStatus.ACCEPTED);
        assertThat(secondRequest.getStatus()).isEqualTo(RequestStatus.MATCHED);
        assertThat(secondMatch.getTransactedVolumeTons()).isEqualTo(250.0);
    }

    @Test
    void accept_neverDeductsMoreThanTheListingHas_evenAcrossRepeatedAccepts() {
        // Depleting past zero would make remainingVolumeTons negative and quietly oversell. The
        // earlier deal legitimately eats most of the stock; the later over-ask is what must be
        // refused, and the listing must be left exactly as the first deal left it.
        Listing listing = listing(500.0, 500.0);

        CarbonRequest firstRequest = request(400.0);
        Match firstMatch = match(listing.getId(), firstRequest.getId());
        stubLookups(firstMatch, listing, firstRequest);
        matchService.accept(firstMatch.getId());
        assertThat(listing.getRemainingVolumeTons()).isEqualTo(100.0);

        CarbonRequest secondRequest = request(9999.0); // asks for far more than what's left
        secondRequest.setId(2L);
        Match secondMatch = match(listing.getId(), secondRequest.getId());
        secondMatch.setId(2L);
        when(matchRepository.findById(2L)).thenReturn(Optional.of(secondMatch));
        when(carbonRequestRepository.findById(2L)).thenReturn(Optional.of(secondRequest));

        assertThatThrownBy(() -> matchService.accept(secondMatch.getId()))
                .isInstanceOf(ConflictException.class);

        assertThat(secondMatch.getTransactedVolumeTons()).isNull();
        assertThat(listing.getRemainingVolumeTons()).isEqualTo(100.0);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void accept_setsRequestStatusToMatched() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        matchService.accept(match.getId());

        assertThat(request.getStatus()).isEqualTo(RequestStatus.MATCHED);
    }

    @Test
    void accept_alreadyDecidedMatch_throwsConflict() {
        Match decided = Match.builder().id(1L).listingId(1L).requestId(1L).status(MatchStatus.ACCEPTED).build();
        when(matchRepository.findById(1L)).thenReturn(Optional.of(decided));

        assertThatThrownBy(() -> matchService.accept(1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void accept_matchNotFound_throwsResourceNotFoundException() {
        when(matchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.accept(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void accept_listingNoLongerActive_throwsConflictInsteadOfSilentlyProcessing() {
        Listing listing = listing(1000.0, 1000.0);
        listing.setStatus(ListingStatus.CLOSED); // claimed by a different match in the meantime
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubFindOnly(match, listing, request);

        assertThatThrownBy(() -> matchService.accept(match.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer active");

        assertThat(match.getStatus()).isEqualTo(MatchStatus.SUGGESTED);
    }

    @Test
    void accept_requestNoLongerOpen_throwsConflictInsteadOfSilentlyProcessing() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        request.setStatus(RequestStatus.MATCHED); // already fulfilled by a different match
        Match match = match(listing.getId(), request.getId());
        stubFindOnly(match, listing, request);

        assertThatThrownBy(() -> matchService.accept(match.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer open");

        assertThat(match.getStatus()).isEqualTo(MatchStatus.SUGGESTED);
    }

    @Test
    void reject_listingNoLongerActive_throwsConflict() {
        Listing listing = listing(1000.0, 1000.0);
        listing.setStatus(ListingStatus.CLOSED);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubFindOnly(match, listing, request);

        assertThatThrownBy(() -> matchService.reject(match.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer active");
    }

    @Test
    void reject_requestNoLongerOpen_throwsConflict() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        request.setStatus(RequestStatus.MATCHED);
        Match match = match(listing.getId(), request.getId());
        stubFindOnly(match, listing, request);

        assertThatThrownBy(() -> matchService.reject(match.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer open");
    }

    // --- Stock rule: deducted only on accept, restored only on revert, reject/cancel untouched ---

    @Test
    void reject_doesNotChangeListingStockOrStatus() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubFindAndSaveMatchOnly(match, listing, request);
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer()); // reject's message names the buyer

        matchService.reject(match.getId());

        assertThat(listing.getRemainingVolumeTons()).isEqualTo(1000.0);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
    }

    @Test
    void cancel_doesNotChangeListingStockOrStatus() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        match.setStatus(MatchStatus.REQUESTED); // only a REQUESTED match can be cancelled
        stubFindAndSaveMatchAndRequest(match, listing, request);

        MatchResponseDTO response = matchService.cancel(match.getId());

        assertThat(response.status()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(listing.getRemainingVolumeTons()).isEqualTo(1000.0);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
    }

    @Test
    void cancel_alreadyDecided_throwsConflict() {
        Match decided = Match.builder().id(1L).listingId(1L).requestId(1L).status(MatchStatus.REJECTED).build();
        when(matchRepository.findById(1L)).thenReturn(Optional.of(decided));

        assertThatThrownBy(() -> matchService.cancel(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not currently requested");
    }

    @Test
    void cancel_stillOnlySuggested_throwsConflict() {
        // Only a REQUESTED match (the buyer explicitly expressed interest) can be cancelled -
        // a passive SUGGESTED candidate they never acted on isn't "sent", so there's nothing to withdraw.
        Match suggested = Match.builder().id(1L).listingId(1L).requestId(1L).status(MatchStatus.SUGGESTED).build();
        when(matchRepository.findById(1L)).thenReturn(Optional.of(suggested));

        assertThatThrownBy(() -> matchService.cancel(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not currently requested");
    }

    @Test
    void cancel_doesNotReopenARequestAlreadyMatchedByADifferentAcceptedMatch() {
        // A buyer can have several REQUESTED matches open against one request at once. Once one
        // of them is accepted the request is MATCHED and owns a live order; withdrawing a
        // *different* one must not flip it back to OPEN, or the same request could be fulfilled
        // a second time.
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        request.setStatus(RequestStatus.MATCHED); // some other match was already accepted
        Match otherMatch = match(listing.getId(), request.getId());
        otherMatch.setId(2L);
        otherMatch.setStatus(MatchStatus.REQUESTED);
        stubFindOnly(otherMatch, listing, request);
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());

        MatchResponseDTO response = matchService.cancel(otherMatch.getId());

        assertThat(response.status()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.MATCHED);
        verify(carbonRequestRepository, never()).save(any());
    }

    @Test
    void cancel_reopensAnOrdinaryStillOpenRequest() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        match.setStatus(MatchStatus.REQUESTED);
        stubFindAndSaveMatchAndRequest(match, listing, request);

        matchService.cancel(match.getId());

        assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
        verify(carbonRequestRepository).save(request);
    }

    // --- Dual ID: each side sees only its OWN personal number on a match ---

    @Test
    void emitterFacingMatch_carriesTheirListingNumber_andNeverTheBuyersRequestNumber() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);
        when(displayIdService.listingDisplayId(listing)).thenReturn(4);

        // accept() responds from the emitter's viewpoint.
        MatchResponseDTO response = matchService.accept(match.getId());

        assertThat(response.personalListingNumber()).isEqualTo(4);
        assertThat(response.personalRequestNumber()).isNull();
        // The buyer's own numbering is never even resolved for an emitter-facing response.
        verify(displayIdService, never()).requestDisplayId(any());
    }

    @Test
    void buyerFacingMatch_carriesTheirRequestNumber_andNeverTheEmittersListingNumber() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        match.setStatus(MatchStatus.REQUESTED);
        stubFindAndSaveMatchAndRequest(match, listing, request);
        when(displayIdService.requestDisplayId(request)).thenReturn(2);

        // cancel() responds from the buyer's viewpoint.
        MatchResponseDTO response = matchService.cancel(match.getId());

        assertThat(response.personalRequestNumber()).isEqualTo(2);
        assertThat(response.personalListingNumber()).isNull();
        verify(displayIdService, never()).listingDisplayId(any());
    }

    // --- request(): the SUGGESTED -> REQUESTED step the buyer takes from "Find Matches" ---

    @Test
    void request_movesASuggestedMatchToRequested_withoutTouchingStockOrRequestStatus() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubFindAndSaveMatchOnly(match, listing, request);
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());

        MatchResponseDTO response = matchService.request(match.getId());

        assertThat(response.status()).isEqualTo(MatchStatus.REQUESTED);
        // REQUESTED is an expression of interest only - no stock moves until accept().
        assertThat(listing.getRemainingVolumeTons()).isEqualTo(1000.0);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
        // No status message yet - REQUESTED isn't a "change" either side needs to be told about.
        assertThat(response.statusMessage()).isNull();
        verify(listingRepository, never()).save(any());
        verify(carbonRequestRepository, never()).save(any());
    }

    @Test
    void request_alreadyDecidedMatch_throwsConflict() {
        Match decided = Match.builder().id(1L).listingId(1L).requestId(1L).status(MatchStatus.ACCEPTED).build();
        when(matchRepository.findById(1L)).thenReturn(Optional.of(decided));

        assertThatThrownBy(() -> matchService.request(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been accepted");
        verify(matchRepository, never()).save(any());
    }

    @Test
    void revert_restoresDeductedStock_andReopensListingAndRequest() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        matchService.accept(match.getId());
        assertThat(listing.getRemainingVolumeTons()).isEqualTo(700.0);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.MATCHED);

        MatchResponseDTO response = matchService.revert(match.getId());

        assertThat(response.status()).isEqualTo(MatchStatus.REVERTED);
        assertThat(listing.getRemainingVolumeTons()).isEqualTo(1000.0);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
    }

    @Test
    void revert_afterFullDepletion_reopensTheNowClosedListing() {
        Listing listing = listing(300.0, 300.0);
        CarbonRequest request = request(300.0); // exactly depletes it
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        matchService.accept(match.getId());
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.CLOSED);

        matchService.revert(match.getId());

        assertThat(listing.getRemainingVolumeTons()).isEqualTo(300.0);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void revert_whenRestoredStockStillUnderThreshold_listingStaysClosedButRequestReopens() {
        Listing listing = listing(1000.0, 0.5); // 0.5 tons left, already CLOSED-worthy
        listing.setStatus(ListingStatus.CLOSED);
        CarbonRequest request = request(0.4);
        Match match = match(listing.getId(), request.getId());
        match.setStatus(MatchStatus.ACCEPTED);
        match.setTransactedVolumeTons(0.4); // tiny transacted amount
        stubFindOnly(match, listing, request);
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(carbonRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());

        matchService.revert(match.getId());

        // 0.5 + 0.4 = 0.9, still under the 1-ton auto-close threshold - stays CLOSED...
        assertThat(listing.getRemainingVolumeTons()).isEqualTo(0.9);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.CLOSED);
        // ...but the buyer's request is unconditionally reopened regardless of this listing's fate.
        assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
    }

    @Test
    void revert_onceShipmentIsInTransit_throwsConflictAndLeavesStockDeducted() {
        // The emitter's Order Status card shows "Mark as In Transit" and "Revert" together.
        // Once the CO2 is on a truck, restoring the listing's stock would re-sell volume that
        // has physically left the site.
        Listing listing = listing(1000.0, 700.0);
        CarbonRequest request = request(300.0);
        request.setStatus(RequestStatus.MATCHED);
        Match match = match(listing.getId(), request.getId());
        match.setStatus(MatchStatus.ACCEPTED);
        match.setTransactedVolumeTons(300.0);
        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));
        when(orderService.findDeliveryStatusByMatchId(match.getId())).thenReturn(DeliveryStatus.IN_TRANSIT);

        assertThatThrownBy(() -> matchService.revert(match.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been dispatched");

        assertThat(listing.getRemainingVolumeTons()).isEqualTo(700.0);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.MATCHED);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.ACCEPTED);
        verify(listingRepository, never()).save(any());
        verify(orderService, never()).markRevertedForMatch(any());
    }

    @Test
    void revert_onceShipmentIsDelivered_throwsConflict() {
        Match match = match(1L, 1L);
        match.setStatus(MatchStatus.ACCEPTED);
        match.setTransactedVolumeTons(300.0);
        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));
        when(orderService.findDeliveryStatusByMatchId(match.getId())).thenReturn(DeliveryStatus.DELIVERED);

        assertThatThrownBy(() -> matchService.revert(match.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been dispatched");
    }

    @Test
    void revert_whileDeliveryStillConfirmed_isAllowed() {
        Listing listing = listing(1000.0, 700.0);
        CarbonRequest request = request(300.0);
        request.setStatus(RequestStatus.MATCHED);
        Match match = match(listing.getId(), request.getId());
        match.setStatus(MatchStatus.ACCEPTED);
        match.setTransactedVolumeTons(300.0);
        stubFindOnly(match, listing, request);
        when(orderService.findDeliveryStatusByMatchId(match.getId())).thenReturn(DeliveryStatus.CONFIRMED);
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(carbonRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());

        MatchResponseDTO response = matchService.revert(match.getId());

        assertThat(response.status()).isEqualTo(MatchStatus.REVERTED);
        assertThat(listing.getRemainingVolumeTons()).isEqualTo(1000.0);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.OPEN);
    }

    @Test
    void revert_notAccepted_throwsConflict() {
        Match suggested = Match.builder().id(1L).listingId(1L).requestId(1L).status(MatchStatus.SUGGESTED).build();
        when(matchRepository.findById(1L)).thenReturn(Optional.of(suggested));

        assertThatThrownBy(() -> matchService.revert(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not currently accepted");
    }

    @Test
    void revert_matchNotFound_throwsResourceNotFoundException() {
        when(matchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.revert(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // --- Order creation on accept, reversion on revert ---

    @Test
    void accept_createsAnOrderWithTheActuallyTransactedVolumeAndCost() {
        Listing listing = listing(1000.0, 600.0);
        CarbonRequest request = request(500.0); // the whole ask, which the stock can cover
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        matchService.accept(match.getId());

        ArgumentCaptor<CostBreakdown> costCaptor = ArgumentCaptor.forClass(CostBreakdown.class);
        verify(orderService).createOrderForAcceptedMatch(eq(match), eq(listing), eq(request), costCaptor.capture());
        CostBreakdown captured = costCaptor.getValue();
        // Snapshotted from the transacted volume, and the breakdown now carries that volume so a
        // client can never show this money beside the listing's (much larger) size.
        assertThat(captured.volumeTons()).isEqualTo(500.0);
        assertThat(captured.basePrice()).isEqualTo(listing.getPricePerTon() * 500.0);
    }

    @Test
    void revert_marksTheCorrespondingOrderReverted() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        matchService.accept(match.getId());
        matchService.revert(match.getId());

        verify(orderService).markRevertedForMatch(match.getId());
    }

    @Test
    void toResponseDto_includesOrderId_forAnAcceptedMatch() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);
        when(orderService.findOrderIdByMatchId(match.getId())).thenReturn(77L);

        MatchResponseDTO response = matchService.accept(match.getId());

        assertThat(response.orderId()).isEqualTo(77L);
    }

    @Test
    void toResponseDto_neverLooksUpAnOrder_forAMatchThatNeverHadOne() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        match.setStatus(MatchStatus.REQUESTED); // cancel()'s precondition
        stubFindAndSaveMatchAndRequest(match, listing, request);

        // CANCELLED is not one of HAS_ORDER_STATUSES - no order was ever created for this match.
        MatchResponseDTO response = matchService.cancel(match.getId());

        assertThat(response.orderId()).isNull();
        verify(orderService, never()).findOrderIdByMatchId(any());
    }

    // --- Buyer-facing status messages ("Order Status" view = getSentMatchesForBuyer(status=FINAL)) ---

    @Test
    void getSentMatchesForBuyer_showsRejectionMessageFromBuyerPerspective_andMarksViewed() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        match.setStatus(MatchStatus.REJECTED);
        match.setCreatedAt(LocalDateTime.now());

        when(carbonRequestRepository.findByBuyerId(request.getBuyerId())).thenReturn(List.of(request));
        when(matchRepository.findByRequestIdIn(List.of(request.getId()))).thenReturn(List.of(match));
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        // buyerCity is now populated on every DTO regardless of message content, so the buyer
        // lookup must be stubbed too (a generic fallback), on top of the emitter's specific name.
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());
        when(userService.requireUser(listing.getEmitterId())).thenReturn(userNamed(listing.getEmitterId(), "Ambuja Cement Plant"));

        List<MatchResponseDTO> results = matchService.getSentMatchesForBuyer(request.getBuyerId(), "FINAL");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).statusMessage()).isEqualTo("Your request was declined by Ambuja Cement Plant");
        assertThat(results.get(0).viewed()).isTrue();
        assertThat(match.isViewed()).isTrue();
    }

    @Test
    void getSentMatchesForBuyer_showsAcceptanceMessageFromBuyerPerspective() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);
        matchService.accept(match.getId());
        match.setCreatedAt(LocalDateTime.now());

        when(carbonRequestRepository.findByBuyerId(request.getBuyerId())).thenReturn(List.of(request));
        when(matchRepository.findByRequestIdIn(List.of(request.getId()))).thenReturn(List.of(match));

        List<MatchResponseDTO> results = matchService.getSentMatchesForBuyer(request.getBuyerId(), "FINAL");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).statusMessage()).isEqualTo("Accepted by Company 10 — 300.0 tons confirmed");
    }

    @Test
    void getSentMatchesForBuyer_showsReversalMessageFromBuyerPerspective() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);
        matchService.accept(match.getId());
        matchService.revert(match.getId()); // request back to OPEN
        match.setCreatedAt(LocalDateTime.now());

        when(carbonRequestRepository.findByBuyerId(request.getBuyerId())).thenReturn(List.of(request));
        when(matchRepository.findByRequestIdIn(List.of(request.getId()))).thenReturn(List.of(match));

        List<MatchResponseDTO> results = matchService.getSentMatchesForBuyer(request.getBuyerId(), "FINAL");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(MatchStatus.REVERTED);
        assertThat(results.get(0).statusMessage())
                .isEqualTo("This match was reversed by Company 10 — your request has been reopened");
    }

    @Test
    void getSentMatchesForBuyer_resolvesOrderIdsInOneBatchedLookup_notOnePerRow() {
        // The list views prefetch every order id in a single query instead of asking per row.
        // The single-match paths still fall back to findOrderIdByMatchId, so this is the only
        // place the batched path is exercised.
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);
        matchService.accept(match.getId());
        match.setCreatedAt(LocalDateTime.now());

        when(carbonRequestRepository.findByBuyerId(request.getBuyerId())).thenReturn(List.of(request));
        when(matchRepository.findByRequestIdIn(List.of(request.getId()))).thenReturn(List.of(match));
        when(orderService.findOrderIdsByMatchIds(List.of(match.getId()))).thenReturn(Map.of(match.getId(), 77L));

        List<MatchResponseDTO> results = matchService.getSentMatchesForBuyer(request.getBuyerId(), "FINAL");

        // findOrderIdByMatchId is deliberately left unstubbed: if the list view fell back to the
        // per-row lookup this would come back null rather than 77.
        assertThat(results).singleElement().extracting(MatchResponseDTO::orderId).isEqualTo(77L);
    }

    @Test
    void getSentMatchesForBuyer_resolvesTheBuyerOnce_regardlessOfHowManyRowsAreReturned() {
        // Every row of this view belongs to the same buyer. Before the shared lookup memo each
        // row re-queried them, so a 20-row tab issued 20 identical user lookups.
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);

        List<Match> matches = new java.util.ArrayList<>();
        for (long id = 1; id <= 5; id++) {
            Match m = match(listing.getId(), request.getId());
            m.setId(id);
            m.setStatus(MatchStatus.REQUESTED);
            m.setCreatedAt(LocalDateTime.now());
            matches.add(m);
        }

        when(carbonRequestRepository.findByBuyerId(request.getBuyerId())).thenReturn(List.of(request));
        when(matchRepository.findByRequestIdIn(List.of(request.getId()))).thenReturn(matches);
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(userService.requireUser(request.getBuyerId())).thenAnswer(fakeUserAnswer());

        List<MatchResponseDTO> results = matchService.getSentMatchesForBuyer(request.getBuyerId(), "REQUESTED");

        assertThat(results).hasSize(5);
        verify(userService, times(1)).requireUser(request.getBuyerId());
        verify(listingRepository, times(1)).findById(listing.getId());
    }

    // --- Emitter-facing status message (their "Incoming Matches" view = getMatchesForListing) ---

    @Test
    void getMatchesForListing_showsCancellationMessageFromEmitterPerspective_andMarksViewed() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        match.setStatus(MatchStatus.CANCELLED);

        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(matchRepository.findByListingId(listing.getId())).thenReturn(List.of(match));
        when(carbonRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(userService.requireUser(request.getBuyerId())).thenReturn(userNamed(request.getBuyerId(), "GreenFuel Synthetics"));

        List<MatchResponseDTO> results = matchService.getMatchesForListing(listing.getId(), "FINAL");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).statusMessage()).isEqualTo("This request was withdrawn by GreenFuel Synthetics");
        assertThat(results.get(0).viewed()).isTrue();
        assertThat(match.isViewed()).isTrue();
    }

    @Test
    void cancel_directResponse_isPhrasedFromTheBuyersOwnPerspective() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        match.setStatus(MatchStatus.REQUESTED);
        stubFindAndSaveMatchAndRequest(match, listing, request);

        MatchResponseDTO response = matchService.cancel(match.getId());

        // The buyer caused this themselves - no company-name lookup needed, no notification to self.
        assertThat(response.statusMessage()).isEqualTo("You withdrew this request");
    }

    // --- "NEW" badge: unviewed-count peek (never marks anything viewed) + mark-on-fetch ---

    @Test
    void getUnviewedCount_forBuyer_countsOnlyUnviewedNotifyStatuses() {
        CarbonRequest request = request(300.0);
        Match accepted = match(1L, request.getId());
        accepted.setId(1L);
        accepted.setStatus(MatchStatus.ACCEPTED);
        accepted.setViewed(false);
        Match alreadySeen = match(2L, request.getId());
        alreadySeen.setId(2L);
        alreadySeen.setStatus(MatchStatus.REJECTED);
        alreadySeen.setViewed(true);
        Match stillPending = match(3L, request.getId());
        stillPending.setId(3L);
        stillPending.setStatus(MatchStatus.SUGGESTED);
        stillPending.setViewed(false);

        when(carbonRequestRepository.findByBuyerId(request.getBuyerId())).thenReturn(List.of(request));
        when(matchRepository.findByRequestIdIn(List.of(request.getId())))
                .thenReturn(List.of(accepted, alreadySeen, stillPending));

        UnviewedCountDTO result = matchService.getUnviewedCount(request.getBuyerId(), null);

        assertThat(result.count()).isEqualTo(1); // only the unviewed ACCEPTED counts
    }

    @Test
    void getUnviewedCount_forEmitter_countsOnlyUnviewedCancelledMatches() {
        Listing listing = listing(1000.0, 1000.0);
        Match cancelled = match(listing.getId(), 1L);
        cancelled.setId(1L);
        cancelled.setStatus(MatchStatus.CANCELLED);
        cancelled.setViewed(false);
        Match accepted = match(listing.getId(), 2L); // not emitter-notify-worthy - the emitter caused it
        accepted.setId(2L);
        accepted.setStatus(MatchStatus.ACCEPTED);
        accepted.setViewed(false);

        when(listingRepository.findByEmitterId(listing.getEmitterId())).thenReturn(List.of(listing));
        when(matchRepository.findByListingIdIn(List.of(listing.getId()))).thenReturn(List.of(cancelled, accepted));

        UnviewedCountDTO result = matchService.getUnviewedCount(null, listing.getEmitterId());

        assertThat(result.count()).isEqualTo(1);
    }

    @Test
    void getUnviewedCount_requiresExactlyOneOfBuyerIdOrEmitterId() {
        assertThatThrownBy(() -> matchService.getUnviewedCount(null, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> matchService.getUnviewedCount(1L, 2L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getSentMatchesForBuyer_marksUnviewedNotifyMatchAsViewed_clearingTheBadge() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        match.setStatus(MatchStatus.REJECTED);
        match.setViewed(false);
        match.setCreatedAt(LocalDateTime.now());

        when(carbonRequestRepository.findByBuyerId(request.getBuyerId())).thenReturn(List.of(request));
        when(matchRepository.findByRequestIdIn(List.of(request.getId()))).thenReturn(List.of(match));
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());

        assertThat(match.isViewed()).isFalse();
        matchService.getSentMatchesForBuyer(request.getBuyerId(), null);
        assertThat(match.isViewed()).isTrue();
    }

    @Test
    void getMatchesForListing_marksUnviewedCancelledMatchAsViewed_clearingTheBadge() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        match.setStatus(MatchStatus.CANCELLED);
        match.setViewed(false);

        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(matchRepository.findByListingId(listing.getId())).thenReturn(List.of(match));
        when(carbonRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());

        assertThat(match.isViewed()).isFalse();
        matchService.getMatchesForListing(listing.getId(), "FINAL");
        assertThat(match.isViewed()).isTrue();
    }

    // --- Location visibility: every match DTO carries the supplier's and buyer's city ---

    @Test
    void toResponseDto_populatesEmitterCityFromListing_andBuyerCityFromBuyersUserRecord() {
        Listing listing = listing(1000.0, 1000.0);
        listing.setCity("Mumbai"); // the listing's own site - independent of the emitter's HQ
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);
        when(userService.requireUser(request.getBuyerId()))
                .thenReturn(userNamedWithCity(request.getBuyerId(), "Company 20", "Chennai"));

        MatchResponseDTO response = matchService.accept(match.getId());

        assertThat(response.emitterCity()).isEqualTo("Mumbai");
        assertThat(response.buyerCity()).isEqualTo("Chennai");
    }

    @Test
    void upsertSuggestion_alsoPopulatesEmitterAndBuyerCity() {
        Listing listing = listing(1000.0, 1000.0);
        listing.setCity("Pune");
        CarbonRequest request = request(300.0);

        when(carbonRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(matchingService.findMatches(request.getId())).thenReturn(List.of(
                new MatchResult(listing.getId(), request.getId(), 90.0, 50.0,
                        DistanceSource.HAVERSINE_FALLBACK,
                        new ScoreBreakdown(100.0, 100.0, 100.0, 100.0))));
        when(matchRepository.findByListingIdAndRequestId(listing.getId(), request.getId())).thenReturn(Optional.empty());
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userService.requireUser(request.getBuyerId()))
                .thenReturn(userNamedWithCity(request.getBuyerId(), "Company 20", "Bangalore"));

        List<MatchResponseDTO> results = matchService.getRankedMatchesWithCost(request.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).emitterCity()).isEqualTo("Pune");
        assertThat(results.get(0).buyerCity()).isEqualTo("Bangalore");
    }

    // --- "My Sent Requests": buyer-wide history across all their requests ---

    @Test
    void getSentMatchesForBuyer_includesActedOnMatches_excludingPureSuggestions() {
        CarbonRequest request = request(300.0);
        Listing listing = listing(1000.0, 1000.0);

        Match requested = match(listing.getId(), request.getId());
        requested.setId(1L);
        requested.setStatus(MatchStatus.REQUESTED);
        requested.setCreatedAt(LocalDateTime.now());
        Match accepted = match(listing.getId(), request.getId());
        accepted.setId(2L);
        accepted.setStatus(MatchStatus.ACCEPTED);
        accepted.setTransactedVolumeTons(300.0);
        accepted.setCreatedAt(LocalDateTime.now());
        Match neverActedOn = match(listing.getId(), request.getId());
        neverActedOn.setId(3L);
        neverActedOn.setStatus(MatchStatus.SUGGESTED);
        neverActedOn.setCreatedAt(LocalDateTime.now());

        when(carbonRequestRepository.findByBuyerId(request.getBuyerId())).thenReturn(List.of(request));
        when(matchRepository.findByRequestIdIn(List.of(request.getId())))
                .thenReturn(List.of(requested, accepted, neverActedOn));
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());

        List<MatchResponseDTO> results = matchService.getSentMatchesForBuyer(request.getBuyerId(), null);

        assertThat(results).extracting(MatchResponseDTO::id).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void getSentMatchesForBuyer_marksBuyerNotifyStatusesAsViewed() {
        CarbonRequest request = request(300.0);
        Listing listing = listing(1000.0, 1000.0);
        Match accepted = match(listing.getId(), request.getId());
        accepted.setStatus(MatchStatus.ACCEPTED);
        accepted.setTransactedVolumeTons(300.0);
        accepted.setViewed(false);
        accepted.setCreatedAt(LocalDateTime.now());

        when(carbonRequestRepository.findByBuyerId(request.getBuyerId())).thenReturn(List.of(request));
        when(matchRepository.findByRequestIdIn(List.of(request.getId()))).thenReturn(List.of(accepted));
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());

        matchService.getSentMatchesForBuyer(request.getBuyerId(), null);

        assertThat(accepted.isViewed()).isTrue();
    }

    @Test
    void getSentMatchesForBuyer_noRequests_returnsEmptyWithoutTouchingMatchRepository() {
        when(carbonRequestRepository.findByBuyerId(99L)).thenReturn(List.of());

        assertThat(matchService.getSentMatchesForBuyer(99L, null)).isEmpty();
    }

    // --- Tab-filtering restructure: Find Matches / Pending Requests / Order Status / Incoming ---

    @Test
    void getRankedMatchesWithCost_excludesAnAlreadyRequestedMatch_thatsPendingRequestsTerritoryNow() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match alreadyRequested = match(listing.getId(), request.getId());
        alreadyRequested.setStatus(MatchStatus.REQUESTED);

        when(carbonRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(matchingService.findMatches(request.getId())).thenReturn(List.of(
                new MatchResult(listing.getId(), request.getId(), 90.0, 50.0,
                        DistanceSource.HAVERSINE_FALLBACK,
                        new ScoreBreakdown(100.0, 100.0, 100.0, 100.0))));
        when(matchRepository.findByListingIdAndRequestId(listing.getId(), request.getId()))
                .thenReturn(Optional.of(alreadyRequested));
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());

        // upsertSuggestion still refreshes the REQUESTED row's score/cost under the hood...
        List<MatchResponseDTO> results = matchService.getRankedMatchesWithCost(request.getId());

        // ...but "Find Matches" itself never shows it - it's an actionable, unrequested-only view.
        assertThat(results).isEmpty();
        assertThat(alreadyRequested.getStatus()).isEqualTo(MatchStatus.REQUESTED);
    }

    @Test
    void getMatchesForListing_defaultsToRequestedOnly_excludingSuggestedAndFinalStatuses() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match suggested = match(listing.getId(), 1L);
        suggested.setId(1L);
        suggested.setStatus(MatchStatus.SUGGESTED);
        Match requested = match(listing.getId(), 2L);
        requested.setId(2L);
        requested.setStatus(MatchStatus.REQUESTED);
        Match accepted = match(listing.getId(), 3L);
        accepted.setId(3L);
        accepted.setStatus(MatchStatus.ACCEPTED);

        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(matchRepository.findByListingId(listing.getId())).thenReturn(List.of(suggested, requested, accepted));
        when(carbonRequestRepository.findById(any())).thenReturn(Optional.of(request));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());

        List<MatchResponseDTO> results = matchService.getMatchesForListing(listing.getId(), null);

        assertThat(results).extracting(MatchResponseDTO::id).containsExactly(2L);
    }

    @Test
    void getMatchesForListing_statusFinal_returnsOnlyDecidedStatuses() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest request = request(300.0);
        Match requested = match(listing.getId(), 1L);
        requested.setId(1L);
        requested.setStatus(MatchStatus.REQUESTED);
        Match rejected = match(listing.getId(), 2L);
        rejected.setId(2L);
        rejected.setStatus(MatchStatus.REJECTED);
        Match cancelled = match(listing.getId(), 3L);
        cancelled.setId(3L);
        cancelled.setStatus(MatchStatus.CANCELLED);

        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(matchRepository.findByListingId(listing.getId())).thenReturn(List.of(requested, rejected, cancelled));
        when(carbonRequestRepository.findById(any())).thenReturn(Optional.of(request));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());

        List<MatchResponseDTO> results = matchService.getMatchesForListing(listing.getId(), "FINAL");

        assertThat(results).extracting(MatchResponseDTO::id).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void getMatchesForListing_invalidStatusValue_throwsBadRequest() {
        Listing listing = listing(1000.0, 1000.0);
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(matchRepository.findByListingId(listing.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> matchService.getMatchesForListing(listing.getId(), "NOT_A_STATUS"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getSentMatchesForBuyer_statusRequested_returnsOnlyPendingRequests() {
        CarbonRequest request = request(300.0);
        Listing listing = listing(1000.0, 1000.0);
        Match requested = match(listing.getId(), request.getId());
        requested.setId(1L);
        requested.setStatus(MatchStatus.REQUESTED);
        requested.setCreatedAt(LocalDateTime.now());
        Match accepted = match(listing.getId(), request.getId());
        accepted.setId(2L);
        accepted.setStatus(MatchStatus.ACCEPTED);
        accepted.setCreatedAt(LocalDateTime.now());

        when(carbonRequestRepository.findByBuyerId(request.getBuyerId())).thenReturn(List.of(request));
        when(matchRepository.findByRequestIdIn(List.of(request.getId()))).thenReturn(List.of(requested, accepted));
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());

        List<MatchResponseDTO> results = matchService.getSentMatchesForBuyer(request.getBuyerId(), "REQUESTED");

        assertThat(results).extracting(MatchResponseDTO::id).containsExactly(1L);
    }

    @Test
    void getSentMatchesForBuyer_invalidStatusValue_throwsBadRequest() {
        CarbonRequest request = request(300.0);
        when(carbonRequestRepository.findByBuyerId(request.getBuyerId())).thenReturn(List.of(request));

        assertThatThrownBy(() -> matchService.getSentMatchesForBuyer(request.getBuyerId(), "NOT_A_STATUS"))
                .isInstanceOf(BadRequestException.class);
    }

    // --- Repro: two buyers on the same/overlapping supply, one accepted first ---

    @Test
    void getRankedMatchesWithCost_omitsUndecidedMatchAgainstNoLongerActiveListing() {
        // Buyer A's request exactly depletes the listing when accepted.
        Listing listing = listing(300.0, 300.0);
        CarbonRequest requestA = request(300.0);
        Match matchA = match(listing.getId(), requestA.getId());
        stubLookups(matchA, listing, requestA);

        matchService.accept(matchA.getId());
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.CLOSED);

        // Buyer B was suggested the very same listing before it closed - that suggestion is
        // still sitting in the DB as an undecided (SUGGESTED) Match row.
        CarbonRequest requestB = CarbonRequest.builder()
                .id(2L).buyerId(21L)
                .minVolumeNeeded(100.0).minPurityRequired(90.0)
                .maxDistanceKm(500.0).maxBudgetPerTon(1000.0)
                .intendedUse("Fuel Synthesis")
                .status(RequestStatus.OPEN)
                .build();
        Match matchB = Match.builder()
                .id(2L).listingId(listing.getId()).requestId(requestB.getId())
                .compatibilityScore(80.0).distanceKm(50.0).distanceSource(DistanceSource.HAVERSINE_FALLBACK)
                .transportCost(500.0).totalEstimatedCost(10_000.0)
                .status(MatchStatus.SUGGESTED)
                .build();

        when(carbonRequestRepository.findById(requestB.getId())).thenReturn(Optional.of(requestB));
        // The real MatchingService only scans ACTIVE listings, so with the listing now CLOSED it
        // would no longer surface it as a fresh suggestion either - mirrored here as an empty list.
        // "Find Matches" no longer falls back to persisted rows at all, so buyer B's stale
        // SUGGESTED row for this listing simply never surfaces, full stop.
        when(matchingService.findMatches(requestB.getId())).thenReturn(List.of());

        List<MatchResponseDTO> buyerBMatches = matchService.getRankedMatchesWithCost(requestB.getId());

        assertThat(buyerBMatches).noneMatch(dto -> dto.id().equals(matchB.getId()));

        // Even if the stale match were somehow still visible/clicked (e.g. a stale cached page),
        // accepting it must 409, never silently succeed or throw an unhandled exception.
        when(matchRepository.findById(matchB.getId())).thenReturn(Optional.of(matchB));
        assertThatThrownBy(() -> matchService.accept(matchB.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer active");

        assertThat(matchB.getStatus()).isEqualTo(MatchStatus.SUGGESTED);
        assertThat(requestB.getStatus()).isEqualTo(RequestStatus.OPEN);
    }

    @Test
    void getRankedMatchesWithCost_neverShowsAnAcceptedMatch_thatsOrderStatusTerritoryNow() {
        Listing listing = listing(300.0, 300.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        matchService.accept(match.getId());
        assertThat(request.getStatus()).isEqualTo(RequestStatus.MATCHED); // fresh scoring is skipped below because of this

        // "Find Matches" never falls back to persisted rows anymore - once accepted, this match
        // belongs to "Order Status" (getSentMatchesForBuyer) exclusively.
        List<MatchResponseDTO> results = matchService.getRankedMatchesWithCost(request.getId());

        assertThat(results).isEmpty();
    }

    @Test
    void getSentMatchesForBuyer_keepsDecidedMatchVisible_evenAfterListingClosed() {
        Listing listing = listing(300.0, 300.0);
        CarbonRequest request = request(300.0);
        Match match = match(listing.getId(), request.getId());
        stubLookups(match, listing, request);

        matchService.accept(match.getId());
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.CLOSED);
        match.setCreatedAt(LocalDateTime.now());

        when(carbonRequestRepository.findByBuyerId(request.getBuyerId())).thenReturn(List.of(request));
        when(matchRepository.findByRequestIdIn(List.of(request.getId()))).thenReturn(List.of(match));

        List<MatchResponseDTO> results = matchService.getSentMatchesForBuyer(request.getBuyerId(), "FINAL");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(MatchStatus.ACCEPTED);
    }

    @Test
    void getMatchesForListing_omitsUndecidedMatchWhoseOwnRequestIsNoLongerOpen() {
        Listing listing = listing(1000.0, 1000.0);
        CarbonRequest matchedRequest = request(300.0);
        matchedRequest.setStatus(RequestStatus.MATCHED); // e.g. matched via a different listing
        Match staleMatch = match(listing.getId(), matchedRequest.getId());
        staleMatch.setStatus(MatchStatus.REQUESTED); // passes the default REQUESTED-only filter...

        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(matchRepository.findByListingId(listing.getId())).thenReturn(List.of(staleMatch));
        when(carbonRequestRepository.findById(matchedRequest.getId())).thenReturn(Optional.of(matchedRequest));

        // ...but is still excluded because its own request has gone stale.
        List<MatchResponseDTO> results = matchService.getMatchesForListing(listing.getId(), null);

        assertThat(results).isEmpty();
    }

    // --- Quick Browse: "Request This Listing" creates a minimal request + an already-REQUESTED match ---

    @Test
    void quickRequest_createsAnAlreadyRequestedMatch_withPermissiveRequestDefaults() {
        Listing listing = listing(1000.0, 1000.0);
        User buyer = userNamedWithCity(20L, "Company 20", "Chennai");
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(userService.requireUserWithRole(20L, Role.BUYER)).thenReturn(buyer);
        when(userService.requireUser(any())).thenReturn(buyer);
        when(carbonRequestRepository.save(any())).thenAnswer(inv -> {
            CarbonRequest saved = inv.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        when(distanceService.getDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new DistanceResult(42.0, DistanceSource.HAVERSINE_FALLBACK));
        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        when(matchRepository.save(matchCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        QuickRequestDTO dto = new QuickRequestDTO(20L, 150.0);
        MatchResponseDTO response = matchService.quickRequest(listing.getId(), dto);

        assertThat(response.status()).isEqualTo(MatchStatus.REQUESTED);
        assertThat(response.distanceKm()).isEqualTo(42.0);
        assertThat(response.distanceSource()).isEqualTo(DistanceSource.HAVERSINE_FALLBACK);

        Match savedMatch = matchCaptor.getValue();
        assertThat(savedMatch.getListingId()).isEqualTo(listing.getId());
        assertThat(savedMatch.getRequestId()).isEqualTo(99L);
        assertThat(savedMatch.getStatus()).isEqualTo(MatchStatus.REQUESTED);

        ArgumentCaptor<CarbonRequest> requestCaptor = ArgumentCaptor.forClass(CarbonRequest.class);
        verify(carbonRequestRepository).save(requestCaptor.capture());
        CarbonRequest savedRequest = requestCaptor.getValue();
        assertThat(savedRequest.getBuyerId()).isEqualTo(20L);
        assertThat(savedRequest.getMinVolumeNeeded()).isEqualTo(150.0);
        assertThat(savedRequest.getMinPurityRequired()).isEqualTo(0.0);
        assertThat(savedRequest.getMaxDistanceKm()).isEqualTo(100_000.0);
        assertThat(savedRequest.getMaxBudgetPerTon()).isEqualTo(1_000_000.0);
    }

    @Test
    void quickRequest_409sWhenListingIsNoLongerActive() {
        Listing listing = listing(1000.0, 1000.0);
        listing.setStatus(ListingStatus.CLOSED);
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> matchService.quickRequest(listing.getId(), new QuickRequestDTO(20L, 150.0)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no longer active");

        verify(carbonRequestRepository, never()).save(any());
        verify(matchRepository, never()).save(any());
    }

    @Test
    void quickRequest_409sWhenAskingForMoreThanRemains() {
        // The Quick Browse form caps its input at the remaining volume, but a check that lives
        // only in the browser isn't a rule - this is the server-side half.
        Listing listing = listing(1000.0, 10.0); // 10 tons left
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> matchService.quickRequest(listing.getId(), new QuickRequestDTO(20L, 15.0)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("10");

        verify(carbonRequestRepository, never()).save(any());
        verify(matchRepository, never()).save(any());
    }

    @Test
    void quickRequest_allowsAnAskThatExactlyDepletesTheListing() {
        Listing listing = listing(1000.0, 10.0);
        stubQuickRequestLookups(listing);

        MatchResponseDTO response = matchService.quickRequest(listing.getId(), new QuickRequestDTO(20L, 10.0));

        assertThat(response.status()).isEqualTo(MatchStatus.REQUESTED);
        assertThat(response.costBreakdown().volumeTons()).isEqualTo(10.0);
    }

    @Test
    void quickRequest_404sWhenListingMissing() {
        when(listingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.quickRequest(1L, new QuickRequestDTO(20L, 150.0)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // Stub set for quickRequest(), which creates both the request and the match from scratch.
    private void stubQuickRequestLookups(Listing listing) {
        User buyer = userNamedWithCity(20L, "Company 20", "Chennai");
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(userService.requireUserWithRole(20L, Role.BUYER)).thenReturn(buyer);
        when(userService.requireUser(any())).thenReturn(buyer);
        when(carbonRequestRepository.save(any())).thenAnswer(inv -> {
            CarbonRequest saved = inv.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        when(distanceService.getDistance(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new DistanceResult(42.0, DistanceSource.HAVERSINE_FALLBACK));
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // Full stub set - for accept()/revert(), which save all three entities.
    private void stubLookups(Match match, Listing listing, CarbonRequest request) {
        stubFindOnly(match, listing, request);
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(carbonRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());
    }

    // For conflict-path tests that throw before any save() would happen - stubbing save() there
    // too would trip Mockito's strict-stubbing "unnecessary stub" check.
    private void stubFindOnly(Match match, Listing listing, CarbonRequest request) {
        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));
        when(listingRepository.findById(listing.getId())).thenReturn(Optional.of(listing));
        when(carbonRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
    }

    // For reject(), which only ever saves the match row (never touches listing/request).
    private void stubFindAndSaveMatchOnly(Match match, Listing listing, CarbonRequest request) {
        stubFindOnly(match, listing, request);
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // For cancel(), which saves the match and reopens the request but never touches the listing.
    private void stubFindAndSaveMatchAndRequest(Match match, Listing listing, CarbonRequest request) {
        stubFindOnly(match, listing, request);
        when(matchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(carbonRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userService.requireUser(any())).thenAnswer(fakeUserAnswer());
    }

    private static org.mockito.stubbing.Answer<User> fakeUserAnswer() {
        return inv -> userNamed(inv.getArgument(0), "Company " + inv.getArgument(0));
    }

    private static User userNamed(Long id, String companyName) {
        return User.builder()
                .id(id).name("Rep").companyName(companyName).role(Role.EMITTER)
                .locationLat(0.0).locationLng(0.0)
                .build();
    }

    private static User userNamedWithCity(Long id, String companyName, String city) {
        return User.builder()
                .id(id).name("Rep").companyName(companyName).role(Role.BUYER).city(city)
                .locationLat(0.0).locationLng(0.0)
                .build();
    }

    private Listing listing(double total, double remaining) {
        return Listing.builder()
                .id(1L).emitterId(10L)
                .totalVolumeTons(total)
                .remainingVolumeTons(remaining)
                .purityPercent(95.0)
                .captureMethod("Direct Air Capture")
                .pricePerTon(900.0)
                .locationLat(0.0).locationLng(0.0)
                .status(ListingStatus.ACTIVE)
                .build();
    }

    private CarbonRequest request(double minVolumeNeeded) {
        return CarbonRequest.builder()
                .id(1L)
                .buyerId(20L)
                .minVolumeNeeded(minVolumeNeeded)
                .minPurityRequired(90.0)
                .maxDistanceKm(500.0)
                .maxBudgetPerTon(1000.0)
                .intendedUse("Fuel Synthesis")
                .status(RequestStatus.OPEN)
                .build();
    }

    private Match match(Long listingId, Long requestId) {
        return Match.builder()
                .id(1L)
                .listingId(listingId)
                .requestId(requestId)
                .compatibilityScore(90.0)
                .distanceKm(50.0)
                .distanceSource(DistanceSource.HAVERSINE_FALLBACK)
                .transportCost(1000.0)
                .totalEstimatedCost(50000.0)
                .status(MatchStatus.SUGGESTED)
                .build();
    }
}
