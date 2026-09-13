package com.carbonlink.service;

import com.carbonlink.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

// Formats "CL-{year}-{4-digit sequence within that year}". The in-memory counter is a simple
// AtomicInteger (no dedicated sequence table needed), but it's re-seeded from the persisted
// order count for the current year the first time it's used after each JVM start (and again
// whenever the year rolls over), so a restart never collides with order numbers that already
// exist in the database.
@Service
public class OrderNumberGenerator {

    private final OrderRepository orderRepository;
    private final AtomicInteger counter = new AtomicInteger();
    private volatile int seededYear = -1;

    public OrderNumberGenerator(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public synchronized String next() {
        int year = LocalDate.now().getYear();
        if (year != seededYear) {
            long existingForYear = orderRepository.countByOrderNumberStartingWith(yearPrefix(year));
            counter.set((int) existingForYear);
            seededYear = year;
        }
        int sequence = counter.incrementAndGet();
        return yearPrefix(year) + String.format("%04d", sequence);
    }

    private static String yearPrefix(int year) {
        return "CL-" + year + "-";
    }
}
