export interface Metric {
  id: number
  serviceName: string
  requestCount: number
  errorCount: number
  errorRate: number
  p95LatencyMs: number
  collectedAt: string
}

export interface Alert {
  id: number
  metricId: number
  serviceName: string
  severity: 'WARNING' | 'CRITICAL'
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'
  ruleCode: string
  message: string
  owner: string | null
  incidentId: number | null
  createdAt: string
  updatedAt: string
}

export interface Incident {
  id: number
  serviceName: string
  severity: 'WARNING' | 'CRITICAL'
  status: 'OPEN' | 'INVESTIGATING' | 'RESOLVED'
  title: string
  owner: string | null
  alertCount: number
  firstAlertAt: string
  lastAlertAt: string
  createdAt: string
  updatedAt: string
  resolvedAt: string | null
}

export interface IncidentSummary {
  open: number
  investigating: number
  resolved: number
  activeCritical: number
}

export interface AlertSummary {
  open: number
  acknowledged: number
  resolved: number
  activeCritical: number
  activeWarning: number
}

export interface AlertWorkload {
  owner: string
  total: number
  open: number
  acknowledged: number
  critical: number
  warning: number
}

export interface AlertBulkResult {
  action: 'ACKNOWLEDGE' | 'RESOLVE'
  count: number
  items: Alert[]
}

export interface AlertBulkOwnerResult {
  owner: string
  count: number
  items: Alert[]
}

export interface AlertDispatchResult {
  owner: string
  action: 'ACKNOWLEDGE' | 'RESOLVE'
  count: number
  changedCount: number
  unchangedCount: number
  outcome: 'COMPLETE' | 'PARTIAL' | 'NO_CHANGE'
  assignedCount: number
  statusChangedCount: number
  criticalCount: number
  warningCount: number
  statusBeforeCounts: Record<string, number>
  serviceCount: number
  services: string[]
  followUpRequired: boolean
  reviewPriority: 'P0' | 'P1' | 'NONE'
  reviewWindowHours: number | null
  nextAction: 'REVIEW_SELECTION' | 'VERIFY_SERVICE_RECOVERY' | 'ESCALATE_SERVICE_OWNER' | 'MONITOR_SERVICE'
  handoffChecklist: string[]
  handoffStepCount: number
  estimatedHandoffMinutes: number
  handoffReadiness: 'SELECTION_REVIEW' | 'ESCALATION_REQUIRED' | 'VERIFICATION_REQUIRED' | 'READY_FOR_MONITORING'
  handoffBlockers: string[]
  handoffSummary: string
  receiptId: string
  executedAt: string
  items: Alert[]
}

export interface AlertDispatchPreview {
  count: number
  assignedCount: number
  statusChangedCount: number
  criticalCount: number
  warningCount: number
  serviceCount: number
  services: string[]
  requiresConfirmation: boolean
}

export interface Summary {
  windowStart: string
  windowEnd: string
  totalRequests: number
  totalErrors: number
  errorRate: number
  averageP95LatencyMs: number
  activeAlerts: number
  activeServices: number
}

export interface ServiceHealth {
  serviceName: string
  requestCount: number
  errorCount: number
  errorRate: number
  averageP95LatencyMs: number
  latestCollectedAt: string
  status: 'HEALTHY' | 'DEGRADED'
}

export interface RealtimeEvent<T = unknown> {
  type: string
  occurredAt: string
  payload?: T
}

export interface MetricInput {
  serviceName: string
  requestCount: number
  errorCount: number
  p95LatencyMs: number
  collectedAt: string
}

export interface TrafficFailoverReadiness {
  primaryHealthy: boolean
  standbyHealthy: boolean
  standbyCapacityPercent: number
  replicationLagSeconds: number
  requestedTrafficPercent: number
  errorBudgetHealthy: boolean
  recommendedTrafficPercent: number
  status: 'READY' | 'PARTIAL_READY' | 'BLOCKED'
  blockers: string[]
  checklist: string[]
}

export interface ServiceCapacityPlan {
  currentRequestsPerSecond: number
  currentInstances: number
  requestsPerSecondPerInstance: number
  targetUtilizationPercent: number
  minimumInstances: number
  maximumInstances: number
  currentUtilizationPercent: number
  requiredInstances: number
  recommendedInstances: number
  instanceDelta: number
  action: 'SCALE_OUT' | 'SCALE_IN' | 'HOLD'
  status: 'CAPACITY_LIMIT' | 'ACTION_REQUIRED' | 'STABLE'
}

export interface CanaryReleaseDecision {
  baselineErrorRatePercent: number
  canaryErrorRatePercent: number
  baselineP95LatencyMs: number
  canaryP95LatencyMs: number
  canarySampleSize: number
  minimumSampleSize: number
  errorRateIncreasePoints: number
  latencyIncreasePercent: number
  action: 'CONTINUE_OBSERVATION' | 'ROLLBACK' | 'PROMOTE'
  status: 'INSUFFICIENT_SAMPLE' | 'ROLLBACK_RECOMMENDED' | 'PROMOTE_READY'
  blockers: string[]
}

export interface StagedReleasePlan {
  requestedFinalTrafficPercent: number
  errorBudgetHealthy: boolean
  rollbackReady: boolean
  minimumObservationMinutes: number
  status: 'READY' | 'BLOCKED'
  blockers: string[]
  stages: Array<{
    trafficPercent: number
    observationMinutes: number
    requiredGate: 'CANARY_METRICS_PASS' | 'FINAL_APPROVAL'
  }>
  automaticTrafficChangeStarted: boolean
}

export interface ReleaseWindowRequest {
  changeId: string
  serviceName: string
  requestedStartMinute: number
  durationMinutes: number
  priority: number
}

export interface ReleaseWindowCoordination {
  status: 'READY' | 'COORDINATED'
  assignments: Array<ReleaseWindowRequest & {
    coordinatedStartMinute: number
    delayedMinutes: number
    sharedServices: string[]
    status: 'ON_TIME' | 'RESCHEDULED'
  }>
  automaticReleaseStarted: boolean
}

export interface RollbackServiceRequest {
  serviceName: string
  rollbackReady: boolean
}

export interface CoordinatedRollbackPlan {
  status: 'READY' | 'BLOCKED'
  blockers: string[]
  steps: Array<{
    sequence: number
    serviceName: string
    dependenciesInPlan: string[]
    requiredGate: 'VERIFY_METRICS_AND_DEPENDENTS'
  }>
  automaticRollbackStarted: boolean
}

export interface ChangeCandidate {
  changeId: string
  serviceName: string
  minutesBeforeIncident: number
  errorRateIncreasePoints: number
}

export interface IncidentChangeRanking {
  incidentService: string
  status: 'STRONG_CANDIDATE' | 'REVIEW_CANDIDATES' | 'WEAK_SIGNAL'
  candidates: Array<ChangeCandidate & {
    relationship: 'DIRECT' | 'DEPENDENCY' | 'UNRELATED'
    correlationScore: number
  }>
  automaticRollbackStarted: boolean
}

