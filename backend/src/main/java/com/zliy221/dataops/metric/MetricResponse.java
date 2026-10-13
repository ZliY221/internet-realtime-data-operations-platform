package com.zliy221.dataops.metric;

import java.time.Instant;

public record MetricResponse(
        Long id,
        String serviceName,
        long requestCount,
        long errorCount,
        double errorRate,
        long p95LatencyMs,
        Instant collectedAt
) {
    public static MetricResponse from(TrafficMetric metric) {
        return new MetricResponse(metric.getId(), metric.getServiceName(), metric.getRequestCount(),
                metric.getErrorCount(), metric.getErrorRate(), metric.getP95LatencyMs(), metric.getCollectedAt());
    }
}

