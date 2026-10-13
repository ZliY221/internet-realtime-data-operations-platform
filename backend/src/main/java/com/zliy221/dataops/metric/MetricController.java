package com.zliy221.dataops.metric;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricController {
    private final MetricService metricService;

    public MetricController(MetricService metricService) {
        this.metricService = metricService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetricResponse ingest(@Valid @RequestBody MetricRequest request) {
        return metricService.ingest(request);
    }

    @GetMapping("/recent")
    public List<MetricResponse> recent(@RequestParam(defaultValue = "60") int limit) {
        return metricService.recent(limit);
    }
}

