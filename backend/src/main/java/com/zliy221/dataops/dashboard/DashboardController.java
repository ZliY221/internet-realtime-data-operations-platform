package com.zliy221.dataops.dashboard;

import com.zliy221.dataops.alert.AlertRepository;
import com.zliy221.dataops.alert.AlertStatus;
import com.zliy221.dataops.metric.TrafficMetric;
import com.zliy221.dataops.metric.TrafficMetricRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final TrafficMetricRepository metricRepository;
    private final AlertRepository alertRepository;

    public DashboardController(TrafficMetricRepository metricRepository, AlertRepository alertRepository) {
        this.metricRepository = metricRepository;
        this.alertRepository = alertRepository;
    }

    @GetMapping("/summary")
    public DashboardSummary summary(@RequestParam(defaultValue = "60") int minutes) {
        int safeMinutes = Math.max(1, Math.min(minutes, 1440));
        Instant end = Instant.now();
        Instant start = end.minus(safeMinutes, ChronoUnit.MINUTES);
        List<TrafficMetric> metrics = metricRepository.findSince(start);
        long requests = metrics.stream().mapToLong(TrafficMetric::getRequestCount).sum();
        long errors = metrics.stream().mapToLong(TrafficMetric::getErrorCount).sum();
        double avgP95 = metrics.stream().mapToLong(TrafficMetric::getP95LatencyMs).average().orElse(0);
        return new DashboardSummary(start, end, requests, errors,
                requests == 0 ? 0 : (double) errors / requests, avgP95,
                alertRepository.countByStatusNot(AlertStatus.RESOLVED),
                (int) metrics.stream().map(TrafficMetric::getServiceName).distinct().count());
    }
}

