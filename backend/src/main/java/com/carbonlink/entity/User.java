package com.carbonlink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String companyName;

    // Optional sign-in handle. Null for accounts created without one (the one-click profile
    // list needs no credentials), unique when present so it can identify an account.
    @Column(unique = true)
    private String username;

    // BCrypt hash, never the password itself - even for throwaway demo accounts, a plaintext
    // password column is the kind of thing that quietly survives into something real.
    @Column
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // The city name typed/selected at registration, stored verbatim alongside the resolved
    // lat/lng so callers never need to reverse-lookup coordinates just to show a place name.
    @Column
    private String city;

    // Free-text display address, shown on orders/receipts only - never used for
    // distance/coordinate calculations, which stay keyed off `city` + the lat/lng above.
    @Column
    private String address;

    @Column(nullable = false)
    private Double locationLat;

    @Column(nullable = false)
    private Double locationLng;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        // Only stamped when the caller hasn't supplied one. Normal API-created rows get "now"
        // exactly as before; the demo seeder can hand in a backdated timestamp to build a
        // realistic history. (`updatable = false` guards the column afterwards - it stops the
        // value drifting on later updates, and does not affect the initial insert.)
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
