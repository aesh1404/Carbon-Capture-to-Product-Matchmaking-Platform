package com.carbonlink.entity;

// Whether the buyer has settled this order. Separate from OrderStatus (is the deal on?) and
// DeliveryStatus (where are the goods?) because those three genuinely move independently:
// an order can be confirmed and in transit while payment is still outstanding.
public enum PaymentStatus {
    PENDING,
    PAID
}
