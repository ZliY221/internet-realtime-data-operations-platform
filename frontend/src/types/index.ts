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
  createdAt: string
  updatedAt: string
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
  assignedCount: number
  statusChangedCount: number
  serviceCount: number
  services: string[]
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

