package com.zjgsu.hly.repository;
import com.zjgsu.hly.model.Technician;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TechnicianRepository extends JpaRepository<Technician, Long> { boolean existsByPhone(String phone); }
