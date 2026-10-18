# 接口说明

## 指标

- `POST /api/v1/metrics`：写入一条服务分钟指标。
- `GET /api/v1/metrics/recent?limit=60`：查询最近指标。

写入示例：

```json
{
  "serviceName": "order-service",
  "requestCount": 12500,
  "errorCount": 186,
  "p95LatencyMs": 420,
  "collectedAt": "2026-10-13T10:30:00Z"
}
```

## 运营摘要

- `GET /api/v1/dashboard/summary?minutes=60&serviceName=checkout`：查询指定时间窗口内的请求量、错误率、平均 P95 延迟与未解决告警数；`serviceName` 可选，用于单服务 KPI 口径。
- `GET /api/v1/dashboard/services?minutes=60`：按服务聚合请求量、错误率、平均 P95 延迟和健康状态，异常服务优先返回。

## 告警

- `GET /api/v1/alerts?status=OPEN&serviceName=checkout`：按状态查询告警，并可叠加 `severity`、`owner` 和 `serviceName` 条件。
- `GET /api/v1/metrics/recent?limit=80&serviceName=checkout`：读取指定服务的最近指标，用于单服务趋势。
- `POST /api/v1/alerts/{id}/acknowledge`：确认告警。
- `POST /api/v1/alerts/{id}/resolve`：解决告警。
- `POST /api/v1/alerts/dispatch/preview`：只读预览统一处置的告警、服务和严重级别影响范围。
- `POST /api/v1/alerts/dispatch`：组合执行分派与状态推进，回执包含严重/警告数量、服务范围和执行编号。
- `POST /api/v1/alerts/dispatch`：统一完成分派与状态推进，返回 `receiptId`、`executedAt`、服务范围及实际更新/跳过数量。

## 实时通道

- `GET ws://localhost:8080/ws/metrics`：接收 `metric.created`、`alert.created`、`alert.updated` 事件。

