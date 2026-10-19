package com.zliy221.dataops.incident;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v2/incidents")
public class IncidentInsightsController {
    private final IncidentInsightsService service;

    public IncidentInsightsController(IncidentInsightsService service) { this.service = service; }

    @GetMapping("/{id}/timeline")
    public List<IncidentInsightsService.TimelineEvent> timeline(@PathVariable long id) {
        return service.timeline(id);
    }
}
