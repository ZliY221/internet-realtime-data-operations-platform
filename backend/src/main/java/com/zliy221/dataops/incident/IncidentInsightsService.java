package com.zliy221.dataops.incident;

import com.zliy221.dataops.metric.TrafficMetric;
import com.zliy221.dataops.metric.TrafficMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.temporal.ChronoUnit;

@Service
public class IncidentInsightsService {
    private final IncidentRepository incidentRepository;
    private final TrafficMetricRepository metricRepository;

    public IncidentInsightsService(IncidentRepository incidentRepository, TrafficMetricRepository metricRepository) {
        this.incidentRepository = incidentRepository;
        this.metricRepository = metricRepository;
    }

    public record TimelineEvent(String type, String label, Instant occurredAt) {}
    public record BlastRadius(String serviceName, List<String> upstream, List<String> downstream,
                              int impactedServiceCount, String riskLevel) {}
    public record ErrorBudget(String serviceName, int windowMinutes, double objective,
                              long requestCount, long errorCount, double availability,
                              long remainingErrorBudget, String status) {}
    public record Runbook(String serviceName, String owner, List<String> steps,
                          boolean postmortemRequired, List<String> postmortemSections) {}
    public record EscalationPlan(String serviceName, String severity, String primaryOwner,
                                 String escalationOwner, int acknowledgeWithinMinutes,
                                 List<String> communicationSteps) {}
    public record MaintenanceImpact(String serviceName, int durationMinutes,
                                    List<String> affectedServices, String riskLevel,
                                    boolean approvalRequired, List<String> checklist) {}
    public record ReliabilityScore(String serviceName, double score, String grade,
                                   double availability, int dependencyPenalty,
                                   String budgetStatus) {}
    public record IncidentBriefing(long incidentId, String serviceName, String severity,
                                   String owner, String escalationOwner, String riskLevel,
                                   int impactedServiceCount, List<String> nextSteps) {}
    public record ChangeWindow(String serviceName, double reliabilityScore,
                               int activeCriticalIncidents, String status,
                               List<String> blockers) {}
    public record OnCallCapacity(int activeIncidents, int criticalIncidents,
                                 int responders, double incidentsPerResponder,
                                 String status, int recommendedAdditionalResponders) {}
    public record RecoveryValidation(boolean metricsRecovered, boolean budgetHealthy,
                                     boolean downstreamHealthy, String status,
                                     List<String> failedChecks) {}
    public record ExecutiveSummary(long incidentId, String serviceName, String severity,
                                   String status, int impactedServiceCount,
                                   String owner, String headline, List<String> decisions) {}
    public record TrafficFailoverReadiness(boolean primaryHealthy, boolean standbyHealthy,
                                           int standbyCapacityPercent, int replicationLagSeconds,
                                           int requestedTrafficPercent, boolean errorBudgetHealthy,
                                           int recommendedTrafficPercent, String status,
                                           List<String> blockers, List<String> checklist) {}
    public record ServiceCapacityPlan(double currentRequestsPerSecond, int currentInstances,
                                      double requestsPerSecondPerInstance,
                                      int targetUtilizationPercent, int minimumInstances,
                                      int maximumInstances, double currentUtilizationPercent,
                                      int requiredInstances, int recommendedInstances,
                                      int instanceDelta, String action, String status) {}
    public record CanaryReleaseDecision(double baselineErrorRatePercent,
                                        double canaryErrorRatePercent,
                                        double baselineP95LatencyMs,
                                        double canaryP95LatencyMs,
                                        long canarySampleSize, long minimumSampleSize,
                                        double errorRateIncreasePoints,
                                        double latencyIncreasePercent,
                                        String action, String status,
                                        List<String> blockers) {}
    public record ReleaseStage(int trafficPercent, int observationMinutes,
                               String requiredGate) {}
    public record StagedReleasePlan(int requestedFinalTrafficPercent,
                                    boolean errorBudgetHealthy, boolean rollbackReady,
                                    int minimumObservationMinutes, String status,
                                    List<String> blockers, List<ReleaseStage> stages,
                                    boolean automaticTrafficChangeStarted) {}
    public record ReleaseWindowRequest(String changeId, String serviceName,
                                       int requestedStartMinute, int durationMinutes,
                                       int priority) {}
    public record ReleaseWindowAssignment(String changeId, String serviceName,
                                          int requestedStartMinute, int coordinatedStartMinute,
                                          int durationMinutes, int delayedMinutes,
                                          List<String> sharedServices, String status) {}
    public record ReleaseWindowCoordination(String status,
                                            List<ReleaseWindowAssignment> assignments,
                                            boolean automaticReleaseStarted) {}
    public record RollbackServiceRequest(String serviceName, boolean rollbackReady) {}
    public record RollbackStep(int sequence, String serviceName,
                               List<String> dependenciesInPlan, String requiredGate) {}
    public record CoordinatedRollbackPlan(String status, List<String> blockers,
                                          List<RollbackStep> steps,
                                          boolean automaticRollbackStarted) {}
    public record ChangeCandidate(String changeId, String serviceName,
                                  int minutesBeforeIncident,
                                  double errorRateIncreasePoints) {}
    public record ChangeCorrelation(String changeId, String serviceName,
                                    String relationship, double correlationScore,
                                    int minutesBeforeIncident,
                                    double errorRateIncreasePoints) {}
    public record IncidentChangeRanking(String incidentService, String status,
                                        List<ChangeCorrelation> candidates,
                                        boolean automaticRollbackStarted) {}
    public record IsolationAction(String upstreamService, String failedService,
                                  String action, String verificationGate) {}
    public record ServiceIsolationPlan(String failedService, String status,
                                       List<String> downstreamToMonitor,
                                       List<IsolationAction> actions,
                                       boolean automaticIsolationStarted) {}
    public record OperatorJourneyRequest(String journeyId, String serviceName,
                                         boolean metricsAvailable,
                                         boolean alertAcknowledged,
                                         boolean ownerAssigned,
                                         boolean dependencyReviewed,
                                         boolean recoveryVerified) {}
    public record OperatorJourneyResult(String journeyId, String serviceName,
                                        String status, String primaryAction,
                                        List<String> steps, String message,
                                        int expectedSteps,
                                        boolean requiresConfirmation) {}
    public record OperatorJourneyRehearsal(String status, int journeyCount,
                                           int readyCount, int guidedCount,
                                           int unresolvedCount,
                                           double averageExpectedSteps,
                                           Map<String, Integer> actionCounts,
                                           List<OperatorJourneyResult> journeys,
                                           boolean incidentChangesStarted) {}
    public record CommandBoardItem(int rank, String journeyId, String serviceName,
                                   String status, String primaryAction,
                                   List<String> steps, String message,
                                   int estimatedMinutes, String resumeKey,
                                   boolean requiresConfirmation) {}
    public record OperatorCommandBoard(String status, int journeyCount,
                                       int visibleCount, int remainingCount,
                                       int estimatedFocusMinutes,
                                       Map<String, Integer> laneCounts,
                                       List<CommandBoardItem> focusItems,
                                       String guidance,
                                       boolean incidentChangesStarted) {}

    private static final Map<String, List<String>> DEPENDENCIES = Map.of(
            "gateway", List.of("checkout", "catalog"),
            "checkout", List.of("payment", "inventory"),
            "payment", List.of("notification"),
            "catalog", List.of("search")
    );

    @Transactional(readOnly = true)
    public List<TimelineEvent> timeline(long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("事故不存在: " + id));
        List<TimelineEvent> events = new ArrayList<>();
        events.add(new TimelineEvent("DETECTED", "首次触发告警", incident.getFirstAlertAt()));
        if (incident.getStatus() == IncidentStatus.INVESTIGATING || incident.getStatus() == IncidentStatus.RESOLVED) {
            events.add(new TimelineEvent("INVESTIGATING", "事故已接手处理", incident.getUpdatedAt()));
        }
        if (incident.getResolvedAt() != null) {
            events.add(new TimelineEvent("RESOLVED", "服务已确认恢复", incident.getResolvedAt()));
        }
        return events.stream().sorted((left, right) -> left.occurredAt().compareTo(right.occurredAt())).toList();
    }

    public BlastRadius blastRadius(String serviceName) {
        String normalized = serviceName == null ? "" : serviceName.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new IllegalArgumentException("服务名称长度应为 1 到 80 个字符");
        }
        List<String> downstream = DEPENDENCIES.getOrDefault(normalized, List.of());
        List<String> upstream = DEPENDENCIES.entrySet().stream()
                .filter(entry -> entry.getValue().contains(normalized)).map(Map.Entry::getKey).sorted().toList();
        int count = downstream.size();
        String risk = count >= 2 ? "HIGH" : count == 1 ? "MEDIUM" : "LOW";
        return new BlastRadius(normalized, upstream, downstream, count, risk);
    }

    @Transactional(readOnly = true)
    public ErrorBudget errorBudget(String serviceName, int minutes, double objective) {
        if (minutes < 1 || minutes > 43200) throw new IllegalArgumentException("观察窗口应为 1 到 43200 分钟");
        if (objective <= 0 || objective >= 1) throw new IllegalArgumentException("SLO 目标应在 0 与 1 之间");
        String normalized = serviceName == null ? "" : serviceName.trim();
        if (normalized.isEmpty() || normalized.length() > 80) throw new IllegalArgumentException("服务名称长度应为 1 到 80 个字符");
        List<TrafficMetric> metrics = metricRepository.findSinceForService(Instant.now().minus(minutes, ChronoUnit.MINUTES), normalized);
        long requests = metrics.stream().mapToLong(TrafficMetric::getRequestCount).sum();
        long errors = metrics.stream().mapToLong(TrafficMetric::getErrorCount).sum();
        double availability = requests == 0 ? 1.0 : 1.0 - (double) errors / requests;
        long allowedErrors = (long) Math.floor(requests * (1.0 - objective));
        long remaining = allowedErrors - errors;
        String status = requests == 0 ? "NO_DATA" : remaining < 0 ? "EXHAUSTED" : remaining <= Math.max(1, allowedErrors / 4) ? "AT_RISK" : "HEALTHY";
        return new ErrorBudget(normalized, minutes, objective, requests, errors, availability, remaining, status);
    }

    @Transactional(readOnly = true)
    public Runbook runbook(long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("事故不存在: " + id));
        List<String> steps = new ArrayList<>();
        steps.add("核对最近指标与关联告警");
        steps.add("确认 " + incident.getServiceName() + " 上下游影响范围");
        if (incident.getSeverity().name().equals("CRITICAL")) steps.add("通知服务负责人并建立升级通道");
        steps.add("执行恢复动作并持续观察错误预算");
        steps.add("确认指标恢复后关闭事故");
        boolean postmortem = incident.getSeverity().name().equals("CRITICAL");
        return new Runbook(incident.getServiceName(), incident.getOwner(), steps, postmortem,
                List.of("事故摘要", "影响范围", "根因", "恢复过程", "后续行动"));
    }

    @Transactional(readOnly = true)
    public EscalationPlan escalationPlan(long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("事故不存在: " + id));
        boolean critical = incident.getSeverity().name().equals("CRITICAL");
        return new EscalationPlan(incident.getServiceName(), incident.getSeverity().name(),
                incident.getOwner(), critical ? "platform-lead" : "service-owner",
                critical ? 10 : 30,
                List.of("确认负责人已收到事故", "同步影响范围与当前状态", "记录下一次更新时间"));
    }

    public MaintenanceImpact maintenanceImpact(String serviceName, int durationMinutes) {
        if (durationMinutes < 1 || durationMinutes > 1440) {
            throw new IllegalArgumentException("维护时长应为 1 到 1440 分钟");
        }
        BlastRadius radius = blastRadius(serviceName);
        boolean approval = radius.impactedServiceCount() >= 2 || durationMinutes > 60;
        String risk = approval ? "HIGH" : radius.impactedServiceCount() == 1 ? "MEDIUM" : "LOW";
        return new MaintenanceImpact(radius.serviceName(), durationMinutes, radius.downstream(), risk,
                approval, List.of("确认维护窗口", "通知受影响服务负责人", "准备回退方案", "维护后验证指标"));
    }

    @Transactional(readOnly = true)
    public ReliabilityScore reliabilityScore(String serviceName, int minutes, double objective) {
        ErrorBudget budget = errorBudget(serviceName, minutes, objective);
        BlastRadius radius = blastRadius(serviceName);
        int penalty = radius.impactedServiceCount() * 5;
        double score = Math.max(0.0, Math.min(100.0, budget.availability() * 100.0 - penalty));
        String grade = score >= 95 ? "A" : score >= 85 ? "B" : score >= 70 ? "C" : "D";
        return new ReliabilityScore(serviceName.trim(), score, grade, budget.availability(),
                penalty, budget.status());
    }

    @Transactional(readOnly = true)
    public IncidentBriefing briefing(long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("事故不存在: " + id));
        BlastRadius radius = blastRadius(incident.getServiceName());
        boolean critical = incident.getSeverity().name().equals("CRITICAL");
        List<String> steps = new ArrayList<>();
        steps.add("确认事故负责人和升级通道");
        if (radius.impactedServiceCount() > 0) steps.add("通知受影响的下游服务");
        steps.add("执行运行手册并记录时间线");
        return new IncidentBriefing(id, incident.getServiceName(), incident.getSeverity().name(),
                incident.getOwner(), critical ? "platform-lead" : "service-owner",
                radius.riskLevel(), radius.impactedServiceCount(), steps);
    }

    public ChangeWindow changeWindow(String serviceName, double reliabilityScore,
                                     int activeCriticalIncidents) {
        if (reliabilityScore < 0 || reliabilityScore > 100 || activeCriticalIncidents < 0) {
            throw new IllegalArgumentException("可靠性分数或事故数量无效");
        }
        blastRadius(serviceName);
        List<String> blockers = new ArrayList<>();
        if (reliabilityScore < 85) blockers.add("服务可靠性低于 85 分");
        if (activeCriticalIncidents > 0) blockers.add("存在活动严重事故");
        return new ChangeWindow(serviceName.trim(), reliabilityScore, activeCriticalIncidents,
                blockers.isEmpty() ? "OPEN" : "FROZEN", blockers);
    }

    public OnCallCapacity onCallCapacity(int activeIncidents, int criticalIncidents, int responders) {
        if (activeIncidents < 0 || criticalIncidents < 0 || criticalIncidents > activeIncidents || responders < 1) {
            throw new IllegalArgumentException("值班容量输入无效");
        }
        double load = (double) activeIncidents / responders;
        String status = criticalIncidents > responders || load > 5 ? "OVERLOADED" : load > 3 ? "BUSY" : "AVAILABLE";
        int required = Math.max(responders, (int) Math.ceil(activeIncidents / 5.0));
        if (criticalIncidents > required) required = criticalIncidents;
        return new OnCallCapacity(activeIncidents, criticalIncidents, responders, load, status,
                Math.max(0, required - responders));
    }

    public RecoveryValidation validateRecovery(boolean metricsRecovered, boolean budgetHealthy,
                                               boolean downstreamHealthy) {
        List<String> failed = new ArrayList<>();
        if (!metricsRecovered) failed.add("核心指标尚未恢复");
        if (!budgetHealthy) failed.add("错误预算仍处于风险状态");
        if (!downstreamHealthy) failed.add("下游服务尚未恢复");
        return new RecoveryValidation(metricsRecovered, budgetHealthy, downstreamHealthy,
                failed.isEmpty() ? "VERIFIED" : "INCOMPLETE", failed);
    }

    @Transactional(readOnly = true)
    public ExecutiveSummary executiveSummary(long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("事故不存在: " + id));
        BlastRadius radius = blastRadius(incident.getServiceName());
        boolean critical = incident.getSeverity().name().equals("CRITICAL");
        List<String> decisions = new ArrayList<>();
        decisions.add(critical ? "保持变更冻结并维持升级通道" : "由服务负责人持续跟进");
        if (radius.impactedServiceCount() > 0) decisions.add("同步下游服务影响与恢复进度");
        return new ExecutiveSummary(id, incident.getServiceName(), incident.getSeverity().name(),
                incident.getStatus().name(), radius.impactedServiceCount(), incident.getOwner(),
                incident.getServiceName() + " 服务事故正在处置", decisions);
    }

    public TrafficFailoverReadiness trafficFailoverReadiness(
            boolean primaryHealthy, boolean standbyHealthy, int standbyCapacityPercent,
            int replicationLagSeconds, int requestedTrafficPercent, boolean errorBudgetHealthy) {
        if (standbyCapacityPercent < 0 || standbyCapacityPercent > 100
                || requestedTrafficPercent < 1 || requestedTrafficPercent > 100
                || replicationLagSeconds < 0) {
            throw new IllegalArgumentException("流量切换输入无效");
        }
        List<String> blockers = new ArrayList<>();
        if (!standbyHealthy) blockers.add("备用服务健康检查未通过");
        if (replicationLagSeconds > 60) blockers.add("数据复制延迟超过 60 秒");
        int recommended = Math.min(requestedTrafficPercent, standbyCapacityPercent);
        if (!errorBudgetHealthy) recommended = Math.min(recommended, 25);
        if (!standbyHealthy || replicationLagSeconds > 60) recommended = 0;
        String status;
        if (!blockers.isEmpty()) status = "BLOCKED";
        else if (recommended < requestedTrafficPercent || !errorBudgetHealthy) status = "PARTIAL_READY";
        else status = "READY";
        List<String> checklist = new ArrayList<>();
        checklist.add("确认备用服务健康与容量");
        checklist.add("核对复制延迟和错误预算");
        checklist.add(primaryHealthy ? "确认计划切流审批" : "记录主服务故障证据");
        checklist.add("按建议比例切流并观察核心指标");
        return new TrafficFailoverReadiness(primaryHealthy, standbyHealthy,
                standbyCapacityPercent, replicationLagSeconds, requestedTrafficPercent,
                errorBudgetHealthy, recommended, status, blockers, checklist);
    }

    public ServiceCapacityPlan serviceCapacityPlan(
            double currentRequestsPerSecond, int currentInstances,
            double requestsPerSecondPerInstance, int targetUtilizationPercent,
            int minimumInstances, int maximumInstances) {
        if (currentRequestsPerSecond < 0 || currentInstances < 1
                || requestsPerSecondPerInstance <= 0
                || targetUtilizationPercent < 10 || targetUtilizationPercent > 90
                || minimumInstances < 1 || maximumInstances < minimumInstances) {
            throw new IllegalArgumentException("服务容量输入无效");
        }
        double safeCapacityPerInstance = requestsPerSecondPerInstance * targetUtilizationPercent / 100.0;
        int required = Math.max(minimumInstances,
                (int) Math.ceil(currentRequestsPerSecond / safeCapacityPerInstance));
        int recommended = Math.min(maximumInstances, required);
        double utilization = currentRequestsPerSecond
                / (currentInstances * requestsPerSecondPerInstance) * 100.0;
        int delta = recommended - currentInstances;
        String action = delta > 0 ? "SCALE_OUT" : delta < 0 ? "SCALE_IN" : "HOLD";
        String status = required > maximumInstances ? "CAPACITY_LIMIT"
                : action.equals("HOLD") ? "STABLE" : "ACTION_REQUIRED";
        return new ServiceCapacityPlan(currentRequestsPerSecond, currentInstances,
                requestsPerSecondPerInstance, targetUtilizationPercent,
                minimumInstances, maximumInstances, utilization, required,
                recommended, delta, action, status);
    }

    public CanaryReleaseDecision canaryReleaseDecision(
            double baselineErrorRatePercent, double canaryErrorRatePercent,
            double baselineP95LatencyMs, double canaryP95LatencyMs,
            long canarySampleSize, long minimumSampleSize,
            double maximumErrorIncreasePoints, double maximumLatencyIncreasePercent) {
        if (baselineErrorRatePercent < 0 || canaryErrorRatePercent < 0
                || baselineP95LatencyMs <= 0 || canaryP95LatencyMs <= 0
                || canarySampleSize < 0 || minimumSampleSize < 1
                || maximumErrorIncreasePoints < 0 || maximumLatencyIncreasePercent < 0) {
            throw new IllegalArgumentException("灰度发布指标输入无效");
        }
        double errorIncrease = canaryErrorRatePercent - baselineErrorRatePercent;
        double latencyIncrease = (canaryP95LatencyMs - baselineP95LatencyMs)
                / baselineP95LatencyMs * 100.0;
        List<String> blockers = new ArrayList<>();
        if (errorIncrease > maximumErrorIncreasePoints) blockers.add("灰度错误率增幅超过门槛");
        if (latencyIncrease > maximumLatencyIncreasePercent) blockers.add("灰度 P95 延迟增幅超过门槛");
        String action;
        String status;
        if (canarySampleSize < minimumSampleSize) {
            action = "CONTINUE_OBSERVATION";
            status = "INSUFFICIENT_SAMPLE";
        } else if (!blockers.isEmpty()) {
            action = "ROLLBACK";
            status = "ROLLBACK_RECOMMENDED";
        } else {
            action = "PROMOTE";
            status = "PROMOTE_READY";
        }
        return new CanaryReleaseDecision(baselineErrorRatePercent, canaryErrorRatePercent,
                baselineP95LatencyMs, canaryP95LatencyMs, canarySampleSize,
                minimumSampleSize, errorIncrease, latencyIncrease, action, status, blockers);
    }

    public StagedReleasePlan stagedReleasePlan(
            int requestedFinalTrafficPercent, boolean errorBudgetHealthy,
            boolean rollbackReady, int minimumObservationMinutes) {
        if (requestedFinalTrafficPercent < 1 || requestedFinalTrafficPercent > 100
                || minimumObservationMinutes < 5 || minimumObservationMinutes > 120) {
            throw new IllegalArgumentException("分阶段发布输入无效");
        }
        List<String> blockers = new ArrayList<>();
        if (!errorBudgetHealthy) blockers.add("错误预算状态不允许扩大灰度流量");
        if (!rollbackReady) blockers.add("回滚方案尚未就绪");
        List<ReleaseStage> stages = new ArrayList<>();
        if (blockers.isEmpty()) {
            for (int traffic : List.of(5, 25, 50, 100)) {
                if (traffic < requestedFinalTrafficPercent) {
                    stages.add(new ReleaseStage(traffic, minimumObservationMinutes,
                            "CANARY_METRICS_PASS"));
                }
            }
            stages.add(new ReleaseStage(requestedFinalTrafficPercent,
                    minimumObservationMinutes,
                    requestedFinalTrafficPercent == 100 ? "FINAL_APPROVAL" : "CANARY_METRICS_PASS"));
        }
        return new StagedReleasePlan(requestedFinalTrafficPercent, errorBudgetHealthy,
                rollbackReady, minimumObservationMinutes,
                blockers.isEmpty() ? "READY" : "BLOCKED", blockers, stages, false);
    }

    public ReleaseWindowCoordination coordinateReleaseWindows(List<ReleaseWindowRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("发布窗口请求不能为空");
        }
        Set<String> changeIds = new HashSet<>();
        for (ReleaseWindowRequest request : requests) {
            if (request == null || request.changeId() == null || request.changeId().isBlank()
                    || !request.changeId().equals(request.changeId().trim())
                    || !changeIds.add(request.changeId())
                    || request.requestedStartMinute() < 0 || request.requestedStartMinute() > 10080
                    || request.durationMinutes() < 1 || request.durationMinutes() > 1440
                    || request.priority() < 1 || request.priority() > 100) {
                throw new IllegalArgumentException("发布窗口输入无效");
            }
            blastRadius(request.serviceName());
        }

        List<ReleaseWindowRequest> ordered = requests.stream()
                .sorted((left, right) -> {
                    int priority = Integer.compare(right.priority(), left.priority());
                    if (priority != 0) return priority;
                    int start = Integer.compare(left.requestedStartMinute(), right.requestedStartMinute());
                    return start != 0 ? start : left.changeId().compareTo(right.changeId());
                }).toList();
        List<ReleaseWindowAssignment> assignments = new ArrayList<>();
        Map<String, Set<String>> affectedByChange = new java.util.HashMap<>();
        for (ReleaseWindowRequest request : ordered) {
            Set<String> affected = affectedServices(request.serviceName());
            affectedByChange.put(request.changeId(), affected);
            int start = request.requestedStartMinute();
            Set<String> shared = new HashSet<>();
            boolean moved;
            do {
                moved = false;
                for (ReleaseWindowAssignment existing : assignments) {
                    Set<String> intersection = new HashSet<>(affected);
                    intersection.retainAll(affectedByChange.get(existing.changeId()));
                    int existingEnd = existing.coordinatedStartMinute() + existing.durationMinutes();
                    int requestedEnd = start + request.durationMinutes();
                    if (!intersection.isEmpty() && start < existingEnd
                            && requestedEnd > existing.coordinatedStartMinute()) {
                        shared.addAll(intersection);
                        start = existingEnd;
                        moved = true;
                    }
                }
            } while (moved);
            int delay = start - request.requestedStartMinute();
            assignments.add(new ReleaseWindowAssignment(request.changeId(), request.serviceName(),
                    request.requestedStartMinute(), start, request.durationMinutes(), delay,
                    shared.stream().sorted().toList(), delay == 0 ? "ON_TIME" : "RESCHEDULED"));
        }
        boolean delayed = assignments.stream().anyMatch(item -> item.delayedMinutes() > 0);
        return new ReleaseWindowCoordination(delayed ? "COORDINATED" : "READY",
                assignments, false);
    }

    public CoordinatedRollbackPlan planCoordinatedRollback(List<RollbackServiceRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("回滚服务不能为空");
        }
        Set<String> names = new HashSet<>();
        List<String> blockers = new ArrayList<>();
        for (RollbackServiceRequest request : requests) {
            if (request == null || request.serviceName() == null
                    || request.serviceName().isBlank()
                    || !request.serviceName().equals(request.serviceName().trim())
                    || !names.add(request.serviceName())) {
                throw new IllegalArgumentException("回滚服务输入无效");
            }
            blastRadius(request.serviceName());
            if (!request.rollbackReady()) blockers.add(request.serviceName() + " 回滚工件未就绪");
        }
        if (!blockers.isEmpty()) {
            return new CoordinatedRollbackPlan("BLOCKED", blockers, List.of(), false);
        }
        List<String> ordered = names.stream()
                .sorted((left, right) -> {
                    int depth = Integer.compare(dependencyDepth(right, names, new HashSet<>()),
                            dependencyDepth(left, names, new HashSet<>()));
                    return depth != 0 ? depth : left.compareTo(right);
                }).toList();
        List<RollbackStep> steps = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            String serviceName = ordered.get(index);
            List<String> dependencies = DEPENDENCIES.getOrDefault(serviceName, List.of())
                    .stream().filter(names::contains).sorted().toList();
            steps.add(new RollbackStep(index + 1, serviceName, dependencies,
                    "VERIFY_METRICS_AND_DEPENDENTS"));
        }
        return new CoordinatedRollbackPlan("READY", List.of(), steps, false);
    }

    public IncidentChangeRanking rankIncidentChanges(String incidentService,
                                                      List<ChangeCandidate> candidates) {
        blastRadius(incidentService);
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("候选变更不能为空");
        }
        Set<String> changeIds = new HashSet<>();
        List<ChangeCorrelation> ranked = new ArrayList<>();
        for (ChangeCandidate candidate : candidates) {
            if (candidate == null || candidate.changeId() == null
                    || candidate.changeId().isBlank()
                    || !candidate.changeId().equals(candidate.changeId().trim())
                    || !changeIds.add(candidate.changeId())
                    || candidate.minutesBeforeIncident() < 0
                    || candidate.minutesBeforeIncident() > 10080
                    || candidate.errorRateIncreasePoints() < 0) {
                throw new IllegalArgumentException("候选变更输入无效");
            }
            blastRadius(candidate.serviceName());
            boolean direct = candidate.serviceName().equals(incidentService);
            boolean related = !direct && (affectedServices(candidate.serviceName()).contains(incidentService)
                    || affectedServices(incidentService).contains(candidate.serviceName()));
            String relationship = direct ? "DIRECT" : related ? "DEPENDENCY" : "UNRELATED";
            double relationshipScore = direct ? 60 : related ? 35 : 0;
            double recencyScore = Math.max(0, 30 * (1 - candidate.minutesBeforeIncident() / 120.0));
            double metricScore = Math.min(10, candidate.errorRateIncreasePoints() * 10);
            double score = Math.round((relationshipScore + recencyScore + metricScore) * 10.0) / 10.0;
            ranked.add(new ChangeCorrelation(candidate.changeId(), candidate.serviceName(),
                    relationship, score, candidate.minutesBeforeIncident(),
                    candidate.errorRateIncreasePoints()));
        }
        ranked.sort((left, right) -> {
            int score = Double.compare(right.correlationScore(), left.correlationScore());
            return score != 0 ? score : left.changeId().compareTo(right.changeId());
        });
        String status = ranked.getFirst().correlationScore() >= 60 ? "STRONG_CANDIDATE"
                : ranked.getFirst().correlationScore() >= 35 ? "REVIEW_CANDIDATES" : "WEAK_SIGNAL";
        return new IncidentChangeRanking(incidentService, status, ranked, false);
    }

    public ServiceIsolationPlan planServiceIsolation(String failedService,
                                                      List<String> fallbackReadyServices) {
        blastRadius(failedService);
        if (fallbackReadyServices == null) {
            throw new IllegalArgumentException("降级准备服务不能为空");
        }
        Set<String> ready = new HashSet<>();
        for (String serviceName : fallbackReadyServices) {
            if (serviceName == null || serviceName.isBlank()
                    || !serviceName.equals(serviceName.trim()) || !ready.add(serviceName)) {
                throw new IllegalArgumentException("降级准备服务输入无效");
            }
            blastRadius(serviceName);
        }
        List<String> upstream = DEPENDENCIES.entrySet().stream()
                .filter(entry -> entry.getValue().contains(failedService))
                .map(Map.Entry::getKey).sorted().toList();
        List<IsolationAction> actions = new ArrayList<>();
        if (upstream.isEmpty()) {
            actions.add(new IsolationAction(failedService, failedService,
                    "BLOCK_DIRECT_INGRESS", "VERIFY_ERROR_RATE_STOPS_GROWING"));
        } else {
            for (String serviceName : upstream) {
                actions.add(new IsolationAction(serviceName, failedService,
                        ready.contains(serviceName) ? "ROUTE_TO_FALLBACK" : "STOP_DEPENDENT_CALLS",
                        "VERIFY_UPSTREAM_DEGRADATION"));
            }
        }
        List<String> downstream = DEPENDENCIES.getOrDefault(failedService, List.of())
                .stream().sorted().toList();
        String status = !upstream.isEmpty() && upstream.stream().allMatch(ready::contains)
                ? "FALLBACK_READY" : "MANUAL_CONTAINMENT_REQUIRED";
        return new ServiceIsolationPlan(failedService, status, downstream, actions, false);
    }

    public OperatorJourneyRehearsal rehearseOperatorJourneys(
            List<OperatorJourneyRequest> journeys) {
        if (journeys == null || journeys.size() != 100) {
            throw new IllegalArgumentException("值班体验演练必须包含 100 条操作旅程");
        }
        Set<String> identifiers = new HashSet<>();
        List<OperatorJourneyResult> results = new ArrayList<>();
        for (OperatorJourneyRequest journey : journeys) {
            if (journey == null || journey.journeyId() == null
                    || journey.journeyId().isBlank()
                    || !journey.journeyId().equals(journey.journeyId().trim())
                    || !identifiers.add(journey.journeyId())) {
                throw new IllegalArgumentException("操作旅程编号必须非空且唯一");
            }
            blastRadius(journey.serviceName());
            String status;
            String action;
            List<String> steps;
            String message;
            boolean confirmation;
            if (!journey.metricsAvailable()) {
                status = "BLOCKED";
                action = "OPEN_METRICS_OVERVIEW";
                steps = List.of("打开服务指标", "确认采集新鲜度", "重新评估告警状态");
                message = "缺少最新指标，暂不推进会改变事故状态的操作。";
                confirmation = false;
            } else if (!journey.alertAcknowledged()) {
                status = "GUIDED";
                action = "ACKNOWLEDGE_ALERT";
                steps = List.of("确认告警影响", "登记接手时间", "进入事故工作区");
                message = "先确认告警，避免多人重复接手或告警无人负责。";
                confirmation = true;
            } else if (!journey.ownerAssigned()) {
                status = "GUIDED";
                action = "ASSIGN_INCIDENT_OWNER";
                steps = List.of("查看值班负载", "选择负责人", "发送处置交接");
                message = "事故尚无负责人，应先完成责任归属。";
                confirmation = true;
            } else if (!journey.dependencyReviewed()) {
                status = "GUIDED";
                action = "REVIEW_DEPENDENCY_BOUNDARY";
                steps = List.of("查看影响半径", "确认上下游状态", "选择隔离或降级路径");
                message = "依赖边界尚未复核，不建议直接执行服务变更。";
                confirmation = true;
            } else if (!journey.recoveryVerified()) {
                status = "VERIFY";
                action = "VERIFY_RECOVERY_GATES";
                steps = List.of("核对核心指标", "检查错误预算", "确认下游恢复");
                message = "处置已推进，但恢复门禁尚未全部确认。";
                confirmation = true;
            } else {
                status = "READY";
                action = "CLOSE_INCIDENT";
                steps = List.of("复核事故时间线", "记录恢复结论", "关闭事故并安排复盘");
                message = "处置证据完整，可进入关闭确认。";
                confirmation = true;
            }
            results.add(new OperatorJourneyResult(journey.journeyId(), journey.serviceName(),
                    status, action, steps, message, steps.size(), confirmation));
        }
        Map<String, Integer> actionCounts = new java.util.TreeMap<>();
        results.forEach(item -> actionCounts.merge(item.primaryAction(), 1, Integer::sum));
        int ready = (int) results.stream().filter(item -> item.status().equals("READY")).count();
        int unresolved = (int) results.stream()
                .filter(item -> item.steps().isEmpty() || item.message().isBlank()).count();
        double average = results.stream().mapToInt(OperatorJourneyResult::expectedSteps)
                .average().orElse(0);
        return new OperatorJourneyRehearsal("USABILITY_REHEARSAL_PASSED",
                results.size(), ready, results.size() - ready, unresolved,
                Math.round(average * 100.0) / 100.0, actionCounts, results, false);
    }

    public OperatorCommandBoard buildOperatorCommandBoard(
            List<OperatorJourneyRequest> journeys, int visibleLimit) {
        if (visibleLimit < 1 || visibleLimit > 25) {
            throw new IllegalArgumentException("焦点队列数量必须在 1 到 25 之间");
        }
        OperatorJourneyRehearsal rehearsal = rehearseOperatorJourneys(journeys);
        Map<String, Integer> statusOrder = Map.of(
                "BLOCKED", 0, "GUIDED", 1, "VERIFY", 2, "READY", 3);
        Map<String, Integer> actionMinutes = Map.of(
                "OPEN_METRICS_OVERVIEW", 4,
                "ACKNOWLEDGE_ALERT", 3,
                "ASSIGN_INCIDENT_OWNER", 5,
                "REVIEW_DEPENDENCY_BOUNDARY", 10,
                "VERIFY_RECOVERY_GATES", 8,
                "CLOSE_INCIDENT", 6);
        List<OperatorJourneyResult> ordered = new ArrayList<>(rehearsal.journeys());
        ordered.sort((left, right) -> {
            int status = Integer.compare(statusOrder.get(left.status()), statusOrder.get(right.status()));
            if (status != 0) return status;
            int confirmation = Boolean.compare(right.requiresConfirmation(), left.requiresConfirmation());
            return confirmation != 0 ? confirmation : left.journeyId().compareTo(right.journeyId());
        });
        List<CommandBoardItem> focus = new ArrayList<>();
        for (int index = 0; index < Math.min(visibleLimit, ordered.size()); index++) {
            OperatorJourneyResult item = ordered.get(index);
            focus.add(new CommandBoardItem(index + 1, item.journeyId(), item.serviceName(),
                    item.status(), item.primaryAction(), item.steps(), item.message(),
                    actionMinutes.get(item.primaryAction()),
                    item.journeyId() + ":" + item.primaryAction(),
                    item.requiresConfirmation()));
        }
        Map<String, Integer> laneCounts = new java.util.TreeMap<>();
        ordered.forEach(item -> laneCounts.merge(item.status(), 1, Integer::sum));
        int minutes = focus.stream().mapToInt(CommandBoardItem::estimatedMinutes).sum();
        return new OperatorCommandBoard("OPERATOR_COMMAND_BOARD_READY", 100,
                focus.size(), 100 - focus.size(), minutes, laneCounts, focus,
                "先补齐实时指标，再确认告警责任和依赖边界，最后完成恢复门禁。", false);
    }

    private int dependencyDepth(String serviceName, Set<String> plan, Set<String> visited) {
        if (!visited.add(serviceName)) return 0;
        return DEPENDENCIES.getOrDefault(serviceName, List.of()).stream()
                .filter(plan::contains)
                .mapToInt(name -> 1 + dependencyDepth(name, plan, new HashSet<>(visited)))
                .max().orElse(0);
    }

    private Set<String> affectedServices(String serviceName) {
        Set<String> affected = new HashSet<>();
        List<String> pending = new ArrayList<>();
        pending.add(serviceName.trim());
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (affected.add(current)) pending.addAll(DEPENDENCIES.getOrDefault(current, List.of()));
        }
        return affected;
    }
}
