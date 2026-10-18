# 用户使用指南

适用版本：`v2.0.0`

本指南随项目版本同步更新，说明当前版本的本地运行、指标接入、实时看板和告警处置方法。首次使用建议先阅读仓库首页的项目简介、系统架构和常用入口，再按本指南启动前后端服务。

## 1. 环境要求

- JDK 21
- Maven 3.9 或更高版本
- Node.js 22 或更高版本
- 完整容器部署需要 Docker Compose

本地 H2 和容器 PostgreSQL 环境独立维护。运行 `./scripts/project-doctor.ps1 -Profile Local` 或 `-Profile Container` 可检查对应环境，详细选择说明见[运行环境分层](runtime-environments.md)。

## 2. 本地启动

后端使用 H2 文件数据库启动：

```powershell
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

另开终端启动前端：

```powershell
cd frontend
npm ci
npm run dev
```

访问前端输出的本地地址。后端默认监听 `http://127.0.0.1:8080`，健康检查为 `/actuator/health`。

## 3. 接入流量指标

向 `POST /api/v1/metrics` 提交服务分钟指标：

```json
{
  "serviceName": "checkout",
  "requestCount": 2000,
  "errorCount": 40,
  "p95LatencyMs": 480,
  "collectedAt": "2026-10-13T05:00:00Z"
}
```

后端保存指标、执行错误率与延迟规则，并通过 `/ws/metrics` 推送指标和告警变化。看板收到事件后自动刷新趋势、服务状态和告警队列。

也可以直接在看板的“提交实时流量样本”区域填写服务名称、请求量、错误量和 P95 延迟。提交时间由浏览器生成，后端校验成功后会将新指标及可能产生的告警实时推送给所有在线页面。

## 4. 运营看板

v2.0.0 采用网络运营中心版型：深色侧栏用于稳定导航，浅色内容区保持长时间值班阅读舒适度，深色趋势面板与图表配色保持一致。页面顶部值班状态条集中显示实时通道、活跃服务、严重告警和观察窗口；蓝、青、橙、红分别表达流量、在线状态、延迟提示和严重告警，状态同时带有文字标签。

- 运营总览：展示选定观察窗口内的请求量、错误率和 P95 延迟；活动告警保持为当前未解决库存，不受历史窗口影响。
- 观察窗口：支持近 15 分钟、1 小时、3 小时和 24 小时，切换后统一刷新顶部 KPI、趋势数据和服务健康聚合。
- 流量监控：请求量与响应延迟趋势；后端按观察窗口过滤后再限制采样点数量，避免旧数据混入实时视图。
- 服务状态：按异常优先级展示当前观察窗口内的服务聚合结果。
- 告警中心：展示待确认、处理中、严重告警摘要与处置队列。
- 事故中心：同一服务的活动告警自动归并为一条事故卡片，展示严重度、关联告警数、负责人和最近更新时间。先选择负责人并“接手处理”，服务恢复后点击“确认恢复”；状态变化通过 WebSocket 同步到全部在线页面。
- 告警组合筛选：可按服务、严重级别和负责人组合过滤，实时刷新后仍保持当前筛选上下文。
- 单服务视图：在服务健康列表点击“查看趋势”，趋势图、告警队列和顶部 KPI 会同时切换到该服务，并高亮当前服务。KPI 中的请求量、错误率、平均 P95 延迟、活动告警和活跃服务数均使用同一服务口径。服务名称会去除首尾空格；查询不存在的服务时返回全零摘要，超出 80 个字符则拒绝请求。再次点击“取消聚焦”、点击趋势区的“查看全部服务”或在告警筛选中选择“全部服务”，均可恢复全局视图；切换范围时会清空已经勾选的告警，避免跨服务误操作。
- 告警责任人：在处置队列中选择平台值班、数据值班或服务负责人，分派结果会实时同步到其他页面。
- 责任负载：按负责人汇总活动告警、待确认和严重告警；未分派告警单独展示，并可按负责人和级别组合筛选队列。
- 批量处置：勾选多条告警后可批量确认或批量解决。前端只发起一次批量请求，后端会在同一事务内校验并更新最多 100 条告警，再通过 WebSocket 通知所有在线页面。
- 批量分派：选择目标值班团队后一次分派最多 100 条告警。任意告警不存在或已解决时整批回滚，成功后发送一次实时广播。
- 统一处置：选择负责人并点击“分派并确认”，后端在同一事务中同时分派和确认告警，仅广播一次组合结果。返回结果分别列出分派数量与状态变化数量。
- 执行预览：统一处置会先读取严重/警告数量、实际分派数、状态变化数和涉及的服务清单。包含严重告警或执行解决动作时，工作台要求二次确认；取消不会修改数据。
- 幂等回执：执行结果显示实际更新和跳过数量，并生成 `DSP-` 开头的短回执编号及执行时间。`outcome` 用 `COMPLETE`、`PARTIAL` 和 `NO_CHANGE` 区分完整更新、部分更新与无变化。重复对同一负责人、同一状态执行时计入跳过；整批无变化时后端不会写库或发送 WebSocket 广播，但仍会返回本次请求的回执编号。回执同时显示严重与警告数量以及操作前各状态数量。实际更新后，严重告警标记 P0 并要求 1 小时内复核，其他告警标记 P1 并要求 4 小时内复核；无变化标记 `NONE`。`handoffChecklist` 会按顺序给出回执核对、负责人联系、建议执行和结果登记步骤，`handoffStepCount` 与 `estimatedHandoffMinutes` 用于评估本次交接规模。`handoffReadiness` 区分需升级、需验证、选择复核与可监控状态，`handoffBlockers` 给出当前阻塞原因。

## 5. 告警处置

事故中心接口：

- `GET /api/v1/incidents?status=OPEN`：查询待接手事故，状态也可使用 `INVESTIGATING` 或 `RESOLVED`。
- `GET /api/v1/incidents/summary`：查看待接手、处理中、已恢复及活动严重事故数量。
- `PATCH /api/v1/incidents/{id}`：提交负责人和目标状态，例如 `{ "owner": "platform-oncall", "status": "INVESTIGATING" }`；确认恢复使用 `RESOLVED`。

告警明细接口：

- `GET /api/v1/alerts/summary`：查看生命周期和严重级别汇总。
- `GET /api/v1/alerts/workloads`：查看活动告警的负责人负载。
- `GET /api/v1/alerts?status=OPEN&severity=CRITICAL&owner=data-oncall&serviceName=checkout`：按状态、级别、负责人和服务名称查询告警。
- `POST /api/v1/alerts/{id}/acknowledge`：确认告警并进入处理中。
- `POST /api/v1/alerts/{id}/resolve`：解决告警。
- `POST /api/v1/alerts/bulk`：批量确认或解决告警，请求包含 `ids` 与 `ACKNOWLEDGE`/`RESOLVE` 动作。
- `PATCH /api/v1/alerts/bulk-owner`：批量分派负责人，请求包含 `ids` 与 `owner`。
- `POST /api/v1/alerts/dispatch`：统一执行负责人分派与 `ACKNOWLEDGE`/`RESOLVE` 状态动作。
- `POST /api/v1/alerts/dispatch/preview`：只读预览统一处置影响范围。
- `PATCH /api/v1/alerts/{id}/owner`：提交 `{ "owner": "platform-oncall" }` 分派负责人。

状态变化会实时广播到已连接的看板。

## 6. 容器部署

```powershell
docker compose up --build
```

该方式启动 PostgreSQL、Spring Boot 后端和前端服务。数据库表由 Flyway 管理，连接信息通过 Compose 环境变量注入。

容器按 PostgreSQL、后端、前端的健康状态顺序启动。需要完整检查时执行 `./scripts/container-smoke.ps1`。

## 7. 工程验证

```powershell
./scripts/verify.ps1
```

统一入口在 Windows PowerShell 5 和 PowerShell 7 下均可使用，执行后端单元与接口闭环检查、Flyway 迁移检查、可执行 JAR 构建、前端依赖锁定安装和生产构建。

## 8. 常见问题

- 后端无法启动：检查 JDK 是否为 21，以及 8080 端口是否占用。
- 页面无实时更新：确认浏览器能连接 `/ws/metrics`，反向代理需要允许 WebSocket Upgrade。
- 本地数据需要重置：停止进程后移走 `backend/data/`；该目录仅保存本地 H2 数据。
- PostgreSQL 连接失败：检查容器健康状态与数据库环境变量。

## 9. 数据边界

内置指标模拟器仅用于本地展示。生产接入时应关闭模拟器，并通过受控服务写入聚合指标；不要在指标或告警正文中提交个人信息和访问凭据。
