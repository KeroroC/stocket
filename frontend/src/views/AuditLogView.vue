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
    <form class="audit-filters" @submit.prevent="load(false)">
      <label>事件类型<input v-model="filters.eventType" /></label>
      <label>结果<select v-model="filters.outcome"><option value="">全部</option><option>SUCCESS</option><option>FAILURE</option></select></label>
      <label>Request ID<input v-model="filters.requestId" /></label>
      <button type="submit">筛选</button>
    </form>
    <p v-if="error" class="st-feedback st-feedback--error" role="alert">{{ error }}</p>
    <div v-if="isDesktop && items.length" class="st-table-wrapper audit-table">
      <table class="st-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>事件</th>
            <th>结果</th>
            <th>操作人</th>
            <th>对象</th>
            <th>Request ID</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="`table-${item.id}`">
            <td><time :datetime="item.occurredAt">{{ new Date(item.occurredAt).toLocaleString() }}</time></td>
            <td>{{ item.eventType }}</td>
            <td>{{ item.outcome }}</td>
            <td>{{ item.actorDisplayName ?? '系统' }}</td>
            <td>{{ item.subjectType }} {{ item.subjectId ?? '' }}</td>
            <td>
              <template v-if="item.requestId">
                <code>{{ item.requestId }}</code>
                <button class="st-button st-button--text" type="button" :aria-label="`复制 request ID ${item.requestId}`" @click="copy(item.requestId)">复制</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
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
          <button class="st-button st-button--text" type="button" aria-label="复制 request ID" @click="copy(item.requestId)">复制</button>
          <span v-if="copied === item.requestId" role="status">已复制</span>
        </p>
        <dl v-if="Object.keys(item.details).length"><template v-for="(value,key) in item.details" :key="key"><dt>{{ detailLabel[key] ?? key }}</dt><dd>{{ value }}</dd></template></dl>
      </li>
    </ul>
    <p v-if="!loading && !items.length" class="st-empty-copy">没有匹配的审计记录</p>
    <button v-if="nextCursor" class="st-button" type="button" :disabled="loading" @click="load(true)">加载更多</button>
  </section>
</template>
