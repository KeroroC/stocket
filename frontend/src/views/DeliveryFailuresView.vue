<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listFailedDeliveries, retryDelivery, type Delivery } from '../api/notification'
import StPageHeader from '../components/StPageHeader.vue'
import StEmptyState from '../components/StEmptyState.vue'
import { useDesktopLayout } from '../composables/useDesktopLayout'

const deliveries = ref<Delivery[]>([])
const message = ref('')
const error = ref('')
const loading = ref(true)
const { isDesktop } = useDesktopLayout()

onMounted(async () => {
  try { deliveries.value = (await listFailedDeliveries()).content }
  catch (cause) { error.value = (cause as { detail?: string }).detail ?? '加载失败投递失败' }
  finally { loading.value = false }
})

async function retry(delivery: Delivery) {
  try {
    await retryDelivery(delivery.id)
    deliveries.value = deliveries.value.filter(item => item.id !== delivery.id)
    message.value = '已重新排队'
  } catch (cause) {
    error.value = (cause as { detail?: string }).detail ?? '重试失败'
  }
}
</script>

<template>
  <section class="st-page">
    <StPageHeader title="通知失败" description="查看永久失败的投递并手工重试" />
    <p v-if="message" class="st-feedback st-feedback--success" role="status">{{ message }}</p>
    <p v-if="error" class="st-feedback st-feedback--error" role="alert">{{ error }}</p>
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-table v-else-if="isDesktop && deliveries.length" :data="deliveries" row-key="id">
      <el-table-column prop="channelType" label="渠道" /><el-table-column prop="lastErrorCode" label="错误分类" /><el-table-column prop="attemptCount" label="尝试次数" /><el-table-column label="操作"><template #default="{ row }"><el-button link type="primary" :aria-label="`重试 ${row.id}`" @click="retry(row as Delivery)">重试</el-button></template></el-table-column>
    </el-table>
    <ul v-else-if="deliveries.length" class="delivery-list">
      <li v-for="delivery in deliveries" :key="delivery.id" class="delivery-card">
        <header class="delivery-card__header">
          <strong>{{ delivery.channelType }}</strong>
          <el-tag type="danger" effect="light">永久失败</el-tag>
        </header>
        <dl>
          <dt>错误分类</dt><dd>{{ delivery.lastErrorCode ?? '未知错误' }}</dd>
          <dt>尝试次数</dt><dd>{{ delivery.attemptCount }}</dd>
        </dl>
        <el-button type="primary" plain :aria-label="`重试 ${delivery.id}`" @click="retry(delivery)">重新排队</el-button>
      </li>
    </ul>
    <StEmptyState v-else-if="!error" title="没有失败投递" description="当前没有需要人工处理的永久失败通知。" />
  </section>
</template>
