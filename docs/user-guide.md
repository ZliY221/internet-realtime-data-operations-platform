# 用户使用指南

适用版本：`v0.5.4`

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

## 4. 运营看板

- 运营总览：近 60 分钟请求量、错误率、P95 延迟和活动告警。
- 流量监控：请求量与响应延迟趋势。
- 服务状态：按异常优先级展示各服务聚合结果。
- 告警中心：展示待确认、处理中、严重告警摘要与处置队列。
- 告警级别筛选：可切换全部、严重和警告，只查看当前需要处理的对应队列。
- 告警责任人：在处置队列中选择平台值班、数据值班或服务负责人，分派结果会实时同步到其他页面。
- 责任负载：按负责人汇总活动告警、待确认和严重告警；未分派告警单独展示，并可按负责人和级别组合筛选队列。

## 5. 告警处置

- `GET /api/v1/alerts/summary`：查看生命周期和严重级别汇总。
- `GET /api/v1/alerts/workloads`：查看活动告警的负责人负载。
- `GET /api/v1/alerts?status=OPEN&severity=CRITICAL&owner=data-oncall`：按状态、级别和负责人查询告警。
- `POST /api/v1/alerts/{id}/acknowledge`：确认告警并进入处理中。
- `POST /api/v1/alerts/{id}/resolve`：解决告警。
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
