package com.salestorm.repository;

import com.salestorm.domain.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for InventoryReservation.
 * The idempotency check (findByIdempotencyKey) is critical:
 * it prevents the same Buy request from creating two reservations.
 */
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, String> {

    /** Idempotency guard — check before creating a new reservation */
    Optional<InventoryReservation> findByIdempotencyKey(String idempotencyKey);

    /** Fetch all RESERVED reservations that have passed their expiry time */
    @Query("SELECT r FROM InventoryReservation r WHERE r.status = 'RESERVED' AND r.expiresAt < :now")
    List<InventoryReservation> findExpiredReservations(@Param("now") LocalDateTime now);

    /** Atomically mark a reservation as RELEASED only if it is still in RESERVED state.
     *  Returns number of rows updated (1 = success, 0 = already released/changed). */
    @Modifying
    @Query("UPDATE InventoryReservation r SET r.status = 'RELEASED', r.updatedAt = :now " +
           "WHERE r.reservationId = :id AND r.status = 'RESERVED'")
    int releaseIfStillReserved(@Param("id") String reservationId, @Param("now") LocalDateTime now);
}
