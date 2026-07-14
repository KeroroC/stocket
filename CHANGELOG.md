# Changelog

本项目的重要变更记录在此文件。版本号遵循 Semantic Versioning；正式发布日期只在 tag 发布与验收报告全部通过后填写。

## [0.1.0] - Unreleased

### Added

- 单家庭初始化、服务端会话、CSRF、成员/邀请/角色管理和本地管理员恢复。
- 分类与位置树、属性模板、物品目录、条码、位置二维码和搜索投影。
- 批次与独立资产库存、入库/消耗/退库/调整/调拨/损耗/报废、幂等流水和对账。
- 临期与低库存提醒，以及应用内、Web Push、SMTP、Webhook 投递和失败重试管理。
- 移动优先 Vue 3 PWA、扫码、入库草稿、受限离线应用壳和桌面管理界面。
- 安全附件、CSV 导出、审计搜索、管理诊断和端到端 request ID 关联。
- HTTPS Gateway、非 root/只读容器、readiness/liveness、结构化日志、Prometheus 指标和分层限流。
- PostgreSQL 与附件一致备份、7 daily + 4 weekly 保留、安全恢复、恢复 smoke 和升级兼容测试。
- PR/nightly/release 三层 CI、Trivy/SBOM 门禁、AMD64/ARM64 原生发布工作流、checksum、Cosign 签名与 attestations。

### Security

- secret 通过只读文件挂载，禁止进入镜像、Git、日志或备份；附件和通知渠道执行内容、路径、SSRF 与敏感字段防护。
- 正式 release workflow 生成 SPDX SBOM、Trivy 报告、SLSA provenance、OIDC keyless 签名和可验证 manifest。

### Known limitations

- `0.1.0` 尚未正式发布：仍需版本 tag 触发 release workflow，并保存镜像 digest、Trivy、SBOM、Cosign、provenance 与 GitHub Release 的正式证据。
- Native Image、nativeTest 与双架构原生发布不纳入本次收口门禁；现有能力将在后续决定是否删除 Native 打包时重新评估。
- PWA 实体手机安装、真实摄像头和安全区布局仍需按设备验收清单人工确认。
- 部署形态为单实例 Docker Compose；不包含集群高可用、远程对象存储或自动跨地域灾备。
