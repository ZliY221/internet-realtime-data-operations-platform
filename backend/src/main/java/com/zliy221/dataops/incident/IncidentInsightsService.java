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
