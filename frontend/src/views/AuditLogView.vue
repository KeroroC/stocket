<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listAuditLogs, type AuditEntry } from '../api/audit'
import StPageHeader from '../components/StPageHeader.vue'
import { useDesktopLayout } from '../composables/useDesktopLayout'
const { isDesktop } = useDesktopLayout()
const items=ref<AuditEntry[]>([]);const nextCursor=ref<string>();const loading=ref(false);const error=ref('');const copied=ref('')
const filters=reactive({eventType:'',outcome:'',requestId:''})
onMounted(()=>load(false))
async function load(more:boolean){loading.value=true;error.value='';try{const page=await listAuditLogs({eventType:filters.eventType||undefined,outcome:filters.outcome||undefined,requestId:filters.requestId||undefined,cursor:more?nextCursor.value:undefined,size:50});items.value=more?[...items.value,...page.items]:page.items;nextCursor.value=page.nextCursor}catch(problem){error.value=(problem as {detail?:string}).detail??'审计日志加载失败'}finally{loading.value=false}}
async function copy(value:string){await navigator.clipboard.writeText(value);copied.value=value}
const detailLabel:Record<string,string>={ownerType:'对象类型',ownerId:'对象 ID',purpose:'用途',filename:'文件名',quantity:'数量',operation:'操作',status:'状态'}
</script>
<template>
  <section class="st-page audit-page">
    <StPageHeader title="审计日志" description="按请求、操作者和事件追踪关键变更" />
    <el-form class="audit-filters" inline @submit.prevent="load(false)"><el-form-item label="事件类型"><el-input v-model="filters.eventType" /></el-form-item><el-form-item label="结果"><el-select v-model="filters.outcome"><el-option label="全部" value="" /><el-option label="SUCCESS" value="SUCCESS" /><el-option label="FAILURE" value="FAILURE" /></el-select></el-form-item><el-form-item label="Request ID"><el-input v-model="filters.requestId" /></el-form-item><el-button native-type="submit" type="primary">筛选</el-button></el-form>
    <p v-if="error" class="st-feedback st-feedback--error" role="alert">{{ error }}</p>
    <el-table v-if="isDesktop && items.length" :data="items" row-key="id" class="audit-table"><el-table-column label="时间" min-width="180"><template #default="{ row }">{{ new Date(row.occurredAt).toLocaleString() }}</template></el-table-column><el-table-column prop="eventType" label="事件" min-width="170" /><el-table-column label="结果" width="100"><template #default="{ row }"><el-tag :type="row.outcome === 'FAILURE' ? 'danger' : 'success'">{{ row.outcome }}</el-tag></template></el-table-column><el-table-column label="操作人" min-width="120"><template #default="{ row }">{{ row.actorDisplayName ?? '系统' }}</template></el-table-column><el-table-column label="对象" min-width="180"><template #default="{ row }">{{ row.subjectType }} {{ row.subjectId ?? '' }}</template></el-table-column><el-table-column label="Request ID" min-width="200"><template #default="{ row }"><template v-if="row.requestId"><code>{{ row.requestId }}</code><el-button link type="primary" :aria-label="`复制 request ID ${row.requestId}`" @click="copy(row.requestId)">复制</el-button></template></template></el-table-column></el-table>
    <ul v-if="!isDesktop" class="audit-list">
      <li v-for="item in items" :key="item.id">
        <header>
          <strong>{{ item.eventType }}</strong>
          <span :class="['audit-outcome', { 'audit-outcome--failure': item.outcome === 'FAILURE' }]">{{ item.outcome }}</span>
          <time :datetime="item.occurredAt">{{ new Date(item.occurredAt).toLocaleString() }}</time>
        </header>
        <p>操作人：{{ item.actorDisplayName ?? '系统' }}</p>
        <p>对象：{{ item.subjectType }} {{ item.subjectId ?? '' }}</p>
        <p v-if="item.requestId">Request ID：<code>{{ item.requestId }}</code>
          <el-button link type="primary" aria-label="复制 request ID" @click="copy(item.requestId)">复制</el-button>
          <span v-if="copied === item.requestId" role="status">已复制</span>
        </p>
        <dl v-if="Object.keys(item.details).length"><template v-for="(value,key) in item.details" :key="key"><dt>{{ detailLabel[key] ?? key }}</dt><dd>{{ value }}</dd></template></dl>
      </li>
    </ul>
    <el-empty v-if="!loading && !items.length" description="没有匹配的审计记录" />
    <el-button v-if="nextCursor" :loading="loading" @click="load(true)">加载更多</el-button>
  </section>
</template>
