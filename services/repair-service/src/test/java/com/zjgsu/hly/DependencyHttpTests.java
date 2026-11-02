package com.zjgsu.hly;
import com.sun.net.httpserver.HttpServer;
import com.zjgsu.hly.client.DependencyClient;
import com.zjgsu.hly.error.BusinessException;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
class DependencyHttpTests {
 @Test void restTemplateReadsRealHttpAndMaps404() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/residents/1",ex->{
   byte[] data="{\"id\":1,\"name\":\"Resident\",\"phone\":\"13900000101\"}".getBytes(StandardCharsets.UTF_8);
   ex.getResponseHeaders().add("Content-Type","application/json"); ex.sendResponseHeaders(200,data.length);
   try(var out=ex.getResponseBody()){out.write(data);}
  });
  server.start();
  try {
   String url="http://127.0.0.1:"+server.getAddress().getPort();
   var discovery=discovery(server.getAddress().getPort());
   var client=new DependencyClient(discovery,"user-service","technician-service",2000,3000);
   assertEquals(1L,client.resident(1L).id());
   assertEquals(404,assertThrows(BusinessException.class,()->client.resident(999L)).status);
  } finally {server.stop(0);}
 }
 @Test void closedEndpointIs503() throws Exception {
  var socket=new java.net.ServerSocket(0); int port=socket.getLocalPort(); socket.close();
  var client=new DependencyClient(discovery(port),"user-service","technician-service",300,300);
  assertEquals(503,assertThrows(BusinessException.class,()->client.resident(1L)).status);
 }
 private DiscoveryClient discovery(int port) {
  var discovery=mock(DiscoveryClient.class);
  when(discovery.getInstances("user-service")).thenReturn(List.of(new DefaultServiceInstance("one","user-service","127.0.0.1",port,false)));
  return discovery;
 }
 @Test void emptyInstancesIs503() {
  var discovery=mock(DiscoveryClient.class);
  when(discovery.getInstances("user-service")).thenReturn(List.of());
  var client=new DependencyClient(discovery,"user-service","technician-service",2000,3000);
  assertEquals(503,assertThrows(BusinessException.class,()->client.resident(1L)).status);
 }
 @Test void rechecksDiscoveryOnEveryOperation() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/residents/1",ex->{
   byte[] data="{\"id\":1,\"name\":\"Resident\",\"phone\":\"13900000101\"}".getBytes(StandardCharsets.UTF_8);
   ex.getResponseHeaders().add("Content-Type","application/json");ex.sendResponseHeaders(200,data.length);
   try(var out=ex.getResponseBody()){out.write(data);}
  });server.start();
  try {
   var discovery=discovery(server.getAddress().getPort());
   var client=new DependencyClient(discovery,"user-service","technician-service",2000,3000);
   assertEquals(1L,client.resident(1L).id());
   when(discovery.getInstances("user-service")).thenReturn(List.of());
   assertEquals(503,assertThrows(BusinessException.class,()->client.resident(1L)).status);
   verify(discovery,times(2)).getInstances("user-service");
  } finally {server.stop(0);}
 }
}
