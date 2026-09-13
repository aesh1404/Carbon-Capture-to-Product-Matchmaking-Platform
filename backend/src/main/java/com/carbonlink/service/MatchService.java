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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Bridges MatchingService's stateless scoring with persisted Match rows so accept/reject have an id to act on.
@Service
public class MatchService {

    // Terminal: a decision has been made on this (listing, request) pairing and it's final -
    // never re-suggested, never actionable again via accept/reject/cancel, always shown as
    // history. REVERTED is included here too: unlike a fresh SUGGESTED/REQUESTED candidate,
    // once a match has been accepted-then-reverted it keeps its own history line rather than
    // silently recycling back into a brand-new suggestion on the very next fetch (which would
    // bury the "this was reversed" message the buyer is supposed to see).
    private static final Set<MatchStatus> DECIDED_STATUSES =
            Set.of(MatchStatus.ACCEPTED, MatchStatus.REJECTED, MatchStatus.CANCELLED, MatchStatus.REVERTED);

    // "My Sent Requests" (the buyer-wide history view) only shows matches the buyer actually
    // acted on - a passive SUGGESTED candidate they've never expressed interest in isn't
    // something they "sent". That browsing role stays with getRankedMatchesWithCost.
    private static final Set<MatchStatus> SENT_STATUSES = Set.of(
            MatchStatus.REQUESTED, MatchStatus.ACCEPTED, MatchStatus.REJECTED,
            MatchStatus.CANCELLED, MatchStatus.REVERTED);

    // Which notify-worthy transitions belong to which side's "NEW" badge / mark-as-viewed sweep.
    // Only the party who did NOT cause the transition needs to be told about it.
    private static final Set<MatchStatus> BUYER_NOTIFY_STATUSES =
            Set.of(MatchStatus.ACCEPTED, MatchStatus.REJECTED, MatchStatus.REVERTED);
    private static final Set<MatchStatus> EMITTER_NOTIFY_STATUSES = Set.of(MatchStatus.CANCELLED);

    private static final double MIN_REMAINING_VOLUME_TONS = 1.0;

    // Only these two statuses ever have an Order behind them - avoid the lookup for everything else.
    private static final Set<MatchStatus> HAS_ORDER_STATUSES = Set.of(MatchStatus.ACCEPTED, MatchStatus.REVERTED);

    // "Request This Listing" (Quick Browse) skips the request-builder form entirely, so these
    // fill in for minPurityRequired/maxDistanceKm/maxBudgetPerTon - deliberately permissive
    // since the buyer already picked this exact listing from real, visible numbers.
    private static final double QUICK_REQUEST_MIN_PURITY_REQUIRED = 0.0;
    private static final double QUICK_REQUEST_MAX_DISTANCE_KM = 100_000.0;
    private static final double QUICK_REQUEST_MAX_BUDGET_PER_TON = 1_000_000.0;
    private static final String QUICK_REQUEST_INTENDED_USE = "Quick Browse direct request";

    private enum Viewpoint { BUYER, EMITTER }

    private final MatchRepository matchRepository;
    private final ListingRepository listingRepository;
    private final CarbonRequestRepository carbonRequestRepository;
    private final MatchingService matchingService;
    private final CostEstimatorService costEstimatorService;
    private final UserService userService;
    private final OrderService orderService;
    private final DistanceService distanceService;
    private final DisplayIdService displayIdService;

    public MatchService(MatchRepository matchRepository,
                         ListingRepository listingRepository,
                         CarbonRequestRepository carbonRequestRepository,
                         MatchingService matchingService,
                         CostEstimatorService costEstimatorService,
                         UserService userService,
                         OrderService orderService,
                         DistanceService distanceService,
                         DisplayIdService displayIdService) {
        this.matchRepository = matchRepository;
        this.listingRepository = listingRepository;
        this.carbonRequestRepository = carbonRequestRepository;
        this.matchingService = matchingService;
        this.costEstimatorService = costEstimatorService;
        this.userService = userService;
        this.orderService = orderService;
        this.distanceService = distanceService;
        this.displayIdService = displayIdService;
    }

    // "Find Matches": fresh, actionable, unrequested recommendations only. Once a candidate has
    // been requested/decided it belongs to "Pending Requests"/"Order Status" instead - this view
    // never resurfaces it, so there's no client-side filtering left to do.
    @Transactional
    public List<MatchResponseDTO> getRankedMatchesWithCost(Long requestId) {
        CarbonRequest request = requireRequest(requestId);

        // A request that's no longer OPEN (already matched/closed) can't take on any new
        // suggestions - running fresh scoring against it would just resurface other ACTIVE
        // listings as if this request could still act on them.
        List<MatchResult> results = request.getStatus() == RequestStatus.OPEN
                ? matchingService.findMatches(requestId)
                : List.of();

        // Shared across every candidate: the buyer behind this request is resolved once, not
        // once per scored listing.
        Lookups lookups = new Lookups();

        List<MatchResponseDTO> fresh = results.stream()
                .map(result -> upsertSuggestion(result, request, lookups))
                .filter(dto -> dto != null)
                // upsertSuggestion still refreshes an already-REQUESTED row's score/cost under
                // the hood (in case it's read elsewhere), but "Find Matches" only ever shows
                // genuinely still-unrequested candidates.
                .filter(dto -> dto.status() == MatchStatus.SUGGESTED)
                .sorted(Comparator.comparing(MatchResponseDTO::compatibilityScore).reversed())
                .toList();

        return fresh;
    }

    // "Pending Requests" (status=REQUESTED) / "Order Status" (status=FINAL) / everything the
    // buyer has ever acted on (no filter) - a history/tracking view across ALL of this buyer's
    // requests, never a browse-candidates view (that's getRankedMatchesWithCost's job).
    @Transactional
    public List<MatchResponseDTO> getSentMatchesForBuyer(Long buyerId, String statusFilter) {
        List<CarbonRequest> requests = carbonRequestRepository.findByBuyerId(buyerId);
        Map<Long, CarbonRequest> requestsById = requests.stream()
                .collect(Collectors.toMap(CarbonRequest::getId, r -> r));
        if (requestsById.isEmpty()) {
            return List.of();
        }

        Set<MatchStatus> allowedStatuses = resolveStatusFilter(statusFilter, SENT_STATUSES);

        List<Match> matches = matchRepository.findByRequestIdIn(new ArrayList<>(requestsById.keySet())).stream()
                .filter(match -> SENT_STATUSES.contains(match.getStatus()))
                .filter(match -> allowedStatuses.contains(match.getStatus()))
                .toList();

        markViewed(matches, BUYER_NOTIFY_STATUSES);

        // One memo shared by every row: this buyer is resolved once instead of once per match,
        // each distinct listing/emitter once instead of once per match that references it, and
        // all the order ids come back in a single query rather than one per decided row.
        Lookups lookups = new Lookups();
        lookups.prefetchOrderIds(matches);
        lookups.prefetchRequestDisplayIds(requestsById.values());

        List<MatchAndListing> pairs = matches.stream()
                .map(match -> new MatchAndListing(match, lookups.listing(match.getListingId())))
                .toList();
        lookups.prefetchListingDisplayIds(pairs.stream().map(MatchAndListing::listing).toList());

        return pairs.stream()
                .map(pair -> toResponseDto(
                        pair.match(), pair.listing(), requestsById.get(pair.match().getRequestId()),
                        Viewpoint.BUYER, lookups))
                .sorted(Comparator.comparing(MatchResponseDTO::createdAt).reversed())
                .toList();
    }

    @Transactional
    public MatchResponseDTO accept(Long matchId) {
        Match match = requireMatch(matchId);
        requireUndecided(match);

        Listing listing = requireListing(match.getListingId());
        CarbonRequest request = requireRequest(match.getRequestId());

        requireStillOpen(listing, request);

        // A short listing used to be filled partially - min(ask, remaining) - which quietly
        // rewrote the deal: the buyer asked for 15 tons, saw a quote for 15 tons, and ended up
        // with an order for 10. Neither side agreed to those terms. Accepting is the moment of
        // commitment, so it either honours the ask in full or refuses and says why. Short
        // listings are still surfaced as matches (scored down for the shortfall) so the buyer can
        // see who holds most of what they need - they just can't be turned into a deal.
        if (listing.getRemainingVolumeTons() < request.getMinVolumeNeeded()) {
            throw new ConflictException("Only " + round2(listing.getRemainingVolumeTons())
                    + " tons remain on listing " + listing.getId() + ", but this request needs "
                    + round2(request.getMinVolumeNeeded()) + " tons");
        }

        // Stock is deducted right here, the moment a match is ACCEPTED - never before (a
        // SUGGESTED/REQUESTED match never touches remainingVolumeTons) and never at some later
        // step, since there is no later fulfillment step in this model.
        double transactedVolumeTons = round2(request.getMinVolumeNeeded());
        double remainingAfter = round2(Math.max(0.0, listing.getRemainingVolumeTons() - transactedVolumeTons));

        match.setStatus(MatchStatus.ACCEPTED);
        match.setTransactedVolumeTons(transactedVolumeTons);
        match.setViewed(false);

        listing.setRemainingVolumeTons(remainingAfter);
        listing.setStatus(remainingAfter < MIN_REMAINING_VOLUME_TONS ? ListingStatus.CLOSED : ListingStatus.ACTIVE);

        request.setStatus(RequestStatus.MATCHED);

        matchRepository.save(match);
        listingRepository.save(listing);
        carbonRequestRepository.save(request);

        // The permanent settlement record - snapshotted now, using the volume actually
        // transacted (not the buyer's original ask), so it never drifts if the listing's price
        // or a later distance lookup changes.
        CostBreakdown settlementCost = costEstimatorService.estimateCost(
                listing, transactedVolumeTons, match.getDistanceKm(), match.getDistanceSource());
        orderService.createOrderForAcceptedMatch(match, listing, request, settlementCost);

        return toResponseDto(match, listing, request, Viewpoint.EMITTER);
    }

    @Transactional
    public MatchResponseDTO reject(Long matchId) {
        Match match = requireMatch(matchId);
        requireUndecided(match);

        Listing listing = requireListing(match.getListingId());
        CarbonRequest request = requireRequest(match.getRequestId());

        requireStillOpen(listing, request);

        // No stock was ever deducted for a SUGGESTED/REQUESTED match, so rejecting it touches
        // neither the listing nor the request's volume - nothing to restore.
        match.setStatus(MatchStatus.REJECTED);
        match.setViewed(false);
        matchRepository.save(match);

        return toResponseDto(match, listing, request, Viewpoint.EMITTER);
    }

    // Buyer withdraws their own already-REQUESTED interest in a listing. No stock was ever
    // deducted for a REQUESTED match (only accept() deducts), so there's nothing to restore.
    @Transactional
    public MatchResponseDTO cancel(Long matchId) {
        Match match = requireMatch(matchId);
        if (match.getStatus() != MatchStatus.REQUESTED) {
            throw new ConflictException("Match " + match.getId() + " is not currently requested, so it cannot be cancelled");
        }

        Listing listing = requireListing(match.getListingId());
        CarbonRequest request = requireRequest(match.getRequestId());

        match.setStatus(MatchStatus.CANCELLED);
        match.setViewed(false);
        matchRepository.save(match);

        // A buyer can have several REQUESTED matches open against one request at the same time.
        // If a DIFFERENT one of them was already accepted, this request is MATCHED and owns a
        // live order - reopening it here would let the same request be fulfilled twice. Since
        // cancel() only ever runs on a REQUESTED match, a MATCHED request always means some
        // other match is the accepted one, so leave its status alone. In the ordinary case the
        // request is still OPEN anyway and this is a no-op.
        if (request.getStatus() != RequestStatus.MATCHED) {
            request.setStatus(RequestStatus.OPEN);
            carbonRequestRepository.save(request);
        }

        return toResponseDto(match, listing, request, Viewpoint.BUYER);
    }

    // Undoes a previously-ACCEPTED match: restores the deducted stock immediately, reopens the
    // listing (unless the restored amount still falls under the auto-close threshold) and
    // reopens the request so the buyer is actionable again.
    @Transactional
    public MatchResponseDTO revert(Long matchId) {
        Match match = requireMatch(matchId);
        if (match.getStatus() != MatchStatus.ACCEPTED) {
            throw new ConflictException("Match " + match.getId() + " is not currently accepted, so it cannot be reverted");
        }

        // Once the emitter has marked the shipment as dispatched, the CO2 has physically left
        // their site. Restoring the listing's stock at that point would re-sell volume that's
        // already on a truck, so reversal is only available while delivery is still CONFIRMED.
        DeliveryStatus deliveryStatus = orderService.findDeliveryStatusByMatchId(match.getId());
        if (deliveryStatus != null && deliveryStatus != DeliveryStatus.CONFIRMED) {
            throw new ConflictException("Match " + match.getId() + " has already been dispatched ("
                    + deliveryStatus + "), so it can no longer be reversed");
        }

        Listing listing = requireListing(match.getListingId());
        CarbonRequest request = requireRequest(match.getRequestId());

        double restoredVolume = round2(Math.min(
                listing.getTotalVolumeTons(),
                listing.getRemainingVolumeTons() + match.getTransactedVolumeTons()));

        listing.setRemainingVolumeTons(restoredVolume);
        listing.setStatus(restoredVolume < MIN_REMAINING_VOLUME_TONS ? ListingStatus.CLOSED : ListingStatus.ACTIVE);

        request.setStatus(RequestStatus.OPEN);

        match.setStatus(MatchStatus.REVERTED);
        match.setViewed(false);

        matchRepository.save(match);
        listingRepository.save(listing);
        carbonRequestRepository.save(request);

        // The order stays as a permanent record - just flipped to REVERTED, not deleted.
        orderService.markRevertedForMatch(match.getId());

        return toResponseDto(match, listing, request, Viewpoint.EMITTER);
    }

    @Transactional
    public MatchResponseDTO request(Long matchId) {
        Match match = requireMatch(matchId);
        requireUndecided(match);

        Listing listing = requireListing(match.getListingId());
        CarbonRequest request = requireRequest(match.getRequestId());

        match.setStatus(MatchStatus.REQUESTED);
        matchRepository.save(match);

        return toResponseDto(match, listing, request, Viewpoint.BUYER);
    }

    // "Request This Listing" (Quick Browse): the buyer supplies only who they are and the
    // volume they need, against one specific listing they already picked - no request-builder
    // form, no SUGGESTED stage (there's no scoring/browsing step to suggest anything from).
    // Creates a brand-new CarbonRequest with permissive defaults, then an already-REQUESTED
    // Match against it, so it flows straight into "Pending Requests" like any other request.
    @Transactional
    public MatchResponseDTO quickRequest(Long listingId, QuickRequestDTO dto) {
        Listing listing = requireListing(listingId);
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new ConflictException("Listing " + listingId + " is no longer active");
        }
        // The buyer picked this exact listing off a screen showing its remaining volume, so asking
        // for more than that is a plain error, not a partial match worth ranking. The Quick Browse
        // form already caps its input - this is the server-side half of that rule, because a check
        // that lives only in the browser isn't a rule.
        if (dto.minVolumeNeeded() > listing.getRemainingVolumeTons()) {
            throw new ConflictException("Listing " + listingId + " has only "
                    + listing.getRemainingVolumeTons() + " tons available");
        }
        User buyer = userService.requireUserWithRole(dto.buyerId(), Role.BUYER);

        CarbonRequest request = CarbonRequest.builder()
                .buyerId(dto.buyerId())
                .minVolumeNeeded(dto.minVolumeNeeded())
                .minPurityRequired(QUICK_REQUEST_MIN_PURITY_REQUIRED)
                .maxDistanceKm(QUICK_REQUEST_MAX_DISTANCE_KM)
                .maxBudgetPerTon(QUICK_REQUEST_MAX_BUDGET_PER_TON)
                .intendedUse(QUICK_REQUEST_INTENDED_USE)
                .build();
        request = carbonRequestRepository.save(request);

        DistanceResult distanceResult = distanceService.getDistance(
                listing.getLocationLat(), listing.getLocationLng(),
                buyer.getLocationLat(), buyer.getLocationLng());

        ScoreBreakdown scoreBreakdown = MatchingService.scoreListing(listing, request, distanceResult.distanceKm());
        double compatibilityScore = MatchingService.weightedScore(scoreBreakdown);

        CostBreakdown costBreakdown = costEstimatorService.estimateCost(
                listing, dto.minVolumeNeeded(), distanceResult.distanceKm(), distanceResult.source());

        Match match = Match.builder()
                .listingId(listing.getId())
                .requestId(request.getId())
                .compatibilityScore(round2(compatibilityScore))
                .distanceKm(round2(distanceResult.distanceKm()))
                .distanceSource(distanceResult.source())
                .transportCost(costBreakdown.transportCost())
                .totalEstimatedCost(costBreakdown.totalCost())
                .status(MatchStatus.REQUESTED)
                .build();
        match = matchRepository.save(match);

        return toResponseDto(match, listing, request, Viewpoint.BUYER);
    }

    // "Incoming Matches" (default / status=REQUESTED - buyer has sent interest, emitter needs to
    // act) / "Order Status" (status=FINAL - archive of decided matches for this listing).
    @Transactional
    public List<MatchResponseDTO> getMatchesForListing(Long listingId, String statusFilter) {
        Listing listing = requireListing(listingId);

        List<Match> matches = matchRepository.findByListingId(listingId);

        // This is the emitter opening their own incoming-matches view - mark any of their
        // not-yet-seen status changes (a buyer cancelling) as viewed now.
        markViewed(matches, EMITTER_NOTIFY_STATUSES);

        Set<MatchStatus> allowedStatuses = resolveStatusFilter(statusFilter, Set.of(MatchStatus.REQUESTED));

        // Every row here is against this one listing, so its emitter is resolved once rather
        // than once per row, and the order ids come back in a single query.
        Lookups lookups = new Lookups();
        lookups.seedListing(listing);
        lookups.prefetchOrderIds(matches);
        lookups.prefetchListingDisplayIds(List.of(listing));

        return matches.stream()
                .filter(match -> allowedStatuses.contains(match.getStatus()))
                .map(match -> new MatchAndRequest(match, requireRequest(match.getRequestId())))
                .filter(pair -> isVisibleWhenStale(pair.match(), listing, pair.request()))
                .map(pair -> toResponseDto(pair.match(), listing, pair.request(), Viewpoint.EMITTER, lookups))
                .toList();
    }

    // Shared status-filter parsing for the two history/browse endpoints above: a plain
    // MatchStatus name (e.g. "REQUESTED"), the special value "FINAL" (any decided/terminal
    // status), or no param at all (falls back to the caller's own default).
    private Set<MatchStatus> resolveStatusFilter(String rawStatus, Set<MatchStatus> defaultStatuses) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return defaultStatuses;
        }
        if ("FINAL".equalsIgnoreCase(rawStatus)) {
            return DECIDED_STATUSES;
        }
        try {
            return Set.of(MatchStatus.valueOf(rawStatus.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("status must be a valid match status or 'FINAL'");
        }
    }

    // "NEW" badge support: a lightweight peek that does NOT mark anything as viewed, so it's
    // safe to check before the user has actually opened the relevant tab.
    @Transactional(readOnly = true)
    public UnviewedCountDTO getUnviewedCount(Long buyerId, Long emitterId) {
        if ((buyerId == null) == (emitterId == null)) {
            throw new BadRequestException("exactly one of buyerId or emitterId is required");
        }
        return new UnviewedCountDTO(buyerId != null ? countUnviewedForBuyer(buyerId) : countUnviewedForEmitter(emitterId));
    }

    private long countUnviewedForBuyer(Long buyerId) {
        List<Long> requestIds = carbonRequestRepository.findByBuyerId(buyerId).stream()
                .map(CarbonRequest::getId)
                .toList();
        if (requestIds.isEmpty()) {
            return 0;
        }
        return matchRepository.findByRequestIdIn(requestIds).stream()
                .filter(match -> BUYER_NOTIFY_STATUSES.contains(match.getStatus()) && !match.isViewed())
                .count();
    }

    private long countUnviewedForEmitter(Long emitterId) {
        List<Long> listingIds = listingRepository.findByEmitterId(emitterId).stream()
                .map(Listing::getId)
                .toList();
        if (listingIds.isEmpty()) {
            return 0;
        }
        return matchRepository.findByListingIdIn(listingIds).stream()
                .filter(match -> EMITTER_NOTIFY_STATUSES.contains(match.getStatus()) && !match.isViewed())
                .count();
    }

    private void markViewed(List<Match> matches, Set<MatchStatus> notifyStatuses) {
        List<Match> toMark = matches.stream()
                .filter(match -> notifyStatuses.contains(match.getStatus()) && !match.isViewed())
                .toList();
        if (!toMark.isEmpty()) {
            toMark.forEach(match -> match.setViewed(true));
            matchRepository.saveAll(toMark);
        }
    }

    private record MatchAndRequest(Match match, CarbonRequest request) {
    }

    private record MatchAndListing(Match match, Listing listing) {
    }

    // Per-call memo for the id -> row lookups toResponseDto needs.
    //
    // The list views are where this matters: every row of "My Sent Requests" belongs to the
    // same buyer, and every row of "Incoming Matches" belongs to the same emitter, but each row
    // re-queried them independently - so a 20-row tab issued 20 identical user lookups (plus
    // another 20 for the emitter on the decided rows). Memoising collapses each repeated
    // lookup to one.
    //
    // Anything not already seen falls through to a direct lookup, which is what the
    // single-match paths (accept/reject/cancel/revert/request) rely on - they build a fresh,
    // empty Lookups and behave exactly as before.
    private final class Lookups {

        private final Map<Long, User> users = new HashMap<>();
        private final Map<Long, Listing> listings = new HashMap<>();

        // Null until prefetched. Distinguishes "no order for this match" (present, maps to
        // null) from "orders were never prefetched, go ask" (map itself null).
        private Map<Long, Long> orderIdsByMatchId;

        User user(Long id) {
            return users.computeIfAbsent(id, userService::requireUser);
        }

        Listing listing(Long id) {
            return listings.computeIfAbsent(id, MatchService.this::requireListing);
        }

        void seedListing(Listing listing) {
            listings.put(listing.getId(), listing);
        }

        void prefetchOrderIds(List<Match> matches) {
            List<Long> matchIds = matches.stream()
                    .filter(match -> HAS_ORDER_STATUSES.contains(match.getStatus()))
                    .map(Match::getId)
                    .toList();
            orderIdsByMatchId = orderService.findOrderIdsByMatchIds(matchIds);
        }

        Long orderId(Long matchId) {
            return orderIdsByMatchId != null
                    ? orderIdsByMatchId.get(matchId)
                    : orderService.findOrderIdByMatchId(matchId);
        }

        // Per-owner display numbers, memoised the same way as everything else here: one
        // resolution covers every row in the view instead of one per match.
        private Map<Long, Integer> listingDisplayIds;
        private Map<Long, Integer> requestDisplayIds;

        // Always copied into a mutable map: DisplayIdService returns Map.of() for an empty
        // input, and computeIfAbsent on that throws.
        Integer listingDisplayId(Listing listing) {
            if (listingDisplayIds == null) {
                listingDisplayIds = new HashMap<>();
            }
            return listingDisplayIds.computeIfAbsent(listing.getId(),
                    id -> displayIdService.listingDisplayId(listing));
        }

        Integer requestDisplayId(CarbonRequest request) {
            if (requestDisplayIds == null) {
                requestDisplayIds = new HashMap<>();
            }
            return requestDisplayIds.computeIfAbsent(request.getId(),
                    id -> displayIdService.requestDisplayId(request));
        }

        // List views resolve every row's numbering up front in one go; the per-row accessors
        // above then just read the map, and only fall back to a lookup for anything missing.
        void prefetchListingDisplayIds(Collection<Listing> listings) {
            listingDisplayIds = new HashMap<>(displayIdService.listingDisplayIds(listings));
        }

        void prefetchRequestDisplayIds(Collection<CarbonRequest> requests) {
            requestDisplayIds = new HashMap<>(displayIdService.requestDisplayIds(requests));
        }
    }

    private MatchResponseDTO upsertSuggestion(MatchResult result, CarbonRequest request, Lookups lookups) {
        Match existing = matchRepository.findByListingIdAndRequestId(result.listingId(), result.requestId())
                .orElse(null);

        if (existing != null && DECIDED_STATUSES.contains(existing.getStatus())) {
            // Already decided (accepted/rejected/cancelled/reverted) - don't resurface it as a
            // new suggestion; it keeps its own history line instead.
            return null;
        }

        Listing listing = lookups.listing(result.listingId());
        User buyer = lookups.user(request.getBuyerId());

        // Set explicitly rather than relying on @PrePersist's null-check default - "Find Matches"
        // filters on this status being exactly SUGGESTED, so it needs to be set in memory immediately.
        Match match = existing != null ? existing : Match.builder()
                .listingId(result.listingId())
                .requestId(result.requestId())
                .status(MatchStatus.SUGGESTED)
                .build();

        match.setCompatibilityScore(result.compatibilityScore());
        match.setDistanceKm(result.distanceKm());
        match.setDistanceSource(result.distanceSource());

        CostBreakdown costBreakdown = costEstimatorService.estimateCost(
                listing, request.getMinVolumeNeeded(), result.distanceKm(), result.distanceSource());
        match.setTransportCost(costBreakdown.transportCost());
        match.setTotalEstimatedCost(costBreakdown.totalCost());

        match = matchRepository.save(match);

        return new MatchResponseDTO(
                match.getId(),
                match.getListingId(),
                match.getRequestId(),
                // "Find Matches" is a buyer-only view: their own request number, never the
                // emitter's listing number.
                null,
                lookups.requestDisplayId(request),
                match.getCompatibilityScore(),
                match.getDistanceKm(),
                match.getDistanceSource(),
                match.getStatus(),
                result.scoreBreakdown(),
                costBreakdown,
                match.getTransactedVolumeTons(),
                match.getCreatedAt(),
                null, // SUGGESTED/REQUESTED never carries a status message
                match.isViewed(),
                listing.getCity(),
                buyer.getCity(),
                null); // SUGGESTED/REQUESTED never has an order behind them
    }

    // Single-match entry point (accept/reject/cancel/revert/request/quickRequest) - one row, so
    // there's nothing to share and a fresh memo is free.
    private MatchResponseDTO toResponseDto(Match match, Listing listing, CarbonRequest request, Viewpoint viewpoint) {
        return toResponseDto(match, listing, request, viewpoint, new Lookups());
    }

    private MatchResponseDTO toResponseDto(Match match, Listing listing, CarbonRequest request,
                                            Viewpoint viewpoint, Lookups lookups) {
        // Once accepted, transactedVolumeTons is the real committed volume - cost should reflect
        // that, not the (possibly larger) volume the buyer originally asked for.
        double effectiveVolumeTons = match.getTransactedVolumeTons() != null
                ? match.getTransactedVolumeTons()
                : request.getMinVolumeNeeded();

        ScoreBreakdown scoreBreakdown = MatchingService.scoreListing(listing, request, match.getDistanceKm());
        CostBreakdown costBreakdown = costEstimatorService.estimateCost(
                listing, effectiveVolumeTons, match.getDistanceKm(), match.getDistanceSource());

        User buyer = lookups.user(request.getBuyerId());
        Long orderId = HAS_ORDER_STATUSES.contains(match.getStatus())
                ? lookups.orderId(match.getId())
                : null;

        return new MatchResponseDTO(
                match.getId(),
                match.getListingId(),
                match.getRequestId(),
                // Only the side that belongs to whoever is looking. The counterparty's entity
                // is identified by its global id above; their personal number is never sent.
                viewpoint == Viewpoint.EMITTER ? lookups.listingDisplayId(listing) : null,
                viewpoint == Viewpoint.BUYER ? lookups.requestDisplayId(request) : null,
                match.getCompatibilityScore(),
                match.getDistanceKm(),
                match.getDistanceSource(),
                match.getStatus(),
                scoreBreakdown,
                costBreakdown,
                match.getTransactedVolumeTons(),
                match.getCreatedAt(),
                computeStatusMessage(match, listing, buyer, viewpoint, lookups),
                match.isViewed(),
                listing.getCity(),
                buyer.getCity(),
                orderId);
    }

    // Phrased from whichever side is looking: the buyer's "My Sent Requests" tab always talks
    // about what the *emitter* did to *their* request, and vice versa for the emitter's
    // "Incoming Matches" tab. SUGGESTED/REQUESTED carry no message - they're not a "change" yet.
    private String computeStatusMessage(Match match, Listing listing, User buyer, Viewpoint viewpoint,
                                         Lookups lookups) {
        return switch (match.getStatus()) {
            case REJECTED -> viewpoint == Viewpoint.BUYER
                    ? "Your request was declined by " + emitterCompanyName(listing, lookups)
                    : "You declined this request from " + buyer.getCompanyName();
            case CANCELLED -> viewpoint == Viewpoint.EMITTER
                    ? "This request was withdrawn by " + buyer.getCompanyName()
                    : "You withdrew this request";
            case ACCEPTED -> viewpoint == Viewpoint.BUYER
                    ? "Accepted by " + emitterCompanyName(listing, lookups) + " — " + match.getTransactedVolumeTons() + " tons confirmed"
                    : "You accepted this request from " + buyer.getCompanyName() + " — " + match.getTransactedVolumeTons() + " tons confirmed";
            case REVERTED -> viewpoint == Viewpoint.BUYER
                    ? "This match was reversed by " + emitterCompanyName(listing, lookups) + " — your request has been reopened"
                    : "You reversed this match — " + buyer.getCompanyName() + "'s request has been reopened";
            case SUGGESTED, REQUESTED -> null;
        };
    }

    private String emitterCompanyName(Listing listing, Lookups lookups) {
        return lookups.user(listing.getEmitterId()).getCompanyName();
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void requireUndecided(Match match) {
        if (DECIDED_STATUSES.contains(match.getStatus())) {
            throw new ConflictException(
                    "Match " + match.getId() + " has already been " + match.getStatus().name().toLowerCase());
        }
    }

    // Guards accept()/reject(): a SUGGESTED/REQUESTED match can go stale between being shown to
    // a user and being acted on - e.g. another buyer claimed the listing's remaining stock, or
    // this same request got matched elsewhere in the meantime. Never silently process a decision
    // against a listing/request that's moved on; surface it as a clear 409 instead.
    private void requireStillOpen(Listing listing, CarbonRequest request) {
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new ConflictException("Listing " + listing.getId() + " is no longer active");
        }
        if (request.getStatus() != RequestStatus.OPEN) {
            throw new ConflictException("Request " + request.getId() + " is no longer open");
        }
    }

    // Decided matches are history and always stay visible. An undecided one is only worth
    // surfacing while it's still actionable - once its listing/request has moved on, it's
    // stale: filter it out at the source rather than shipping a dead Accept/Reject button to
    // the frontend.
    private boolean isVisibleWhenStale(Match match, Listing listing, CarbonRequest request) {
        if (DECIDED_STATUSES.contains(match.getStatus())) {
            return true;
        }
        return listing.getStatus() == ListingStatus.ACTIVE && request.getStatus() == RequestStatus.OPEN;
    }

    private Match requireMatch(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found with id: " + id));
    }

    private Listing requireListing(Long id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found with id: " + id));
    }

    private CarbonRequest requireRequest(Long id) {
        return carbonRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id: " + id));
    }
}
