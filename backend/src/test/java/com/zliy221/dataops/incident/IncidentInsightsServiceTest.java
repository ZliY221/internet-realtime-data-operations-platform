package com.zliy221.dataops.incident;

import com.zliy221.dataops.alert.AlertSeverity;
import com.zliy221.dataops.metric.TrafficMetricRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

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

    @Test
    void checkoutMaintenanceRequiresApproval() {
        var impact = service.maintenanceImpact("checkout", 30);
        assertTrue(impact.approvalRequired());
        assertEquals("HIGH", impact.riskLevel());
        assertTrue(impact.affectedServices().contains("payment"));
    }

    @Test
    void reliabilityScoreIncludesDependencyPenalty() {
        when(metrics.findSinceForService(any(), eq("checkout"))).thenReturn(List.of());
        var score = service.reliabilityScore("checkout", 60, 0.999);
        assertEquals(10, score.dependencyPenalty());
        assertEquals(90.0, score.score());
        assertEquals("B", score.grade());
    }

    @Test
    void briefingCombinesIncidentAndDependencyContext() {
        Incident incident = new Incident("checkout", AlertSeverity.CRITICAL, Instant.parse("2026-10-20T00:00:00Z"));
        when(incidents.findById(10L)).thenReturn(Optional.of(incident));
        var briefing = service.briefing(10L);
        assertEquals("platform-lead", briefing.escalationOwner());
        assertEquals(2, briefing.impactedServiceCount());
        assertEquals(3, briefing.nextSteps().size());
    }

    @Test
    void changeWindowFreezesLowReliabilityService() {
        var window = service.changeWindow("checkout", 80, 0);
        assertEquals("FROZEN", window.status());
        assertEquals(1, window.blockers().size());
    }

    @Test
    void onCallCapacityRecommendsAdditionalResponders() {
        var capacity = service.onCallCapacity(12, 3, 2);
        assertEquals("OVERLOADED", capacity.status());
        assertEquals(1, capacity.recommendedAdditionalResponders());
    }
}
