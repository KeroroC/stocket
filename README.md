# Stocket

Stocket 是面向单个家庭、多位成员的自托管家庭资产与日常物品管理系统。项目采用 Spring Boot 模块化单体后端、Vue 3 前端、PostgreSQL，并支持 GraalVM Native Image 与 Docker Compose 部署。

## 环境要求

- JDK 25
- Node.js 24 与 npm
- Docker Engine 或兼容的容器 daemon，以及 Docker Compose
- GraalVM 25（仅 `native-test` 与本地原生构建需要）

## 验证命令

```bash
make test
make build
make aot
make compose-config
make native-test
```

也可以分别运行 `make backend-test` 和 `make frontend-test`。Make 的前端目标会检查 `frontend/node_modules/.package-lock.json`；依赖尚未安装或 `package.json`、`package-lock.json` 更新时，会自动运行 `npm ci`，不会在每次执行时重复安装。

原生 AOT 测试集合不包含标注 `@DisabledInAotMode` 的数据库迁移、模块架构、Testcontainers 集成和纯反射形状测试；这些门禁由 JVM 测试覆盖。可在原生环境运行的领域、契约与 MVC 测试仍会进入 Native Image。CI 还会实际构建并启动 Native Compose 栈，通过网关对系统 API 和 readiness 端点执行 HTTP smoke。

## 本地启动后端

先准备宿主机可访问的 PostgreSQL 17。默认配置连接 `localhost:5432`，需要创建 database `stocket`、user `stocket`，并将密码设置为 `stocket-local-dev`。也可以通过 `STOCKET_DB_URL`、`STOCKET_DB_USER` 和 `STOCKET_DB_PASSWORD` 覆盖连接配置。

然后使用 `local` profile 启动后端：

```bash
cd backend
STOCKET_DB_PASSWORD=stocket-local-dev ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

后端默认监听 `http://localhost:8080`。

## 本地管理员恢复

当管理员忘记密码或无法登录时，可以使用本地维护命令重置密码。该命令直接操作数据库，不需要通过 Web 接口。

**JVM 模式：**

```bash
java -jar backend/target/stocket-backend-0.1.0-SNAPSHOT.jar \
  --stocket.maintenance.reset-admin=owner
```

**原生模式：**

```bash
./stocket --stocket.maintenance.reset-admin=owner
```

命令执行后会：
1. 生成一个 20 位临时密码
2. 强制用户在首次登录时修改密码
3. 撤销该用户的所有活跃会话
4. 写入 `PasswordRecoveredLocally` 审计事件

临时密码会打印到标准输出，请妥善保管。

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

## 阶段一完成

JVM 测试套件、前端测试/构建、Spring AOT 处理、PostgreSQL 迁移测试、GraalVM 原生测试和原生 Docker 冒烟测试均已为工程基础通过。

## 阶段二完成：身份与家庭

系统初始化、服务端会话、CSRF 防护、登录/登出、账户管理、邀请接受、角色授权、密码修改、管理员重置、本地维护恢复和身份审计事件均已实现并通过验收。

### 初始化

首次启动时通过 `/api/v1/setup/initialize` 创建家庭和管理员账户。该操作只能成功一次；首位管理员自动获得会话。

### Cookie 与 HTTPS 要求

会话令牌通过 `STOCKET_SESSION` Cookie 传输，设置 `Secure`、`HttpOnly` 和 `SameSite=Lax`。生产环境必须启用 HTTPS，否则 Cookie 无法发送。

### 会话期限

- 空闲超时：30 天
- 绝对超时：90 天
- 密码变更后全量撤销所有会话

### 管理员恢复

当管理员忘记密码时，可使用本地维护命令：

```bash
# JVM 模式
java -jar backend/target/stocket-backend-0.1.0-SNAPSHOT.jar \
  --stocket.maintenance.reset-admin=<用户名>

# 原生模式
./backend/target/stocket-backend \
  --stocket.maintenance.reset-admin=<用户名>
```

命令会生成临时密码、撤销所有会话、写入审计事件。临时密码仅输出到标准输出。

### 角色权限矩阵

| 操作 | ADMIN | MEMBER | VIEWER |
|------|-------|--------|--------|
| 读取数据 | Yes | Yes | Yes |
| 写入数据 | Yes | Yes | No |
| 管理成员/邀请 | Yes | No | No |

### 验证命令

```bash
# 阶段二验收测试
cd backend && ./mvnw -Dtest=IdentityAcceptanceTest test

# 全量后端测试
cd backend && ./mvnw test

# 前端测试与构建
cd frontend && npm test && npm run typecheck && npm run build

# 维护命令冒烟测试
cd backend && ./mvnw -DskipTests package
./scripts/identity-maintenance-smoke.sh
```

## 阶段三完成：目录与位置

系统已实现家庭内的分类树、位置树、分类属性模式、可复用物品定义、标签、条码、位置二维码、归档规则与目录搜索投影。

### API 与权限

- `ADMIN`：维护分类树和位置树，也可维护物品。
- `MEMBER`：读取分类与位置，创建、更新、归档和恢复物品。
- `VIEWER`：只读访问分类、位置、物品与搜索。
- 所有资源均按当前家庭隔离；未知或其他家庭资源统一按不存在处理。

分类属性支持 `TEXT`、`NUMBER`、`BOOLEAN`、`DATE` 和 `ENUM`。分类模式更新会校验已有活跃物品，避免把现有数据变成无效状态。分类、位置和物品更新均使用乐观版本；父节点归档、恢复和循环规则由服务端强制执行。

### 搜索与二维码

- `GET /api/v1/catalog/search?q=...` 会规范化首尾空白和大小写。
- 完整条码精确命中优先于名称、品牌、规格、标签和分类路径的文本结果。
- 归档物品默认不进入搜索结果，目录写事务会同步更新搜索投影。
- 位置二维码载荷使用 `stocket:location:<public-code>`；公开码不可替代认证，解析结果仍受家庭隔离约束。

### 阶段三验收记录

2026-07-14 在 GraalVM 25.0.1、Docker 29.4.0 和 PostgreSQL 17.5 Testcontainers 环境完成：

```bash
cd backend && ./mvnw -Dtest=CatalogLocationAcceptanceTest test
# 1 个阶段验收测试通过

make test
# 后端 196 个测试、前端 98 个测试、类型检查和配置契约通过

make build
# JVM 可执行 JAR 与前端生产构建通过

make aot
# Spring AOT 处理通过

make native-test
# Native Image 生成成功；32 个原生适用测试通过，0 失败
```

## 文档

- [产品与技术设计规格](docs/superpowers/specs/2026-07-10-stocket-design.md)
- [交付路线图](docs/superpowers/plans/2026-07-11-delivery-roadmap.md)
- [基础与原生构建实施说明](docs/superpowers/plans/2026-07-11-foundation-native-baseline.md)
- [阶段三目录与位置实施计划](docs/superpowers/plans/2026-07-12-catalog-location.md)
