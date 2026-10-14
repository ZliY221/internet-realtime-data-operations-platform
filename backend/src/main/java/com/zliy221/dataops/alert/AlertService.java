package com.zliy221.dataops.alert;

import com.zliy221.dataops.realtime.MetricWebSocketHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AlertService {
    private final AlertRepository repository;
    private final MetricWebSocketHandler webSocketHandler;

    public AlertService(AlertRepository repository, MetricWebSocketHandler webSocketHandler) {
        this.repository = repository;
        this.webSocketHandler = webSocketHandler;
    }

    @Transactional(readOnly = true)
    public List<Alert> list(AlertStatus status, AlertSeverity severity) {
        return list(status, severity, null);
    }

    @Transactional(readOnly = true)
    public List<Alert> list(AlertStatus status, AlertSeverity severity, String owner) {
        String normalizedOwner = owner == null || owner.isBlank() ? null : owner.trim();
        if (normalizedOwner != null && severity != null) {
            return repository.findTop100ByStatusAndSeverityAndOwnerOrderByCreatedAtDesc(status, severity, normalizedOwner);
        }
        if (normalizedOwner != null) {
            return repository.findTop100ByStatusAndOwnerOrderByCreatedAtDesc(status, normalizedOwner);
        }
        return severity == null ? repository.findTop100ByStatusOrderByCreatedAtDesc(status)
                : repository.findTop100ByStatusAndSeverityOrderByCreatedAtDesc(status, severity);
    }

    @Transactional(readOnly = true)
    public List<AlertWorkload> workloads() {
        Map<String, long[]> grouped = new TreeMap<>();
        for (Alert alert : repository.findTop200ByStatusNotOrderByCreatedAtDesc(AlertStatus.RESOLVED)) {
            String owner = alert.getOwner() == null ? "未分派" : alert.getOwner();
            long[] counts = grouped.computeIfAbsent(owner, ignored -> new long[5]);
            counts[0]++;
            counts[alert.getStatus() == AlertStatus.OPEN ? 1 : 2]++;
            counts[alert.getSeverity() == AlertSeverity.CRITICAL ? 3 : 4]++;
        }
        return grouped.entrySet().stream()
                .map(entry -> new AlertWorkload(entry.getKey(), entry.getValue()[0], entry.getValue()[1],
                        entry.getValue()[2], entry.getValue()[3], entry.getValue()[4]))
                .sorted((left, right) -> {
                    int critical = Long.compare(right.critical(), left.critical());
                    return critical != 0 ? critical : Long.compare(right.total(), left.total());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AlertSummary summary() {
        return new AlertSummary(
                repository.countByStatus(AlertStatus.OPEN),
                repository.countByStatus(AlertStatus.ACKNOWLEDGED),
                repository.countByStatus(AlertStatus.RESOLVED),
                repository.countBySeverityAndStatusNot(AlertSeverity.CRITICAL, AlertStatus.RESOLVED),
                repository.countBySeverityAndStatusNot(AlertSeverity.WARNING, AlertStatus.RESOLVED)
        );
    }

    @Transactional
    public Alert acknowledge(long id) {
        Alert alert = find(id);
        alert.acknowledge();
        Alert updated = repository.save(alert);
        webSocketHandler.broadcast("alert.updated", updated);
        return updated;
    }

    @Transactional
    public Alert resolve(long id) {
        Alert alert = find(id);
        alert.resolve();
        Alert updated = repository.save(alert);
        webSocketHandler.broadcast("alert.updated", updated);
        return updated;
    }

    @Transactional
    public Alert assign(long id, String owner) {
        Alert alert = find(id);
        alert.assign(owner);
        Alert updated = repository.save(alert);
        webSocketHandler.broadcast("alert.assigned", updated);
        return updated;
    }

    private Alert find(long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "告警不存在"));
    }
}

