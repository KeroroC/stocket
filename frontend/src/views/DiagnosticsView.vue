<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDiagnostics, type DiagnosticCheck } from '../api/diagnostics'
import StPageHeader from '../components/StPageHeader.vue'
const checks=ref<Record<string,DiagnosticCheck>>({});const error=ref('')
const labels:Record<string,string>={database:'数据库',attachmentStorage:'附件存储',incompleteEvents:'未完成事件',deadDeliveries:'失败投递',openReconciliation:'库存对账',missingAttachments:'缺失附件'}
const advice:Record<string,string>={CHECK_DATABASE:'检查数据库连接和健康状态。',CHECK_ATTACHMENT_STORAGE:'检查附件目录权限和可用空间。',REPUBLISH_MODULE_EVENTS:'重新投递未完成的模块事件。',RETRY_DEAD_DELIVERIES:'检查通知渠道后重试失败投递。',RUN_INVENTORY_RECONCILIATION:'运行库存对账并处理差异。',REPAIR_ATTACHMENT_STORAGE:'检查附件存储并运行恢复任务。'}
onMounted(async()=>{try{checks.value=(await getDiagnostics()).checks}catch{error.value='诊断信息加载失败'}})
function status(check:DiagnosticCheck){return check.status==='OK'?'正常':check.status==='WARN'?'需要处理':'异常'}
</script>
<template><section class="st-page"><StPageHeader title="系统诊断" description="只显示安全、可行动的运行状态"/><p v-if="error" class="st-feedback st-feedback--error" role="alert">{{error}}</p><div class="diagnostics-grid"><article v-for="(check,key) in checks" :key="key" :class="`diagnostic diagnostic--${check.status.toLowerCase()}`"><h2>{{labels[key]??key}}</h2><strong>{{status(check)}}</strong><p>数量：{{check.count}}</p><p>{{advice[check.actionCode]??'联系管理员检查此项。'}}</p><time :datetime="check.checkedAt">检查于 {{new Date(check.checkedAt).toLocaleString()}}</time></article></div></section></template>
