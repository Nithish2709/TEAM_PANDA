import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';

// Custom metrics
const successfulReservations = new Counter('successful_reservations');
const soldOutRejections = new Counter('sold_out_rejections');
const duplicateRequests = new Counter('duplicate_requests');
const reservationLatency = new Trend('reservation_latency');
const errorRate = new Rate('errors');

// Test Configuration
// We run a scenario that attempts to mimic a flash sale: a sudden massive spike of traffic.
export const options = {
  scenarios: {
    flash_sale_spike: {
      executor: 'shared-iterations',
      vus: 10000, // Simulating 10,000 concurrent users
      iterations: 10000, // Each user tries exactly once
      maxDuration: '30s', // The entire spike happens within 30 seconds
    },
  },
  thresholds: {
    // We expect 99% of requests to complete within 2 seconds, despite the database lock contention
    'reservation_latency': ['p(95)<1000', 'p(99)<2000'],
    // HTTP errors (5xx) should be near zero, though 409 (Sold out) is expected and not an "error"
    'errors': ['rate<0.01'], 
  },
};

const BASE_URL = 'http://localhost:8080/api';

export default function () {
  // Generate a mock user
  const customerId = Math.floor(Math.random() * 100000) + 1;
  const isDuplicate = Math.random() < 0.02; // 2% chance to simulate a double click
  
  // Idempotency Key logic
  const idempotencyKey = isDuplicate 
    ? `IDEM-${__VU}-DOUBLE` 
    : `IDEM-${__VU}-${__ITER}`;

  const payload = JSON.stringify({
    productId: 101, // The flash sale product
    customerId: customerId,
    quantity: 1
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey
    },
  };

  // Hit the Reservation API
  const res = http.post(`${BASE_URL}/reservations`, payload, params);

  // Record custom latency metric
  reservationLatency.add(res.timings.duration);

  // Validate the response
  const isSuccess = check(res, {
    'is status 201 (Reserved) or 409 (Sold Out)': (r) => r.status === 201 || r.status === 409,
  });

  if (!isSuccess) {
    errorRate.add(1);
  }

  // Tally business metrics based on response
  if (res.status === 201) {
    // HTTP 201 Created -> Successfully reserved
    successfulReservations.add(1);
  } else if (res.status === 409) {
    // HTTP 409 Conflict -> Correctly rejected due to zero inventory
    soldOutRejections.add(1);
  }

  // Give a tiny random sleep to prevent perfectly uniform synthetic traffic
  sleep(Math.random() * 0.1);
}
