package com.zjgsu.hly.controller;
import com.zjgsu.hly.dto.TechnicianDto.*;
import com.zjgsu.hly.service.TechnicianService;
import jakarta.validation.Valid;
import java.util.List;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/technicians")
public class TechnicianController {
    private final TechnicianService service;
    public TechnicianController(TechnicianService service) { this.service=service; }
    @PostMapping public ResponseEntity<View> create(@Valid @RequestBody Create body) {
        View result=service.create(body); return ResponseEntity.created(URI.create("/api/technicians/"+result.id())).body(result);
    }
    @GetMapping public List<View> list() { return service.list(); }
    @GetMapping("/{id}") public View get(@PathVariable Long id) { return service.get(id); }
}
