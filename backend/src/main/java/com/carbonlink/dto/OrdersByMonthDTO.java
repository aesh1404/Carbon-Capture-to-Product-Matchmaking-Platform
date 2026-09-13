package com.carbonlink.dto;

public record OrdersByMonthDTO(
        String month,
        long count,
        double volumeTons
) {
}
