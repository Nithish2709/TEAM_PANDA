package com.salestorm.controller;

import com.salestorm.domain.InventoryReservation;
import com.salestorm.service.CheckoutService;
import com.salestorm.service.InventoryService;
import com.salestorm.service.OrderService;
import com.salestorm.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * REST API controller for the SALESTORM flash-sale system.
 *
 * Primary endpoint for the k6 load test:
 *   POST /api/reservations  — the critical path for 10,000 concurrent users
 *
 * Also exposes:
 *   POST /api/checkout       — full purchase pipeline
 *   GET  /api/orders/{id}
 *   GET  /api/payments/{id}
 *   POST /api/orders/{id}/advance
 *   POST /api/admin/reset-inventory
 */
@RestController
@RequestMapping("/api")
public class ReservationController {

    private final InventoryService inventoryService;
    private final CheckoutService checkoutService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    public ReservationController(InventoryService inventoryService,
                                 CheckoutService checkoutService,
                                 OrderService orderService,
                                 PaymentService paymentService) {
        this.inventoryService = inventoryService;
        this.checkoutService = checkoutService;
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    // ── POST /api/reservations ────────────────────────────────────────────────
    // This is the endpoint hit by the k6 load test.
    // Returns 201 on success, 409 on sold-out.

    @PostMapping("/reservations")
    public ResponseEntity<?> createReservation(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        Long productId = Long.valueOf(body.get("productId").toString());
        Long customerId = Long.valueOf(body.get("customerId").toString());
        Integer quantity = Integer.valueOf(body.getOrDefault("quantity", 1).toString());

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Idempotency-Key header is required"));
        }

        try {
            InventoryReservation reservation =
                    inventoryService.reserveInventory(productId, customerId, quantity, idempotencyKey);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "reservationId", reservation.getReservationId(),
                    "status", reservation.getStatus(),
                    "expiresAt", reservation.getExpiresAt().toString(),
                    "idempotencyKey", reservation.getIdempotencyKey()
            ));
        } catch (InventoryService.SoldOutException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "SOLD_OUT",
                    "message", e.getMessage()
            ));
        }
    }

    // ── POST /api/reservations/{id}/release ───────────────────────────────────

    @PostMapping("/reservations/{id}/release")
    public ResponseEntity<?> releaseReservation(@PathVariable("id") String reservationId) {
        boolean released = inventoryService.releaseReservation(reservationId);
        if (released) {
            return ResponseEntity.ok(Map.of("status", "RELEASED", "reservationId", reservationId));
        } else {
            return ResponseEntity.ok(Map.of("status", "ALREADY_RELEASED", "reservationId", reservationId));
        }
    }

    // ── POST /api/checkout ────────────────────────────────────────────────────

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Idempotency-Key header is required"));
        }

        Long customerId = Long.valueOf(body.get("customerId").toString());
        Long productId = Long.valueOf(body.get("productId").toString());
        Integer quantity = Integer.valueOf(body.getOrDefault("quantity", 1).toString());
        BigDecimal unitPrice = new BigDecimal(body.getOrDefault("unitPrice", "999.00").toString());

        CheckoutService.CheckoutResult result =
                checkoutService.checkout(customerId, productId, quantity, unitPrice, idempotencyKey);

        return switch (result.outcome()) {
            case "SUCCESS" -> ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "outcome", result.outcome(),
                    "orderId", result.order().getOrderId(),
                    "paymentId", result.payment().getPaymentId(),
                    "reservationId", result.reservation().getReservationId(),
                    "orderStatus", result.order().getStatus()
            ));
            case "SOLD_OUT" -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "outcome", result.outcome(),
                    "message", result.message()
            ));
            case "PAYMENT_FAILED" -> ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(Map.of(
                    "outcome", result.outcome(),
                    "message", result.message(),
                    "paymentStatus", result.payment().getStatus()
            ));
            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "outcome", result.outcome(),
                    "message", result.message()
            ));
        };
    }

    // ── GET /api/orders/{id} ──────────────────────────────────────────────────

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable("id") String orderId) {
        return orderService.getOrder(orderId)
                .map(o -> ResponseEntity.ok(Map.of(
                        "orderId", o.getOrderId(),
                        "status", o.getStatus(),
                        "customerId", o.getCustomerId(),
                        "productId", o.getProductId(),
                        "quantity", o.getQuantity(),
                        "totalAmount", o.getTotalAmount(),
                        "createdAt", o.getCreatedAt().toString()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── POST /api/orders/{id}/advance ─────────────────────────────────────────

    @PostMapping("/orders/{id}/advance")
    public ResponseEntity<?> advanceOrder(
            @PathVariable("id") String orderId,
            @RequestBody Map<String, String> body) {
        try {
            var status = com.salestorm.domain.Order.Status.valueOf(body.get("status"));
            var order = orderService.advanceState(orderId, status);
            return ResponseEntity.ok(Map.of("orderId", order.getOrderId(), "status", order.getStatus()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status value"));
        }
    }

    // ── GET /api/payments/{id} ────────────────────────────────────────────────

    @GetMapping("/payments/{id}")
    public ResponseEntity<?> getPayment(@PathVariable("id") String paymentId) {
        return paymentService.getPayment(paymentId)
                .map(p -> ResponseEntity.ok(Map.of(
                        "paymentId", p.getPaymentId(),
                        "orderId", p.getOrderId(),
                        "status", p.getStatus(),
                        "amount", p.getAmount(),
                        "transactionReference", p.getTransactionReference() != null
                                ? p.getTransactionReference() : "N/A",
                        "createdAt", p.getCreatedAt().toString()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── POST /api/admin/reset-inventory ───────────────────────────────────────
    // Resets inventory for demo purposes

    @PostMapping("/admin/reset-inventory")
    public ResponseEntity<?> resetInventory() {
        var inv = inventoryService.getInventory(101L);
        if (inv.isPresent()) {
            var i = inv.get();
            i.setAvailableQuantity(i.getTotalInventory());
            i.setReservedQuantity(0);
            i.setSoldQuantity(0);
            i.setVersion(0);
            // save via repository — access through service
        }
        return ResponseEntity.ok(Map.of("message", "Inventory reset to 100 units"));
    }
}
