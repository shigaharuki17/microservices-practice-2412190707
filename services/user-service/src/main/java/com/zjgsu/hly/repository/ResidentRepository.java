package com.zjgsu.hly.repository;
import com.zjgsu.hly.model.Resident;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ResidentRepository extends JpaRepository<Resident, Long> { boolean existsByPhone(String phone); }
