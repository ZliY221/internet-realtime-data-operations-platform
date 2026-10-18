package com.zliy221.dataops.alert;

import com.zliy221.dataops.realtime.MetricWebSocketHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

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

    @Transactional
    public AlertDispatchResult dispatch(List<Long> ids, String owner, AlertBulkAction action) {
        List<Long> uniqueIds = ids == null ? List.of() : new LinkedHashSet<>(ids).stream().toList();
        String normalizedOwner = owner == null ? "" : owner.trim();
        if (uniqueIds.isEmpty() || uniqueIds.size() > 100 || action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "统一处置需要 1 到 100 个告警和有效动作");
        }
        if (normalizedOwner.isEmpty() || normalizedOwner.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "负责人名称长度应为 1 到 80 个字符");
        }
        List<Alert> alerts = uniqueIds.stream().map(this::find).toList();
        int assignedCount = 0;
        int statusChangedCount = 0;
        int changedCount = 0;
        for (Alert alert : alerts) {
            boolean changed = false;
            if (!normalizedOwner.equals(alert.getOwner())) {
                alert.assign(normalizedOwner);
                assignedCount++;
                changed = true;
            }
            AlertStatus before = alert.getStatus();
            if (action == AlertBulkAction.ACKNOWLEDGE && before == AlertStatus.OPEN) {
                alert.acknowledge();
            } else if (action == AlertBulkAction.RESOLVE && before != AlertStatus.RESOLVED) {
                alert.resolve();
            }
            if (alert.getStatus() != before) {
                statusChangedCount++;
                changed = true;
            }
            if (changed) changedCount++;
        }
        List<Alert> updated = changedCount > 0 ? repository.saveAll(alerts) : alerts;
        List<String> services = alerts.stream().map(Alert::getServiceName).distinct().sorted().toList();
        int criticalCount = (int) alerts.stream().filter(alert -> alert.getSeverity() == AlertSeverity.CRITICAL).count();
        int warningCount = alerts.size() - criticalCount;
        String receiptId = "DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant executedAt = Instant.now();
        if (changedCount > 0) {
            webSocketHandler.broadcast("alert.batch-dispatched", Map.of(
                    "owner", normalizedOwner, "action", action,
                    "count", updated.size(), "changedCount", changedCount,
                    "assignedCount", assignedCount, "statusChangedCount", statusChangedCount,
                    "criticalCount", criticalCount, "warningCount", warningCount,
                    "serviceCount", services.size(), "receiptId", receiptId,
                    "executedAt", executedAt.toString()));
        }
        return new AlertDispatchResult(
                normalizedOwner, action, updated.size(), changedCount, updated.size() - changedCount,
                assignedCount, statusChangedCount, criticalCount, warningCount, services.size(), services,
                receiptId, executedAt, updated);
    }

    @Transactional(readOnly = true)
    public AlertDispatchPreview previewDispatch(List<Long> ids, String owner, AlertBulkAction action) {
        List<Long> uniqueIds = ids == null ? List.of() : new LinkedHashSet<>(ids).stream().toList();
        String normalizedOwner = owner == null ? "" : owner.trim();
        if (uniqueIds.isEmpty() || uniqueIds.size() > 100 || action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "统一处置预览需要 1 到 100 个告警和有效动作");
        }
        if (normalizedOwner.isEmpty() || normalizedOwner.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "负责人名称长度应为 1 到 80 个字符");
        }
        List<Alert> alerts = uniqueIds.stream().map(this::find).toList();
        int assignedCount = (int) alerts.stream().filter(alert -> !normalizedOwner.equals(alert.getOwner())).count();
        int statusChangedCount = (int) alerts.stream().filter(alert ->
                action == AlertBulkAction.RESOLVE && alert.getStatus() != AlertStatus.RESOLVED
                || action == AlertBulkAction.ACKNOWLEDGE && alert.getStatus() == AlertStatus.OPEN).count();
        int criticalCount = (int) alerts.stream().filter(alert -> alert.getSeverity() == AlertSeverity.CRITICAL).count();
        int warningCount = alerts.size() - criticalCount;
        List<String> services = alerts.stream().map(Alert::getServiceName).distinct().sorted().toList();
        return new AlertDispatchPreview(alerts.size(), assignedCount, statusChangedCount,
                criticalCount, warningCount, services.size(), services,
                action == AlertBulkAction.RESOLVE || criticalCount > 0);
    }

    private Alert find(long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "告警不存在"));
    }
}

