package com.carbonlink.dto;

import com.carbonlink.entity.Role;

import java.time.LocalDateTime;

// locationLat/locationLng are deliberately NOT exposed - same reasoning as ListingResponseDTO:
// coordinates are an internal derivation of `city`, and the client only ever shows the name.
public record UserResponseDTO(
        Long id,
        String name,
        String companyName,

        // The sign-in handle, when the account has one. The password hash is never exposed.
        String username,
        Role role,
        String city,
        String address,
        LocalDateTime createdAt
) {
}
