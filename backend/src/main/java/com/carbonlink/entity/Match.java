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
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long listingId;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private Double compatibilityScore;

    @Column(nullable = false)
    private Double distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DistanceSource distanceSource;

    @Column(nullable = false)
    private Double transportCost;

    @Column(nullable = false)
    private Double totalEstimatedCost;

    // Null until the match is ACCEPTED - the volume actually transacted is only known once
    // the listing's remaining stock at that moment is checked against the request's need.
    @Column
    private Double transactedVolumeTons;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    // Flips true once the party this status change is for has opened the relevant dashboard
    // tab (see MatchService.markViewed) - drives the simple "NEW" badge, not a real
    // notification center. Reset to false whenever a fresh notify-worthy transition happens.
    @Column(nullable = false)
    private boolean viewed;

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
            this.status = MatchStatus.SUGGESTED;
        }
    }
}
