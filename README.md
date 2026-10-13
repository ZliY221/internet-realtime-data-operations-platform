# 互联网流量实时监控与数据运营平台

面向互联网业务流量监控场景的全栈数据应用。平台接收服务级分钟指标，完成聚合查询、阈值告警、状态流转与 WebSocket 实时推送，并通过运营看板呈现吞吐量、错误率、响应延迟和待处理告警。

当前版本：`v0.3.2`。完整操作见 [用户使用指南](docs/user-guide.md)。

## 核心能力

- 接收服务访问量、错误量与 P95 响应时间，持久化至 PostgreSQL。
- 根据错误率和响应延迟生成分级告警，支持确认与解决状态流转。
- 通过 WebSocket 推送新指标和告警变化，页面无需刷新即可更新。
- 提供 Vue 3 + ECharts 运营看板，展示关键指标、趋势、服务明细和告警队列。
- 提供服务维度健康排行，按时间窗口聚合请求量、错误率与平均 P95 延迟。
- 使用 Flyway 管理数据库结构，提供 H2 本地模式和 Docker Compose 部署方式。

## 技术栈

- 后端：Java 21、Spring Boot 4.1、Spring Web、Spring Data JPA、WebSocket
- 前端：Vue 3、TypeScript、Vite、ECharts
- 数据库：PostgreSQL、Flyway；本地模式使用 H2
- 工程化：Maven、npm、Docker Compose、GitHub Actions

## 快速启动

### 本地开发模式

需要 Java 21+、Maven 3.6.3+、Node.js 20+。

终端一：

```powershell
./scripts/start-backend.ps1
```

终端二：

```powershell
./scripts/start-frontend.ps1
```

访问 `http://localhost:5173`。本地模式使用文件型 H2 数据库，并自动生成演示指标。

### Docker Compose

```bash
docker compose up --build
```

访问 `http://localhost:8088`，后端健康检查地址为 `http://localhost:8080/actuator/health`。

## 工程结构

```text
backend/             Spring Boot API、告警规则、WebSocket 与数据库迁移
frontend/            Vue 运营看板
docs/                架构、接口与运行说明
scripts/             本地启动与工程校验入口
compose.yaml         PostgreSQL、后端与前端编排
VERSION_LOG.md       版本改动记录
```

## 文档

- [系统架构](docs/architecture.md)
- [用户使用指南](docs/user-guide.md)
- [接口说明](docs/api.md)
- [本地开发](docs/local-development.md)
- [版本日志](VERSION_LOG.md)

## License

MIT

