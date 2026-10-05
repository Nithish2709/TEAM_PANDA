package com.salestorm.controller;

import com.salestorm.domain.Inventory;
import com.salestorm.repository.InventoryRepository;
import com.salestorm.simulator.FailureSimulator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final InventoryRepository inventoryRepository;
    private final FailureSimulator failureSimulator;

    public ApiController(InventoryRepository inventoryRepository, FailureSimulator failureSimulator) {
        this.inventoryRepository = inventoryRepository;
        this.failureSimulator = failureSimulator;
        
        // Seed initial data if empty for demo purposes
        if(inventoryRepository.count() == 0) {
            inventoryRepository.save(new Inventory(101L, 100));
        }
    }

    @GetMapping("/inventory/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Inventory inv = inventoryRepository.findById(101L).orElse(new Inventory(101L, 100));
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", inv.getTotalInventory());
        stats.put("available", inv.getAvailableQuantity());
        stats.put("reserved", inv.getReservedQuantity());
        stats.put("sold", inv.getSoldQuantity());
        
        // In a real app, these would come from an analytics table or prometheus metrics
        stats.put("requests", 0);
        stats.put("success", inv.getSoldQuantity());
        stats.put("rejected", 0);
        stats.put("overselling", 0);
        
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/admin/simulate-failure")
    public ResponseEntity<String> simulateFailure(@RequestBody Map<String, String> payload) {
        String type = payload.get("failure_type");
        try {
            FailureSimulator.FailureType failureType = FailureSimulator.FailureType.valueOf(type);
            failureSimulator.enableFailure(failureType);
            return ResponseEntity.ok("Failure injected: " + type);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Unknown failure type");
        }
    }
}
