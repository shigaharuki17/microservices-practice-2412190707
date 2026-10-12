package com.zjgsu.hly.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "repair_orders", indexes = @Index(name = "idx_order_status", columnList = "status"))
public class RepairOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50)
    private String residentName;
    @Column(nullable = false, length = 20)
    private String contactPhone;
    @Column(nullable = false, length = 200)
    private String location;
    @Column(nullable = false, length = 1000)
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private RepairCategory category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private OrderStatus status;
    @Column(length = 300)
    private String reviewNote;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id", foreignKey = @ForeignKey(name = "fk_order_technician"))
    private Technician technician;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Version
    private Long version;

    protected RepairOrder() {}
    public RepairOrder(String residentName, String contactPhone, String location,
                       String description, RepairCategory category) {
        this.residentName = residentName; this.contactPhone = contactPhone;
        this.location = location; this.description = description;
        this.category = category; this.status = OrderStatus.SUBMITTED;
    }
    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public void review(boolean approved, String note) {
        status = approved ? OrderStatus.APPROVED : OrderStatus.REJECTED;
        reviewNote = note;
    }
    public void assign(Technician technician) { this.technician = technician; status = OrderStatus.ASSIGNED; }
    public void accept() { status = OrderStatus.ACCEPTED; }
    public Long getId() { return id; }
    public String getResidentName() { return residentName; }
    public String getContactPhone() { return contactPhone; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public RepairCategory getCategory() { return category; }
    public OrderStatus getStatus() { return status; }
    public String getReviewNote() { return reviewNote; }
    public Technician getTechnician() { return technician; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
