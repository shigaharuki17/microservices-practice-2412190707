package com.zjgsu.hly;
import com.zjgsu.hly.dto.TechnicianDto.Create;
import com.zjgsu.hly.service.TechnicianService;
import com.zjgsu.hly.repository.TechnicianRepository;
import com.zjgsu.hly.error.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class TechnicianTests {
 @Autowired TechnicianService service;
 @Autowired TechnicianRepository repository;
 @Autowired EntityManager em;
 @Test void contextLoads() { assertNotNull(service); }
 @Test void writesAndReadsOwnDatabase() {
  var saved=service.create(new Create("测试师傅", "13800000101", "PLUMBING", true)); em.clear();
  assertEquals(saved.name(),service.get(saved.id()).name());
  assertEquals(1,repository.count());
 }
 @Test void rejectsDuplicatePhone() {
  service.create(new Create("测试师傅", "13800000101", "PLUMBING", true));
  assertEquals(409,assertThrows(BusinessException.class,()->service.create(new Create("测试师傅", "13800000101", "PLUMBING", true))).status);
 }
 @Test void missingResourceIs404() { assertEquals(404,assertThrows(BusinessException.class,()->service.get(999L)).status); }
}
