import type { Alert, AlertBulkOwnerResult, AlertBulkResult, AlertDispatchPreview, AlertDispatchResult, AlertSummary, AlertWorkload, CanaryReleaseDecision, ChangeCandidate, CoordinatedRollbackPlan, Incident, IncidentChangeRanking, IncidentSummary, Metric, MetricInput, OperatorJourneyRequest, OperatorJourneyRehearsal, ReleaseWindowCoordination, ReleaseWindowRequest, RollbackServiceRequest, ServiceCapacityPlan, ServiceHealth, ServiceIsolationPlan, StagedReleasePlan, Summary, TrafficFailoverReadiness } from '../types'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (!response.ok) throw new Error(`请求失败：${response.status}`)
  return response.json() as Promise<T>
}

export const api = {
  ingestMetric: (metric: MetricInput) => request<Metric>('/api/v1/metrics', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(metric),
  }),
  summary: (serviceName = '', minutes = 60) => {
    const query = new URLSearchParams({ minutes: String(minutes) })
    if (serviceName) query.set('serviceName', serviceName)
    return request<Summary>(`/api/v1/dashboard/summary?${query}`)
  },
  serviceHealth: (minutes = 60) => request<ServiceHealth[]>(`/api/v1/dashboard/services?minutes=${minutes}`),
  recentMetrics: (serviceName = '', minutes = 60) => {
    const query = new URLSearchParams({ limit: '80', minutes: String(minutes) })
    if (serviceName) query.set('serviceName', serviceName)
    return request<Metric[]>(`/api/v1/metrics/recent?${query}`)
  },
  alerts: (status: Alert['status'], severity = '', owner = '', serviceName = '') => {
    const query = new URLSearchParams({ status })
    if (severity) query.set('severity', severity)
    if (owner) query.set('owner', owner)
    if (serviceName) query.set('serviceName', serviceName)
    return request<Alert[]>(`/api/v1/alerts?${query}`)
  },
  alertSummary: () => request<AlertSummary>('/api/v1/alerts/summary'),
  alertWorkloads: () => request<AlertWorkload[]>('/api/v1/alerts/workloads'),
  acknowledge: (id: number) => request<Alert>(`/api/v1/alerts/${id}/acknowledge`, { method: 'POST' }),
  resolve: (id: number) => request<Alert>(`/api/v1/alerts/${id}/resolve`, { method: 'POST' }),
  bulkUpdate: (ids: number[], action: 'ACKNOWLEDGE' | 'RESOLVE') => request<AlertBulkResult>('/api/v1/alerts/bulk', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ids, action }),
  }),
  bulkAssign: (ids: number[], owner: string) => request<AlertBulkOwnerResult>('/api/v1/alerts/bulk-owner', {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ids, owner }),
  }),
  dispatch: (ids: number[], owner: string, action: 'ACKNOWLEDGE' | 'RESOLVE') => request<AlertDispatchResult>('/api/v1/alerts/dispatch', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ids, owner, action }),
  }),
  previewDispatch: (ids: number[], owner: string, action: 'ACKNOWLEDGE' | 'RESOLVE') => request<AlertDispatchPreview>('/api/v1/alerts/dispatch/preview', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ids, owner, action }),
  }),
  assign: (id: number, owner: string) => request<Alert>(`/api/v1/alerts/${id}/owner`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ owner }),
  }),
  incidents: (status: Incident['status']) => request<Incident[]>(`/api/v1/incidents?status=${status}`),
  incidentSummary: () => request<IncidentSummary>('/api/v1/incidents/summary'),
  updateIncident: (id: number, owner: string | null, status: Incident['status']) => request<Incident>(`/api/v1/incidents/${id}`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ owner, status }),
  }),
  trafficFailoverReadiness: (input: {
    primaryHealthy: boolean; standbyHealthy: boolean; standbyCapacityPercent: number;
    replicationLagSeconds: number; requestedTrafficPercent: number; errorBudgetHealthy: boolean;
  }) => request<TrafficFailoverReadiness>(`/api/v2/incidents/traffic-failover-readiness?${new URLSearchParams({
    primaryHealthy: String(input.primaryHealthy), standbyHealthy: String(input.standbyHealthy),
    standbyCapacityPercent: String(input.standbyCapacityPercent), replicationLagSeconds: String(input.replicationLagSeconds),
    requestedTrafficPercent: String(input.requestedTrafficPercent), errorBudgetHealthy: String(input.errorBudgetHealthy),
  })}`),
  serviceCapacityPlan: (input: {
    currentRequestsPerSecond: number; currentInstances: number;
    requestsPerSecondPerInstance: number; targetUtilizationPercent?: number;
    minimumInstances?: number; maximumInstances?: number;
  }) => request<ServiceCapacityPlan>(`/api/v2/incidents/service-capacity-plan?${new URLSearchParams({
    currentRequestsPerSecond: String(input.currentRequestsPerSecond),
    currentInstances: String(input.currentInstances),
    requestsPerSecondPerInstance: String(input.requestsPerSecondPerInstance),
    targetUtilizationPercent: String(input.targetUtilizationPercent ?? 65),
    minimumInstances: String(input.minimumInstances ?? 2),
    maximumInstances: String(input.maximumInstances ?? 50),
  })}`),
  canaryReleaseDecision: (input: {
    baselineErrorRatePercent: number; canaryErrorRatePercent: number;
    baselineP95LatencyMs: number; canaryP95LatencyMs: number;
    canarySampleSize: number; minimumSampleSize?: number;
    maximumErrorIncreasePoints?: number; maximumLatencyIncreasePercent?: number;
  }) => request<CanaryReleaseDecision>(`/api/v2/incidents/canary-release-decision?${new URLSearchParams({
    baselineErrorRatePercent: String(input.baselineErrorRatePercent),
    canaryErrorRatePercent: String(input.canaryErrorRatePercent),
    baselineP95LatencyMs: String(input.baselineP95LatencyMs),
    canaryP95LatencyMs: String(input.canaryP95LatencyMs),
    canarySampleSize: String(input.canarySampleSize),
    minimumSampleSize: String(input.minimumSampleSize ?? 1000),
    maximumErrorIncreasePoints: String(input.maximumErrorIncreasePoints ?? 0.2),
    maximumLatencyIncreasePercent: String(input.maximumLatencyIncreasePercent ?? 20),
  })}`),
  stagedReleasePlan: (input: {
    requestedFinalTrafficPercent: number; errorBudgetHealthy: boolean;
    rollbackReady: boolean; minimumObservationMinutes?: number;
  }) => request<StagedReleasePlan>(`/api/v2/incidents/staged-release-plan?${new URLSearchParams({
    requestedFinalTrafficPercent: String(input.requestedFinalTrafficPercent),
    errorBudgetHealthy: String(input.errorBudgetHealthy),
    rollbackReady: String(input.rollbackReady),
    minimumObservationMinutes: String(input.minimumObservationMinutes ?? 15),
  })}`),
  coordinateReleaseWindows: (input: ReleaseWindowRequest[]) =>
    request<ReleaseWindowCoordination>('/api/v3/incidents/release-window-coordination', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
    }),
  coordinatedRollbackPlan: (input: RollbackServiceRequest[]) =>
    request<CoordinatedRollbackPlan>('/api/v3/incidents/coordinated-rollback-plan', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
    }),
  rankIncidentChanges: (incidentService: string, input: ChangeCandidate[]) =>
    request<IncidentChangeRanking>(`/api/v3/incidents/${encodeURIComponent(incidentService)}/change-correlation`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
    }),
  serviceIsolationPlan: (failedService: string, fallbackReadyServices: string[]) =>
    request<ServiceIsolationPlan>(`/api/v3/incidents/${encodeURIComponent(failedService)}/isolation-plan`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fallbackReadyServices),
    }),
  operatorRehearsal: (input: OperatorJourneyRequest[]) =>
    request<OperatorJourneyRehearsal>('/api/v3/incidents/operator-rehearsal', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
    }),
}

