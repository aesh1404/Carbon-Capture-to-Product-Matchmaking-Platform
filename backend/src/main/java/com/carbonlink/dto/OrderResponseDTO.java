package com.carbonlink.dto;

import com.carbonlink.entity.DeliveryStatus;
import com.carbonlink.entity.DistanceSource;
import com.carbonlink.entity.OrderStatus;
import com.carbonlink.entity.PaymentStatus;

import java.time.LocalDateTime;

public record OrderResponseDTO(
        Long id,
        String orderNumber,
        Long matchId,
        Long emitterId,
        Long buyerId,
        Long listingId,
        Long requestId,
        Double transactedVolumeTons,
        Double pricePerTon,
        Double basePrice,
        Double transportCost,
        Double totalCost,
        Double distanceKm,
        DistanceSource distanceSource,
        OrderStatus status,
        DeliveryStatus deliveryStatus,
        PaymentStatus paymentStatus,
        LocalDateTime createdAt
) {
}
