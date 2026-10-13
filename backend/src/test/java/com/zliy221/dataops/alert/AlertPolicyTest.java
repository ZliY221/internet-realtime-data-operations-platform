package com.zliy221.dataops.alert;

import com.zliy221.dataops.metric.TrafficMetric;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AlertPolicyTest {
    private final AlertPolicy policy = new AlertPolicy();

    @Test
    void createsCriticalAlertForHighErrorRate() {
        TrafficMetric metric = new TrafficMetric("order-service", 1000, 75, 320, Instant.now());

        var alerts = policy.evaluate(metric);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.getFirst().getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(alerts.getFirst().getRuleCode()).isEqualTo("HIGH_ERROR_RATE");
    }

    @Test
    void createsLatencyAlertAtThreshold() {
        TrafficMetric metric = new TrafficMetric("search-service", 1000, 3, 800, Instant.now());

        var alerts = policy.evaluate(metric);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.getFirst().getSeverity()).isEqualTo(AlertSeverity.WARNING);
    }

    @Test
    void keepsHealthyMetricWithoutAlert() {
        TrafficMetric metric = new TrafficMetric("gateway", 1000, 8, 260, Instant.now());

        assertThat(policy.evaluate(metric)).isEmpty();
    }

    @Test
    void assignsAnOwnerToAnActiveAlert() {
        Alert alert = new Alert(1L, "gateway", AlertSeverity.WARNING, "LATENCY", "延迟升高", Instant.now());

        alert.assign(" platform-oncall ");

        assertThat(alert.getOwner()).isEqualTo("platform-oncall");
    }
}

