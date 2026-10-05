package com.salestorm.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    private Long productId;
    
    private Integer totalInventory;
    private Integer availableQuantity;
    private Integer reservedQuantity = 0;
    private Integer soldQuantity = 0;
    private Integer version = 0;

    public Inventory() {}

    public Inventory(Long productId, Integer totalInventory) {
        this.productId = productId;
        this.totalInventory = totalInventory;
        this.availableQuantity = totalInventory;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    
    public Integer getTotalInventory() { return totalInventory; }
    public void setTotalInventory(Integer totalInventory) { this.totalInventory = totalInventory; }
    
    public Integer getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
    
    public Integer getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    
    public Integer getSoldQuantity() { return soldQuantity; }
    public void setSoldQuantity(Integer soldQuantity) { this.soldQuantity = soldQuantity; }
    
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
