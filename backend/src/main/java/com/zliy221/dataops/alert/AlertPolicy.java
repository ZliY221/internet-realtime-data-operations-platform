package com.zliy221.dataops.alert;

import com.zliy221.dataops.metric.TrafficMetric;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class AlertPolicy {
    public static final double CRITICAL_ERROR_RATE = 0.05;
    public static final long WARNING_P95_MS = 800;

    public List<Alert> evaluate(TrafficMetric metric) {
        List<Alert> alerts = new ArrayList<>();
        if (metric.getErrorRate() >= CRITICAL_ERROR_RATE) {
            String message = String.format(Locale.ROOT, "错误率 %.2f%% 超过 %.2f%% 阈值",
                    metric.getErrorRate() * 100, CRITICAL_ERROR_RATE * 100);
            alerts.add(new Alert(metric.getId(), metric.getServiceName(), AlertSeverity.CRITICAL,
                    "HIGH_ERROR_RATE", message, metric.getCollectedAt()));
        }
        if (metric.getP95LatencyMs() >= WARNING_P95_MS) {
            String message = "P95 响应时间 " + metric.getP95LatencyMs() + "ms 超过 " + WARNING_P95_MS + "ms 阈值";
            alerts.add(new Alert(metric.getId(), metric.getServiceName(), AlertSeverity.WARNING,
                    "HIGH_P95_LATENCY", message, metric.getCollectedAt()));
        }
        return alerts;
    }
}

