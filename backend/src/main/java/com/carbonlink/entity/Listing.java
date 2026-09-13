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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long emitterId;

    @Column(nullable = false)
    private Double totalVolumeTons;

    @Column(nullable = false)
    private Double remainingVolumeTons;

    @Column(nullable = false)
    private Double purityPercent;

    @Column(nullable = false)
    private String captureMethod;

    @Column(nullable = false)
    private Double pricePerTon;

    // The city this specific listing's supply is located in - independent of the emitter's own
    // registered city, since one company can list supply from multiple sites.
    @Column
    private String city;

    // Free-text display address (e.g. "Plot 14, MIDC Industrial Area, Pune") shown on orders
    // and receipts only - never used for distance/coordinate calculations, which stay keyed
    // off `city` + the resolved lat/lng above.
    @Column
    private String address;

    @Column(nullable = false)
    private Double locationLat;

    @Column(nullable = false)
    private Double locationLng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status;

    // Optimistic lock guarding remainingVolumeTons. Two emitters accepting against the same
    // listing at the same moment would otherwise both read the same remaining stock and both
    // write their own "remaining - transacted", silently losing one of the deductions. With
    // this, the second commit fails the version check and MatchService.accept() surfaces a 409
    // instead of overselling the listing.
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

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
        if (this.status == null) {
            this.status = ListingStatus.ACTIVE;
        }
        if (this.remainingVolumeTons == null) {
            this.remainingVolumeTons = this.totalVolumeTons;
        }
    }
}
