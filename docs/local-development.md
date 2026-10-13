# 本地开发

## 环境要求

- Java 21 或更高版本
- Maven 3.6.3 或更高版本
- Node.js 20 或更高版本
- npm 10 或更高版本

## 后端

```powershell
./scripts/start-backend.ps1
```

脚本优先查找本机 Java 21+，使用 `local` Profile 启动后端。数据库文件位于项目根目录的 `data/`，该目录不会提交到仓库。

## 前端

```powershell
./scripts/start-frontend.ps1
```

Vite 将 `/api`、`/actuator` 和 `/ws` 转发到 `http://localhost:8080`。

## 工程校验

```powershell
./scripts/verify.ps1
```

该入口依次执行后端单元测试和前端生产构建。

