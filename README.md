# Stocket

Stocket 是面向单个家庭、多位成员的自托管家庭资产与日常物品管理系统。项目采用 Spring Boot 模块化单体后端、Vue 3 前端、PostgreSQL，并支持 GraalVM Native Image 与 Docker Compose 部署。

## 环境要求

- JDK 21
- Node.js 24 与 npm
- Docker Engine 或兼容的容器 daemon，以及 Docker Compose
- GraalVM 21（仅 `native-test` 与本地原生构建需要）

## 验证命令

```bash
make test
make build
make aot
make compose-config
make native-test
```

也可以分别运行 `make backend-test` 和 `make frontend-test`。Make 的前端目标会检查 `frontend/node_modules/.package-lock.json`；依赖尚未安装或 `package.json`、`package-lock.json` 更新时，会自动运行 `npm ci`，不会在每次执行时重复安装。

当前开发机未安装 Docker，因此依赖 Testcontainers 的完整 `backend-test`、`build` 和 `native-test` 无法在本机完成，`compose-config` 也无法执行。当前本机 GraalVM 为 25，而 `native-test` 的基准环境是 GraalVM 21。原生 AOT 测试集合不包含标注 `@DisabledInAotMode` 的 `DatabaseMigrationTest` 和 `ArchitectureTest`，这两项由 CI 的 JVM job 覆盖；CI 还会实际构建并启动 Native Compose 栈，通过网关对系统 API 和 readiness 端点执行 HTTP smoke。CI 会在具备 Docker 和 GraalVM 21 的环境中运行全部检查。不要把本机受环境阻塞的结果视为全部验证通过。

## 本地启动后端

先准备宿主机可访问的 PostgreSQL 17。默认配置连接 `localhost:5432`，需要创建 database `stocket`、user `stocket`，并将密码设置为 `stocket-local-dev`。也可以通过 `STOCKET_DB_URL`、`STOCKET_DB_USER` 和 `STOCKET_DB_PASSWORD` 覆盖连接配置。

然后使用 `local` profile 启动后端：

```bash
cd backend
STOCKET_DB_PASSWORD=stocket-local-dev ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

后端默认监听 `http://localhost:8080`。

## 本地启动前端

```bash
cd frontend
npm ci
# 首次开发、需要更新依赖锁时也可以使用 npm install
npm run dev
```

Vite 开发服务器会把 `/api` 请求代理到 `http://localhost:8080`。

## 原生 Compose 部署

```bash
cp .env.example .env
docker compose --env-file .env -f deploy/compose.yml up --build
```

启动完成后，通过 `.env` 中的 `STOCKET_PORT` 访问 Stocket，默认地址为 `http://localhost:8088`。

## 文档

- [产品与技术设计规格](docs/superpowers/specs/2026-07-10-stocket-design.md)
- [交付路线图](docs/superpowers/plans/2026-07-11-delivery-roadmap.md)
- [基础与原生构建实施说明](docs/superpowers/plans/2026-07-11-foundation-native-baseline.md)
