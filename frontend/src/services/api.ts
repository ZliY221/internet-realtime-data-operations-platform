import type { Alert, AlertSummary, AlertWorkload, Metric, ServiceHealth, Summary } from '../types'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (!response.ok) throw new Error(`请求失败：${response.status}`)
  return response.json() as Promise<T>
}

export const api = {
  summary: () => request<Summary>('/api/v1/dashboard/summary?minutes=60'),
  serviceHealth: () => request<ServiceHealth[]>('/api/v1/dashboard/services?minutes=60'),
  recentMetrics: () => request<Metric[]>('/api/v1/metrics/recent?limit=80'),
  alerts: (status: Alert['status'], severity = '', owner = '') => {
    const query = new URLSearchParams({ status })
    if (severity) query.set('severity', severity)
    if (owner) query.set('owner', owner)
    return request<Alert[]>(`/api/v1/alerts?${query}`)
  },
  alertSummary: () => request<AlertSummary>('/api/v1/alerts/summary'),
  alertWorkloads: () => request<AlertWorkload[]>('/api/v1/alerts/workloads'),
  acknowledge: (id: number) => request<Alert>(`/api/v1/alerts/${id}/acknowledge`, { method: 'POST' }),
  resolve: (id: number) => request<Alert>(`/api/v1/alerts/${id}/resolve`, { method: 'POST' }),
  assign: (id: number, owner: string) => request<Alert>(`/api/v1/alerts/${id}/owner`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ owner }),
  }),
}

