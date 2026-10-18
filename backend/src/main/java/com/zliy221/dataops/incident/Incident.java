package com.zliy221.dataops.incident;

import com.zliy221.dataops.alert.AlertSeverity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "incidents")
public class Incident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false, length = 80)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 80)
    private String owner;

    @Column(name = "alert_count", nullable = false)
    private int alertCount;

    @Column(name = "first_alert_at", nullable = false)
    private Instant firstAlertAt;

    @Column(name = "last_alert_at", nullable = false)
    private Instant lastAlertAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Incident() {}

    public Incident(String serviceName, AlertSeverity severity, Instant firstAlertAt) {
        this.serviceName = serviceName;
        this.severity = severity;
        this.status = IncidentStatus.OPEN;
        this.title = serviceName + " 服务异常";
        this.alertCount = 1;
        this.firstAlertAt = firstAlertAt;
        this.lastAlertAt = firstAlertAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void append(AlertSeverity newSeverity, Instant alertAt) {
        alertCount++;
        if (newSeverity == AlertSeverity.CRITICAL) severity = AlertSeverity.CRITICAL;
        if (alertAt.isBefore(firstAlertAt)) firstAlertAt = alertAt;
        if (alertAt.isAfter(lastAlertAt)) lastAlertAt = alertAt;
        updatedAt = Instant.now();
    }

    public void assign(String newOwner) {
        String normalized = newOwner == null ? "" : newOwner.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new IllegalArgumentException("负责人名称长度应为 1 到 80 个字符");
        }
        if (status == IncidentStatus.RESOLVED) throw new IllegalStateException("已恢复事故不能重新分派");
        owner = normalized;
        updatedAt = Instant.now();
    }

    public void investigate() {
        if (status == IncidentStatus.RESOLVED) throw new IllegalStateException("已恢复事故不能重新处理中");
        status = IncidentStatus.INVESTIGATING;
        updatedAt = Instant.now();
    }

    public void resolve() {
        status = IncidentStatus.RESOLVED;
        resolvedAt = Instant.now();
        updatedAt = resolvedAt;
    }

    public Long getId() { return id; }
    public String getServiceName() { return serviceName; }
    public AlertSeverity getSeverity() { return severity; }
    public IncidentStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getOwner() { return owner; }
    public int getAlertCount() { return alertCount; }
    public Instant getFirstAlertAt() { return firstAlertAt; }
    public Instant getLastAlertAt() { return lastAlertAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
