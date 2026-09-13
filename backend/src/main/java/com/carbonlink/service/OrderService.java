package com.carbonlink.service;

import com.carbonlink.dto.CostBreakdown;
import com.carbonlink.dto.OrderResponseDTO;
import com.carbonlink.entity.CarbonRequest;
import com.carbonlink.entity.DeliveryStatus;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.Match;
import com.carbonlink.entity.Order;
import com.carbonlink.entity.OrderStatus;
import com.carbonlink.entity.PaymentStatus;
import com.carbonlink.exception.BadRequestException;
import com.carbonlink.exception.ConflictException;
import com.carbonlink.exception.ResourceNotFoundException;
import com.carbonlink.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderService {

    // Delivery only ever moves forward one step at a time; DELIVERED has no entry, so it's a
    // dead end. Kept as an explicit map (rather than enum ordinal math) so the allowed sequence
    // is obvious at a glance.
    private static final Map<DeliveryStatus, DeliveryStatus> NEXT_DELIVERY_STATUS = Map.of(
            DeliveryStatus.CONFIRMED, DeliveryStatus.IN_TRANSIT,
            DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final OrderNumberGenerator orderNumberGenerator;

    public OrderService(OrderRepository orderRepository, OrderNumberGenerator orderNumberGenerator) {
        this.orderRepository = orderRepository;
        this.orderNumberGenerator = orderNumberGenerator;
    }

    // Called from MatchService.accept() - snapshots the actual agreed numbers (not a reference
    // back to the match/listing) so this order's record never changes even if the listing's
    // price or a later distance lookup does.
    @Transactional
    public OrderResponseDTO createOrderForAcceptedMatch(Match match, Listing listing, CarbonRequest request,
                                                          CostBreakdown settlementCost) {
        Order order = Order.builder()
                .orderNumber(orderNumberGenerator.next())
                .matchId(match.getId())
                .emitterId(listing.getEmitterId())
                .buyerId(request.getBuyerId())
                .listingId(listing.getId())
                .requestId(request.getId())
                .transactedVolumeTons(match.getTransactedVolumeTons())
                .pricePerTon(listing.getPricePerTon())
                .basePrice(settlementCost.basePrice())
                .transportCost(settlementCost.transportCost())
                .totalCost(settlementCost.totalCost())
                .distanceKm(settlementCost.distanceKm())
                .distanceSource(settlementCost.distanceSource())
                .deliveryStatus(DeliveryStatus.CONFIRMED)
                .build();

        return toResponseDto(orderRepository.save(order));
    }

    // Called from MatchService.revert() - the order stays as a permanent record, just flipped
    // to REVERTED so its history (and the receipt view) reflects that it no longer stands.
    @Transactional
    public void markRevertedForMatch(Long matchId) {
        orderRepository.findByMatchId(matchId).ifPresent(order -> {
            order.setStatus(OrderStatus.REVERTED);
            orderRepository.save(order);
        });
    }

    @Transactional(readOnly = true)
    public Long findOrderIdByMatchId(Long matchId) {
        return orderRepository.findByMatchId(matchId).map(Order::getId).orElse(null);
    }

    // Used by MatchService.revert() to refuse reversing a match whose goods have already left
    // the emitter's site. Null when the match never had an order behind it.
    @Transactional(readOnly = true)
    public DeliveryStatus findDeliveryStatusByMatchId(Long matchId) {
        return orderRepository.findByMatchId(matchId).map(Order::getDeliveryStatus).orElse(null);
    }

    // Batch form of findOrderIdByMatchId for the match list views, which would otherwise issue
    // one order query per decided row. Matches with no order behind them are simply absent
    // from the result.
    @Transactional(readOnly = true)
    public Map<Long, Long> findOrderIdsByMatchIds(List<Long> matchIds) {
        if (matchIds.isEmpty()) {
            return Map.of();
        }
        return orderRepository.findByMatchIdIn(matchIds).stream()
                .collect(Collectors.toMap(Order::getMatchId, Order::getId));
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO getOrder(Long id) {
        return toResponseDto(requireOrder(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getOrdersForUser(Long userId) {
        return orderRepository.findByEmitterIdOrBuyerId(userId, userId).stream()
                .map(this::toResponseDto)
                .sorted(Comparator.comparing(OrderResponseDTO::createdAt).reversed())
                .toList();
    }

    // Marks the buyer's payment as settled, which also completes the order.
    //
    // Payment is the last thing that has to happen in this model - delivery was already
    // blocked waiting on it - so settling up finishes the shipment rather than leaving it a
    // step short and expecting the emitter to come back and click one more button. The
    // progress bar reads as fully complete because every earlier step counts as reached once
    // the status is DELIVERED.
    //
    // Idempotent: paying twice is a no-op rather than an error, because a double-submit
    // shouldn't look like a failure to the person clicking.
    @Transactional
    public OrderResponseDTO markPaid(Long id) {
        Order order = requireOrder(id);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ConflictException("Order " + id + " is not CONFIRMED, so it cannot be paid");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return toResponseDto(order);
        }
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setDeliveryStatus(DeliveryStatus.DELIVERED);
        return toResponseDto(orderRepository.save(order));
    }

    // Only the emitter advances shipment progress, one step at a time: CONFIRMED -> IN_TRANSIT
    // -> DELIVERED. Blocked entirely once the order itself isn't CONFIRMED (e.g. REVERTED), and
    // blocked once delivery has reached DELIVERED - there's no further step to request.
    @Transactional
    public OrderResponseDTO updateDeliveryStatus(Long id, String rawStatus) {
        Order order = requireOrder(id);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ConflictException(
                    "Order " + id + " is not CONFIRMED, so its delivery status cannot be updated");
        }

        DeliveryStatus requested = parseDeliveryStatus(rawStatus);
        DeliveryStatus expectedNext = NEXT_DELIVERY_STATUS.get(order.getDeliveryStatus());
        if (expectedNext == null || expectedNext != requested) {
            throw new ConflictException(
                    "Cannot move delivery status from " + order.getDeliveryStatus() + " to " + requested);
        }

        // Handover is the point of no return: once an order reads DELIVERED the buyer has the
        // goods, so it must not get there while the money is still outstanding. Dispatch
        // (IN_TRANSIT) is deliberately still allowed unpaid - goods can be on the road while
        // payment clears - but the final step waits for it.
        if (requested == DeliveryStatus.DELIVERED && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new ConflictException(
                    "Order " + id + " cannot be marked delivered until the buyer has paid");
        }

        order.setDeliveryStatus(requested);
        return toResponseDto(orderRepository.save(order));
    }

    private DeliveryStatus parseDeliveryStatus(String rawStatus) {
        if (rawStatus == null) {
            throw new BadRequestException("status must be one of IN_TRANSIT or DELIVERED");
        }
        try {
            return DeliveryStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("status must be one of IN_TRANSIT or DELIVERED");
        }
    }

    private Order requireOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    private OrderResponseDTO toResponseDto(Order order) {
        return new OrderResponseDTO(
                order.getId(),
                order.getOrderNumber(),
                order.getMatchId(),
                order.getEmitterId(),
                order.getBuyerId(),
                order.getListingId(),
                order.getRequestId(),
                order.getTransactedVolumeTons(),
                order.getPricePerTon(),
                order.getBasePrice(),
                order.getTransportCost(),
                order.getTotalCost(),
                order.getDistanceKm(),
                order.getDistanceSource(),
                order.getStatus(),
                order.getDeliveryStatus(),
                order.getPaymentStatus(),
                order.getCreatedAt());
    }
}
