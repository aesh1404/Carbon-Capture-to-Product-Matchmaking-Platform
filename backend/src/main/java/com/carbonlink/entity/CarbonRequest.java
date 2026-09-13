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
@Table(name = "requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarbonRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Double minVolumeNeeded;

    @Column(nullable = false)
    private Double minPurityRequired;

    @Column(nullable = false)
    private Double maxDistanceKm;

    @Column(nullable = false)
    private Double maxBudgetPerTon;

    @Column(nullable = false)
    private String intendedUse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

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
            this.status = RequestStatus.OPEN;
        }
    }
}
