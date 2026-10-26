package com.zjgsu.hly.controller;
import com.zjgsu.hly.dto.RepairRequests.*;
import com.zjgsu.hly.model.RepairOrder;
import com.zjgsu.hly.service.RepairService;
import jakarta.validation.Valid;
import java.util.List;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/repair-orders")
public class RepairController {
    private final RepairService service;
    public RepairController(RepairService service) { this.service=service; }
    @PostMapping public ResponseEntity<RepairOrder> create(@Valid @RequestBody Create r) {
        var o=service.create(r); return ResponseEntity.created(URI.create("/api/repair-orders/"+o.id)).body(o);
    }
    @GetMapping public List<RepairOrder> list() { return service.list(); }
    @GetMapping("/{id}") public RepairOrder get(@PathVariable Long id) { return service.get(id); }
    @PostMapping("/{id}/review") public RepairOrder review(@PathVariable Long id,@Valid @RequestBody Review r) { return service.review(id,r); }
    @PostMapping("/{id}/assign") public RepairOrder assign(@PathVariable Long id,@Valid @RequestBody Assign r) { return service.assign(id,r); }
    @PostMapping("/{id}/accept") public RepairOrder accept(@PathVariable Long id,@Valid @RequestBody Assign r) { return service.accept(id,r); }
}
