package com.zjgsu.hly.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="repair_orders")
public class RepairOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public Long residentId;
    public Long technicianId; // External ID only: deliberately no JPA relationship or foreign key.
    @Column(nullable=false, length=50) public String residentName;
    @Column(nullable=false, length=20) public String contactPhone;
    @Column(nullable=false, length=200) public String location;
    @Column(nullable=false, length=1000) public String description;
    @Column(nullable=false, length=20) public String category;
    @Column(nullable=false, length=20) public String status;
    @Column(length=300) public String reviewNote;
    @Column(nullable=false, updatable=false) public LocalDateTime createdAt;
    @Column(nullable=false) public LocalDateTime updatedAt;
    @Version public Long version;
    @PrePersist void created() { createdAt=updatedAt=LocalDateTime.now(); }
    @PreUpdate void updated() { updatedAt=LocalDateTime.now(); }
}
