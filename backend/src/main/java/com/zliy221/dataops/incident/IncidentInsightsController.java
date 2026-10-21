package com.zliy221.dataops.incident;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v2/incidents")
public class IncidentInsightsController {
    private final IncidentInsightsService service;

    public IncidentInsightsController(IncidentInsightsService service) { this.service = service; }

    @GetMapping("/{id}/timeline")
    public List<IncidentInsightsService.TimelineEvent> timeline(@PathVariable long id) {
        return service.timeline(id);
    }

    @GetMapping("/blast-radius")
    public IncidentInsightsService.BlastRadius blastRadius(@RequestParam String serviceName) {
        return service.blastRadius(serviceName);
    }

    @GetMapping("/error-budget")
    public IncidentInsightsService.ErrorBudget errorBudget(
            @RequestParam String serviceName,
            @RequestParam(defaultValue = "1440") int minutes,
            @RequestParam(defaultValue = "0.999") double objective) {
        return service.errorBudget(serviceName, minutes, objective);
    }

    @GetMapping("/{id}/runbook")
    public IncidentInsightsService.Runbook runbook(@PathVariable long id) {
        return service.runbook(id);
    }

    @GetMapping("/{id}/escalation")
    public IncidentInsightsService.EscalationPlan escalation(@PathVariable long id) {
        return service.escalationPlan(id);
    }

    @GetMapping("/maintenance-impact")
    public IncidentInsightsService.MaintenanceImpact maintenanceImpact(
            @RequestParam String serviceName,
            @RequestParam(defaultValue = "30") int durationMinutes) {
        return service.maintenanceImpact(serviceName, durationMinutes);
    }

    @GetMapping("/reliability-score")
    public IncidentInsightsService.ReliabilityScore reliabilityScore(
            @RequestParam String serviceName,
            @RequestParam(defaultValue = "1440") int minutes,
            @RequestParam(defaultValue = "0.999") double objective) {
        return service.reliabilityScore(serviceName, minutes, objective);
    }

    @GetMapping("/{id}/briefing")
    public IncidentInsightsService.IncidentBriefing briefing(@PathVariable long id) {
        return service.briefing(id);
    }

    @GetMapping("/change-window")
    public IncidentInsightsService.ChangeWindow changeWindow(
            @RequestParam String serviceName, @RequestParam double reliabilityScore,
            @RequestParam(defaultValue = "0") int activeCriticalIncidents) {
        return service.changeWindow(serviceName, reliabilityScore, activeCriticalIncidents);
    }

    @GetMapping("/on-call-capacity")
    public IncidentInsightsService.OnCallCapacity onCallCapacity(
            @RequestParam int activeIncidents, @RequestParam int criticalIncidents,
            @RequestParam int responders) {
        return service.onCallCapacity(activeIncidents, criticalIncidents, responders);
    }

    @GetMapping("/recovery-validation")
    public IncidentInsightsService.RecoveryValidation recoveryValidation(
            @RequestParam boolean metricsRecovered, @RequestParam boolean budgetHealthy,
            @RequestParam boolean downstreamHealthy) {
        return service.validateRecovery(metricsRecovered, budgetHealthy, downstreamHealthy);
    }

    @GetMapping("/{id}/executive-summary")
    public IncidentInsightsService.ExecutiveSummary executiveSummary(@PathVariable long id) {
        return service.executiveSummary(id);
    }

    @GetMapping("/traffic-failover-readiness")
    public IncidentInsightsService.TrafficFailoverReadiness trafficFailoverReadiness(
            @RequestParam boolean primaryHealthy,
            @RequestParam boolean standbyHealthy,
            @RequestParam int standbyCapacityPercent,
            @RequestParam int replicationLagSeconds,
            @RequestParam int requestedTrafficPercent,
            @RequestParam boolean errorBudgetHealthy) {
        return service.trafficFailoverReadiness(primaryHealthy, standbyHealthy,
                standbyCapacityPercent, replicationLagSeconds, requestedTrafficPercent,
                errorBudgetHealthy);
    }

    @GetMapping("/service-capacity-plan")
    public IncidentInsightsService.ServiceCapacityPlan serviceCapacityPlan(
            @RequestParam double currentRequestsPerSecond,
            @RequestParam int currentInstances,
            @RequestParam double requestsPerSecondPerInstance,
            @RequestParam(defaultValue = "65") int targetUtilizationPercent,
            @RequestParam(defaultValue = "2") int minimumInstances,
            @RequestParam(defaultValue = "50") int maximumInstances) {
        return service.serviceCapacityPlan(currentRequestsPerSecond, currentInstances,
                requestsPerSecondPerInstance, targetUtilizationPercent,
                minimumInstances, maximumInstances);
    }

    @GetMapping("/canary-release-decision")
    public IncidentInsightsService.CanaryReleaseDecision canaryReleaseDecision(
            @RequestParam double baselineErrorRatePercent,
            @RequestParam double canaryErrorRatePercent,
            @RequestParam double baselineP95LatencyMs,
            @RequestParam double canaryP95LatencyMs,
            @RequestParam long canarySampleSize,
            @RequestParam(defaultValue = "1000") long minimumSampleSize,
            @RequestParam(defaultValue = "0.2") double maximumErrorIncreasePoints,
            @RequestParam(defaultValue = "20") double maximumLatencyIncreasePercent) {
        return service.canaryReleaseDecision(baselineErrorRatePercent, canaryErrorRatePercent,
                baselineP95LatencyMs, canaryP95LatencyMs, canarySampleSize,
                minimumSampleSize, maximumErrorIncreasePoints, maximumLatencyIncreasePercent);
    }

    @GetMapping("/staged-release-plan")
    public IncidentInsightsService.StagedReleasePlan stagedReleasePlan(
            @RequestParam int requestedFinalTrafficPercent,
            @RequestParam boolean errorBudgetHealthy,
            @RequestParam boolean rollbackReady,
            @RequestParam(defaultValue = "15") int minimumObservationMinutes) {
        return service.stagedReleasePlan(requestedFinalTrafficPercent, errorBudgetHealthy,
                rollbackReady, minimumObservationMinutes);
    }
}
