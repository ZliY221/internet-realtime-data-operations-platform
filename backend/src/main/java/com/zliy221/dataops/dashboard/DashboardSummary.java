package com.zliy221.dataops.dashboard;

import java.time.Instant;

public record DashboardSummary(
        Instant windowStart,
        Instant windowEnd,
        long totalRequests,
        long totalErrors,
        double errorRate,
        double averageP95LatencyMs,
        long activeAlerts,
        int activeServices
) {}

