package com.zliy221.dataops.alert;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alerts")
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_id", nullable = false)
    private Long metricId;

    @Column(name = "service_name", nullable = false, length = 80)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertStatus status;

    @Column(nullable = false, length = 40)
    private String ruleCode;

    @Column(nullable = false, length = 240)
    private String message;

    @Column(length = 80)
    private String owner;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Alert() {}

    public Alert(Long metricId, String serviceName, AlertSeverity severity, String ruleCode, String message, Instant createdAt) {
        this.metricId = metricId;
        this.serviceName = serviceName;
        this.severity = severity;
        this.ruleCode = ruleCode;
        this.message = message;
        this.status = AlertStatus.OPEN;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void acknowledge() {
        if (status == AlertStatus.RESOLVED) throw new IllegalStateException("已解决告警不能再次确认");
        status = AlertStatus.ACKNOWLEDGED;
        updatedAt = Instant.now();
    }

    public void resolve() {
        status = AlertStatus.RESOLVED;
        updatedAt = Instant.now();
    }

    public void assign(String owner) {
        String normalized = owner == null ? "" : owner.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new IllegalArgumentException("负责人名称长度应为 1 到 80 个字符");
        }
        if (status == AlertStatus.RESOLVED) {
            throw new IllegalStateException("已解决告警不能重新分派");
        }
        this.owner = normalized;
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getMetricId() { return metricId; }
    public String getServiceName() { return serviceName; }
    public AlertSeverity getSeverity() { return severity; }
    public AlertStatus getStatus() { return status; }
    public String getRuleCode() { return ruleCode; }
    public String getMessage() { return message; }
    public String getOwner() { return owner; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

