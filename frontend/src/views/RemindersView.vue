<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { acknowledgeReminder, listReminders, type Reminder } from '../api/reminder'
import StPageHeader from '../components/StPageHeader.vue'
import ReminderList from '../components/reminder/ReminderList.vue'

const reminders = ref<Reminder[]>([])
const type = ref('')
const error = ref('')
const loading = ref(false)
const open = computed(() => reminders.value.filter(reminder => reminder.status === 'OPEN'))
const acknowledged = computed(() => reminders.value.filter(reminder => reminder.status === 'ACKNOWLEDGED'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    reminders.value = (await listReminders({ type: type.value || undefined, size: 50 })).content
  } catch (cause) {
    error.value = (cause as { detail?: string; code?: string }).detail ?? '加载提醒失败'
  } finally {
    loading.value = false
  }
}

async function acknowledge(reminder: Reminder) {
  const updated = await acknowledgeReminder(reminder.id)
  reminders.value = reminders.value.map(item => item.id === updated.id ? updated : item)
}

watch(type, load)
onMounted(load)
</script>

<template>
  <section class="st-page reminders-page">
    <StPageHeader title="提醒中心" description="集中处理临期、过期和低库存事项" />
    <label class="reminder-filter">提醒类型
      <select v-model="type" aria-label="提醒类型">
        <option value="">全部</option><option value="EXPIRING">临期</option>
        <option value="EXPIRED">过期</option><option value="LOW_STOCK">低库存</option>
        <option value="INTEGRITY">库存异常</option>
      </select>
    </label>
    <p v-if="error" class="st-feedback st-feedback--error" role="alert">{{ error }}</p>
    <p v-if="loading" class="st-feedback">正在加载...</p>
    <template v-else>
      <section class="reminder-section"><header><div><span>需要操作</span><h2>待处理</h2></div><strong>{{ open.length }}</strong></header><ReminderList :reminders="open" acknowledgeable @acknowledge="acknowledge" /></section>
      <section class="reminder-section reminder-section--muted"><header><div><span>历史记录</span><h2>已确认</h2></div><strong>{{ acknowledged.length }}</strong></header><ReminderList :reminders="acknowledged" /></section>
    </template>
  </section>
</template>
