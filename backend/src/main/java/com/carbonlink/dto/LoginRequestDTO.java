package com.carbonlink.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "password is required") String password
) {
}
