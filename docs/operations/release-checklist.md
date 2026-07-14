# 正式发布检查清单

## 发布前

- `main` 上的工作树必须干净，tag 必须是 `vMAJOR.MINOR.PATCH`，且 Maven 与 npm 版本完全一致。
- PR CI 与最近一次 nightly 必须成功；过期 `.trivyignore` 豁免会直接阻止发布。
- 记录变更范围、迁移影响、已知限制和升级前备份要求。

## 自动发布证据

`release.yml` 只由版本 tag 触发，执行 JVM tests，并构建 AMD64 与 ARM64 多架构 JVM 容器镜像。

聚合作业必须产生并验证：

- multi-arch image digest；
- SPDX JSON SBOM 与 Trivy JSON；
- Cosign OIDC image signature、SBOM attestation、SLSA provenance；
- `release-manifest.json`、`SHA256SUMS` 和测试摘要。

`deploy/release/verify-release.sh` 会重新验证 checksum、manifest schema、版本/tag、平台列表、digest、SBOM、漏洞豁免有效期、Cosign 身份与 attestation。任何缺项都不得创建 GitHub Release。

## 本地验证边界

```bash
make release-test
```

本地测试使用 stub Cosign 验证篡改、缺 digest、错误 digest 和过期豁免的失败路径；它不构成真实签名或多架构镜像证据。只有 GitHub release workflow 的 run URL、commit、image digest 与 OIDC verification 输出可作为正式发布证据。

## 发布后

1. 从 GitHub Release 下载资产并执行 `sha256sum --check SHA256SUMS`。
2. 对 `release-manifest.json` 中的 multi-arch image 执行 `cosign verify` 与 `cosign verify-attestation`。
3. 在空环境执行 `make restore-smoke`，记录 backup ID、恢复耗时、Flyway 版本、附件哈希和库存对账。
4. 完成生产 HTTPS、登录、搜索、入库、消耗、调拨、提醒、附件、导出与审计验收。
5. 发现阻塞问题时停止流量，按升级前备份回滚；不得删除或重写已发布 tag。
