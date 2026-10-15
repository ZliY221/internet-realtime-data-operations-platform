import type { Alert, AlertBulkResult, AlertSummary, AlertWorkload, Metric, MetricInput, ServiceHealth, Summary } from '../types'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (!response.ok) throw new Error(`请求失败：${response.status}`)
  return response.json() as Promise<T>
}

export const api = {
  ingestMetric: (metric: MetricInput) => request<Metric>('/api/v1/metrics', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(metric),
  }),
  summary: (serviceName = '') => {
    const query = new URLSearchParams({ minutes: '60' })
    if (serviceName) query.set('serviceName', serviceName)
    return request<Summary>(`/api/v1/dashboard/summary?${query}`)
  },
  serviceHealth: () => request<ServiceHealth[]>('/api/v1/dashboard/services?minutes=60'),
  recentMetrics: (serviceName = '') => {
    const query = new URLSearchParams({ limit: '80' })
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
  assign: (id: number, owner: string) => request<Alert>(`/api/v1/alerts/${id}/owner`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ owner }),
  }),
}

