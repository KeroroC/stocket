<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listFailedDeliveries, retryDelivery, type Delivery } from '../api/notification'
import StPageHeader from '../components/StPageHeader.vue'

const deliveries = ref<Delivery[]>([])
const message = ref('')
const error = ref('')

onMounted(async () => {
  try { deliveries.value = (await listFailedDeliveries()).content }
  catch (cause) { error.value = (cause as { detail?: string }).detail ?? '加载失败投递失败' }
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
    <el-table v-if="deliveries.length" :data="deliveries" row-key="id">
      <el-table-column prop="channelType" label="渠道" /><el-table-column prop="lastErrorCode" label="错误分类" /><el-table-column prop="attemptCount" label="尝试次数" /><el-table-column label="操作"><template #default="{ row }"><el-button link type="primary" :aria-label="`重试 ${row.id}`" @click="retry(row as Delivery)">重试</el-button></template></el-table-column>
    </el-table>
    <el-empty v-else description="暂无失败投递" />
  </section>
</template>
