package com.zjgsu.hly.model;

import jakarta.persistence.*;

@Entity
@Table(name = "technicians", uniqueConstraints = @UniqueConstraint(name = "uk_technician_phone", columnNames = "phone"))
public class Technician {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(nullable = false, length = 20)
    private String phone;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private RepairCategory skill;
    @Column(nullable = false)
    private boolean available;

    protected Technician() {}
    public Technician(String name, String phone, RepairCategory skill, boolean available) {
        this.name = name; this.phone = phone; this.skill = skill; this.available = available;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public RepairCategory getSkill() { return skill; }
    public boolean isAvailable() { return available; }
}
