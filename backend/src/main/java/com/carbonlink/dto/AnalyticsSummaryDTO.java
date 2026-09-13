package com.carbonlink.dto;

import java.util.List;

public record AnalyticsSummaryDTO(
        long totalListingsCount,
        long totalActiveListings,
        long totalRequestsCount,
        long totalOpenRequests,
        long totalOrdersConfirmed,
        double totalVolumeTransactedTons,
        double totalValueTransacted,
        double totalCo2DivertedTons,
        String topCaptureMethod,
        List<OrdersByMonthDTO> ordersByMonth
) {
}
