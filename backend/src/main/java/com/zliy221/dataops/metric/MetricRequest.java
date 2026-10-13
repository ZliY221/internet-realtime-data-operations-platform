package com.zliy221.dataops.metric;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record MetricRequest(
        @NotBlank @Size(max = 80) String serviceName,
        @Min(0) long requestCount,
        @Min(0) long errorCount,
        @Min(0) long p95LatencyMs,
        @NotNull Instant collectedAt
) {}

