package com.zliy221.dataops.alert;

import com.zliy221.dataops.realtime.MetricWebSocketHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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
        return severity == null
                ? repository.findTop100ByStatusOrderByCreatedAtDesc(status)
                : repository.findTop100ByStatusAndSeverityOrderByCreatedAtDesc(status, severity);
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

    private Alert find(long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "告警不存在"));
    }
}

