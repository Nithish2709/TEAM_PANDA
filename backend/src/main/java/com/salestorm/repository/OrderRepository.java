package com.salestorm.repository;

import com.salestorm.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for Order.
 */
public interface OrderRepository extends JpaRepository<Order, String> {

    /** Idempotency check: prevents creating a duplicate order for the same checkout */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
