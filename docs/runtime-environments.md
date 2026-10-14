# 运行环境分层

项目提供本地开发与容器集成两套独立环境。

| 环境 | 适用场景 | 数据库 | 主要依赖 | 检查命令 |
| --- | --- | --- | --- | --- |
| Local | 前后端开发、接口调试 | H2 文件库 | JDK 21+、Maven、Node.js 20+ | `./scripts/project-doctor.ps1 -Profile Local` |
| Container | 完整部署与跨服务联调 | PostgreSQL 17 | Docker Compose | `./scripts/project-doctor.ps1 -Profile Container` |

本地脚本会从 `JAVA_HOME`、当前 PATH 和常见 JDK 安装目录选择可用的 JDK 21+，因此不受系统默认 Java 8 影响。容器环境按 PostgreSQL、后端、前端的健康状态顺序启动；后端镜像不绑定具体版本 JAR 文件名，升级版本时无需同步修改复制路径。

日常开发使用 Local；检查 Flyway、PostgreSQL、反向代理和 WebSocket 跨容器行为时使用 Container。`./scripts/container-smoke.ps1` 会构建完整环境并检查前端和后端健康入口。
