package com.carbonlink.repository;

import com.carbonlink.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByListingId(Long listingId);

    List<Match> findByListingIdIn(List<Long> listingIds);

    List<Match> findByRequestIdIn(List<Long> requestIds);

    Optional<Match> findByListingIdAndRequestId(Long listingId, Long requestId);
}
