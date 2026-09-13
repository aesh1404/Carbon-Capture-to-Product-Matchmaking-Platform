package com.carbonlink.dto;

import jakarta.validation.constraints.NotBlank;

public record DeliveryStatusUpdateRequestDTO(
        @NotBlank(message = "status is required") String status
) {
}
