package com.zliy221.dataops.incident;

import com.zliy221.dataops.alert.AlertSeverity;
import com.zliy221.dataops.metric.TrafficMetricRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentInsightsServiceTest {
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final TrafficMetricRepository metrics = mock(TrafficMetricRepository.class);
    private final IncidentInsightsService service = new IncidentInsightsService(incidents, metrics);

    @Test
    void blastRadiusReturnsDependencyRisk() {
        var result = service.blastRadius("checkout");
        assertEquals(2, result.impactedServiceCount());
        assertEquals("HIGH", result.riskLevel());
        assertTrue(result.downstream().contains("payment"));
    }

    @Test
    void criticalIncidentGetsEscalationAndPostmortemRunbook() {
        Incident incident = new Incident("payment", AlertSeverity.CRITICAL, Instant.parse("2026-10-19T00:00:00Z"));
        when(incidents.findById(8L)).thenReturn(Optional.of(incident));
        var runbook = service.runbook(8L);
        assertTrue(runbook.postmortemRequired());
        assertTrue(runbook.steps().stream().anyMatch(step -> step.contains("升级")));
        assertEquals(5, runbook.postmortemSections().size());
    }

    @Test
    void criticalIncidentEscalatesToPlatformLead() {
        Incident incident = new Incident("payment", AlertSeverity.CRITICAL, Instant.parse("2026-10-20T00:00:00Z"));
        when(incidents.findById(9L)).thenReturn(Optional.of(incident));
        var plan = service.escalationPlan(9L);
        assertEquals("platform-lead", plan.escalationOwner());
        assertEquals(10, plan.acknowledgeWithinMinutes());
    }
}
