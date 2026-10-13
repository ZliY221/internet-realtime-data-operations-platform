package com.zliy221.dataops.dashboard;

import com.zliy221.dataops.alert.AlertRepository;
import com.zliy221.dataops.metric.TrafficMetric;
import com.zliy221.dataops.metric.TrafficMetricRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardControllerTest {
    @Test
    void aggregatesAndPrioritizesDegradedServices() {
        TrafficMetricRepository metrics = mock(TrafficMetricRepository.class);
        AlertRepository alerts = mock(AlertRepository.class);
        when(metrics.findSince(any())).thenReturn(List.of(
                new TrafficMetric("gateway", 1000, 4, 240, Instant.parse("2026-10-13T10:00:00Z")),
                new TrafficMetric("gateway", 2000, 8, 300, Instant.parse("2026-10-13T10:01:00Z")),
                new TrafficMetric("order-service", 1000, 70, 920, Instant.parse("2026-10-13T10:01:00Z"))
        ));
        DashboardController controller = new DashboardController(metrics, alerts);

        List<ServiceHealth> result = controller.services(60);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().serviceName()).isEqualTo("order-service");
        assertThat(result.getFirst().status()).isEqualTo("DEGRADED");
        assertThat(result.get(1).requestCount()).isEqualTo(3000);
        assertThat(result.get(1).errorRate()).isEqualTo(0.004);
        assertThat(result.get(1).latestCollectedAt()).isEqualTo("2026-10-13T10:01:00Z");
    }
}
