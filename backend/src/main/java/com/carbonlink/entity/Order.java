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

// The permanent settlement record created the moment a match is ACCEPTED. Values are
// snapshotted at that instant (not just a reference back to the Match/Listing), since a
// listing's price or a distance lookup could change later and the order must keep reflecting
// what was actually agreed at the time of the deal.
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Column(nullable = false)
    private Long matchId;

    @Column(nullable = false)
    private Long emitterId;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long listingId;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private Double transactedVolumeTons;

    @Column(nullable = false)
    private Double pricePerTon;

    @Column(nullable = false)
    private Double basePrice;

    @Column(nullable = false)
    private Double transportCost;

    @Column(nullable = false)
    private Double totalCost;

    @Column(nullable = false)
    private Double distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DistanceSource distanceSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // Tracks physical shipment progress independently of the settlement status above - only
    // moves forward (CONFIRMED -> IN_TRANSIT -> DELIVERED) and only while status is CONFIRMED.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus;

    // Whether the buyer has paid. Persisted rather than held in the buyer's browser because
    // the EMITTER needs it too: delivery cannot be marked DELIVERED until this reads PAID, and
    // an emitter can't act on a flag that only exists in someone else's session.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

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
            this.status = OrderStatus.CONFIRMED;
        }
        if (this.deliveryStatus == null) {
            this.deliveryStatus = DeliveryStatus.CONFIRMED;
        }
        if (this.paymentStatus == null) {
            this.paymentStatus = PaymentStatus.PENDING;
        }
    }
}
