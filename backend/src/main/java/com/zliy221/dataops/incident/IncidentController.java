package com.zliy221.dataops.incident;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {
    private final IncidentService service;

    public IncidentController(IncidentService service) { this.service = service; }

    @GetMapping
    public List<Incident> list(@RequestParam(defaultValue = "OPEN") IncidentStatus status) {
        return service.list(status);
    }

    @GetMapping("/summary")
    public IncidentSummary summary() { return service.summary(); }

    @PatchMapping("/{id}")
    public Incident update(@PathVariable long id, @RequestBody IncidentUpdateRequest request) {
        return service.update(id, request);
    }
}
