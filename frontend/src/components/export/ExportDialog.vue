<script setup lang="ts">
import { ref } from 'vue'
import { downloadCsv, type ExportKind } from '../../api/export'
const props=defineProps<{kind:ExportKind;filters?:Record<string,unknown>;label?:string}>();const pending=ref(false);const error=ref('')
async function run(){pending.value=true;error.value='';try{const blob=await downloadCsv(props.kind,props.filters);const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=`${props.kind}.csv`;link.click();URL.revokeObjectURL(url)}catch(problem){error.value=(problem as {detail?:string}).detail??'导出失败，请缩小筛选范围后重试'}finally{pending.value=false}}
</script>
<template><div class="export-control"><button type="button" :disabled="pending" @click="run">{{pending?'正在导出':(label??'导出 CSV')}}</button><small>最多导出 100,000 行，内容采用 UTF-8 并防止公式注入。</small><p v-if="error" role="alert">{{error}}</p></div></template>
