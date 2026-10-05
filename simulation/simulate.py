import threading
import random
import time
from collections import defaultdict

# --- Configuration ---
INITIAL_INVENTORY = 100
TOTAL_REQUESTS = 10000
PAYMENT_FAILURE_RATE = 0.05
TIMEOUT_RATE = 0.05
DUPLICATE_RATE = 0.02

# --- Database State ---
class Database:
    def __init__(self):
        self.lock = threading.Lock() # Simulates Pessimistic Row Lock (SELECT ... FOR UPDATE)
        self.total_inventory = INITIAL_INVENTORY
        self.available_quantity = INITIAL_INVENTORY
        self.reserved_quantity = 0
        self.sold_quantity = 0
        
        self.successful_reservations = 0
        self.rejected_sold_out = 0
        self.duplicate_requests = 0
        self.payment_failures = 0
        self.timeouts = 0
        self.overselling_detected = 0
        
        # Track idempotency keys
        self.seen_keys = set()

db = Database()

def simulate_request(request_id):
    is_duplicate = random.random() < DUPLICATE_RATE
    idempotency_key = f"IDEM-{request_id // 2}" if is_duplicate else f"IDEM-{request_id}"
    
    # 1. Idempotency Check (Thread-safe)
    with db.lock:
        if idempotency_key in db.seen_keys:
            db.duplicate_requests += 1
            return
        db.seen_keys.add(idempotency_key)

    # 2. Inventory Reservation (Pessimistic Lock)
    with db.lock:
        if db.available_quantity > 0:
            db.available_quantity -= 1
            db.reserved_quantity += 1
            db.successful_reservations += 1
            reserved = True
        else:
            db.rejected_sold_out += 1
            reserved = False

    if not reserved:
        return
        
    # 3. Simulate Payment Gateway (Async to DB lock)
    time.sleep(random.uniform(0.001, 0.005)) 
    rand = random.random()
    
    if rand < PAYMENT_FAILURE_RATE:
        # Payment Failed -> Release Reservation
        with db.lock:
            db.reserved_quantity -= 1
            db.available_quantity += 1
            db.payment_failures += 1
    elif rand < PAYMENT_FAILURE_RATE + TIMEOUT_RATE:
        # Timeout -> Release Reservation
        with db.lock:
            db.reserved_quantity -= 1
            db.available_quantity += 1
            db.timeouts += 1
    else:
        # Payment Success -> Confirm Order
        with db.lock:
            db.reserved_quantity -= 1
            db.sold_quantity += 1
            
            # Check invariant violation
            if db.sold_quantity > db.total_inventory:
                db.overselling_detected += 1

def run_simulation():
    threads = []
    
    # Create 10,000 threads simulating concurrent users hitting the API
    for i in range(TOTAL_REQUESTS):
        t = threading.Thread(target=simulate_request, args=(i,))
        threads.append(t)
        
    # Start all threads almost simultaneously
    for t in threads:
        t.start()
        
    # Wait for all to finish
    for t in threads:
        t.join()

    # Generate Report
    print("========================================")
    print("SALESTORM FLASH SALE SIMULATION")
    print("========================================")
    print(f"\nInitial Inventory : {INITIAL_INVENTORY}")
    print(f"Purchase Requests : {TOTAL_REQUESTS}\n")
    
    print(f"Successful Reservations : {db.successful_reservations}")
    print(f"Rejected - Sold Out     : {db.rejected_sold_out}")
    print(f"Duplicate Requests      : {db.duplicate_requests}")
    print(f"Payment Failures        : {db.payment_failures}")
    print(f"Timeouts                : {db.timeouts}\n")
    
    print(f"Final Available         : {db.available_quantity}")
    print(f"Final Reserved          : {db.reserved_quantity}")
    print(f"Final Sold              : {db.sold_quantity}\n")
    
    print(f"Overselling             : {db.overselling_detected}")
    negative_inv = 1 if db.available_quantity < 0 else 0
    print(f"Negative Inventory      : {negative_inv}")
    
    invariant_check = db.available_quantity + db.reserved_quantity + db.sold_quantity == db.total_inventory
    print(f"Invariant Maintained    : {invariant_check}")
    print("========================================")

if __name__ == "__main__":
    print("Starting simulation with 10,000 concurrent threads. Please wait...")
    run_simulation()
