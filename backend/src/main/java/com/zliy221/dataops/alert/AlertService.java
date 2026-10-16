package com.zliy221.dataops.alert;

import com.zliy221.dataops.realtime.MetricWebSocketHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.LinkedHashSet;
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
        return list(status, severity, null, null);
    }

    @Transactional(readOnly = true)
    public List<Alert> list(AlertStatus status, AlertSeverity severity, String owner) {
        return list(status, severity, owner, null);
    }

    @Transactional(readOnly = true)
    public List<Alert> list(AlertStatus status, AlertSeverity severity, String owner, String serviceName) {
        String normalizedOwner = owner == null || owner.isBlank() ? null : owner.trim();
        String normalizedService = serviceName == null || serviceName.isBlank() ? null : serviceName.trim();
        if (normalizedService != null && normalizedService.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "服务名称不能超过 80 个字符");
        }
        if (normalizedService != null && normalizedOwner != null && severity != null) {
            return repository.findTop100ByStatusAndSeverityAndOwnerAndServiceNameOrderByCreatedAtDesc(
                    status, severity, normalizedOwner, normalizedService);
        }
        if (normalizedService != null && normalizedOwner != null) {
            return repository.findTop100ByStatusAndOwnerAndServiceNameOrderByCreatedAtDesc(
                    status, normalizedOwner, normalizedService);
        }
        if (normalizedService != null && severity != null) {
            return repository.findTop100ByStatusAndSeverityAndServiceNameOrderByCreatedAtDesc(
                    status, severity, normalizedService);
        }
        if (normalizedService != null) {
            return repository.findTop100ByStatusAndServiceNameOrderByCreatedAtDesc(status, normalizedService);
        }
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

    @Transactional
    public AlertBulkResult bulk(List<Long> ids, AlertBulkAction action) {
        List<Long> uniqueIds = ids == null ? List.of() : new LinkedHashSet<>(ids).stream().toList();
        if (uniqueIds.isEmpty() || uniqueIds.size() > 100 || action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量操作需要 1 到 100 个告警和有效动作");
        }
        List<Alert> alerts = uniqueIds.stream().map(this::find).toList();
        for (Alert alert : alerts) {
            if (action == AlertBulkAction.ACKNOWLEDGE && alert.getStatus() == AlertStatus.OPEN) {
                alert.acknowledge();
            } else if (action == AlertBulkAction.RESOLVE) {
                alert.resolve();
            }
        }
        List<Alert> updated = repository.saveAll(alerts);
        webSocketHandler.broadcast("alert.batch-updated", Map.of("action", action, "count", updated.size()));
        return new AlertBulkResult(action, updated.size(), updated);
    }

    @Transactional
    public AlertBulkOwnerResult bulkAssign(List<Long> ids, String owner) {
        List<Long> uniqueIds = ids == null ? List.of() : new LinkedHashSet<>(ids).stream().toList();
        String normalizedOwner = owner == null ? "" : owner.trim();
        if (uniqueIds.isEmpty() || uniqueIds.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量分派需要 1 到 100 个告警");
        }
        if (normalizedOwner.isEmpty() || normalizedOwner.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "负责人名称长度应为 1 到 80 个字符");
        }
        List<Alert> alerts = uniqueIds.stream().map(this::find).toList();
        alerts.forEach(alert -> alert.assign(normalizedOwner));
        List<Alert> updated = repository.saveAll(alerts);
        webSocketHandler.broadcast("alert.batch-assigned", Map.of("owner", normalizedOwner, "count", updated.size()));
        return new AlertBulkOwnerResult(normalizedOwner, updated.size(), updated);
    }

    private Alert find(long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "告警不存在"));
    }
}

