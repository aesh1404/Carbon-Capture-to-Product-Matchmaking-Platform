package com.carbonlink.repository;

import com.carbonlink.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByMatchId(Long matchId);

    List<Order> findByMatchIdIn(List<Long> matchIds);

    List<Order> findByEmitterIdOrBuyerId(Long emitterId, Long buyerId);

    long countByOrderNumberStartingWith(String prefix);
}
