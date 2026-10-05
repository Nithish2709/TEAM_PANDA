package com.salestorm.scheduler;

import com.salestorm.service.InventoryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background scheduler that sweeps for expired reservations every 60 seconds.
 *
 * WHY THIS EXISTS:
 * When a user reserves inventory but never completes payment (browser closed,
 * network dropped, card declined without callback), the reserved units would
 * be stuck and never available again without this sweeper.
 *
 * IDEMPOTENCY GUARANTEE:
 * The actual release logic uses a conditional UPDATE:
 *   UPDATE inventory_reservation SET status = 'RELEASED'
 *   WHERE reservation_id = ? AND status = 'RESERVED'
 * If two sweeper instances run simultaneously (multi-pod), only one will
 * get affected_rows = 1. The other gets 0 and skips — no double-release.
 *
 * PRODUCTION NOTE:
 * In production, use ShedLock or Quartz to ensure only one pod runs the
 * sweeper at a time in a horizontally-scaled deployment.
 */
@Component
public class ReservationExpiryScheduler {

    private final InventoryService inventoryService;

    public ReservationExpiryScheduler(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Runs every 60 seconds. Finds all RESERVED rows where expires_at < NOW()
     * and releases them, returning inventory to the available pool.
     */
    @Scheduled(fixedDelay = 60_000)
    public void sweepExpiredReservations() {
        System.out.println("[Scheduler] Running reservation expiry sweep...");
        inventoryService.releaseExpiredReservations();
    }
}
