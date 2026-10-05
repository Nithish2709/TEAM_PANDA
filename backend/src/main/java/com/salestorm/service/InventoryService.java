package com.salestorm.service;

import com.salestorm.domain.Inventory;
import com.salestorm.domain.InventoryReservation;
import com.salestorm.repository.InventoryRepository;
import com.salestorm.repository.InventoryReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core inventory service. All inventory mutations are transactional.
 *
 * CONCURRENCY STRATEGY: Pessimistic Write Lock (SELECT ... FOR UPDATE).
 * When a transaction acquires the lock, all other concurrent transactions
 * block until it commits. This serializes inventory updates at the DB level,
 * making it impossible to oversell. Chosen over optimistic locking because
 * under a flash-sale spike, optimistic locking causes a thundering-herd of
 * retries that degrades throughput further.
 *
 * INVARIANT (must always hold):
 *   available_quantity + reserved_quantity + sold_quantity == total_inventory
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryReservationRepository reservationRepository) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
    }

    // ── Reserve ───────────────────────────────────────────────────────────────

    /**
     * Attempts to reserve `quantity` units for a customer.
     * If the idempotency key was already used, the previous reservation is returned
     * without touching inventory again (duplicate-safe).
     */
    @Transactional
    public InventoryReservation reserveInventory(Long productId, Long customerId,
                                                  Integer quantity, String idempotencyKey) {

        // 1. Idempotency check: if we've seen this key before, return previous result
        Optional<InventoryReservation> existing = reservationRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. Acquire Pessimistic Row Lock — serializes all concurrent updates
        Inventory inventory = inventoryRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        // 3. Check availability (prevents overselling / negative inventory)
        if (inventory.getAvailableQuantity() < quantity) {
            throw new SoldOutException("Product " + productId + " is sold out.");
        }

        // 4. Decrement available, increment reserved (atomic within transaction)
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        inventory.setVersion(inventory.getVersion() + 1);
        inventoryRepository.save(inventory);

        // 5. Create reservation record
        InventoryReservation reservation = new InventoryReservation();
        reservation.setReservationId("RES-" + UUID.randomUUID());
        reservation.setProductId(productId);
        reservation.setCustomerId(customerId);
        reservation.setQuantity(quantity);
        reservation.setStatus(InventoryReservation.Status.RESERVED);
        reservation.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        reservation.setIdempotencyKey(idempotencyKey);

        return reservationRepository.save(reservation);
    }

    // ── Release ───────────────────────────────────────────────────────────────

    /**
     * Releases inventory for a failed/expired reservation.
     * Uses a conditional UPDATE to prevent double-release (idempotent).
     */
    @Transactional
    public boolean releaseReservation(String reservationId) {
        int updated = reservationRepository.releaseIfStillReserved(reservationId, LocalDateTime.now());
        if (updated == 0) {
            return false; // Already released or not in RESERVED state — no-op
        }

        InventoryReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + reservationId));

        Inventory inventory = inventoryRepository.findByIdForUpdate(reservation.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        inventory.setReservedQuantity(inventory.getReservedQuantity() - reservation.getQuantity());
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + reservation.getQuantity());
        inventoryRepository.save(inventory);

        return true;
    }

    // ── Confirm ───────────────────────────────────────────────────────────────

    /**
     * Confirms a reservation after successful payment. Moves reserved → sold.
     */
    @Transactional
    public void confirmReservation(String reservationId) {
        InventoryReservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + reservationId));

        if (reservation.getStatus() != InventoryReservation.Status.RESERVED &&
            reservation.getStatus() != InventoryReservation.Status.PAYMENT_PENDING) {
            throw new IllegalStateException("Cannot confirm reservation in state: " + reservation.getStatus());
        }

        Inventory inventory = inventoryRepository.findByIdForUpdate(reservation.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        inventory.setReservedQuantity(inventory.getReservedQuantity() - reservation.getQuantity());
        inventory.setSoldQuantity(inventory.getSoldQuantity() + reservation.getQuantity());
        inventoryRepository.save(inventory);

        reservation.setStatus(InventoryReservation.Status.SOLD);
        reservationRepository.save(reservation);
    }

    // ── Expiry Sweep ──────────────────────────────────────────────────────────

    /**
     * Called by the ReservationExpiryScheduler every 60 seconds.
     * Finds all RESERVED reservations past their expiry and releases them safely.
     */
    @Transactional
    public void releaseExpiredReservations() {
        List<InventoryReservation> expired = reservationRepository.findExpiredReservations(LocalDateTime.now());
        for (InventoryReservation r : expired) {
            try {
                releaseReservation(r.getReservationId());
            } catch (Exception e) {
                // Log and continue; one failure must not block others
                System.err.println("[ExpiryScheduler] Failed to release " + r.getReservationId() + ": " + e.getMessage());
            }
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public Optional<Inventory> getInventory(Long productId) {
        return inventoryRepository.findById(productId);
    }

    // ── Exceptions ────────────────────────────────────────────────────────────

    public static class SoldOutException extends RuntimeException {
        public SoldOutException(String message) { super(message); }
    }
}
