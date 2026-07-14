# 依赖与镜像安全扫描

Stocket 将快速合并门禁与耗时安全验证分开。PR CI 执行 JVM、模块边界、迁移兼容、前端、Compose、shell/Bats 和 workflow lint；nightly 在无仓库写权限、无部署 secret 的环境中执行浏览器验收、恢复演练、Trivy 和 SBOM。

## 漏洞门禁

Trivy 扫描仓库文件系统以及 app、Gateway、backup 三个发布形态镜像。存在有修复版本的 `HIGH` 或 `CRITICAL` 漏洞时 job 失败；扫描 JSON 即使失败也保留 14 天。`ignore-unfixed` 只排除当前没有上游修复的条目，不等于永久接受风险。

本地安装 Trivy 后可复现文件系统门禁：

```bash
python3 scripts/validate-trivyignore.py .trivyignore
trivy fs --ignorefile .trivyignore --ignore-unfixed \
  --severity HIGH,CRITICAL --exit-code 1 .
```

## 临时豁免

`.trivyignore` 的每个 CVE 前必须紧邻四行元数据：`impact`、`mitigation`、`owner`、`expires`。到期日不得早于检查日，也不得超过 30 天；过期、缺字段、重复或未知格式都会由 `scripts/validate-trivyignore.py` 阻止。豁免只用于记录已评估的短期例外，修复合入后必须立即删除。

新增豁免前运行：

```bash
bash scripts/test-validate-trivyignore.sh
python3 scripts/validate-trivyignore.py .trivyignore
```

## SBOM 与报告

Nightly 使用 Syft 生成 SPDX JSON SBOM，并上传 Trivy JSON、build/restore 日志及测试报告。artifact 不应包含 `.env`、数据库 dump、附件目录、Cookie、TLS 私钥或渠道凭据。正式发布的 SBOM、签名、provenance 和 digest 由 release workflow 生成，nightly artifact 不能替代正式发布证据。

Dependabot 每周分别检查 Maven、npm 和 GitHub Actions，生成独立 PR；不启用自动合并，主版本升级必须人工阅读 release notes、运行完整门禁并评估迁移影响。
