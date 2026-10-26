package com.zjgsu.hly;
import com.zjgsu.hly.dto.ResidentDto.Create;
import com.zjgsu.hly.service.ResidentService;
import com.zjgsu.hly.repository.ResidentRepository;
import com.zjgsu.hly.error.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class ResidentTests {
 @Autowired ResidentService service;
 @Autowired ResidentRepository repository;
 @Autowired EntityManager em;
 @Test void contextLoads() { assertNotNull(service); }
 @Test void writesAndReadsOwnDatabase() {
  var saved=service.create(new Create("测试住户", "13900000101")); em.clear();
  assertEquals(saved.name(),service.get(saved.id()).name());
  assertEquals(1,repository.count());
 }
 @Test void rejectsDuplicatePhone() {
  service.create(new Create("测试住户", "13900000101"));
  assertEquals(409,assertThrows(BusinessException.class,()->service.create(new Create("测试住户", "13900000101"))).status);
 }
 @Test void missingResourceIs404() { assertEquals(404,assertThrows(BusinessException.class,()->service.get(999L)).status); }
}
