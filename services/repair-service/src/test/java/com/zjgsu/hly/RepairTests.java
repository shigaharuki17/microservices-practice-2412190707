package com.zjgsu.hly;
import com.zjgsu.hly.client.DependencyClient;
import com.zjgsu.hly.client.DependencyClient.*;
import com.zjgsu.hly.dto.RepairRequests.*;
import com.zjgsu.hly.error.BusinessException;
import com.zjgsu.hly.repository.RepairOrderRepository;
import com.zjgsu.hly.service.RepairService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class RepairTests {
 @Autowired RepairService service;
 @Autowired RepairOrderRepository orders;
 @MockitoBean DependencyClient dependency;
 @BeforeEach void setup() {
  when(dependency.resident(1L)).thenReturn(new ResidentInfo(1L,"测试住户","13900000101"));
  when(dependency.technician(1L)).thenReturn(new TechnicianInfo(1L,"水管师傅","13800000101","PLUMBING",true));
 }
 private Create request() { return new Create(1L,"1幢101","漏水","PLUMBING"); }
 @Test void contextLoads() { assertNotNull(service); }
 @Test void completesCrossServiceWorkflow() {
  var order=service.create(request());
  assertEquals("SUBMITTED",order.status);
  service.review(order.id,new Review(true,"同意"));
  service.assign(order.id,new Assign(1L));
  service.accept(order.id,new Assign(1L));
  assertEquals("ACCEPTED",service.get(order.id).status);
  verify(dependency).resident(1L); verify(dependency,times(2)).technician(1L);
 }
 @Test void absentResidentNeverCreatesLocalRow() {
  when(dependency.resident(1L)).thenThrow(new BusinessException(404,"依赖资源不存在"));
  assertEquals(404,assertThrows(BusinessException.class,()->service.create(request())).status);
  assertEquals(0,orders.count());
 }
 @Test void unavailableResidentServiceNeverCreatesLocalRow() {
  when(dependency.resident(1L)).thenThrow(new BusinessException(503,"依赖服务不可用"));
  assertEquals(503,assertThrows(BusinessException.class,()->service.create(request())).status);
  assertEquals(0,orders.count());
 }
 @Test void mismatchedSkillDoesNotAssign() {
  var o=service.create(request()); service.review(o.id,new Review(true,"同意"));
  when(dependency.technician(2L)).thenReturn(new TechnicianInfo(2L,"电工","13800000102","ELECTRICAL",true));
  assertEquals(409,assertThrows(BusinessException.class,()->service.assign(o.id,new Assign(2L))).status);
  assertEquals("APPROVED",service.get(o.id).status); assertNull(o.technicianId);
 }
 @Test void missingTechnicianDoesNotAssign() {
  var o=service.create(request()); service.review(o.id,new Review(true,"同意"));
  when(dependency.technician(999L)).thenThrow(new BusinessException(404,"不存在"));
  assertEquals(404,assertThrows(BusinessException.class,()->service.assign(o.id,new Assign(999L))).status);
  assertNull(o.technicianId); assertEquals("APPROVED",o.status);
 }
 @Test void invalidStateCannotSkipReview() {
  var o=service.create(request());
  assertEquals(409,assertThrows(BusinessException.class,()->service.assign(o.id,new Assign(1L))).status);
  verify(dependency,never()).technician(anyLong());
 }
}
