package com.zjgsu.hly.client;
import com.zjgsu.hly.client.DependencyClient.ResidentInfo;
import com.zjgsu.hly.dto.PortalRequests.ResidentCreate;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "${dependencies.user-service:user-service}")
public interface ResidentFeignClient {
    @GetMapping("/api/residents/{id}") ResidentInfo get(@PathVariable("id") Long id);
    @GetMapping("/api/residents") List<ResidentInfo> list();
    @PostMapping("/api/residents") ResidentInfo create(@RequestBody ResidentCreate request);
}
