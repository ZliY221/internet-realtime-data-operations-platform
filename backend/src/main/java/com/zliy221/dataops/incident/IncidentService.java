package com.zliy221.dataops.incident;

import com.zliy221.dataops.alert.Alert;
import com.zliy221.dataops.alert.AlertRepository;
import com.zliy221.dataops.alert.AlertSeverity;
import com.zliy221.dataops.realtime.MetricWebSocketHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IncidentService {
    private final IncidentRepository repository;
    private final AlertRepository alertRepository;
    private final MetricWebSocketHandler webSocketHandler;

    public IncidentService(IncidentRepository repository, AlertRepository alertRepository,
                           MetricWebSocketHandler webSocketHandler) {
        this.repository = repository;
        this.alertRepository = alertRepository;
        this.webSocketHandler = webSocketHandler;
    }

    @Transactional
    public List<Incident> recordAlerts(List<Alert> alerts) {
        Map<Long, Incident> touched = new LinkedHashMap<>();
        for (Alert alert : alerts) {
            Incident incident = repository
                    .findFirstByServiceNameAndStatusNotOrderByUpdatedAtDesc(
                            alert.getServiceName(), IncidentStatus.RESOLVED)
                    .orElse(null);
            if (incident == null) {
                incident = repository.save(new Incident(
                        alert.getServiceName(), alert.getSeverity(), alert.getCreatedAt()));
            } else {
                incident.append(alert.getSeverity(), alert.getCreatedAt());
                incident = repository.save(incident);
            }
            alert.attachToIncident(incident.getId());
            alertRepository.save(alert);
            touched.put(incident.getId(), incident);
        }
        return new ArrayList<>(touched.values());
    }

    @Transactional(readOnly = true)
    public List<Incident> list(IncidentStatus status) {
        return repository.findTop100ByStatusOrderByUpdatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public IncidentSummary summary() {
        return new IncidentSummary(
                repository.countByStatus(IncidentStatus.OPEN),
                repository.countByStatus(IncidentStatus.INVESTIGATING),
                repository.countByStatus(IncidentStatus.RESOLVED),
                repository.countByStatusNotAndSeverity(IncidentStatus.RESOLVED, AlertSeverity.CRITICAL));
    }

    @Transactional
    public Incident update(long id, IncidentUpdateRequest request) {
        Incident incident = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("事故不存在: " + id));
        if (request.owner() != null && !request.owner().isBlank()) incident.assign(request.owner());
        if (request.status() == IncidentStatus.INVESTIGATING) incident.investigate();
        else if (request.status() == IncidentStatus.RESOLVED) incident.resolve();
        else if (request.status() == IncidentStatus.OPEN && incident.getStatus() != IncidentStatus.OPEN) {
            throw new IllegalArgumentException("事故状态不能回退为 OPEN");
        }
        Incident updated = repository.save(incident);
        webSocketHandler.broadcast("incident.updated", updated);
        return updated;
    }
}
