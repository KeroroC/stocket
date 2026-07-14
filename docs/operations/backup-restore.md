# 备份、保留与恢复

Stocket 的正式备份同时覆盖 PostgreSQL、附件目录和不含秘密的配置摘要。数据库与附件是两个存储介质，因此一致备份必须先停止应用写入；仅运行 `pg_dump` 和 `tar` 不能被视为一致快照。

## 备份格式

每次成功备份发布为 `/var/lib/stocket/backups/<UTC timestamp>/`：

- `database.dump`：PostgreSQL custom-format dump，不含 owner/privilege；
- `attachments.tar`：完整附件目录；
- `attachment-files.sha256`：附件逐文件 SHA-256；
- `config-summary.json`：版本、格式版本和附件数量，不含 `.env`、密码、密钥、TLS 私钥或渠道 secret；
- `manifest.json`：备份 ID、创建时间、应用版本和格式 schema；
- `SHA256SUMS`：上述所有文件的 SHA-256。

备份先写入 `<id>.partial/`。只有 `pg_restore --list`、tar 列表、manifest schema 和全部 checksum 验证成功后才原子重命名为 `<id>/`，并更新 `latest` symlink。失败的 `.partial` 不会被恢复脚本接受，超过 24 小时才由 retention 清理。

## 创建一致备份

在部署目录准备仅管理员可读的 `.env` 和 secret files，然后执行：

```bash
deploy/backup/run-consistent.sh
```

该宿主脚本按顺序停止 `app`、运行一次 `backup` profile 容器、重新启动 `app`。backup 容器没有 Docker socket，附件卷只读，只有备份卷可写。若 app 无法停止或备份校验失败，命令返回非零。

也可以由 systemd timer 或 cron 调用同一脚本。不要在 app 仍可写时手工设置 `STOCKET_BACKUP_QUIESCED=true`。

默认保留策略是最近 7 个不同 UTC 日的最新备份和最近 4 个 ISO 周的最新备份；两组可以重叠。`latest` 指向的成功备份始终保留。校验失败或结构未知的目录不会自动删除，必须人工调查。

建议目标：每日执行，RPO 不高于 24 小时。RTO 取决于数据库和附件体积；每季度至少在独立空环境记录一次真实恢复耗时。

## 验证备份

```bash
docker compose --env-file .env \
  -f deploy/compose.yml -f deploy/compose.production.yml \
  --profile operations run --rm --entrypoint /opt/stocket/backup/verify.sh \
  backup /var/lib/stocket/backups/<backup-id>
```

验证成功只证明备份格式可读；正式发布还必须执行 `make restore-smoke`，在新数据库和新附件目录中恢复并运行迁移、哈希、库存对账与 API 冒烟。

## 恢复

1. 停止 app，并创建当前环境的安全备份。
2. 准备空 PostgreSQL 数据库和空的绝对附件目录。
3. 使用只在 `operations` profile 中启用的 `restore` 服务执行：

```bash
docker compose --env-file .env \
  -f deploy/compose.yml -f deploy/compose.production.yml \
  --profile operations run --rm restore \
  /var/lib/stocket/backups/<backup-id> /var/lib/stocket/attachments
```

`restore` 服务以附件卷所有者 UID 运行，附件父卷可写、备份卷只读；普通 `backup` 服务仍保持附件卷只读。恢复脚本先验证 checksum、dump、tar 和 manifest，再把附件解包到同一文件系统的 staging 目录并校验逐文件 SHA-256；数据库恢复成功后才原子切换附件目录。默认拒绝非空数据库或附件目录。

`--force` 仅用于事故恢复，并且必须设置独立的 `PRE_RESTORE_BACKUP_ROOT`。脚本会先创建恢复前备份；没有回滚点时不会覆盖现有数据。附件目标必须是非根绝对路径。

恢复后启动候选版本，等待 `/readyz`，再运行：Flyway 版本检查、附件哈希、库存对账、登录/搜索/入库/消耗/调拨/提醒/下载 API 冒烟。任何一步失败都应保留备份 ID和脱敏日志，停止对外流量并按恢复前备份回滚。

## 测试脚本

备份镜像内含 Bats。仓库测试覆盖命令失败不发布、含空格路径、并发锁、checksum 篡改、空目标约束和 7 daily + 4 weekly 保留：

```bash
docker build -t stocket-backup-test -f deploy/backup/Dockerfile .
docker run --rm --entrypoint bats -v "$PWD:/workspace" -w /workspace \
  stocket-backup-test deploy/backup/tests
```
