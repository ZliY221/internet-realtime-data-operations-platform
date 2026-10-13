<script setup lang="ts">
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use, type EChartsType } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Metric } from '../types'

const props = defineProps<{ metrics: Metric[] }>()
const chartElement = ref<HTMLDivElement>()
use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
let chart: EChartsType | undefined

const seriesData = computed(() => [...props.metrics].reverse().slice(-36))

function render() {
  if (!chart) return
  const data = seriesData.value
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', backgroundColor: '#10263d', borderColor: '#2a4966', textStyle: { color: '#f4f8fb' } },
    legend: { data: ['请求量', 'P95 延迟'], textStyle: { color: '#91a6b9' }, top: 2 },
    grid: { left: 50, right: 56, top: 42, bottom: 32 },
    xAxis: {
      type: 'category',
      data: data.map(item => new Date(item.collectedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })),
      axisLine: { lineStyle: { color: '#34506a' } },
      axisLabel: { color: '#7690a5' },
    },
    yAxis: [
      { type: 'value', axisLabel: { color: '#7690a5' }, splitLine: { lineStyle: { color: '#1b344c' } } },
      { type: 'value', axisLabel: { color: '#7690a5', formatter: '{value}ms' }, splitLine: { show: false } },
    ],
    series: [
      {
        name: '请求量', type: 'line', smooth: true, showSymbol: false,
        data: data.map(item => item.requestCount),
        lineStyle: { color: '#31d6c4', width: 2 },
        areaStyle: { color: 'rgba(49,214,196,.12)' },
      },
      {
        name: 'P95 延迟', type: 'line', yAxisIndex: 1, smooth: true, showSymbol: false,
        data: data.map(item => item.p95LatencyMs), lineStyle: { color: '#ffb85c', width: 2 },
      },
    ],
  })
}

function resize() { chart?.resize() }
onMounted(() => {
  if (chartElement.value) chart = init(chartElement.value)
  render()
  window.addEventListener('resize', resize)
})
watch(seriesData, render, { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); chart?.dispose() })
</script>

<template><div ref="chartElement" class="chart" role="img" aria-label="请求量与响应延迟趋势图" /></template>

<style scoped>.chart { height: 310px; width: 100%; }</style>

