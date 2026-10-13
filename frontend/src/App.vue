<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import TrendChart from './components/TrendChart.vue'
import { api } from './services/api'
import type { Alert, Metric, RealtimeEvent, Summary } from './types'

const emptySummary: Summary = {
  windowStart: '', windowEnd: '', totalRequests: 0, totalErrors: 0,
  errorRate: 0, averageP95LatencyMs: 0, activeAlerts: 0, activeServices: 0,
}
const summary = ref<Summary>(emptySummary)
const metrics = ref<Metric[]>([])
const alerts = ref<Alert[]>([])
const loading = ref(true)
const errorMessage = ref('')
const connected = ref(false)
const lastUpdated = ref<Date>()
let socket: WebSocket | undefined
let reconnectTimer: number | undefined

const latestByService = computed(() => {
  const seen = new Set<string>()
  return metrics.value.filter(item => {
    if (seen.has(item.serviceName)) return false
    seen.add(item.serviceName)
    return true
  })
})

const requestLabel = computed(() => new Intl.NumberFormat('zh-CN', { notation: 'compact' }).format(summary.value.totalRequests))
const updatedLabel = computed(() => lastUpdated.value
  ? lastUpdated.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  : '--:--:--')

async function loadData() {
  try {
    const [summaryData, metricData, openAlerts, acknowledgedAlerts] = await Promise.all([
      api.summary(), api.recentMetrics(), api.alerts('OPEN'), api.alerts('ACKNOWLEDGED'),
    ])
    summary.value = summaryData
    metrics.value = metricData
    alerts.value = [...openAlerts, ...acknowledgedAlerts]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    lastUpdated.value = new Date()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '数据加载失败'
  } finally {
    loading.value = false
  }
}

function connect() {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
  socket = new WebSocket(`${scheme}://${location.host}/ws/metrics`)
  socket.onopen = () => { connected.value = true }
  socket.onmessage = async event => {
    const message = JSON.parse(event.data) as RealtimeEvent
    if (message.type !== 'connection.ready') await loadData()
  }
  socket.onclose = () => {
    connected.value = false
    reconnectTimer = window.setTimeout(connect, 2500)
  }
  socket.onerror = () => socket?.close()
}

async function updateAlert(alert: Alert, action: 'acknowledge' | 'resolve') {
  try {
    if (action === 'acknowledge') await api.acknowledge(alert.id)
    else await api.resolve(alert.id)
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '告警操作失败'
  }
}

function percent(value: number) { return `${(value * 100).toFixed(2)}%` }
function time(value: string) { return new Date(value).toLocaleString('zh-CN', { hour12: false }) }

onMounted(async () => { await loadData(); connect() })
onBeforeUnmount(() => { socket?.close(); if (reconnectTimer) window.clearTimeout(reconnectTimer) })
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">D</span><div><strong>DataOps</strong><small>数据运营平台</small></div></div>
      <nav aria-label="主导航">
        <a class="nav-item active" href="#overview"><span>01</span>运营总览</a>
        <a class="nav-item" href="#traffic"><span>02</span>流量监控</a>
        <a class="nav-item" href="#alerts"><span>03</span>告警中心</a>
      </nav>
      <div class="sidebar-note">
        <span :class="['status-dot', { online: connected }]" />
        {{ connected ? '实时通道已连接' : '正在重新连接' }}
      </div>
    </aside>

    <main>
      <header class="topbar" id="overview">
        <div><p class="eyebrow">OPERATIONS CENTER</p><h1>互联网流量实时监控</h1></div>
        <div class="update-time"><span>数据更新时间</span><strong>{{ updatedLabel }}</strong></div>
      </header>

      <p v-if="errorMessage" class="error-banner">{{ errorMessage }} <button @click="loadData">重新加载</button></p>

      <section class="kpi-grid" aria-label="关键运营指标">
        <article class="kpi"><p>近 60 分钟请求量</p><strong>{{ requestLabel }}</strong><span>累计访问请求</span></article>
        <article class="kpi"><p>错误率</p><strong :class="{ danger: summary.errorRate >= .05 }">{{ percent(summary.errorRate) }}</strong><span>错误 {{ summary.totalErrors.toLocaleString() }} 次</span></article>
        <article class="kpi"><p>平均 P95 延迟</p><strong>{{ summary.averageP95LatencyMs.toFixed(0) }}<i> ms</i></strong><span>覆盖 {{ summary.activeServices }} 个服务</span></article>
        <article class="kpi"><p>待处理告警</p><strong class="warning">{{ summary.activeAlerts }}</strong><span>需要运营跟进</span></article>
      </section>

      <section class="content-grid" id="traffic">
        <article class="panel trend-panel">
          <div class="panel-heading"><div><p class="eyebrow">TRAFFIC TREND</p><h2>请求量与响应延迟</h2></div><span class="period">最近 60 分钟</span></div>
          <TrendChart :metrics="metrics" />
        </article>

        <article class="panel service-panel">
          <div class="panel-heading"><div><p class="eyebrow">SERVICE HEALTH</p><h2>服务实时状态</h2></div></div>
          <div v-if="loading" class="empty">正在加载服务指标</div>
          <div v-else class="service-list">
            <div v-for="metric in latestByService" :key="metric.serviceName" class="service-row">
              <div><strong>{{ metric.serviceName }}</strong><span>{{ time(metric.collectedAt) }}</span></div>
              <div class="service-stat"><span>错误率</span><b :class="{ danger: metric.errorRate >= .05 }">{{ percent(metric.errorRate) }}</b></div>
              <div class="service-stat"><span>P95</span><b>{{ metric.p95LatencyMs }}ms</b></div>
            </div>
          </div>
        </article>
      </section>

      <section class="panel alert-panel" id="alerts">
        <div class="panel-heading"><div><p class="eyebrow">INCIDENT QUEUE</p><h2>告警处置队列</h2></div><span class="count">{{ alerts.length }} 条</span></div>
        <div v-if="alerts.length === 0" class="empty">当前没有待处理告警</div>
        <div v-else class="alert-table-wrap">
          <table>
            <thead><tr><th>级别</th><th>服务</th><th>告警内容</th><th>发生时间</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="alert in alerts" :key="alert.id">
                <td><span :class="['severity', alert.severity.toLowerCase()]">{{ alert.severity === 'CRITICAL' ? '严重' : '警告' }}</span></td>
                <td><strong>{{ alert.serviceName }}</strong></td>
                <td>{{ alert.message }}</td>
                <td>{{ time(alert.createdAt) }}</td>
                <td>{{ alert.status === 'OPEN' ? '待确认' : '处理中' }}</td>
                <td class="actions"><button v-if="alert.status === 'OPEN'" @click="updateAlert(alert, 'acknowledge')">确认</button><button class="primary" @click="updateAlert(alert, 'resolve')">解决</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </main>
  </div>
</template>

