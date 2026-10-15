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

