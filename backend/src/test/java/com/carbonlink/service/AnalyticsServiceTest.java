package com.carbonlink.service;

import com.carbonlink.dto.AnalyticsSummaryDTO;
import com.carbonlink.dto.OrdersByMonthDTO;
import com.carbonlink.entity.DistanceSource;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import com.carbonlink.entity.Order;
import com.carbonlink.entity.OrderStatus;
import com.carbonlink.entity.RequestStatus;
import com.carbonlink.repository.CarbonRequestRepository;
import com.carbonlink.repository.ListingRepository;
import com.carbonlink.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private CarbonRequestRepository carbonRequestRepository;
    @Mock
    private OrderRepository orderRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(listingRepository, carbonRequestRepository, orderRepository);
    }

    @Test
    void getSummary_aggregatesListingsRequestsAndConfirmedOrdersOnly() {
        Listing active1 = listing(ListingStatus.ACTIVE, "Post-combustion", 500.0);
        Listing active2 = listing(ListingStatus.ACTIVE, "Direct Air Capture", 100.0);
        Listing closed = listing(ListingStatus.CLOSED, "Oxy-fuel", 900.0);
        when(listingRepository.findAll()).thenReturn(List.of(active1, active2, closed));

        when(carbonRequestRepository.count()).thenReturn(5L);
        when(carbonRequestRepository.findByStatus(RequestStatus.OPEN)).thenReturn(List.of());

        Order confirmed1 = order(OrderStatus.CONFIRMED, 50.0, 10000.0, LocalDateTime.of(2026, 3, 1, 10, 0));
        Order confirmed2 = order(OrderStatus.CONFIRMED, 30.0, 6000.0, LocalDateTime.of(2026, 3, 15, 10, 0));
        Order reverted = order(OrderStatus.REVERTED, 999.0, 999.0, LocalDateTime.of(2026, 3, 20, 10, 0));
        when(orderRepository.findAll()).thenReturn(List.of(confirmed1, confirmed2, reverted));

        AnalyticsSummaryDTO summary = analyticsService.getSummary();

        assertThat(summary.totalListingsCount()).isEqualTo(3);
        assertThat(summary.totalActiveListings()).isEqualTo(2);
        assertThat(summary.totalRequestsCount()).isEqualTo(5);
        assertThat(summary.totalOpenRequests()).isZero();
        assertThat(summary.totalOrdersConfirmed()).isEqualTo(2);
        assertThat(summary.totalVolumeTransactedTons()).isEqualTo(80.0);
        assertThat(summary.totalCo2DivertedTons()).isEqualTo(80.0);
        assertThat(summary.totalValueTransacted()).isEqualTo(16000.0);
        assertThat(summary.topCaptureMethod()).isEqualTo("Post-combustion");
    }

    @Test
    void getSummary_sumsTransactedVolumeAcrossThreeConfirmedOrders() {
        when(listingRepository.findAll()).thenReturn(List.of());
        when(carbonRequestRepository.count()).thenReturn(0L);
        when(carbonRequestRepository.findByStatus(RequestStatus.OPEN)).thenReturn(List.of());

        Order order100 = order(OrderStatus.CONFIRMED, 100.0, 50000.0, LocalDateTime.of(2026, 4, 1, 9, 0));
        Order order200 = order(OrderStatus.CONFIRMED, 200.0, 100000.0, LocalDateTime.of(2026, 4, 2, 9, 0));
        Order order300 = order(OrderStatus.CONFIRMED, 300.0, 150000.0, LocalDateTime.of(2026, 4, 3, 9, 0));
        when(orderRepository.findAll()).thenReturn(List.of(order100, order200, order300));

        AnalyticsSummaryDTO summary = analyticsService.getSummary();

        assertThat(summary.totalOrdersConfirmed()).isEqualTo(3);
        assertThat(summary.totalVolumeTransactedTons()).isEqualTo(600.0);
        assertThat(summary.totalCo2DivertedTons()).isEqualTo(600.0);
        assertThat(summary.totalValueTransacted()).isEqualTo(300000.0);
    }

    @Test
    void getSummary_groupsConfirmedOrdersByCreationMonth_sortedAscending() {
        when(listingRepository.findAll()).thenReturn(List.of());
        when(carbonRequestRepository.count()).thenReturn(0L);
        when(carbonRequestRepository.findByStatus(RequestStatus.OPEN)).thenReturn(List.of());

        Order feb = order(OrderStatus.CONFIRMED, 10.0, 1000.0, LocalDateTime.of(2026, 2, 10, 9, 0));
        Order march1 = order(OrderStatus.CONFIRMED, 20.0, 2000.0, LocalDateTime.of(2026, 3, 1, 9, 0));
        Order march2 = order(OrderStatus.CONFIRMED, 5.0, 500.0, LocalDateTime.of(2026, 3, 28, 9, 0));
        when(orderRepository.findAll()).thenReturn(List.of(march1, feb, march2));

        List<OrdersByMonthDTO> byMonth = analyticsService.getSummary().ordersByMonth();

        assertThat(byMonth).extracting(OrdersByMonthDTO::month).containsExactly("2026-02", "2026-03");
        assertThat(byMonth.get(0).count()).isEqualTo(1);
        assertThat(byMonth.get(1).count()).isEqualTo(2);
        assertThat(byMonth.get(1).volumeTons()).isEqualTo(25.0);
    }

    @Test
    void getSummary_aggregatesAreRounded_soNoBinaryFloatArtifactReachesTheApi() {
        // Each order's own totals are already rounded when the order is written, but summing
        // doubles reintroduces drift: 0.1 + 0.2 == 0.30000000000000004. Without a final round
        // the API would serialise things like 218520.00000001 straight into the dashboard.
        when(listingRepository.findAll()).thenReturn(List.of());
        when(carbonRequestRepository.count()).thenReturn(0L);
        when(carbonRequestRepository.findByStatus(RequestStatus.OPEN)).thenReturn(List.of());

        Order a = order(OrderStatus.CONFIRMED, 0.1, 218520.01, LocalDateTime.of(2026, 5, 1, 9, 0));
        Order b = order(OrderStatus.CONFIRMED, 0.2, 0.02, LocalDateTime.of(2026, 5, 2, 9, 0));
        when(orderRepository.findAll()).thenReturn(List.of(a, b));

        AnalyticsSummaryDTO summary = analyticsService.getSummary();

        assertThat(summary.totalVolumeTransactedTons()).isEqualTo(0.3);
        assertThat(summary.totalCo2DivertedTons()).isEqualTo(0.3);
        assertThat(summary.totalValueTransacted()).isEqualTo(218520.03);
        assertThat(summary.ordersByMonth()).singleElement()
                .extracting(OrdersByMonthDTO::volumeTons).isEqualTo(0.3);
    }

    @Test
    void getSummary_noActiveListings_topCaptureMethodIsNull() {
        Listing closed = listing(ListingStatus.CLOSED, "Oxy-fuel", 900.0);
        when(listingRepository.findAll()).thenReturn(List.of(closed));
        when(carbonRequestRepository.count()).thenReturn(0L);
        when(carbonRequestRepository.findByStatus(RequestStatus.OPEN)).thenReturn(List.of());
        when(orderRepository.findAll()).thenReturn(List.of());

        assertThat(analyticsService.getSummary().topCaptureMethod()).isNull();
    }

    private Listing listing(ListingStatus status, String captureMethod, double totalVolumeTons) {
        return Listing.builder()
                .id(1L).emitterId(1L)
                .totalVolumeTons(totalVolumeTons).remainingVolumeTons(totalVolumeTons)
                .purityPercent(95.0).captureMethod(captureMethod)
                .pricePerTon(1000.0).city("Mumbai")
                .locationLat(0.0).locationLng(0.0)
                .status(status)
                .build();
    }

    private Order order(OrderStatus status, double transactedVolumeTons, double totalCost, LocalDateTime createdAt) {
        return Order.builder()
                .id(1L).orderNumber("CL-2026-0001")
                .matchId(1L).emitterId(1L).buyerId(2L)
                .listingId(1L).requestId(1L)
                .transactedVolumeTons(transactedVolumeTons).pricePerTon(1000.0)
                .basePrice(totalCost).transportCost(0.0).totalCost(totalCost)
                .distanceKm(10.0).distanceSource(DistanceSource.HAVERSINE_FALLBACK)
                .status(status)
                .createdAt(createdAt)
                .build();
    }
}
