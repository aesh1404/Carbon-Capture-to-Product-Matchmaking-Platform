package com.carbonlink.service;

import com.carbonlink.dto.CostBreakdown;
import com.carbonlink.dto.OrderResponseDTO;
import com.carbonlink.entity.CarbonRequest;
import com.carbonlink.entity.DeliveryStatus;
import com.carbonlink.entity.DistanceSource;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import com.carbonlink.entity.Match;
import com.carbonlink.entity.MatchStatus;
import com.carbonlink.entity.Order;
import com.carbonlink.entity.OrderStatus;
import com.carbonlink.entity.PaymentStatus;
import com.carbonlink.entity.RequestStatus;
import com.carbonlink.exception.BadRequestException;
import com.carbonlink.exception.ConflictException;
import com.carbonlink.exception.ResourceNotFoundException;
import com.carbonlink.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderNumberGenerator orderNumberGenerator;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderNumberGenerator);
    }

    @Test
    void createOrderForAcceptedMatch_snapshotsAllValuesFromTheMatchListingAndCost() {
        Listing listing = listing();
        CarbonRequest request = request();
        Match match = match(listing.getId(), request.getId());
        match.setTransactedVolumeTons(200.0);
        CostBreakdown cost = new CostBreakdown(180000.0, 6000.0, 186000.0, 200.0, 50.0, DistanceSource.HAVERSINE_FALLBACK);

        when(orderNumberGenerator.next()).thenReturn("CL-2026-0001");
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            o.setCreatedAt(LocalDateTime.now());
            return o;
        });

        OrderResponseDTO response = orderService.createOrderForAcceptedMatch(match, listing, request, cost);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();

        assertThat(saved.getOrderNumber()).isEqualTo("CL-2026-0001");
        assertThat(saved.getMatchId()).isEqualTo(match.getId());
        assertThat(saved.getEmitterId()).isEqualTo(listing.getEmitterId());
        assertThat(saved.getBuyerId()).isEqualTo(request.getBuyerId());
        assertThat(saved.getListingId()).isEqualTo(listing.getId());
        assertThat(saved.getRequestId()).isEqualTo(request.getId());
        assertThat(saved.getTransactedVolumeTons()).isEqualTo(200.0);
        assertThat(saved.getPricePerTon()).isEqualTo(listing.getPricePerTon());
        assertThat(saved.getBasePrice()).isEqualTo(180000.0);
        assertThat(saved.getTransportCost()).isEqualTo(6000.0);
        assertThat(saved.getTotalCost()).isEqualTo(186000.0);
        assertThat(saved.getDistanceKm()).isEqualTo(50.0);
        assertThat(saved.getDistanceSource()).isEqualTo(DistanceSource.HAVERSINE_FALLBACK);
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.CONFIRMED);

        assertThat(response.orderNumber()).isEqualTo("CL-2026-0001");
        assertThat(response.totalCost()).isEqualTo(186000.0);
    }

    @Test
    void markRevertedForMatch_flipsAnExistingOrdersStatusToReverted() {
        Order order = Order.builder().id(5L).matchId(10L).status(OrderStatus.CONFIRMED).build();
        when(orderRepository.findByMatchId(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.markRevertedForMatch(10L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REVERTED);
        verify(orderRepository).save(order);
    }

    @Test
    void markRevertedForMatch_noOrderForThatMatch_doesNothingSilently() {
        when(orderRepository.findByMatchId(999L)).thenReturn(Optional.empty());

        orderService.markRevertedForMatch(999L);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void findOrderIdByMatchId_returnsIdWhenFound() {
        Order order = Order.builder().id(5L).matchId(10L).build();
        when(orderRepository.findByMatchId(10L)).thenReturn(Optional.of(order));

        assertThat(orderService.findOrderIdByMatchId(10L)).isEqualTo(5L);
    }

    @Test
    void findOrderIdByMatchId_returnsNullWhenNotFound() {
        when(orderRepository.findByMatchId(10L)).thenReturn(Optional.empty());

        assertThat(orderService.findOrderIdByMatchId(10L)).isNull();
    }

    @Test
    void getOrder_notFound_throwsResourceNotFoundException() {
        when(orderRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(42L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrder_found_returnsFullDetail() {
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderResponseDTO response = orderService.getOrder(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.orderNumber()).isEqualTo("CL-2026-0001");
        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void getOrdersForUser_returnsOrdersWhereUserIsEitherEmitterOrBuyer_newestFirst() {
        Order older = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        older.setCreatedAt(LocalDateTime.now().minusDays(1));
        Order newer = fullOrder(2L, "CL-2026-0002", 300L, 100L); // this user (100) is the buyer here
        newer.setCreatedAt(LocalDateTime.now());
        when(orderRepository.findByEmitterIdOrBuyerId(100L, 100L)).thenReturn(List.of(older, newer));

        List<OrderResponseDTO> results = orderService.getOrdersForUser(100L);

        assertThat(results).extracting(OrderResponseDTO::id).containsExactly(2L, 1L);
    }

    @Test
    void updateDeliveryStatus_confirmedToInTransit_advancesOneStep() {
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponseDTO response = orderService.updateDeliveryStatus(1L, "IN_TRANSIT");

        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
    }

    @Test
    void updateDeliveryStatus_inTransitToDelivered_advancesFinalStep() {
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        order.setDeliveryStatus(DeliveryStatus.IN_TRANSIT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponseDTO response = orderService.updateDeliveryStatus(1L, "DELIVERED");

        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void updateDeliveryStatus_skippingAheadToDelivered_throwsConflict() {
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDeliveryStatus(1L, "DELIVERED"))
                .isInstanceOf(ConflictException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateDeliveryStatus_alreadyDelivered_throwsConflictOnAnyFurtherUpdate() {
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        order.setDeliveryStatus(DeliveryStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDeliveryStatus(1L, "IN_TRANSIT"))
                .isInstanceOf(ConflictException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateDeliveryStatus_orderNotConfirmedOverall_throwsConflict() {
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        order.setStatus(OrderStatus.REVERTED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDeliveryStatus(1L, "IN_TRANSIT"))
                .isInstanceOf(ConflictException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateDeliveryStatus_invalidStatusString_throwsBadRequest() {
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDeliveryStatus(1L, "SHIPPED"))
                .isInstanceOf(BadRequestException.class);
        verify(orderRepository, never()).save(any());
    }

    // --- Handover waits for payment ---

    @Test
    void updateDeliveryStatus_toDelivered_isBlockedWhilePaymentIsPending() {
        // An order reading DELIVERED means the buyer has the goods. It must not get there
        // while the money is still outstanding - and the emitter can only see that because
        // payment is persisted rather than living in the buyer's browser.
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        order.setDeliveryStatus(DeliveryStatus.IN_TRANSIT);
        order.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDeliveryStatus(1L, "DELIVERED"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("until the buyer has paid");

        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateDeliveryStatus_toInTransit_isAllowedWhileUnpaid() {
        // Dispatch on trust is fine - goods can be on the road while payment clears. Only the
        // final handover waits.
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        order.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(orderService.updateDeliveryStatus(1L, "IN_TRANSIT").deliveryStatus())
                .isEqualTo(DeliveryStatus.IN_TRANSIT);
    }

    @Test
    void markPaid_completesTheDelivery_soTheOrderIsntLeftAStepShort() {
        // Payment is the last thing that has to happen - delivery was blocked waiting on it -
        // so settling up finishes the shipment rather than needing one more emitter click.
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        order.setDeliveryStatus(DeliveryStatus.IN_TRANSIT);
        order.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponseDTO paid = orderService.markPaid(1L);

        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void markPaid_completesDeliveryEvenIfTheShipmentWasNeverMarkedInTransit() {
        // A buyer can pay the moment the match is accepted. Completing from CONFIRMED is
        // deliberate - the deal is done, and the progress bar shows every earlier step as
        // reached once the status is DELIVERED, so nothing looks skipped.
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        order.setPaymentStatus(PaymentStatus.PENDING);
        assertThat(order.getDeliveryStatus()).isEqualTo(DeliveryStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(orderService.markPaid(1L).deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void markPaid_flipsPendingToPaid_andIsIdempotent() {
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        order.setPaymentStatus(PaymentStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(orderService.markPaid(1L).paymentStatus()).isEqualTo(PaymentStatus.PAID);
        // Paying twice is a no-op, not an error - a double-submit shouldn't read as a failure.
        assertThat(orderService.markPaid(1L).paymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(orderRepository, times(1)).save(any());
    }

    @Test
    void markPaid_onANonConfirmedOrder_throwsConflict() {
        Order reverted = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        reverted.setStatus(OrderStatus.REVERTED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(reverted));

        assertThatThrownBy(() -> orderService.markPaid(1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void updateDeliveryStatus_movingBackward_throwsConflict() {
        Order order = fullOrder(1L, "CL-2026-0001", 100L, 200L);
        order.setDeliveryStatus(DeliveryStatus.IN_TRANSIT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateDeliveryStatus(1L, "CONFIRMED"))
                .isInstanceOf(ConflictException.class);
        verify(orderRepository, never()).save(any());
    }

    private Order fullOrder(Long id, String orderNumber, Long emitterId, Long buyerId) {
        return Order.builder()
                .id(id).orderNumber(orderNumber)
                .matchId(id).emitterId(emitterId).buyerId(buyerId)
                .listingId(1L).requestId(1L)
                .transactedVolumeTons(200.0).pricePerTon(900.0)
                .basePrice(180000.0).transportCost(6000.0).totalCost(186000.0)
                .distanceKm(50.0).distanceSource(DistanceSource.HAVERSINE_FALLBACK)
                .status(OrderStatus.CONFIRMED)
                .deliveryStatus(DeliveryStatus.CONFIRMED)
                // Paid by default so the delivery-sequencing tests stay about sequencing. The
                // payment gate has its own tests below.
                .paymentStatus(PaymentStatus.PAID)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Listing listing() {
        return Listing.builder()
                .id(1L).emitterId(10L)
                .totalVolumeTons(1000.0).remainingVolumeTons(200.0)
                .purityPercent(95.0).captureMethod("Direct Air Capture")
                .pricePerTon(900.0).city("Mumbai")
                .locationLat(0.0).locationLng(0.0)
                .status(ListingStatus.ACTIVE)
                .build();
    }

    private CarbonRequest request() {
        return CarbonRequest.builder()
                .id(1L).buyerId(20L)
                .minVolumeNeeded(500.0).minPurityRequired(90.0)
                .maxDistanceKm(500.0).maxBudgetPerTon(1000.0)
                .intendedUse("Fuel Synthesis")
                .status(RequestStatus.OPEN)
                .build();
    }

    private Match match(Long listingId, Long requestId) {
        return Match.builder()
                .id(1L).listingId(listingId).requestId(requestId)
                .compatibilityScore(90.0).distanceKm(50.0)
                .distanceSource(DistanceSource.HAVERSINE_FALLBACK)
                .transportCost(6000.0).totalEstimatedCost(186000.0)
                .status(MatchStatus.ACCEPTED)
                .build();
    }
}
