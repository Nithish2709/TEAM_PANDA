package com.salestorm.service;

import com.salestorm.domain.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Order state machine transitions.
 * Validates that the State Pattern correctly enforces legal and illegal transitions.
 */
class OrderStateMachineTest {

    private Order createOrderInState(Order.Status status) {
        Order order = new Order();
        order.setStatus(status);
        return order;
    }

    @Test
    @DisplayName("Valid transition: CREATED → PAYMENT_PENDING")
    void createdToPaymentPending() {
        Order order = createOrderInState(Order.Status.CREATED);
        order.transitionTo(Order.Status.PAYMENT_PENDING);
        assertThat(order.getStatus()).isEqualTo(Order.Status.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("Valid transition: PAYMENT_PENDING → CONFIRMED")
    void paymentPendingToConfirmed() {
        Order order = createOrderInState(Order.Status.PAYMENT_PENDING);
        order.transitionTo(Order.Status.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(Order.Status.CONFIRMED);
    }

    @Test
    @DisplayName("Valid transition: CONFIRMED → PROCESSING → SHIPPED → OUT_FOR_DELIVERY → DELIVERED")
    void fullHappyPathTransitions() {
        Order order = createOrderInState(Order.Status.CONFIRMED);
        order.transitionTo(Order.Status.PROCESSING);
        order.transitionTo(Order.Status.SHIPPED);
        order.transitionTo(Order.Status.OUT_FOR_DELIVERY);
        order.transitionTo(Order.Status.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(Order.Status.DELIVERED);
    }

    @Test
    @DisplayName("Invalid transition: DELIVERED → PAYMENT_PENDING must throw")
    void deliveredToPaymentPendingMustFail() {
        Order order = createOrderInState(Order.Status.DELIVERED);
        assertThatThrownBy(() -> order.transitionTo(Order.Status.PAYMENT_PENDING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    @DisplayName("Invalid transition: CONFIRMED → CREATED must throw")
    void confirmedToCreatedMustFail() {
        Order order = createOrderInState(Order.Status.CONFIRMED);
        assertThatThrownBy(() -> order.transitionTo(Order.Status.CREATED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Invalid transition: CANCELLED → any state must throw")
    void cancelledOrderCannotBeAdvanced() {
        Order order = createOrderInState(Order.Status.CANCELLED);
        assertThatThrownBy(() -> order.transitionTo(Order.Status.CONFIRMED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Payment failure path: PAYMENT_PENDING → PAYMENT_FAILED → CANCELLED")
    void paymentFailurePath() {
        Order order = createOrderInState(Order.Status.PAYMENT_PENDING);
        order.transitionTo(Order.Status.PAYMENT_FAILED);
        order.transitionTo(Order.Status.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
    }

    @Test
    @DisplayName("CREATED → CANCELLED is valid (manual cancel before payment)")
    void createdToCancelledIsValid() {
        Order order = createOrderInState(Order.Status.CREATED);
        order.transitionTo(Order.Status.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
    }
}
