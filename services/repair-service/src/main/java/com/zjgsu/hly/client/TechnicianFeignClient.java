package com.zjgsu.hly.client;
import com.zjgsu.hly.client.DependencyClient.TechnicianInfo;
import com.zjgsu.hly.dto.PortalRequests.TechnicianCreate;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "${dependencies.technician-service:technician-service}")
public interface TechnicianFeignClient {
    @GetMapping("/api/technicians/{id}") TechnicianInfo get(@PathVariable("id") Long id);
    @GetMapping("/api/technicians") List<TechnicianInfo> list();
    @PostMapping("/api/technicians") TechnicianInfo create(@RequestBody TechnicianCreate request);
}
