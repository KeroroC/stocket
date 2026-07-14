<script setup lang="ts">
import type { Reminder } from '../../api/reminder'

defineProps<{ reminders: Reminder[]; acknowledgeable?: boolean }>()
const emit = defineEmits<{ acknowledge: [reminder: Reminder] }>()

function label(type: Reminder['type']) {
  return ({ EXPIRING: '临期', EXPIRED: '已过期', LOW_STOCK: '低库存', INTEGRITY: '库存异常' })[type]
}

function kindClass(type: Reminder['type']) {
  return `reminder-kind--${type.toLowerCase().replace('_', '-')}`
}

function date(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
</script>

<template>
  <ul v-if="reminders.length" class="reminder-list">
    <li v-for="reminder in reminders" :key="reminder.id" class="reminder-card">
      <div class="reminder-card__header">
        <strong>{{ reminder.itemName }}</strong>
        <span :class="['reminder-kind', kindClass(reminder.type)]">{{ label(reminder.type) }}</span>
      </div>
      <p>{{ date(reminder.triggerAt) }}</p>
      <p v-if="reminder.locationName">位置：{{ reminder.locationName }}</p>
      <p v-if="reminder.availableQuantity !== null">剩余 {{ reminder.availableQuantity }}</p>
      <button
        v-if="acknowledgeable && reminder.status === 'OPEN'"
        class="st-button st-button--primary"
        :aria-label="`确认 ${reminder.itemName}`"
        @click="emit('acknowledge', reminder)"
      >确认</button>
    </li>
  </ul>
  <p v-else>暂无提醒</p>
</template>
