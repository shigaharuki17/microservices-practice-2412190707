package com.zjgsu.hly.controller;
import com.zjgsu.hly.dto.RepairRequests.*;
import com.zjgsu.hly.dto.RepairViews.*;
import com.zjgsu.hly.service.RepairService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RepairController {
    private final RepairService service;
    public RepairController(RepairService service) { this.service = service; }
    @PostMapping("/technicians")
    public ResponseEntity<TechnicianView> createTechnician(@Valid @RequestBody CreateTechnician request) {
        return ResponseEntity.status(201).body(service.createTechnician(request));
    }
    @GetMapping("/technicians") public List<TechnicianView> technicians() { return service.technicians(); }
    @PostMapping("/repair-orders")
    public ResponseEntity<OrderView> createOrder(@Valid @RequestBody CreateOrder request) {
        var order = service.createOrder(request);
        return ResponseEntity.created(URI.create("/api/repair-orders/" + order.id())).body(order);
    }
    @GetMapping("/repair-orders") public List<OrderView> orders() { return service.orders(); }
    @GetMapping("/repair-orders/{id}") public OrderView order(@PathVariable Long id) { return service.order(id); }
    @PostMapping("/repair-orders/{id}/review")
    public OrderView review(@PathVariable Long id, @Valid @RequestBody Review request) { return service.review(id, request); }
    @PostMapping("/repair-orders/{id}/assign")
    public OrderView assign(@PathVariable Long id, @Valid @RequestBody TechnicianAction request) { return service.assign(id, request); }
    @PostMapping("/repair-orders/{id}/accept")
    public OrderView accept(@PathVariable Long id, @Valid @RequestBody TechnicianAction request) { return service.accept(id, request); }
}
