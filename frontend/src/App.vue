<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import TrendChart from './components/TrendChart.vue'
import { api } from './services/api'
import type { Alert, AlertSummary, AlertWorkload, Incident, IncidentSummary, Metric, RealtimeEvent, ServiceHealth, Summary } from './types'

const emptySummary: Summary = {
  windowStart: '', windowEnd: '', totalRequests: 0, totalErrors: 0,
  errorRate: 0, averageP95LatencyMs: 0, activeAlerts: 0, activeServices: 0,
}
const summary = ref<Summary>(emptySummary)
const metrics = ref<Metric[]>([])
const alerts = ref<Alert[]>([])
const alertSummary = ref<AlertSummary>({ open: 0, acknowledged: 0, resolved: 0, activeCritical: 0, activeWarning: 0 })
const severityFilter = ref<'' | Alert['severity']>('')
const ownerFilter = ref('')
const serviceFilter = ref('')
const timeWindowMinutes = ref(60)
const alertWorkloads = ref<AlertWorkload[]>([])
const incidents = ref<Incident[]>([])
const incidentSummary = ref<IncidentSummary>({ open: 0, investigating: 0, resolved: 0, activeCritical: 0 })
const services = ref<ServiceHealth[]>([])
const loading = ref(true)
const errorMessage = ref('')
const connected = ref(false)
const lastUpdated = ref<Date>()
const submittingMetric = ref(false)
const bulkUpdating = ref(false)
const bulkOwner = ref('platform-oncall')
const operationMessage = ref('')
const selectedAlerts = ref<Set<number>>(new Set())
const metricForm = ref({ serviceName: 'checkout', requestCount: 2000, errorCount: 20, p95LatencyMs: 320 })
type WorkspacePage = 'overview' | 'traffic' | 'incidents' | 'alerts'
const activePage = ref<WorkspacePage>('overview')
const pageCopy: Record<WorkspacePage, { title: string; description: string }> = {
  overview: { title: '互联网流量实时监控', description: '先判断全局状态，再选择需要深入处理的服务。' },
  traffic: { title: '实时流量与服务健康', description: '对照请求、错误率和延迟变化定位异常服务。' },
  incidents: { title: '服务事故协同中心', description: '按事故完成接手、责任分派和恢复确认。' },
  alerts: { title: '告警处置工作台', description: '筛选有效告警，批量分派并保留处置回执。' },
}
let socket: WebSocket | undefined
let reconnectTimer: number | undefined
let realtimeRefreshTimer: number | undefined
let loadPromise: Promise<void> | undefined
let reloadQueued = false
let pendingRealtimeRefresh = false
let mounted = false
let disposed = false
const workspacePages: WorkspacePage[] = ['overview', 'traffic', 'incidents', 'alerts']

const requestLabel = computed(() => new Intl.NumberFormat('zh-CN', { notation: 'compact' }).format(summary.value.totalRequests))
const updatedLabel = computed(() => lastUpdated.value
  ? lastUpdated.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  : '--:--:--')
const timeWindowLabel = computed(() => ({ 15: '近 15 分钟', 60: '近 1 小时', 180: '近 3 小时', 1440: '近 24 小时' }[timeWindowMinutes.value] || `近 ${timeWindowMinutes.value} 分钟`))
const trendScopeLabel = computed(() => serviceFilter.value ? `${serviceFilter.value} · ${timeWindowLabel.value}` : `全部服务 · ${timeWindowLabel.value}`)
const summaryScopeLabel = computed(() => serviceFilter.value ? `${serviceFilter.value} · ${timeWindowLabel.value}` : timeWindowLabel.value)
const activePageCopy = computed(() => pageCopy[activePage.value])

function activatePage(page: WorkspacePage, refresh = true) {
  activePage.value = page
  window.history.replaceState(null, '', `#${page}`)
  try { localStorage.setItem('internet-operations-page', page) } catch (_) {}
  window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
  if (refresh && mounted) void loadData()
}

function movePageFocus(event: KeyboardEvent, index: number) {
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? workspacePages.length - 1 : (index + (event.key === 'ArrowDown' ? 1 : -1) + workspacePages.length) % workspacePages.length
  document.querySelectorAll<HTMLAnchorElement>('.nav-item')[next]?.focus()
}

async function performLoadData() {
  try {
    const page = activePage.value
    if (page === 'overview') {
      const [summaryData, alertSummaryData, incidentSummaryData] = await Promise.all([
        api.summary(serviceFilter.value, timeWindowMinutes.value), api.alertSummary(), api.incidentSummary(),
      ])
      summary.value = summaryData; alertSummary.value = alertSummaryData; incidentSummary.value = incidentSummaryData
    } else if (page === 'traffic') {
      const [summaryData, metricData, serviceData] = await Promise.all([
        api.summary(serviceFilter.value, timeWindowMinutes.value), api.recentMetrics(serviceFilter.value, timeWindowMinutes.value), api.serviceHealth(timeWindowMinutes.value),
      ])
      summary.value = summaryData; metrics.value = metricData; services.value = serviceData
    } else if (page === 'incidents') {
      const [incidentSummaryData, openIncidents, investigatingIncidents] = await Promise.all([
        api.incidentSummary(), api.incidents('OPEN'), api.incidents('INVESTIGATING'),
      ])
      incidentSummary.value = incidentSummaryData
      incidents.value = [...openIncidents, ...investigatingIncidents].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    } else {
      const [serviceData, alertSummaryData, workloadData, openAlerts, acknowledgedAlerts] = await Promise.all([
        api.serviceHealth(timeWindowMinutes.value), api.alertSummary(), api.alertWorkloads(), api.alerts('OPEN', severityFilter.value, ownerFilter.value, serviceFilter.value), api.alerts('ACKNOWLEDGED', severityFilter.value, ownerFilter.value, serviceFilter.value),
      ])
      services.value = serviceData; alertSummary.value = alertSummaryData; alertWorkloads.value = workloadData
      alerts.value = [...openAlerts, ...acknowledgedAlerts].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      selectedAlerts.value = new Set([...selectedAlerts.value].filter(id => alerts.value.some(alert => alert.id === id)))
    }
    lastUpdated.value = new Date()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '数据加载失败'
  }
}

async function loadData() {
  if (loadPromise) { reloadQueued = true; return loadPromise }
  loading.value = true
  loadPromise = performLoadData()
  try { await loadPromise } finally {
    loadPromise = undefined
    loading.value = false
    if (reloadQueued) { reloadQueued = false; void loadData() }
  }
}

function scheduleRealtimeRefresh() {
  if (document.hidden) { pendingRealtimeRefresh = true; return }
  if (realtimeRefreshTimer) window.clearTimeout(realtimeRefreshTimer)
  realtimeRefreshTimer = window.setTimeout(() => {
    realtimeRefreshTimer = undefined
    pendingRealtimeRefresh = false
    void loadData()
  }, 180)
}

function handleVisibilityChange() {
  if (!document.hidden && pendingRealtimeRefresh) scheduleRealtimeRefresh()
}

async function updateIncident(incident: Incident, status: 'INVESTIGATING' | 'RESOLVED') {
  try {
    await api.updateIncident(incident.id, incident.owner || 'platform-oncall', status)
    operationMessage.value = status === 'RESOLVED'
      ? `事故 #${incident.id} 已恢复，关联 ${incident.alertCount} 条告警。`
      : `事故 #${incident.id} 已由 ${incident.owner || 'platform-oncall'} 接手。`
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '事故状态更新失败'
  }
}

async function assignIncident(incident: Incident, event: Event) {
  const owner = (event.target as HTMLSelectElement).value
  if (!owner || owner === incident.owner) return
  try {
    await api.updateIncident(incident.id, owner, incident.status)
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '事故分派失败'
  }
}

function connect() {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
  socket = new WebSocket(`${scheme}://${location.host}/ws/metrics`)
  socket.onopen = () => { connected.value = true }
  socket.onmessage = event => {
    const message = JSON.parse(event.data) as RealtimeEvent
    if (message.type !== 'connection.ready') scheduleRealtimeRefresh()
  }
  socket.onclose = () => {
    connected.value = false
    if (!disposed) reconnectTimer = window.setTimeout(connect, 2500)
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

async function assignAlert(alert: Alert, event: Event) {
  const owner = (event.target as HTMLSelectElement).value
  if (!owner || owner === alert.owner) return
  try {
    await api.assign(alert.id, owner)
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '告警分派失败'
  }
}

async function submitMetric() {
  submittingMetric.value = true
  try {
    await api.ingestMetric({ ...metricForm.value, collectedAt: new Date().toISOString() })
    errorMessage.value = ''
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '指标提交失败'
  } finally {
    submittingMetric.value = false
  }
}

function toggleAlert(id: number, checked: boolean) {
  const next = new Set(selectedAlerts.value)
  if (checked) next.add(id); else next.delete(id)
  selectedAlerts.value = next
}

function toggleAll(checked: boolean) {
  selectedAlerts.value = checked ? new Set(alerts.value.map(alert => alert.id)) : new Set()
}

function focusService(serviceName: string) {
  serviceFilter.value = serviceFilter.value === serviceName ? '' : serviceName
  selectedAlerts.value = new Set()
  activatePage('traffic')
}

async function clearServiceFocus() {
  serviceFilter.value = ''
  selectedAlerts.value = new Set()
  await loadData()
}

async function changeServiceFilter() {
  selectedAlerts.value = new Set()
  await loadData()
}

async function changeTimeWindow() {
  selectedAlerts.value = new Set()
  await loadData()
}

async function bulkUpdate(action: 'acknowledge' | 'resolve') {
  const targets = alerts.value.filter(alert => selectedAlerts.value.has(alert.id))
  if (!targets.length) return
  bulkUpdating.value = true
  try {
    await api.bulkUpdate(targets.map(alert => alert.id), action === 'acknowledge' ? 'ACKNOWLEDGE' : 'RESOLVE')
    selectedAlerts.value = new Set()
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '批量操作失败'
  } finally {
    bulkUpdating.value = false
  }
}

async function bulkAssign() {
  const ids = [...selectedAlerts.value]
  if (!ids.length || !bulkOwner.value) return
  bulkUpdating.value = true
  try {
    await api.bulkAssign(ids, bulkOwner.value)
    selectedAlerts.value = new Set()
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '批量分派失败'
  } finally {
    bulkUpdating.value = false
  }
}

async function dispatchSelected() {
  const ids = [...selectedAlerts.value]
  if (!ids.length || !bulkOwner.value) return
  bulkUpdating.value = true
  try {
    const preview = await api.previewDispatch(ids, bulkOwner.value, 'ACKNOWLEDGE')
    if (preview.requiresConfirmation && !window.confirm(
      `将处置 ${preview.count} 条告警，覆盖 ${preview.serviceCount} 个服务（${preview.services.join('、')}），其中严重 ${preview.criticalCount} 条；分派 ${preview.assignedCount} 条，状态推进 ${preview.statusChangedCount} 条。确认继续吗？`
    )) return
    const result = await api.dispatch(ids, bulkOwner.value, 'ACKNOWLEDGE')
    const before = Object.entries(result.statusBeforeCounts).map(([status, count]) => `${status} ${count}`).join(' / ')
    operationMessage.value = `${result.handoffSummary} · 交接 ${result.handoffReadiness} · 复核 ${result.reviewPriority} · 清单 ${result.handoffStepCount} 步 / 预计 ${result.estimatedHandoffMinutes} 分钟：${result.handoffChecklist.join(' → ')} · 原状态 ${before} · 严重 ${result.criticalCount} 条 / 警告 ${result.warningCount} 条 · 实际更新 ${result.changedCount} 条，跳过 ${result.unchangedCount} 条`
    selectedAlerts.value = new Set()
    await loadData()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '统一处置失败'
  } finally {
    bulkUpdating.value = false
  }
}

function percent(value: number) { return `${(value * 100).toFixed(2)}%` }
function time(value: string) { return new Date(value).toLocaleString('zh-CN', { hour12: false }) }

onMounted(async () => {
  let storedPage = ''
  try { storedPage = localStorage.getItem('internet-operations-page') || '' } catch (_) {}
  const initial = (location.hash.slice(1) || storedPage) as WorkspacePage
  if (initial in pageCopy) activePage.value = initial
  mounted = true
  document.addEventListener('visibilitychange', handleVisibilityChange)
  await loadData()
  connect()
})
onBeforeUnmount(() => {
  disposed = true
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  socket?.close()
  if (reconnectTimer) window.clearTimeout(reconnectTimer)
  if (realtimeRefreshTimer) window.clearTimeout(realtimeRefreshTimer)
})
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <div class="app-shell" :class="{ 'is-updating': loading }" :data-page="activePage" :aria-busy="loading">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark" aria-hidden="true"><i></i><i></i><i></i></span><div><strong>Signal Desk</strong><small>网络运营中心</small></div></div>
      <p class="sidebar-context">聚焦服务健康、流量变化与告警处置，把值班动作放在同一条工作路径中。</p>
      <nav aria-label="主导航">
        <a :class="['nav-item', { active: activePage === 'overview' }]" href="#overview" :aria-current="activePage === 'overview' ? 'page' : undefined" @click.prevent="activatePage('overview')" @keydown="movePageFocus($event, 0)"><span>01</span>运营总览</a>
        <a :class="['nav-item', { active: activePage === 'traffic' }]" href="#traffic" :aria-current="activePage === 'traffic' ? 'page' : undefined" @click.prevent="activatePage('traffic')" @keydown="movePageFocus($event, 1)"><span>02</span>流量监控</a>
        <a :class="['nav-item', { active: activePage === 'incidents' }]" href="#incidents" :aria-current="activePage === 'incidents' ? 'page' : undefined" @click.prevent="activatePage('incidents')" @keydown="movePageFocus($event, 2)"><span>03</span>事故中心</a>
        <a :class="['nav-item', { active: activePage === 'alerts' }]" href="#alerts" :aria-current="activePage === 'alerts' ? 'page' : undefined" @click.prevent="activatePage('alerts')" @keydown="movePageFocus($event, 3)"><span>04</span>告警明细</a>
      </nav>
      <div class="sidebar-note">
        <span :class="['status-dot', { online: connected }]" />
        {{ connected ? '实时通道已连接' : '正在重新连接' }}
      </div>
    </aside>

    <main id="main-content" tabindex="-1">
      <header class="topbar" id="overview">
        <div><p class="eyebrow">NETWORK OPERATIONS CENTER</p><h1>{{ activePageCopy.title }}</h1><p class="topbar-description">{{ activePageCopy.description }}</p></div>
        <div class="topbar-actions"><label class="time-window"><span>观察窗口</span><select v-model.number="timeWindowMinutes" aria-label="选择实时观察窗口" @change="changeTimeWindow"><option :value="15">近 15 分钟</option><option :value="60">近 1 小时</option><option :value="180">近 3 小时</option><option :value="1440">近 24 小时</option></select></label><div class="update-time"><span>数据更新时间</span><strong>{{ updatedLabel }}</strong></div></div>
      </header>

      <section v-show="activePage === 'overview'" class="command-strip page-view" aria-label="值班状态摘要">
        <article><span :class="['signal-light', { online: connected }]" aria-hidden="true" /><div><small>实时通道</small><strong>{{ connected ? '在线' : '重连中' }}</strong></div></article>
        <article><span class="command-code">SVC</span><div><small>活跃服务</small><strong>{{ summary.activeServices }}</strong></div></article>
        <article><span class="command-code critical">P1</span><div><small>严重告警</small><strong>{{ alertSummary.activeCritical }}</strong></div></article>
        <article><span class="command-code window">WIN</span><div><small>当前窗口</small><strong>{{ timeWindowLabel }}</strong></div></article>
      </section>

      <p v-if="errorMessage" class="error-banner" role="alert">{{ errorMessage }} <button @click="loadData">重新加载</button></p>

      <section v-show="activePage === 'overview'" class="panel metric-entry page-view" aria-labelledby="metric-entry-title">
        <div class="panel-heading"><div><p class="eyebrow">LIVE INPUT</p><h2 id="metric-entry-title">提交实时流量样本</h2></div><span class="period">提交后同步至所有在线页面</span></div>
        <form @submit.prevent="submitMetric">
          <label><span>服务名称</span><input v-model.trim="metricForm.serviceName" maxlength="80" required></label>
          <label><span>请求量</span><input v-model.number="metricForm.requestCount" type="number" min="0" required></label>
          <label><span>错误量</span><input v-model.number="metricForm.errorCount" type="number" min="0" :max="metricForm.requestCount" required></label>
          <label><span>P95 延迟（ms）</span><input v-model.number="metricForm.p95LatencyMs" type="number" min="0" required></label>
          <button class="primary" type="submit" :disabled="submittingMetric">{{ submittingMetric ? '正在提交' : '发送实时指标' }}</button>
        </form>
      </section>

      <section v-show="activePage === 'overview'" class="kpi-grid page-view" aria-label="关键运营指标">
        <article class="kpi"><p>{{ summaryScopeLabel }}请求量</p><strong>{{ requestLabel }}</strong><span>累计访问请求</span></article>
        <article class="kpi"><p>错误率</p><strong :class="{ danger: summary.errorRate >= .05 }">{{ percent(summary.errorRate) }}</strong><span>错误 {{ summary.totalErrors.toLocaleString() }} 次</span></article>
        <article class="kpi"><p>平均 P95 延迟</p><strong>{{ summary.averageP95LatencyMs.toFixed(0) }}<i> ms</i></strong><span>覆盖 {{ summary.activeServices }} 个服务</span></article>
        <article class="kpi"><p>待处理告警</p><strong class="warning">{{ summary.activeAlerts }}</strong><span>需要运营跟进</span></article>
      </section>

      <section v-show="activePage === 'traffic'" class="content-grid page-view" id="traffic">
        <article class="panel trend-panel">
          <div class="panel-heading"><div><p class="eyebrow">TRAFFIC TREND</p><h2>请求量与响应延迟</h2></div><div class="trend-scope"><span class="period">{{ trendScopeLabel }}</span><button v-if="serviceFilter" type="button" @click="clearServiceFocus">查看全部服务</button></div></div>
          <TrendChart :metrics="metrics" />
        </article>

        <article class="panel service-panel">
          <div class="panel-heading"><div><p class="eyebrow">SERVICE HEALTH</p><h2>服务实时状态</h2></div></div>
          <div v-if="loading" class="empty">正在加载服务指标</div>
          <div v-else class="service-list">
            <div v-for="service in services" :key="service.serviceName" :class="['service-row', { active: serviceFilter === service.serviceName }]">
              <div><strong>{{ service.serviceName }}</strong><span>{{ time(service.latestCollectedAt) }}</span></div>
              <div class="service-stat"><span>错误率</span><b :class="{ danger: service.status === 'DEGRADED' }">{{ percent(service.errorRate) }}</b></div>
              <div class="service-stat"><span>平均 P95</span><b>{{ service.averageP95LatencyMs.toFixed(0) }}ms</b></div><button class="service-focus" @click="focusService(service.serviceName)">{{ serviceFilter === service.serviceName ? '取消聚焦' : '查看趋势' }}</button>
            </div>
          </div>
        </article>
      </section>

      <section v-show="activePage === 'incidents'" class="panel incident-panel page-view" id="incidents">
        <div class="panel-heading"><div><p class="eyebrow">INCIDENT CENTER</p><h2>服务事故中心</h2></div><span class="count">待接手 {{ incidentSummary.open }} · 处理中 {{ incidentSummary.investigating }} · 严重 {{ incidentSummary.activeCritical }}</span></div>
        <p class="incident-intro">同一服务的关联告警自动归并为一条事故，值班人员可先接手定位，再在恢复后关闭事故。</p>
        <div v-if="incidents.length === 0" class="empty">当前没有进行中的服务事故</div>
        <div v-else class="incident-grid">
          <article v-for="incident in incidents" :key="incident.id" :class="['incident-card', incident.severity.toLowerCase()]">
            <div class="incident-card-head"><span>INC-{{ String(incident.id).padStart(4, '0') }}</span><span :class="['severity', incident.severity.toLowerCase()]">{{ incident.severity === 'CRITICAL' ? '严重' : '警告' }}</span></div>
            <h3>{{ incident.title }}</h3>
            <p>{{ incident.alertCount }} 条关联告警 · 最近更新 {{ time(incident.lastAlertAt) }}</p>
            <div class="incident-meta"><strong>{{ incident.status === 'OPEN' ? '待接手' : '处理中' }}</strong><select :value="incident.owner || ''" aria-label="事故负责人" @change="assignIncident(incident, $event)"><option value="" disabled>选择负责人</option><option value="platform-oncall">平台值班</option><option value="data-oncall">数据值班</option><option value="service-owner">服务负责人</option></select></div>
            <div class="incident-actions"><button v-if="incident.status === 'OPEN'" class="primary" @click="updateIncident(incident, 'INVESTIGATING')">接手处理</button><button @click="updateIncident(incident, 'RESOLVED')">确认恢复</button></div>
          </article>
        </div>
      </section>

      <section v-show="activePage === 'alerts'" class="panel alert-panel page-view" id="alerts">
        <div class="panel-heading"><div><p class="eyebrow">INCIDENT QUEUE</p><h2>告警处置队列</h2></div><div class="alert-controls"><select v-model="serviceFilter" aria-label="按服务筛选" @change="changeServiceFilter"><option value="">全部服务</option><option v-for="service in services" :key="service.serviceName" :value="service.serviceName">{{ service.serviceName }}</option></select><select v-model="severityFilter" aria-label="按告警级别筛选" @change="loadData"><option value="">全部级别</option><option value="CRITICAL">严重</option><option value="WARNING">警告</option></select><select v-model="ownerFilter" aria-label="按负责人筛选" @change="loadData"><option value="">全部负责人</option><option v-for="item in alertWorkloads.filter(item => item.owner !== '未分派')" :key="item.owner" :value="item.owner">{{ item.owner }}</option></select><span class="count">待确认 {{ alertSummary.open }} · 处理中 {{ alertSummary.acknowledged }} · 严重 {{ alertSummary.activeCritical }}</span></div></div>
        <div class="bulk-actions"><span>已选择 {{ selectedAlerts.size }} 条告警</span><small v-if="operationMessage" role="status">{{ operationMessage }}</small><select v-model="bulkOwner" aria-label="批量分派负责人"><option value="platform-oncall">平台值班</option><option value="data-oncall">数据值班</option><option value="service-owner">服务负责人</option></select><button class="primary" :disabled="!selectedAlerts.size || bulkUpdating" @click="dispatchSelected">分派并确认</button><button :disabled="!selectedAlerts.size || bulkUpdating" @click="bulkAssign">仅分派</button><button :disabled="!selectedAlerts.size || bulkUpdating" @click="bulkUpdate('acknowledge')">仅确认</button><button :disabled="!selectedAlerts.size || bulkUpdating" @click="bulkUpdate('resolve')">批量解决</button></div>
        <div class="workload-strip" aria-label="告警责任负载"><article v-for="item in alertWorkloads" :key="item.owner"><span>{{ item.owner }}</span><strong>{{ item.total }}</strong><small>待确认 {{ item.open }} · 严重 {{ item.critical }}</small></article></div>
        <div v-if="alerts.length === 0" class="empty">当前没有待处理告警</div>
        <div v-else class="alert-table-wrap">
          <table>
            <thead><tr><th><input type="checkbox" aria-label="选择全部告警" :checked="alerts.length > 0 && selectedAlerts.size === alerts.length" @change="toggleAll(($event.target as HTMLInputElement).checked)"></th><th>级别</th><th>服务</th><th>告警内容</th><th>发生时间</th><th>负责人</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="alert in alerts" :key="alert.id">
                <td><input type="checkbox" :checked="selectedAlerts.has(alert.id)" :aria-label="`选择 ${alert.serviceName} 告警`" @change="toggleAlert(alert.id, ($event.target as HTMLInputElement).checked)"></td>
                <td><span :class="['severity', alert.severity.toLowerCase()]">{{ alert.severity === 'CRITICAL' ? '严重' : '警告' }}</span></td>
                <td><strong>{{ alert.serviceName }}</strong></td>
                <td>{{ alert.message }}</td>
                <td>{{ time(alert.createdAt) }}</td>
                <td><select class="owner-select" :value="alert.owner || ''" aria-label="分派告警负责人" @change="assignAlert(alert, $event)"><option value="" disabled>待分派</option><option value="platform-oncall">平台值班</option><option value="data-oncall">数据值班</option><option value="service-owner">服务负责人</option></select></td>
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

