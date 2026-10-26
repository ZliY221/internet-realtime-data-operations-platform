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
- `POST /api/v1/alerts/dispatch`：组合执行分派与状态推进，回执包含执行结论、风险范围、复核要求、下一步动作、交接清单和交接摘要。
- `POST /api/v1/alerts/dispatch`：统一完成分派与状态推进，返回 `receiptId`、`executedAt`、服务范围及实际更新/跳过数量。

## 实时通道

- `GET ws://localhost:8080/ws/metrics`：接收 `metric.created`、`alert.created`、`alert.updated` 事件。

# 事故中心

- `GET /api/v1/incidents?status=OPEN`：查询指定状态的事故。
- `GET /api/v1/incidents/summary`：查询事故状态与严重度汇总。
- `PATCH /api/v1/incidents/{id}`：分派负责人并推进到 `INVESTIGATING` 或 `RESOLVED`。

# v3.2 协同回滚计划

`POST /api/v3/incidents/coordinated-rollback-plan` 接收服务名称和回滚工件就绪状态，返回依赖感知的回滚步骤、每步验证门禁和阻断原因。接口只生成计划，不自动执行回滚。

# v3.3 事故关联变更排序

`POST /api/v3/incidents/{incidentService}/change-correlation` 接收候选服务变更，返回服务关系、时间和错误率增幅构成的关联分数及排查顺序。接口不自动触发回滚。

# v3.4 故障服务依赖边界隔离

`POST /api/v3/incidents/{failedService}/isolation-plan` 接收已完成降级准备的上游服务名称数组，返回受影响的直接上游、需要监控的下游依赖、隔离动作和验证门禁。接口只生成处置计划，不自动切流或停止服务调用。

# v3.5 值班操作旅程演练

`POST /api/v3/incidents/operator-rehearsal` 接收恰好 100 条值班操作旅程，按指标、告警确认、负责人、依赖边界和恢复门禁返回逐条首要动作与操作步骤。响应汇总动作分布、平均步骤和未解决场景；接口不自动改变事故或服务状态。

