<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import TrendChart from './components/TrendChart.vue'
import { api } from './services/api'
import type { Alert, AlertSummary, AlertWorkload, Metric, RealtimeEvent, ServiceHealth, Summary } from './types'

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
let socket: WebSocket | undefined
let reconnectTimer: number | undefined

const requestLabel = computed(() => new Intl.NumberFormat('zh-CN', { notation: 'compact' }).format(summary.value.totalRequests))
const updatedLabel = computed(() => lastUpdated.value
  ? lastUpdated.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  : '--:--:--')
const timeWindowLabel = computed(() => ({ 15: '近 15 分钟', 60: '近 1 小时', 180: '近 3 小时', 1440: '近 24 小时' }[timeWindowMinutes.value] || `近 ${timeWindowMinutes.value} 分钟`))
const trendScopeLabel = computed(() => serviceFilter.value ? `${serviceFilter.value} · ${timeWindowLabel.value}` : `全部服务 · ${timeWindowLabel.value}`)
const summaryScopeLabel = computed(() => serviceFilter.value ? `${serviceFilter.value} · ${timeWindowLabel.value}` : timeWindowLabel.value)

async function loadData() {
  try {
    const [summaryData, metricData, serviceData, alertSummaryData, workloadData, openAlerts, acknowledgedAlerts] = await Promise.all([
      api.summary(serviceFilter.value, timeWindowMinutes.value), api.recentMetrics(serviceFilter.value, timeWindowMinutes.value), api.serviceHealth(timeWindowMinutes.value), api.alertSummary(), api.alertWorkloads(), api.alerts('OPEN', severityFilter.value, ownerFilter.value, serviceFilter.value), api.alerts('ACKNOWLEDGED', severityFilter.value, ownerFilter.value, serviceFilter.value),
    ])
    summary.value = summaryData
    metrics.value = metricData
    services.value = serviceData
    alertSummary.value = alertSummaryData
    alertWorkloads.value = workloadData
    alerts.value = [...openAlerts, ...acknowledgedAlerts]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    selectedAlerts.value = new Set([...selectedAlerts.value].filter(id => alerts.value.some(alert => alert.id === id)))
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

async function focusService(serviceName: string) {
  serviceFilter.value = serviceFilter.value === serviceName ? '' : serviceName
  selectedAlerts.value = new Set()
  await loadData()
  document.querySelector('#traffic')?.scrollIntoView({ behavior: 'smooth' })
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
      `将处置 ${preview.count} 条告警，其中严重 ${preview.criticalCount} 条；分派 ${preview.assignedCount} 条，状态推进 ${preview.statusChangedCount} 条。确认继续吗？`
    )) return
    const result = await api.dispatch(ids, bulkOwner.value, 'ACKNOWLEDGE')
    operationMessage.value = `实际更新 ${result.changedCount} 条，跳过 ${result.unchangedCount} 条`
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
        <div class="topbar-actions"><label class="time-window"><span>观察窗口</span><select v-model.number="timeWindowMinutes" aria-label="选择实时观察窗口" @change="changeTimeWindow"><option :value="15">近 15 分钟</option><option :value="60">近 1 小时</option><option :value="180">近 3 小时</option><option :value="1440">近 24 小时</option></select></label><div class="update-time"><span>数据更新时间</span><strong>{{ updatedLabel }}</strong></div></div>
      </header>

      <p v-if="errorMessage" class="error-banner">{{ errorMessage }} <button @click="loadData">重新加载</button></p>

      <section class="panel metric-entry" aria-labelledby="metric-entry-title">
        <div class="panel-heading"><div><p class="eyebrow">LIVE INPUT</p><h2 id="metric-entry-title">提交实时流量样本</h2></div><span class="period">提交后同步至所有在线页面</span></div>
        <form @submit.prevent="submitMetric">
          <label><span>服务名称</span><input v-model.trim="metricForm.serviceName" maxlength="80" required></label>
          <label><span>请求量</span><input v-model.number="metricForm.requestCount" type="number" min="0" required></label>
          <label><span>错误量</span><input v-model.number="metricForm.errorCount" type="number" min="0" :max="metricForm.requestCount" required></label>
          <label><span>P95 延迟（ms）</span><input v-model.number="metricForm.p95LatencyMs" type="number" min="0" required></label>
          <button class="primary" type="submit" :disabled="submittingMetric">{{ submittingMetric ? '正在提交' : '发送实时指标' }}</button>
        </form>
      </section>

      <section class="kpi-grid" aria-label="关键运营指标">
        <article class="kpi"><p>{{ summaryScopeLabel }}请求量</p><strong>{{ requestLabel }}</strong><span>累计访问请求</span></article>
        <article class="kpi"><p>错误率</p><strong :class="{ danger: summary.errorRate >= .05 }">{{ percent(summary.errorRate) }}</strong><span>错误 {{ summary.totalErrors.toLocaleString() }} 次</span></article>
        <article class="kpi"><p>平均 P95 延迟</p><strong>{{ summary.averageP95LatencyMs.toFixed(0) }}<i> ms</i></strong><span>覆盖 {{ summary.activeServices }} 个服务</span></article>
        <article class="kpi"><p>待处理告警</p><strong class="warning">{{ summary.activeAlerts }}</strong><span>需要运营跟进</span></article>
      </section>

      <section class="content-grid" id="traffic">
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

      <section class="panel alert-panel" id="alerts">
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

