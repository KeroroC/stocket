# 升级与回滚

## 升级前

1. 阅读 `CHANGELOG.md` 和目标版本 release manifest。
2. 验证镜像签名、SBOM、digest 与 `SHA256SUMS`。
3. 执行一致备份并记录 backup ID。
4. 在独立环境运行 `make restore-smoke`，确认当前备份能由候选版本恢复。
5. 确认磁盘空间、附件卷权限和 `/readyz` 监控。

## 升级

应用 Flyway 只允许向前迁移。先拉取按 digest 固定的候选镜像，再停止 app、创建最终备份、启动候选 app，等待 `/readyz` 后执行 `deploy/smoke/api-smoke.sh`。Gateway 最后切换流量。

`UpgradeCompatibilityTest` 先把数据库迁移到 Stage 6 schema，写入目录与库存 fixture，再迁移到当前版本，验证历史 checksum 未改变、数据可读和新 schema 已创建。已有 migration 文件一经发布不得修改。

破坏性变更必须使用 expand → migrate → contract 并跨至少两个正式版本：先新增兼容字段/表，后台迁移数据，确认所有运行实例已升级后才删除旧结构。v1 不承诺数据库降级迁移。

## 回滚

如果候选尚未执行不兼容迁移，可回退到上一个按 digest 固定的镜像。若已经执行前向迁移或数据写入，停止流量并从升级前 backup ID 恢复到空数据库和空附件目录；不要在原数据库上手工改 `flyway_schema_history`。

回滚后验证 `/readyz`、登录、目录搜索、库存流水、附件哈希、提醒和库存对账。记录版本、commit、备份 ID、失败时间线和脱敏日志。
