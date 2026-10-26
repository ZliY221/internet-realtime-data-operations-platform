package com.zliy221.dataops;

import com.jayway.jsonpath.JsonPath;
import com.zliy221.dataops.alert.AlertRepository;
import com.zliy221.dataops.metric.TrafficMetricRepository;
import com.zliy221.dataops.incident.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiWorkflowIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TrafficMetricRepository metricRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @BeforeEach
    void clearData() {
        alertRepository.deleteAll();
        incidentRepository.deleteAll();
        metricRepository.deleteAll();
    }

    @Test
    void alertsAreGroupedIntoAnAssignableIncident() throws Exception {
        mockMvc.perform(post("/api/v1/metrics").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceName":"payment","requestCount":1000,"errorCount":90,
                 "p95LatencyMs":950,"collectedAt":"2026-10-18T08:00:00Z"}
                """)).andExpect(status().isCreated());

        String incidents = mockMvc.perform(get("/api/v1/incidents").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceName").value("payment"))
                .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[0].alertCount").value(2))
                .andReturn().getResponse().getContentAsString();
        Number incidentId = JsonPath.read(incidents, "$[0].id");

        mockMvc.perform(patch("/api/v1/incidents/{id}", incidentId.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"platform-oncall\",\"status\":\"INVESTIGATING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("platform-oncall"))
                .andExpect(jsonPath("$.status").value("INVESTIGATING"));

        mockMvc.perform(get("/api/v1/incidents/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(0))
                .andExpect(jsonPath("$.investigating").value(1))
                .andExpect(jsonPath("$.activeCritical").value(1));

        mockMvc.perform(patch("/api/v1/incidents/{id}", incidentId.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }

    @Test
    void metricToAlertOperationsWorkflowIsAvailable() throws Exception {
        String metric = """
                {
                  "serviceName": "order-service",
                  "requestCount": 1000,
                  "errorCount": 80,
                  "p95LatencyMs": 920,
                  "collectedAt": "2026-10-14T01:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metric))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceName").value("order-service"))
                .andExpect(jsonPath("$.errorRate").value(0.08));

        mockMvc.perform(get("/api/v1/metrics/recent").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestCount").value(1000));

        String alerts = mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        Number alertId = JsonPath.read(alerts, "$[0].id");

        mockMvc.perform(patch("/api/v1/alerts/{id}/owner", alertId.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"data-ops\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("data-ops"));

        mockMvc.perform(post("/api/v1/alerts/{id}/acknowledge", alertId.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        mockMvc.perform(post("/api/v1/alerts/{id}/resolve", alertId.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(get("/api/v1/alerts/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(1));

        mockMvc.perform(get("/api/v1/alerts/workloads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].owner").value("未分派"))
                .andExpect(jsonPath("$[0].total").value(1));

        mockMvc.perform(get("/api/v1/dashboard/summary").param("minutes", "1440"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboard/services").param("minutes", "1440"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidMetricIsRejectedWithoutPersistence() throws Exception {
        String invalidMetric = """
                {
                  "serviceName": "gateway",
                  "requestCount": 10,
                  "errorCount": 11,
                  "p95LatencyMs": 100,
                  "collectedAt": "2026-10-14T01:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidMetric))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/metrics/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void alertQueueSupportsAtomicBulkResolution() throws Exception {
        mockMvc.perform(post("/api/v1/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceName":"gateway","requestCount":1000,"errorCount":90,
                                 "p95LatencyMs":950,"collectedAt":"2026-10-15T01:00:00Z"}
                                """))
                .andExpect(status().isCreated());
        String alerts = mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andReturn().getResponse().getContentAsString();
        Number first = JsonPath.read(alerts, "$[0].id");
        Number second = JsonPath.read(alerts, "$[1].id");

        mockMvc.perform(post("/api/v1/alerts/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + first + "," + second + "],\"action\":\"RESOLVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.items[0].status").value("RESOLVED"));

        mockMvc.perform(get("/api/v1/alerts/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(2));
    }

    @Test
    void bulkAlertActionsRejectEmptyInputAndDeduplicateIds() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[],\"action\":\"ACKNOWLEDGE\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceName":"checkout","requestCount":1000,"errorCount":90,
                                 "p95LatencyMs":950,"collectedAt":"2026-10-15T02:00:00Z"}
                                """))
                .andExpect(status().isCreated());
        String alerts = mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(alerts, "$[0].id");
        mockMvc.perform(post("/api/v1/alerts/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "," + id + "],\"action\":\"ACKNOWLEDGE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void alertQueueSupportsAtomicBulkAssignment() throws Exception {
        mockMvc.perform(post("/api/v1/metrics").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceName":"search","requestCount":1000,"errorCount":90,
                 "p95LatencyMs":950,"collectedAt":"2026-10-15T02:30:00Z"}
                """)).andExpect(status().isCreated());
        String alerts = mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andReturn().getResponse().getContentAsString();
        Number first = JsonPath.read(alerts, "$[0].id");
        Number second = JsonPath.read(alerts, "$[1].id");
        mockMvc.perform(patch("/api/v1/alerts/bulk-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + first + "," + second + "],\"owner\":\"data-oncall\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.owner").value("data-oncall"))
                .andExpect(jsonPath("$.items[0].owner").value("data-oncall"));
    }

    @Test
    void unifiedDispatchAssignsAndAcknowledgesInOneRequest() throws Exception {
        mockMvc.perform(post("/api/v1/metrics").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceName":"catalog","requestCount":1000,"errorCount":90,
                 "p95LatencyMs":950,"collectedAt":"2026-10-15T02:45:00Z"}
                """)).andExpect(status().isCreated());
        String alerts = mockMvc.perform(get("/api/v1/alerts").param("status", "OPEN"))
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(alerts, "$[0].id");
        mockMvc.perform(post("/api/v1/alerts/dispatch/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "],\"owner\":\"platform-oncall\",\"action\":\"ACKNOWLEDGE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.criticalCount").value(1))
                .andExpect(jsonPath("$.serviceCount").value(1))
                .andExpect(jsonPath("$.services[0]").value("catalog"))
                .andExpect(jsonPath("$.requiresConfirmation").value(true));
        mockMvc.perform(post("/api/v1/alerts/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "],\"owner\":\"platform-oncall\",\"action\":\"ACKNOWLEDGE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedCount").value(1))
                .andExpect(jsonPath("$.statusChangedCount").value(1))
                .andExpect(jsonPath("$.changedCount").value(1))
                .andExpect(jsonPath("$.outcome").value("COMPLETE"))
                .andExpect(jsonPath("$.unchangedCount").value(0))
                .andExpect(jsonPath("$.criticalCount").value(1))
                .andExpect(jsonPath("$.warningCount").value(0))
                .andExpect(jsonPath("$.statusBeforeCounts.OPEN").value(1))
                .andExpect(jsonPath("$.followUpRequired").value(true))
                .andExpect(jsonPath("$.reviewPriority").value("P0"))
                .andExpect(jsonPath("$.nextAction").value("ESCALATE_SERVICE_OWNER"))
                .andExpect(jsonPath("$.handoffChecklist[0]").value("VERIFY_DISPATCH_RECEIPT"))
                .andExpect(jsonPath("$.handoffChecklist[3]").value("RECORD_REVIEW_RESULT"))
                .andExpect(jsonPath("$.handoffStepCount").value(4))
                .andExpect(jsonPath("$.estimatedHandoffMinutes").value(20))
                .andExpect(jsonPath("$.handoffReadiness").value("ESCALATION_REQUIRED"))
                .andExpect(jsonPath("$.handoffBlockers[0]").value("SERVICE_OWNER_ESCALATION_REQUIRED"))
                .andExpect(jsonPath("$.reviewWindowHours").value(1))
                .andExpect(jsonPath("$.handoffSummary").value(org.hamcrest.Matchers.containsString("1h review")))
                .andExpect(jsonPath("$.serviceCount").value(1))
                .andExpect(jsonPath("$.services[0]").value("catalog"))
                .andExpect(jsonPath("$.receiptId").value(org.hamcrest.Matchers.matchesPattern("DSP-[0-9A-F]{8}")))
                .andExpect(jsonPath("$.executedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[0].owner").value("platform-oncall"))
                .andExpect(jsonPath("$.items[0].status").value("ACKNOWLEDGED"));
        mockMvc.perform(post("/api/v1/alerts/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + id + "],\"owner\":\"platform-oncall\",\"action\":\"ACKNOWLEDGE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedCount").value(0))
                .andExpect(jsonPath("$.outcome").value("NO_CHANGE"))
                .andExpect(jsonPath("$.followUpRequired").value(false))
                .andExpect(jsonPath("$.reviewPriority").value("NONE"))
                .andExpect(jsonPath("$.nextAction").value("REVIEW_SELECTION"))
                .andExpect(jsonPath("$.handoffChecklist[0]").value("REVIEW_SELECTION"))
                .andExpect(jsonPath("$.handoffStepCount").value(1))
                .andExpect(jsonPath("$.estimatedHandoffMinutes").value(5))
                .andExpect(jsonPath("$.handoffReadiness").value("SELECTION_REVIEW"))
                .andExpect(jsonPath("$.handoffBlockers[0]").value("REVIEW_SELECTION"))
                .andExpect(jsonPath("$.reviewWindowHours").doesNotExist())
                .andExpect(jsonPath("$.unchangedCount").value(1))
                .andExpect(jsonPath("$.receiptId").value(org.hamcrest.Matchers.matchesPattern("DSP-[0-9A-F]{8}")));
    }

    @Test
    void recentMetricsRespectTheRequestedObservationWindow() throws Exception {
        String stale = Instant.now().minusSeconds(7200).toString();
        String current = Instant.now().minusSeconds(30).toString();
        mockMvc.perform(post("/api/v1/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceName\":\"checkout\",\"requestCount\":100,\"errorCount\":1,"
                                + "\"p95LatencyMs\":200,\"collectedAt\":\"" + stale + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceName\":\"checkout\",\"requestCount\":200,\"errorCount\":2,"
                                + "\"p95LatencyMs\":250,\"collectedAt\":\"" + current + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/metrics/recent")
                        .param("limit", "80")
                        .param("minutes", "60")
                        .param("serviceName", "checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestCount").value(200));
    }

    @Test
    void alertQueueCanBeFilteredByService() throws Exception {
        String observedAt = Instant.now().minusSeconds(60).toString();
        for (String service : List.of("gateway", "checkout")) {
            mockMvc.perform(post("/api/v1/metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"serviceName\":\"" + service
                                    + "\",\"requestCount\":1000,\"errorCount\":90,"
                                    + "\"p95LatencyMs\":950,\"collectedAt\":\"" + observedAt + "\"}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/v1/alerts")
                        .param("status", "OPEN")
                        .param("serviceName", "checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].serviceName").value("checkout"));
        mockMvc.perform(get("/api/v1/metrics/recent")
                        .param("limit", "80")
                        .param("minutes", "1440")
                        .param("serviceName", "checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceName").value("checkout"));
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .param("minutes", "1440")
                        .param("serviceName", " checkout "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(1000))
                .andExpect(jsonPath("$.activeAlerts").value(2))
                .andExpect(jsonPath("$.activeServices").value(1));
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .param("minutes", "1440")
                        .param("serviceName", "missing-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(0))
                .andExpect(jsonPath("$.activeAlerts").value(0))
                .andExpect(jsonPath("$.activeServices").value(0));
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .param("serviceName", "x".repeat(81)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void failoverReadinessEndpointReturnsBoundedTrafficPlan() throws Exception {
        mockMvc.perform(get("/api/v2/incidents/traffic-failover-readiness")
                        .param("primaryHealthy", "false")
                        .param("standbyHealthy", "true")
                        .param("standbyCapacityPercent", "60")
                        .param("replicationLagSeconds", "10")
                        .param("requestedTrafficPercent", "80")
                        .param("errorBudgetHealthy", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL_READY"))
                .andExpect(jsonPath("$.recommendedTrafficPercent").value(60))
                .andExpect(jsonPath("$.blockers.length()").value(0))
                .andExpect(jsonPath("$.checklist.length()").value(4));
    }

    @Test
    void serviceCapacityEndpointReturnsScalePlan() throws Exception {
        mockMvc.perform(get("/api/v2/incidents/service-capacity-plan")
                        .param("currentRequestsPerSecond", "900")
                        .param("currentInstances", "5")
                        .param("requestsPerSecondPerInstance", "200")
                        .param("targetUtilizationPercent", "60")
                        .param("minimumInstances", "2")
                        .param("maximumInstances", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("SCALE_OUT"))
                .andExpect(jsonPath("$.recommendedInstances").value(8))
                .andExpect(jsonPath("$.status").value("ACTION_REQUIRED"));
    }

    @Test
    void canaryReleaseEndpointReturnsRollbackDecision() throws Exception {
        mockMvc.perform(get("/api/v2/incidents/canary-release-decision")
                        .param("baselineErrorRatePercent", "0.4")
                        .param("canaryErrorRatePercent", "0.9")
                        .param("baselineP95LatencyMs", "200")
                        .param("canaryP95LatencyMs", "270")
                        .param("canarySampleSize", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("ROLLBACK"))
                .andExpect(jsonPath("$.status").value("ROLLBACK_RECOMMENDED"))
                .andExpect(jsonPath("$.blockers.length()").value(2));
    }

    @Test
    void stagedReleaseEndpointReturnsProgressivePlan() throws Exception {
        mockMvc.perform(get("/api/v2/incidents/staged-release-plan")
                        .param("requestedFinalTrafficPercent", "100")
                        .param("errorBudgetHealthy", "true")
                        .param("rollbackReady", "true")
                        .param("minimumObservationMinutes", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.stages.length()").value(4))
                .andExpect(jsonPath("$.stages[3].requiredGate").value("FINAL_APPROVAL"))
                .andExpect(jsonPath("$.automaticTrafficChangeStarted").value(false));
    }

    @Test
    void releaseWindowCoordinationEndpointReschedulesSharedDependency() throws Exception {
        mockMvc.perform(post("/api/v3/incidents/release-window-coordination")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"changeId":"checkout-42","serviceName":"checkout","requestedStartMinute":60,"durationMinutes":30,"priority":90},
                                  {"changeId":"payment-18","serviceName":"payment","requestedStartMinute":70,"durationMinutes":20,"priority":60}
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COORDINATED"))
                .andExpect(jsonPath("$.assignments[1].coordinatedStartMinute").value(90))
                .andExpect(jsonPath("$.assignments[1].sharedServices.length()").value(2))
                .andExpect(jsonPath("$.assignments[1].sharedServices[1]").value("payment"))
                .andExpect(jsonPath("$.automaticReleaseStarted").value(false));
    }

    @Test
    void coordinatedRollbackEndpointReturnsDependencyOrder() throws Exception {
        mockMvc.perform(post("/api/v3/incidents/coordinated-rollback-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"serviceName":"payment","rollbackReady":true},
                                  {"serviceName":"checkout","rollbackReady":true}
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.steps[0].serviceName").value("checkout"))
                .andExpect(jsonPath("$.steps[1].serviceName").value("payment"))
                .andExpect(jsonPath("$.automaticRollbackStarted").value(false));
    }

    @Test
    void changeCorrelationEndpointRanksCandidateChanges() throws Exception {
        mockMvc.perform(post("/api/v3/incidents/checkout/change-correlation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"changeId":"payment-1","serviceName":"payment","minutesBeforeIncident":5,"errorRateIncreasePoints":0.3},
                                  {"changeId":"checkout-2","serviceName":"checkout","minutesBeforeIncident":20,"errorRateIncreasePoints":0.5}
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STRONG_CANDIDATE"))
                .andExpect(jsonPath("$.candidates[0].changeId").value("checkout-2"))
                .andExpect(jsonPath("$.automaticRollbackStarted").value(false));
    }

    @Test
    void serviceIsolationEndpointReturnsContainmentBoundary() throws Exception {
        mockMvc.perform(post("/api/v3/incidents/payment/isolation-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"checkout\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALLBACK_READY"))
                .andExpect(jsonPath("$.actions[0].upstreamService").value("checkout"))
                .andExpect(jsonPath("$.downstreamToMonitor[0]").value("notification"))
                .andExpect(jsonPath("$.automaticIsolationStarted").value(false));
    }

    @Test
    void operatorRehearsalCoversOneHundredOnCallJourneys() throws Exception {
        StringBuilder payload = new StringBuilder("[");
        for (int index = 0; index < 100; index++) {
            if (index > 0) payload.append(',');
            int scenario = index % 6;
            payload.append("{\"journeyId\":\"journey-").append(index)
                    .append("\",\"serviceName\":\"checkout\",\"metricsAvailable\":")
                    .append(scenario != 0)
                    .append(",\"alertAcknowledged\":").append(scenario > 1)
                    .append(",\"ownerAssigned\":").append(scenario > 2)
                    .append(",\"dependencyReviewed\":").append(scenario > 3)
                    .append(",\"recoveryVerified\":").append(scenario > 4).append('}');
        }
        payload.append(']');
        mockMvc.perform(post("/api/v3/incidents/operator-rehearsal")
                        .contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.journeyCount").value(100))
                .andExpect(jsonPath("$.unresolvedCount").value(0))
                .andExpect(jsonPath("$.incidentChangesStarted").value(false));
    }

    @Test
    void operatorCommandBoardReturnsABoundedPriorityQueue() throws Exception {
        StringBuilder payload = new StringBuilder("[");
        for (int index = 0; index < 100; index++) {
            if (index > 0) payload.append(',');
            int scenario = index % 6;
            payload.append("{\"journeyId\":\"board-").append(index)
                    .append("\",\"serviceName\":\"checkout\",\"metricsAvailable\":")
                    .append(scenario != 0)
                    .append(",\"alertAcknowledged\":").append(scenario > 1)
                    .append(",\"ownerAssigned\":").append(scenario > 2)
                    .append(",\"dependencyReviewed\":").append(scenario > 3)
                    .append(",\"recoveryVerified\":").append(scenario > 4).append('}');
        }
        payload.append(']');
        mockMvc.perform(post("/api/v3/incidents/operator-command-board")
                        .param("visibleLimit", "10")
                        .contentType(MediaType.APPLICATION_JSON).content(payload.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPERATOR_COMMAND_BOARD_READY"))
                .andExpect(jsonPath("$.visibleCount").value(10))
                .andExpect(jsonPath("$.focusItems[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.incidentChangesStarted").value(false));
    }

    @Test
    void sloBurnRateEndpointReturnsMultiWindowAssessment() throws Exception {
        mockMvc.perform(post("/api/v3/incidents/slo-burn-rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName":"checkout",
                                  "objectivePercent":99.9,
                                  "fastWindowErrorRatePercent":0.8,
                                  "slowWindowErrorRatePercent":0.4,
                                  "fastWindowMinutes":5,
                                  "slowWindowMinutes":60,
                                  "sloWindowDays":30,
                                  "remainingBudgetPercent":18
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WARNING"))
                .andExpect(jsonPath("$.urgency").value("RESPOND_WITHIN_30_MINUTES"))
                .andExpect(jsonPath("$.automaticMitigationStarted").value(false));
    }
}
