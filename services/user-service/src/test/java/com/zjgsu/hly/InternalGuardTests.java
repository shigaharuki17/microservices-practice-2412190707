package com.zjgsu.hly;
import com.zjgsu.hly.security.InternalRequestFilter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
class InternalGuardTests {
 String key="unit-test-internal-key-not-for-deployment-123456";
 @Test void blocksForgedIdentityWithoutServerKey()throws Exception{
  var req=new MockHttpServletRequest("GET","/api/residents");req.setServletPath("/api/residents");
  req.addHeader("X-Authenticated-Role","ADMIN");
  var response=new MockHttpServletResponse();var called=new AtomicBoolean();
  new InternalRequestFilter(key).doFilter(req,response,(r,s)->called.set(true));
  assertEquals(403,response.getStatus());assertFalse(called.get());
 }
 @Test void permitsServerAuthenticatedCall()throws Exception{
  var req=new MockHttpServletRequest("GET","/api/residents");req.setServletPath("/api/residents");req.addHeader("X-Internal-API-Key",key);
  var response=new MockHttpServletResponse();var called=new AtomicBoolean();
  new InternalRequestFilter(key).doFilter(req,response,(r,s)->called.set(true));assertTrue(called.get());
 }
}
