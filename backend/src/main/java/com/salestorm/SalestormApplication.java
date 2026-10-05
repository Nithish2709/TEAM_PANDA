package com.salestorm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SALESTORM — Main application entry point.
 * @EnableScheduling activates the ReservationExpiryScheduler.
 */
@SpringBootApplication
@EnableScheduling
public class SalestormApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalestormApplication.class, args);
    }
}
