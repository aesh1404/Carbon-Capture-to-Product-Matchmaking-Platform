package com.carbonlink.repository;

import com.carbonlink.entity.CarbonRequest;
import com.carbonlink.entity.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarbonRequestRepository extends JpaRepository<CarbonRequest, Long> {

    List<CarbonRequest> findByStatus(RequestStatus status);

    List<CarbonRequest> findByBuyerId(Long buyerId);

    // Same idea as ListingRepository's ordered finder - backs per-buyer request numbering.
    List<CarbonRequest> findByBuyerIdInOrderByCreatedAtAscIdAsc(List<Long> buyerIds);
}
