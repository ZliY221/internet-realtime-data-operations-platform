package com.zliy221.dataops.incident;

import com.zliy221.dataops.metric.TrafficMetric;
import com.zliy221.dataops.metric.TrafficMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
}
