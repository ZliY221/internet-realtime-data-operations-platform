import type { Alert, AlertSummary, Metric, ServiceHealth, Summary } from '../types'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (!response.ok) throw new Error(`请求失败：${response.status}`)
  return response.json() as Promise<T>
}

export const api = {
  summary: () => request<Summary>('/api/v1/dashboard/summary?minutes=60'),
  serviceHealth: () => request<ServiceHealth[]>('/api/v1/dashboard/services?minutes=60'),
  recentMetrics: () => request<Metric[]>('/api/v1/metrics/recent?limit=80'),
  alerts: (status: Alert['status'], severity = '') => {
    const query = new URLSearchParams({ status })
    if (severity) query.set('severity', severity)
    return request<Alert[]>(`/api/v1/alerts?${query}`)
  },
  alertSummary: () => request<AlertSummary>('/api/v1/alerts/summary'),
  acknowledge: (id: number) => request<Alert>(`/api/v1/alerts/${id}/acknowledge`, { method: 'POST' }),
  resolve: (id: number) => request<Alert>(`/api/v1/alerts/${id}/resolve`, { method: 'POST' }),
}

