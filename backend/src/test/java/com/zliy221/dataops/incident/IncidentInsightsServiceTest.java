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

    @Test
    void serviceIsolationUsesFallbackForReadyUpstream() {
        var result = service.planServiceIsolation("payment", List.of("checkout"));
        assertEquals("FALLBACK_READY", result.status());
        assertEquals("checkout", result.actions().getFirst().upstreamService());
        assertEquals("ROUTE_TO_FALLBACK", result.actions().getFirst().action());
        assertEquals(List.of("notification"), result.downstreamToMonitor());
        assertFalse(result.automaticIsolationStarted());
    }

    @Test
    void serviceIsolationRequiresManualContainmentWithoutFallback() {
        var result = service.planServiceIsolation("checkout", List.of());
        assertEquals("MANUAL_CONTAINMENT_REQUIRED", result.status());
        assertEquals("STOP_DEPENDENT_CALLS", result.actions().getFirst().action());
    }

    @Test
    void oneHundredOperatorJourneysAllReceiveGuidedActions() {
        var journeys = new java.util.ArrayList<IncidentInsightsService.OperatorJourneyRequest>();
        for (int index = 0; index < 100; index++) {
            int scenario = index % 6;
            journeys.add(new IncidentInsightsService.OperatorJourneyRequest(
                    "journey-" + index, scenario % 2 == 0 ? "checkout" : "payment",
                    scenario != 0, scenario > 1, scenario > 2,
                    scenario > 3, scenario > 4));
        }
        var result = service.rehearseOperatorJourneys(journeys);
        assertEquals("USABILITY_REHEARSAL_PASSED", result.status());
        assertEquals(100, result.journeyCount());
        assertEquals(0, result.unresolvedCount());
        assertTrue(result.journeys().stream().allMatch(item -> !item.steps().isEmpty()));
        assertFalse(result.incidentChangesStarted());
    }

    @Test
    void operatorRehearsalRejectsIncompleteSimulation() {
        assertThrows(IllegalArgumentException.class, () -> service.rehearseOperatorJourneys(List.of(
                new IncidentInsightsService.OperatorJourneyRequest(
                        "one", "checkout", true, true, true, true, true)
        )));
    }

    @Test
    void operatorCommandBoardPrioritisesBlockedJourneysAndBoundsVisibleWork() {
        var journeys = new java.util.ArrayList<IncidentInsightsService.OperatorJourneyRequest>();
        for (int index = 0; index < 100; index++) {
            int scenario = index % 6;
            journeys.add(new IncidentInsightsService.OperatorJourneyRequest(
                    "command-" + index, scenario % 2 == 0 ? "checkout" : "payment",
                    scenario != 0, scenario > 1, scenario > 2,
                    scenario > 3, scenario > 4));
        }
        var result = service.buildOperatorCommandBoard(journeys, 10);
        assertEquals("OPERATOR_COMMAND_BOARD_READY", result.status());
        assertEquals(100, result.journeyCount());
        assertEquals(10, result.visibleCount());
        assertEquals(90, result.remainingCount());
        assertTrue(result.focusItems().stream().allMatch(item -> item.status().equals("BLOCKED")));
        assertFalse(result.incidentChangesStarted());
    }

    @Test
    void operatorCommandBoardRejectsAnUnboundedVisibleQueue() {
        var journeys = new java.util.ArrayList<IncidentInsightsService.OperatorJourneyRequest>();
        for (int index = 0; index < 100; index++) {
            journeys.add(new IncidentInsightsService.OperatorJourneyRequest(
                    "command-" + index, "checkout", true, true, true, true, true));
        }
        assertThrows(IllegalArgumentException.class,
                () -> service.buildOperatorCommandBoard(journeys, 26));
    }

    @Test
    void sloBurnRatePagesWhenFastAndSlowWindowsAreExhaustingBudget() {
        var result = service.assessSloBurnRate(new IncidentInsightsService.SloBurnRateRequest(
                "checkout", 99.9, 2.0, 0.8, 5, 60, 30, 40));
        assertEquals("CRITICAL", result.status());
        assertEquals("PAGE_NOW", result.urgency());
        assertTrue(result.fastBurnRate() >= 14.4);
        assertFalse(result.automaticMitigationStarted());
    }

    @Test
    void sloBurnRateKeepsHealthyServiceInRoutineMonitoring() {
        var result = service.assessSloBurnRate(new IncidentInsightsService.SloBurnRateRequest(
                "catalog", 99.9, 0.05, 0.04, 5, 60, 30, 80));
        assertEquals("HEALTHY", result.status());
        assertEquals("ROUTINE_MONITORING", result.urgency());
        assertTrue(result.estimatedBudgetExhaustionHours() > 0);
    }

    @Test
    void sloBurnRateRejectsInvalidObjective() {
        assertThrows(IllegalArgumentException.class, () -> service.assessSloBurnRate(
                new IncidentInsightsService.SloBurnRateRequest(
                        "checkout", 100, 1, 1, 5, 60, 30, 50)));
    }

    @Test
    void alertCorrelationGroupsDuplicatesAndDetectsFlapping() {
        var result = service.correlateAlertSignals(List.of(
                new IncidentInsightsService.AlertSignal("e1", "checkout", "error-rate", "WARNING", 100, "OPEN"),
                new IncidentInsightsService.AlertSignal("e2", "checkout", "error-rate", "CRITICAL", 102, "RESOLVED"),
                new IncidentInsightsService.AlertSignal("e3", "checkout", "error-rate", "WARNING", 104, "OPEN")
        ), 10);
        assertEquals("CORRELATED", result.status());
        assertEquals(1, result.groupCount());
        assertEquals(2, result.suppressedDuplicateCount());
        assertTrue(result.groups().getFirst().flapping());
        assertEquals("CRITICAL", result.groups().getFirst().severity());
        assertFalse(result.automaticAlertChangesStarted());
    }

    @Test
    void alertCorrelationRejectsDuplicateEventIds() {
        var signal = new IncidentInsightsService.AlertSignal(
                "same", "checkout", "latency", "WARNING", 100, "OPEN");
        assertThrows(IllegalArgumentException.class,
                () -> service.correlateAlertSignals(List.of(signal, signal), 10));
    }

    @Test
    void maintenancePlanSuppressesExpectedWarningButReviewsCriticalAlert() {
        var result = service.planMaintenanceAlerts(
                new IncidentInsightsService.MaintenanceAlertPlanRequest(
                        List.of(new IncidentInsightsService.MaintenanceWindowRequest(
                                "mw-1", "checkout", 100, 160, true, true)),
                        List.of(
                                new IncidentInsightsService.MaintenanceAlertSignal(
                                        "e1", "payment", 120, "WARNING"),
                                new IncidentInsightsService.MaintenanceAlertSignal(
                                        "e2", "checkout", 130, "CRITICAL"),
                                new IncidentInsightsService.MaintenanceAlertSignal(
                                        "e3", "search", 130, "WARNING")),
                        5));
        assertEquals("REVIEW_REQUIRED", result.status());
        assertEquals(1, result.suppressedCount());
        assertEquals(1, result.reviewCount());
        assertEquals(1, result.keptCount());
        assertFalse(result.automaticAlertChangesStarted());
    }

    @Test
    void maintenancePlanRejectsDuplicateWindowIds() {
        var window = new IncidentInsightsService.MaintenanceWindowRequest(
                "same", "checkout", 100, 160, true, true);
        assertThrows(IllegalArgumentException.class, () -> service.planMaintenanceAlerts(
                new IncidentInsightsService.MaintenanceAlertPlanRequest(
                        List.of(window, window),
                        List.of(new IncidentInsightsService.MaintenanceAlertSignal(
                                "e1", "checkout", 120, "WARNING")), 0)));
    }
}
