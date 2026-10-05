package com.salestorm.simulator;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton component used purely for the hackathon demonstration.
 * Allows the frontend dashboard to trigger specific failure scenarios
 * to prove the architecture's resilience and recovery mechanisms.
 */
@Component
public class FailureSimulator {

    public enum FailureType {
        PAYMENT_FAILURE,
        PAYMENT_TIMEOUT,
        ORDER_SERVICE_CRASH,
        DATABASE_DROP
    }

    private final ConcurrentHashMap<FailureType, Boolean> activeFailures = new ConcurrentHashMap<>();

    public void enableFailure(FailureType type) {
        activeFailures.put(type, true);
    }

    public void disableFailure(FailureType type) {
        activeFailures.put(type, false);
    }

    public void checkAndSimulate(FailureType type) {
        if (activeFailures.getOrDefault(type, false)) {
            switch (type) {
                case PAYMENT_FAILURE -> throw new SimulatedFailureException("Simulated Payment Gateway Rejection");
                case PAYMENT_TIMEOUT -> simulateTimeout();
                case ORDER_SERVICE_CRASH -> throw new SimulatedFailureException("Simulated Order Service Crash (OOM)");
                case DATABASE_DROP -> throw new SimulatedFailureException("Simulated Transient SQLException");
            }
        }
    }

    private void simulateTimeout() {
        try {
            Thread.sleep(30000); // 30 seconds
            throw new SimulatedFailureException("Simulated Gateway Timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class SimulatedFailureException extends RuntimeException {
        public SimulatedFailureException(String message) {
            super(message);
        }
    }
}
