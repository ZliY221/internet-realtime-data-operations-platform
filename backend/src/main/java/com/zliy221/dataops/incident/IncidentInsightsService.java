package com.zliy221.dataops.incident;

import com.zliy221.dataops.metric.TrafficMetric;
import com.zliy221.dataops.metric.TrafficMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.temporal.ChronoUnit;

@Service
public class IncidentInsightsService {
    private final IncidentRepository incidentRepository;
    private final TrafficMetricRepository metricRepository;

    public IncidentInsightsService(IncidentRepository incidentRepository, TrafficMetricRepository metricRepository) {
        this.incidentRepository = incidentRepository;
        this.metricRepository = metricRepository;
    }

    public record TimelineEvent(String type, String label, Instant occurredAt) {}
    public record BlastRadius(String serviceName, List<String> upstream, List<String> downstream,
                              int impactedServiceCount, String riskLevel) {}
    public record ErrorBudget(String serviceName, int windowMinutes, double objective,
                              long requestCount, long errorCount, double availability,
                              long remainingErrorBudget, String status) {}

    private static final Map<String, List<String>> DEPENDENCIES = Map.of(
            "gateway", List.of("checkout", "catalog"),
            "checkout", List.of("payment", "inventory"),
            "payment", List.of("notification"),
            "catalog", List.of("search")
    );

    @Transactional(readOnly = true)
    public List<TimelineEvent> timeline(long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("事故不存在: " + id));
        List<TimelineEvent> events = new ArrayList<>();
        events.add(new TimelineEvent("DETECTED", "首次触发告警", incident.getFirstAlertAt()));
        if (incident.getStatus() == IncidentStatus.INVESTIGATING || incident.getStatus() == IncidentStatus.RESOLVED) {
            events.add(new TimelineEvent("INVESTIGATING", "事故已接手处理", incident.getUpdatedAt()));
        }
        if (incident.getResolvedAt() != null) {
            events.add(new TimelineEvent("RESOLVED", "服务已确认恢复", incident.getResolvedAt()));
        }
        return events.stream().sorted((left, right) -> left.occurredAt().compareTo(right.occurredAt())).toList();
    }

    public BlastRadius blastRadius(String serviceName) {
        String normalized = serviceName == null ? "" : serviceName.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new IllegalArgumentException("服务名称长度应为 1 到 80 个字符");
        }
        List<String> downstream = DEPENDENCIES.getOrDefault(normalized, List.of());
        List<String> upstream = DEPENDENCIES.entrySet().stream()
                .filter(entry -> entry.getValue().contains(normalized)).map(Map.Entry::getKey).sorted().toList();
        int count = downstream.size();
        String risk = count >= 2 ? "HIGH" : count == 1 ? "MEDIUM" : "LOW";
        return new BlastRadius(normalized, upstream, downstream, count, risk);
    }

    @Transactional(readOnly = true)
    public ErrorBudget errorBudget(String serviceName, int minutes, double objective) {
        if (minutes < 1 || minutes > 43200) throw new IllegalArgumentException("观察窗口应为 1 到 43200 分钟");
        if (objective <= 0 || objective >= 1) throw new IllegalArgumentException("SLO 目标应在 0 与 1 之间");
        String normalized = serviceName == null ? "" : serviceName.trim();
        if (normalized.isEmpty() || normalized.length() > 80) throw new IllegalArgumentException("服务名称长度应为 1 到 80 个字符");
        List<TrafficMetric> metrics = metricRepository.findSinceForService(Instant.now().minus(minutes, ChronoUnit.MINUTES), normalized);
        long requests = metrics.stream().mapToLong(TrafficMetric::getRequestCount).sum();
        long errors = metrics.stream().mapToLong(TrafficMetric::getErrorCount).sum();
        double availability = requests == 0 ? 1.0 : 1.0 - (double) errors / requests;
        long allowedErrors = (long) Math.floor(requests * (1.0 - objective));
        long remaining = allowedErrors - errors;
        String status = requests == 0 ? "NO_DATA" : remaining < 0 ? "EXHAUSTED" : remaining <= Math.max(1, allowedErrors / 4) ? "AT_RISK" : "HEALTHY";
        return new ErrorBudget(normalized, minutes, objective, requests, errors, availability, remaining, status);
    }
}
