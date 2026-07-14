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
  <section>
    <StPageHeader title="通知失败" description="查看永久失败的投递并手工重试" />
    <p v-if="message" role="status">{{ message }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <table v-if="deliveries.length">
      <thead><tr><th>渠道</th><th>错误分类</th><th>尝试次数</th><th>操作</th></tr></thead>
      <tbody><tr v-for="delivery in deliveries" :key="delivery.id">
        <td>{{ delivery.channelType }}</td><td>{{ delivery.lastErrorCode }}</td><td>{{ delivery.attemptCount }}</td>
        <td><button :aria-label="`重试 ${delivery.id}`" @click="retry(delivery)">重试</button></td>
      </tr></tbody>
    </table>
    <p v-else>暂无失败投递</p>
  </section>
</template>
