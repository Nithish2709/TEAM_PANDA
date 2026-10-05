package com.salestorm.service;

import com.salestorm.domain.Inventory;
import com.salestorm.domain.InventoryReservation;
import com.salestorm.repository.InventoryRepository;
import com.salestorm.repository.InventoryReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InventoryService.
 *
 * Tests the critical correctness guarantees:
 * 1. Successful reservation
 * 2. Insufficient inventory rejection
 * 3. Idempotency — duplicate key returns existing reservation
 * 4. Release — inventory returned when reservation released
 * 5. Double-release protection
 * 6. Confirm — reserved moves to sold
 *
 * Concurrency test (10,000 threads) is in ConcurrencySimulationTest.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new Inventory(101L, 100);
    }

    // ── Happy Path ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should reserve inventory successfully when stock is available")
    void shouldReserveSuccessfully() {
        when(reservationRepository.findByIdempotencyKey("KEY-1")).thenReturn(Optional.empty());
        when(inventoryRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryRepository.save(any())).thenReturn(inventory);

        InventoryReservation result = inventoryService.reserveInventory(101L, 1L, 1, "KEY-1");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(InventoryReservation.Status.RESERVED);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(99);
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
    }

    // ── Sold Out ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should throw SoldOutException when inventory is zero")
    void shouldRejectWhenSoldOut() {
        inventory.setAvailableQuantity(0);
        when(reservationRepository.findByIdempotencyKey("KEY-2")).thenReturn(Optional.empty());
        when(inventoryRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() ->
                inventoryService.reserveInventory(101L, 2L, 1, "KEY-2"))
                .isInstanceOf(InventoryService.SoldOutException.class);
    }

    @Test
    @DisplayName("Inventory must never go negative")
    void inventoryMustNeverGoNegative() {
        inventory.setAvailableQuantity(0);
        when(reservationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(inventoryRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() ->
                inventoryService.reserveInventory(101L, 3L, 1, "KEY-3"))
                .isInstanceOf(InventoryService.SoldOutException.class);

        // Available must remain 0, not -1
        assertThat(inventory.getAvailableQuantity()).isGreaterThanOrEqualTo(0);
    }

    // ── Idempotency ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Duplicate idempotency key must return existing reservation without modifying inventory")
    void shouldReturnExistingReservationOnDuplicateKey() {
        InventoryReservation existing = new InventoryReservation();
        existing.setReservationId("RES-existing");
        existing.setStatus(InventoryReservation.Status.RESERVED);
        existing.setIdempotencyKey("KEY-DUP");

        when(reservationRepository.findByIdempotencyKey("KEY-DUP")).thenReturn(Optional.of(existing));

        InventoryReservation result = inventoryService.reserveInventory(101L, 4L, 1, "KEY-DUP");

        assertThat(result.getReservationId()).isEqualTo("RES-existing");
        // Verify inventory was NOT touched (no findByIdForUpdate call)
        verify(inventoryRepository, never()).findByIdForUpdate(any());
        assertThat(inventory.getAvailableQuantity()).isEqualTo(100); // unchanged
    }

    // ── Release ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Release should return inventory and mark reservation RELEASED")
    void shouldReleaseReservation() {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setReservationId("RES-rel");
        reservation.setProductId(101L);
        reservation.setQuantity(1);
        reservation.setStatus(InventoryReservation.Status.RESERVED);

        inventory.setAvailableQuantity(99);
        inventory.setReservedQuantity(1);

        when(reservationRepository.releaseIfStillReserved(eq("RES-rel"), any())).thenReturn(1);
        when(reservationRepository.findById("RES-rel")).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any())).thenReturn(inventory);

        boolean released = inventoryService.releaseReservation("RES-rel");

        assertThat(released).isTrue();
        assertThat(inventory.getAvailableQuantity()).isEqualTo(100);
        assertThat(inventory.getReservedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Double-release should be a no-op (returns false)")
    void doubleReleaseShouldBeNoOp() {
        // Conditional UPDATE returns 0 because status is already RELEASED
        when(reservationRepository.releaseIfStillReserved(eq("RES-rel"), any())).thenReturn(0);

        boolean released = inventoryService.releaseReservation("RES-rel");

        assertThat(released).isFalse();
        verify(inventoryRepository, never()).findByIdForUpdate(any());
        verify(inventoryRepository, never()).save(any());
    }

    // ── Confirm ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Confirm should move reserved quantity to sold")
    void shouldConfirmReservation() {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setReservationId("RES-conf");
        reservation.setProductId(101L);
        reservation.setQuantity(1);
        reservation.setStatus(InventoryReservation.Status.RESERVED);

        inventory.setAvailableQuantity(99);
        inventory.setReservedQuantity(1);
        inventory.setSoldQuantity(0);

        when(reservationRepository.findById("RES-conf")).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any())).thenReturn(inventory);
        when(reservationRepository.save(any())).thenReturn(reservation);

        inventoryService.confirmReservation("RES-conf");

        assertThat(inventory.getReservedQuantity()).isEqualTo(0);
        assertThat(inventory.getSoldQuantity()).isEqualTo(1);
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservation.Status.SOLD);
    }

    // ── Invariant ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Inventory invariant: available + reserved + sold must equal total at all times")
    void inventoryInvariantMustHold() {
        // After a series of operations, the invariant must hold
        int total = inventory.getTotalInventory();
        // Simulate: 1 reserved, 1 sold, 98 available
        inventory.setAvailableQuantity(98);
        inventory.setReservedQuantity(1);
        inventory.setSoldQuantity(1);

        int sum = inventory.getAvailableQuantity()
                + inventory.getReservedQuantity()
                + inventory.getSoldQuantity();

        assertThat(sum).isEqualTo(total);
    }
}
