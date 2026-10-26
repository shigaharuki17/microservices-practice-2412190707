package com.zjgsu.hly.model;
import jakarta.persistence.*;
@Entity
@Table(name="residents", uniqueConstraints=@UniqueConstraint(name="uk_residents_phone", columnNames="phone"))
public class Resident {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false, length=50) public String name;
    @Column(nullable=false, length=20) public String phone;

    @Version public Long version;
}
