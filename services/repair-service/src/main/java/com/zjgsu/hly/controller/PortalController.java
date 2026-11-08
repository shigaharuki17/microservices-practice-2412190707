package com.zjgsu.hly.controller;

import com.zjgsu.hly.client.DependencyClient;
import com.zjgsu.hly.client.DependencyClient.*;
import com.zjgsu.hly.dto.PortalRequests.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Same-origin UI facade. Each owning service still validates and stores its own data. */
@RestController
@RequestMapping("/api/portal")
public class PortalController {
    private final DependencyClient dependencies;
    public PortalController(DependencyClient dependencies) { this.dependencies = dependencies; }

    @GetMapping("/residents")
    public List<ResidentInfo> residents() { return dependencies.residents(); }

    @PostMapping("/residents")
    public ResponseEntity<ResidentInfo> createResident(@Valid @RequestBody ResidentCreate request) {
        var resident = dependencies.createResident(request);
        return ResponseEntity.created(URI.create("/api/portal/residents/" + resident.id())).body(resident);
    }

    @GetMapping("/residents/{id}")
    public ResidentInfo resident(@PathVariable Long id) { return dependencies.resident(id); }

    @GetMapping("/technicians")
    public List<TechnicianInfo> technicians() { return dependencies.technicians(); }

    @PostMapping("/technicians")
    public ResponseEntity<TechnicianInfo> createTechnician(@Valid @RequestBody TechnicianCreate request) {
        var technician = dependencies.createTechnician(request);
        return ResponseEntity.created(URI.create("/api/portal/technicians/" + technician.id())).body(technician);
    }

    @GetMapping("/technicians/{id}")
    public TechnicianInfo technician(@PathVariable Long id) { return dependencies.technician(id); }
}
