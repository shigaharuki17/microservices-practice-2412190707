package com.zjgsu.hly.model;
import jakarta.persistence.*;
@Entity
@Table(name="technicians", uniqueConstraints=@UniqueConstraint(name="uk_technicians_phone", columnNames="phone"))
public class Technician {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false, length=50) public String name;
    @Column(nullable=false, length=20) public String phone;
    @Column(nullable=false, length=20) public String skill;
    @Column(nullable=false) public boolean available;
    @Version public Long version;
}
