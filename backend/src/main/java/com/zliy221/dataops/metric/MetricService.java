package com.zliy221.dataops.metric;

import com.zliy221.dataops.alert.Alert;
import com.zliy221.dataops.alert.AlertPolicy;
import com.zliy221.dataops.alert.AlertRepository;
import com.zliy221.dataops.realtime.MetricWebSocketHandler;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Comparator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class MetricService {
    private final TrafficMetricRepository metricRepository;
    private final AlertRepository alertRepository;
    private final AlertPolicy alertPolicy;
    private final MetricWebSocketHandler webSocketHandler;

    public MetricService(TrafficMetricRepository metricRepository, AlertRepository alertRepository,
                         AlertPolicy alertPolicy, MetricWebSocketHandler webSocketHandler) {
        this.metricRepository = metricRepository;
        this.alertRepository = alertRepository;
        this.alertPolicy = alertPolicy;
        this.webSocketHandler = webSocketHandler;
    }

    @Transactional
    public MetricResponse ingest(MetricRequest request) {
        if (request.errorCount() > request.requestCount()) {
            throw new IllegalArgumentException("errorCount 不能大于 requestCount");
        }
        TrafficMetric metric = metricRepository.save(new TrafficMetric(request.serviceName().trim(),
                request.requestCount(), request.errorCount(), request.p95LatencyMs(), request.collectedAt()));
        List<Alert> alerts = alertPolicy.evaluate(metric).stream().map(alertRepository::save).toList();
        MetricResponse response = MetricResponse.from(metric);
        afterCommit(() -> {
            webSocketHandler.broadcast("metric.created", response);
            alerts.forEach(alert -> webSocketHandler.broadcast("alert.created", alert));
        });
        return response;
    }

    @Transactional(readOnly = true)
    public List<MetricResponse> recent(int limit) {
        return recent(limit, null);
    }

    @Transactional(readOnly = true)
    public List<MetricResponse> recent(int limit, String serviceName) {
        return recent(limit, serviceName, null);
    }

    @Transactional(readOnly = true)
    public List<MetricResponse> recent(int limit, String serviceName, Integer minutes) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        String normalizedService = serviceName == null || serviceName.isBlank() ? null : serviceName.trim();
        if (normalizedService != null && normalizedService.length() > 80) {
            throw new IllegalArgumentException("serviceName 不能超过 80 个字符");
        }
        List<TrafficMetric> metrics;
        if (minutes == null) {
            metrics = normalizedService == null
                    ? metricRepository.findAllByOrderByCollectedAtDesc(PageRequest.of(0, safeLimit))
                    : metricRepository.findByServiceNameOrderByCollectedAtDesc(
                            normalizedService, PageRequest.of(0, safeLimit));
        } else {
            int safeMinutes = Math.max(1, Math.min(minutes, 1440));
            Instant since = Instant.now().minus(safeMinutes, ChronoUnit.MINUTES);
            metrics = normalizedService == null
                    ? metricRepository.findSince(since)
                    : metricRepository.findSinceForService(since, normalizedService);
        }
        return metrics
                .stream()
                .sorted(Comparator.comparing(TrafficMetric::getCollectedAt).reversed())
                .limit(safeLimit)
                .map(MetricResponse::from).toList();
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { action.run(); }
        });
    }
}

