package com.zjgsu.hly.controller;
import com.zjgsu.hly.dto.ResidentDto.*;
import com.zjgsu.hly.service.ResidentService;
import jakarta.validation.Valid;
import java.util.List;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/residents")
public class ResidentController {
    private final ResidentService service;
    public ResidentController(ResidentService service) { this.service=service; }
    @PostMapping public ResponseEntity<View> create(@Valid @RequestBody Create body) {
        View result=service.create(body); return ResponseEntity.created(URI.create("/api/residents/"+result.id())).body(result);
    }
    @GetMapping public List<View> list() { return service.list(); }
    @GetMapping("/{id}") public View get(@PathVariable Long id) { return service.get(id); }
}
