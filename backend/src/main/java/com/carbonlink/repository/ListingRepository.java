package com.carbonlink.repository;

import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    List<Listing> findByStatus(ListingStatus status);

    List<Listing> findByEmitterId(Long emitterId);

    // Creation order for an emitter's own listings, which is what their per-emitter display
    // numbering is derived from. Ordered by id as a tiebreaker so two listings created in the
    // same millisecond still get a stable, repeatable order.
    List<Listing> findByEmitterIdInOrderByCreatedAtAscIdAsc(List<Long> emitterIds);
}
