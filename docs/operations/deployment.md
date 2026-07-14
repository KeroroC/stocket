# 生产部署

## 支持边界

Stocket v1 支持 Docker Compose 单实例自托管。生产只公开 HTTPS Gateway；app、PostgreSQL、附件卷和备份卷位于内部网络。正式发布镜像支持 Linux AMD64/ARM64，架构证据由 release workflow 的原生 runner 产生。

## 准备 secret 与 TLS

复制 `.env.example` 为不入库的 `.env`，至少设置公开 `https://` Origin、版本和 trusted proxy CIDR。创建：

- PostgreSQL 随机长密码文件；
- 32 字节 Base64 `STOCKET_MASTER_KEY` 文件；
- TLS certificate chain 与私钥。

secret 文件必须仅部署管理员可读，不能放进 build context、镜像、备份、日志或 Git。Gateway 容器需要读取 TLS 文件；建议宿主权限为 root 或部署组拥有、只读挂载。

## 启动

```bash
docker compose --env-file .env \
  -f deploy/compose.yml -f deploy/compose.production.yml \
  config --quiet
docker compose --env-file .env \
  -f deploy/compose.yml -f deploy/compose.production.yml \
  up -d --build postgres app gateway
```

生产映射宿主 80/443，80 只返回 HTTPS redirect。app/db 不发布宿主端口。容器使用非 root、只读根文件系统、`cap_drop: ALL`、`no-new-privileges`、资源限制和日志轮转；只有 PostgreSQL、附件、备份及受限 tmpfs 可写。

## 上线检查

```bash
curl -fsS https://stocket.example.com/livez
curl -fsS https://stocket.example.com/readyz
STOCKET_SMOKE_BASE_URL=https://stocket.example.com deploy/smoke/api-smoke.sh
```

`/livez` 只反映进程生存，数据库或外部渠道故障不会使其 DOWN。`/readyz` 包含数据库、主密钥和附件写入检查。Gateway 不代理通用 `/actuator/*`；Prometheus 端点仅供内部网络抓取。

## 可信代理

Gateway 会覆盖而不是追加客户端 `X-Forwarded-For`。应用仅在 socket peer 属于 `STOCKET_TRUSTED_PROXY_CIDRS` 时接受单个转发地址；公网直连或逗号代理链均回退到 socket 地址。不要把 `0.0.0.0/0` 或 `::/0` 配为可信代理。

## 数据与备份

`postgres-data`、`attachment-data`、`backup-data` 必须放在持久介质。附件目录不得位于 Web root。定时任务应调用 `deploy/backup/run-consistent.sh`；完整流程见 `docs/operations/backup-restore.md`。
