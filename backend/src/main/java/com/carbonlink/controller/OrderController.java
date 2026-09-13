package com.carbonlink.controller;

import com.carbonlink.dto.DeliveryStatusUpdateRequestDTO;
import com.carbonlink.dto.OrderResponseDTO;
import com.carbonlink.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public OrderResponseDTO getOrder(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @GetMapping("/by-user/{userId}")
    public List<OrderResponseDTO> getOrdersForUser(@PathVariable Long userId) {
        return orderService.getOrdersForUser(userId);
    }

    // Called by the buyer's checkout screen once its simulated payment "succeeds". It records
    // that payment happened - there is still no payment provider, no card data and no money
    // movement anywhere in this system.
    @PostMapping("/{id}/pay")
    public OrderResponseDTO pay(@PathVariable Long id) {
        return orderService.markPaid(id);
    }

    @PostMapping("/{id}/delivery-status")
    public OrderResponseDTO updateDeliveryStatus(@PathVariable Long id,
                                                  @Valid @RequestBody DeliveryStatusUpdateRequestDTO dto) {
        return orderService.updateDeliveryStatus(id, dto.status());
    }
}
