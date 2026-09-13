package com.carbonlink.service;

import com.carbonlink.dto.AnalyticsSummaryDTO;
import com.carbonlink.dto.OrdersByMonthDTO;
import com.carbonlink.entity.Listing;
import com.carbonlink.entity.ListingStatus;
import com.carbonlink.entity.Order;
import com.carbonlink.entity.OrderStatus;
import com.carbonlink.entity.RequestStatus;
import com.carbonlink.repository.CarbonRequestRepository;
import com.carbonlink.repository.ListingRepository;
import com.carbonlink.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ListingRepository listingRepository;
    private final CarbonRequestRepository carbonRequestRepository;
    private final OrderRepository orderRepository;

    public AnalyticsService(ListingRepository listingRepository,
                             CarbonRequestRepository carbonRequestRepository,
                             OrderRepository orderRepository) {
        this.listingRepository = listingRepository;
        this.carbonRequestRepository = carbonRequestRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryDTO getSummary() {
        List<Listing> listings = listingRepository.findAll();
        long totalActiveListings = listings.stream()
                .filter(listing -> listing.getStatus() == ListingStatus.ACTIVE)
                .count();

        long totalRequestsCount = carbonRequestRepository.count();
        long totalOpenRequests = carbonRequestRepository.findByStatus(RequestStatus.OPEN).size();

        List<Order> confirmedOrders = orderRepository.findAll().stream()
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED)
                .toList();

        // Summing doubles reintroduces binary-float drift even though every individual order
        // was stored already rounded (0.1 + 0.2 == 0.30000000000000004), so every aggregate is
        // re-rounded on the way out - the API must never emit ₹218520.00000001.
        double totalVolumeTransactedTons = round2(confirmedOrders.stream()
                .mapToDouble(Order::getTransactedVolumeTons)
                .sum());
        double totalValueTransacted = round2(confirmedOrders.stream()
                .mapToDouble(Order::getTotalCost)
                .sum());

        String topCaptureMethod = listings.stream()
                .filter(listing -> listing.getStatus() == ListingStatus.ACTIVE)
                .collect(Collectors.groupingBy(Listing::getCaptureMethod,
                        Collectors.summingDouble(Listing::getTotalVolumeTons)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        List<OrdersByMonthDTO> ordersByMonth = confirmedOrders.stream()
                .collect(Collectors.groupingBy(order -> order.getCreatedAt().format(MONTH_FORMAT)))
                .entrySet().stream()
                .map(entry -> new OrdersByMonthDTO(
                        entry.getKey(),
                        entry.getValue().size(),
                        round2(entry.getValue().stream().mapToDouble(Order::getTransactedVolumeTons).sum())))
                .sorted(Comparator.comparing(OrdersByMonthDTO::month))
                .toList();

        return new AnalyticsSummaryDTO(
                listings.size(),
                totalActiveListings,
                totalRequestsCount,
                totalOpenRequests,
                confirmedOrders.size(),
                totalVolumeTransactedTons,
                totalValueTransacted,
                // "CO2 diverted" is the same quantity as volume transacted - every ton bought
                // through the platform is a ton that didn't vent. Surfaced under its own name
                // because that's the framing the Impact Dashboard headline uses.
                totalVolumeTransactedTons,
                topCaptureMethod,
                ordersByMonth);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
