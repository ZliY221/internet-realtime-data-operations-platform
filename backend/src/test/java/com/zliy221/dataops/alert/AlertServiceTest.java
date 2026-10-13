package com.zliy221.dataops.alert;

import com.zliy221.dataops.realtime.MetricWebSocketHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

class AlertServiceTest {
    @Test
    void summarizesLifecycleAndActiveSeverity() {
        AlertRepository repository = mock(AlertRepository.class);
        when(repository.countByStatus(AlertStatus.OPEN)).thenReturn(4L);
        when(repository.countByStatus(AlertStatus.ACKNOWLEDGED)).thenReturn(2L);
        when(repository.countByStatus(AlertStatus.RESOLVED)).thenReturn(9L);
        when(repository.countBySeverityAndStatusNot(AlertSeverity.CRITICAL, AlertStatus.RESOLVED)).thenReturn(3L);
        when(repository.countBySeverityAndStatusNot(AlertSeverity.WARNING, AlertStatus.RESOLVED)).thenReturn(3L);

        AlertSummary summary = new AlertService(repository, mock(MetricWebSocketHandler.class)).summary();

        assertThat(summary.open()).isEqualTo(4);
        assertThat(summary.acknowledged()).isEqualTo(2);
        assertThat(summary.resolved()).isEqualTo(9);
        assertThat(summary.activeCritical()).isEqualTo(3);
        assertThat(summary.activeWarning()).isEqualTo(3);
    }

    @Test
    void filtersAlertQueueBySeverityWhenRequested() {
        AlertRepository repository = mock(AlertRepository.class);
        when(repository.findTop100ByStatusAndSeverityOrderByCreatedAtDesc(
                AlertStatus.OPEN, AlertSeverity.CRITICAL
        )).thenReturn(List.of());
        AlertService service = new AlertService(repository, mock(MetricWebSocketHandler.class));

        assertThat(service.list(AlertStatus.OPEN, AlertSeverity.CRITICAL)).isEmpty();
    }
}
