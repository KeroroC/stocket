# Stocket v1 正式验收报告模板

> 本模板用于版本 tag 触发后的正式发布验收。复制为带版本和日期的报告后填写；未知或未执行的项目必须写 `PENDING`/`BLOCKED`，不得以本地 stub、缓存构建或单架构结果替代正式证据。报告不得包含秘密、真实家庭数据、Cookie、数据库 dump 或附件正文。

## 发布标识

| 字段 | 记录 |
| --- | --- |
| 版本/tag | `vX.Y.Z` |
| commit SHA | `PENDING` |
| 验收日期（UTC） | `PENDING` |
| 验收负责人 | `PENDING` |
| GitHub workflow run URL | `PENDING` |
| 结论 | `PENDING` / `PASS` / `FAIL` |

## 自动化门禁

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| `make test` | `PENDING` | 测试摘要/artifact |
| `make build` | `PENDING` | 构建日志 |
| 浏览器验收 | `PENDING` | Playwright 报告 |
| 备份 Bats | `PENDING` | artifact |
| 干净环境恢复演练 | `PENDING` | 脱敏恢复日志 |
| Trivy HIGH/CRITICAL 门禁 | `PENDING` | `trivy-release.json` |
| SPDX SBOM | `PENDING` | `stocket-app.spdx.json` |
| checksum/manifest 验证 | `PENDING` | `SHA256SUMS`、`release-manifest.json` |
| Cosign image signature | `PENDING` | `cosign verify` 输出 |
| SBOM/SLSA attestations | `PENDING` | `cosign verify-attestation` 输出 |

## 发布产物

| 产物 | digest/checksum | 验证结果 |
| --- | --- | --- |
| multi-arch image index | `sha256:PENDING` | `PENDING` |

记录 `docker buildx imagetools inspect` 的平台列表，确认镜像同时提供 AMD64 和 ARM64 平台。

## 恢复证据

| 字段 | 记录 |
| --- | --- |
| backup ID | `PENDING` |
| 备份格式/应用版本 | `PENDING` |
| 恢复开始/结束时间 | `PENDING` |
| 恢复总时长 | `PENDING` |
| checksum 与 manifest | `PENDING` |
| Flyway schema/version | `PENDING` |
| 附件数量与哈希 | `PENDING` |
| 库存对账 | `PENDING` |
| 登录、搜索、入库、消耗、调拨、提醒、附件、导出、审计 smoke | `PENDING` |

恢复必须在空数据库和空附件目录执行。失败时保留脱敏日志和临时环境，不得覆盖生产数据或把仅验证备份格式记为恢复成功。

## 人工上线检查

- [ ] HTTPS certificate chain、redirect、HSTS 与公开 Origin 正确。
- [ ] 生产只公开 Gateway，app/PostgreSQL/Prometheus 未暴露公网端口。
- [ ] secret files、数据卷、附件卷和备份卷权限正确。
- [ ] `/livez`、`/readyz`、内部指标抓取与最小告警已配置。
- [ ] 初始化/登录/搜索/库存操作/提醒/附件/CSV/审计流程通过。
- [ ] 升级前 backup ID、回滚触发条件和负责人已记录。
- [ ] 实体手机 PWA 安装、摄像头和安全区按设备清单确认。

## 例外、已知限制与签署

列出所有失败、临时漏洞豁免、人工待办和风险接受人。任一必需门禁为 `PENDING`、`BLOCKED` 或 `FAIL` 时，结论不得为 `PASS`，不得宣告 v1 正式发布。

| 角色 | 姓名 | 日期 | 结论 |
| --- | --- | --- | --- |
| 发布负责人 | `PENDING` | `PENDING` | `PENDING` |
| 运维/恢复复核 | `PENDING` | `PENDING` | `PENDING` |
| 安全复核 | `PENDING` | `PENDING` | `PENDING` |
