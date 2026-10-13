package com.zliy221.dataops.metric;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "traffic_metrics")
public class TrafficMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false, length = 80)
    private String serviceName;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "error_count", nullable = false)
    private long errorCount;

    @Column(name = "p95_latency_ms", nullable = false)
    private long p95LatencyMs;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    protected TrafficMetric() {}

    public TrafficMetric(String serviceName, long requestCount, long errorCount, long p95LatencyMs, Instant collectedAt) {
        this.serviceName = serviceName;
        this.requestCount = requestCount;
        this.errorCount = errorCount;
        this.p95LatencyMs = p95LatencyMs;
        this.collectedAt = collectedAt;
    }

    public Long getId() { return id; }
    public String getServiceName() { return serviceName; }
    public long getRequestCount() { return requestCount; }
    public long getErrorCount() { return errorCount; }
    public long getP95LatencyMs() { return p95LatencyMs; }
    public Instant getCollectedAt() { return collectedAt; }
    public double getErrorRate() { return requestCount == 0 ? 0 : (double) errorCount / requestCount; }
}

