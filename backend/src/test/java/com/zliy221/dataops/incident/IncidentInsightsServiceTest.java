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

    @Test
    void recoveryRequiresMetricsBudgetAndDownstream() {
        var validation = service.validateRecovery(true, false, true);
        assertEquals("INCOMPLETE", validation.status());
        assertEquals(1, validation.failedChecks().size());
    }

    @Test
    void executiveSummaryKeepsDecisionContext() {
        Incident incident = new Incident("checkout", AlertSeverity.CRITICAL, Instant.parse("2026-10-20T00:00:00Z"));
        when(incidents.findById(11L)).thenReturn(Optional.of(incident));
        var summary = service.executiveSummary(11L);
        assertEquals(2, summary.impactedServiceCount());
        assertTrue(summary.decisions().getFirst().contains("变更冻结"));
    }

    @Test
    void failoverReadinessLimitsTrafficToStandbyCapacity() {
        var result = service.trafficFailoverReadiness(false, true, 60, 10, 80, true);
        assertEquals("PARTIAL_READY", result.status());
        assertEquals(60, result.recommendedTrafficPercent());
        assertTrue(result.blockers().isEmpty());
        assertEquals(4, result.checklist().size());
    }

    @Test
    void failoverReadinessBlocksUnhealthyStandby() {
        var result = service.trafficFailoverReadiness(false, false, 100, 10, 50, true);
        assertEquals("BLOCKED", result.status());
        assertEquals(0, result.recommendedTrafficPercent());
        assertEquals(1, result.blockers().size());
    }

    @Test
    void serviceCapacityPlanRecommendsScaleOutWithHeadroom() {
        var result = service.serviceCapacityPlan(900, 5, 200, 60, 2, 20);
        assertEquals("SCALE_OUT", result.action());
        assertEquals(8, result.recommendedInstances());
        assertEquals(3, result.instanceDelta());
        assertEquals("ACTION_REQUIRED", result.status());
    }

    @Test
    void serviceCapacityPlanExposesConfiguredLimit() {
        var result = service.serviceCapacityPlan(5000, 10, 200, 60, 2, 20);
        assertEquals("CAPACITY_LIMIT", result.status());
        assertEquals(20, result.recommendedInstances());
        assertTrue(result.requiredInstances() > result.maximumInstances());
    }

    @Test
    void canaryReleaseDecisionRecommendsRollbackOnMetricRegression() {
        var result = service.canaryReleaseDecision(
                0.4, 0.9, 200, 270, 5000, 1000, 0.2, 20);
        assertEquals("ROLLBACK", result.action());
        assertEquals("ROLLBACK_RECOMMENDED", result.status());
        assertEquals(2, result.blockers().size());
    }

    @Test
    void canaryReleaseDecisionWaitsForEnoughSamples() {
        var result = service.canaryReleaseDecision(
                0.4, 0.4, 200, 190, 200, 1000, 0.2, 20);
        assertEquals("CONTINUE_OBSERVATION", result.action());
        assertEquals("INSUFFICIENT_SAMPLE", result.status());
    }

    @Test
    void stagedReleasePlanBuildsProgressiveTrafficGates() {
        var result = service.stagedReleasePlan(100, true, true, 15);
        assertEquals("READY", result.status());
        assertEquals(List.of(5, 25, 50, 100),
                result.stages().stream().map(IncidentInsightsService.ReleaseStage::trafficPercent).toList());
        assertEquals("FINAL_APPROVAL", result.stages().getLast().requiredGate());
        assertFalse(result.automaticTrafficChangeStarted());
    }

    @Test
    void stagedReleasePlanBlocksWithoutRollbackReadiness() {
        var result = service.stagedReleasePlan(50, true, false, 15);
        assertEquals("BLOCKED", result.status());
        assertTrue(result.stages().isEmpty());
        assertEquals(1, result.blockers().size());
    }

    @Test
    void releaseWindowCoordinationMovesLowerPrioritySharedDependency() {
        var result = service.coordinateReleaseWindows(List.of(
                new IncidentInsightsService.ReleaseWindowRequest("checkout-42", "checkout", 60, 30, 90),
                new IncidentInsightsService.ReleaseWindowRequest("payment-18", "payment", 70, 20, 60),
                new IncidentInsightsService.ReleaseWindowRequest("search-7", "search", 70, 20, 50)
        ));
        assertEquals("COORDINATED", result.status());
        var payment = result.assignments().stream()
                .filter(item -> item.changeId().equals("payment-18")).findFirst().orElseThrow();
        assertEquals(90, payment.coordinatedStartMinute());
        assertTrue(payment.sharedServices().contains("payment"));
        var search = result.assignments().stream()
                .filter(item -> item.changeId().equals("search-7")).findFirst().orElseThrow();
        assertEquals("ON_TIME", search.status());
        assertFalse(result.automaticReleaseStarted());
    }

    @Test
    void releaseWindowCoordinationRejectsDuplicateChangeId() {
        assertThrows(IllegalArgumentException.class, () -> service.coordinateReleaseWindows(List.of(
                new IncidentInsightsService.ReleaseWindowRequest("same", "checkout", 60, 30, 90),
                new IncidentInsightsService.ReleaseWindowRequest("same", "payment", 70, 20, 60)
        )));
    }

    @Test
    void coordinatedRollbackOrdersDependentsBeforeDependencies() {
        var result = service.planCoordinatedRollback(List.of(
                new IncidentInsightsService.RollbackServiceRequest("payment", true),
                new IncidentInsightsService.RollbackServiceRequest("gateway", true),
                new IncidentInsightsService.RollbackServiceRequest("checkout", true)
        ));
        assertEquals("READY", result.status());
        assertEquals(List.of("gateway", "checkout", "payment"),
                result.steps().stream().map(IncidentInsightsService.RollbackStep::serviceName).toList());
        assertFalse(result.automaticRollbackStarted());
    }

    @Test
    void coordinatedRollbackBlocksWhenArtifactIsNotReady() {
        var result = service.planCoordinatedRollback(List.of(
                new IncidentInsightsService.RollbackServiceRequest("checkout", false)
        ));
        assertEquals("BLOCKED", result.status());
        assertTrue(result.steps().isEmpty());
        assertEquals(1, result.blockers().size());
    }

    @Test
    void changeCorrelationRanksDirectRecentChangeFirst() {
        var result = service.rankIncidentChanges("checkout", List.of(
                new IncidentInsightsService.ChangeCandidate("catalog-1", "catalog", 10, 0.8),
                new IncidentInsightsService.ChangeCandidate("checkout-2", "checkout", 20, 0.5),
                new IncidentInsightsService.ChangeCandidate("payment-3", "payment", 5, 0.3)
        ));
        assertEquals("STRONG_CANDIDATE", result.status());
        assertEquals("checkout-2", result.candidates().getFirst().changeId());
        assertEquals("DIRECT", result.candidates().getFirst().relationship());
        assertFalse(result.automaticRollbackStarted());
    }

    @Test
    void changeCorrelationRejectsDuplicateChangeId() {
        assertThrows(IllegalArgumentException.class, () -> service.rankIncidentChanges(
                "checkout", List.of(
                        new IncidentInsightsService.ChangeCandidate("same", "checkout", 10, 0.2),
                        new IncidentInsightsService.ChangeCandidate("same", "payment", 20, 0.3)
                )));
    }
}
