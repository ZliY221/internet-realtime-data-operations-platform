package com.zliy221.dataops.dashboard;

import java.time.Instant;

public record ServiceHealth(
        String serviceName,
        long requestCount,
        long errorCount,
        double errorRate,
        double averageP95LatencyMs,
        Instant latestCollectedAt,
        String status
) {}

