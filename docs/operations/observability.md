# 可观测性与告警

Stocket 的运维信号分为探针、Prometheus 指标和结构化日志。生产 Gateway 只公开 `/livez` 与 `/readyz`；通用 `/actuator/*` 不对公网代理，Prometheus 端点只能由内部监控网络抓取。

## 探针语义

- `/livez`：只表示进程仍可处理请求。数据库、主密钥或附件存储故障不会使 liveness 失败，避免故障期间反复重启进程。
- `/readyz`：同时检查应用 readiness state、PostgreSQL、主密钥和附件目录读写能力。任一依赖不可用时返回 `503`，Gateway 或调度器应停止发送业务流量。

建议每 15 秒探测一次，连续 3 次失败后告警。`/livez` 失败可触发容器重启；`/readyz` 失败应先保留实例与日志用于诊断，不能自动删除数据卷。

## Prometheus 指标

应用通过内部 `/actuator/prometheus` 暴露 Spring Boot/Micrometer 指标。除 JVM、HTTP、连接池和进程指标外，重点关注：

| 指标 | 含义 | 建议告警 |
| --- | --- | --- |
| `stocket_events_incomplete` | 未完成的持久化应用事件 | 持续增长或超过正常峰值 15 分钟 |
| `stocket_deliveries_dead` | 已进入 `DEAD` 的通知投递 | 大于 0 持续 5 分钟 |
| `stocket_reconciliation_open` | OPEN 库存对账问题 | 大于 0 立即通知管理员 |
| `stocket_attachments_missing` | 元数据存在但 blob 缺失的附件 | 大于 0 立即通知管理员 |
| `stocket_business_operations_total` | 按有限 `operation`、`outcome` 标签统计的业务操作 | `FAILURE`/`REJECTED` 比例异常升高 |

业务指标只使用固定白名单标签，不包含家庭、账户、附件、request ID 或路径，避免高基数和隐私泄露。Gauge 查询失败时返回 `NaN`；这本身应被视为数据库或 schema 诊断信号。

## 结构化日志

production profile 以 JSON 输出日志，并包含 `service=stocket`、版本、`requestId` 和可用时的 `accountId`。业务完成日志只记录有限的 `operation`、`outcome` 与 `source`。Gateway 保留合法客户端 `X-Request-Id`，无效或缺失值会生成新的 ID，应用响应也会返回该值。

日志不得包含 Cookie、Authorization、密码、会话/邀请令牌、主密钥、通知渠道 secret、TLS 私钥、附件正文、数据库 dump 或完整请求体。日志采集端应按 request ID 关联 Gateway 与应用日志，并设置容量和保留期告警；Compose 默认启用 `json-file` 轮转以限制本机磁盘占用。

## 最小告警与排障顺序

至少配置以下告警：readiness 连续失败、HTTP 5xx 比例升高、数据库连接池耗尽、磁盘/数据卷空间不足、容器重启、四个业务 Gauge 非零或不可读、备份任务失败或超过 24 小时无成功备份。

排障时依次记录时间、版本、commit、request ID、受影响端点和脱敏日志；再检查 `/livez`、`/readyz`、数据库、附件卷、未完成事件、DEAD 投递与 OPEN 对账。不要通过删除数据库记录、重写 Flyway 历史或清空附件目录来消除告警。
