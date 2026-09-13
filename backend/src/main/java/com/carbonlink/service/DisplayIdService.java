package com.carbonlink.service;

import com.carbonlink.entity.CarbonRequest;
import com.carbonlink.entity.Listing;
import com.carbonlink.repository.CarbonRequestRepository;
import com.carbonlink.repository.ListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a global database id into a number that means something to the person looking at it.
 *
 * <p>An emitter with three listings does not care that theirs are rows 4, 9 and 17 of a shared
 * table - to them these are their 1st, 2nd and 3rd listings. This resolves that per-owner
 * sequence, ordered by creation, for display only.
 *
 * <p>Nothing is persisted and nothing internal changes: every relation, lookup, URL and foreign
 * key still uses the real global id. A display number is a label, never an identifier - it is
 * only stable within one owner's own set, and two different emitters both have a "Listing #1".
 *
 * <p>The naive way to compute this is a count query per row ("how many of this emitter's
 * listings predate this one?"), which is a textbook N+1. These methods take whole collections
 * and resolve them in a single query across every owner involved, producing identical numbers.
 */
@Service
public class DisplayIdService {

    private final ListingRepository listingRepository;
    private final CarbonRequestRepository carbonRequestRepository;

    public DisplayIdService(ListingRepository listingRepository,
                             CarbonRequestRepository carbonRequestRepository) {
        this.listingRepository = listingRepository;
        this.carbonRequestRepository = carbonRequestRepository;
    }

    /** listing id -> that listing's 1-based position among its own emitter's listings. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> listingDisplayIds(Collection<Listing> listings) {
        List<Long> emitterIds = distinct(listings.stream().map(Listing::getEmitterId).toList());
        if (emitterIds.isEmpty()) {
            return Map.of();
        }
        return positionsByOwner(
                listingRepository.findByEmitterIdInOrderByCreatedAtAscIdAsc(emitterIds),
                Listing::getEmitterId, Listing::getId);
    }

    /** request id -> that request's 1-based position among its own buyer's requests. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> requestDisplayIds(Collection<CarbonRequest> requests) {
        List<Long> buyerIds = distinct(requests.stream().map(CarbonRequest::getBuyerId).toList());
        if (buyerIds.isEmpty()) {
            return Map.of();
        }
        return positionsByOwner(
                carbonRequestRepository.findByBuyerIdInOrderByCreatedAtAscIdAsc(buyerIds),
                CarbonRequest::getBuyerId, CarbonRequest::getId);
    }

    /** Convenience for the single-row paths (create/fetch one listing). */
    @Transactional(readOnly = true)
    public Integer listingDisplayId(Listing listing) {
        return listingDisplayIds(List.of(listing)).get(listing.getId());
    }

    /** Convenience for the single-row paths (create/fetch one request). */
    @Transactional(readOnly = true)
    public Integer requestDisplayId(CarbonRequest request) {
        return requestDisplayIds(List.of(request)).get(request.getId());
    }

    // Walks each owner's rows in creation order and numbers them 1, 2, 3... The rows arrive
    // already sorted by the repository, so this is a single pass with a per-owner counter.
    private static <T> Map<Long, Integer> positionsByOwner(List<T> ownedRows,
                                                            java.util.function.Function<T, Long> ownerId,
                                                            java.util.function.Function<T, Long> rowId) {
        Map<Long, Integer> nextPosition = new HashMap<>();
        Map<Long, Integer> displayIds = new LinkedHashMap<>();
        for (T row : ownedRows) {
            int position = nextPosition.merge(ownerId.apply(row), 1, Integer::sum);
            displayIds.put(rowId.apply(row), position);
        }
        return displayIds;
    }

    private static List<Long> distinct(List<Long> ids) {
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }
}
