package com.carbonlink.dto;

import jakarta.validation.constraints.NotBlank;

public record UserRequestDTO(
        @NotBlank(message = "name is required") String name,

        @NotBlank(message = "companyName is required") String companyName,

        @NotBlank(message = "role is required") String role,

        @NotBlank(message = "city is required") String city,

        // Sign-in credentials. Optional at the API level so existing callers keep working,
        // but the sign-up form requires both - an account created without them could never be
        // signed into again.
        String username,
        String password,

        // Optional free-text display address - never used for distance/coordinate calculations.
        String address
) {
}
