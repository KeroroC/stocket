<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listNotificationChannels, updateNotificationChannel, type NotificationChannel } from '../api/notification'
import { usePushSubscription } from '../notification/usePushSubscription'
import StPageHeader from '../components/StPageHeader.vue'

const channels = ref<NotificationChannel[]>([])
const smtp = reactive({ enabled: true, host: '', port: 587, tlsMode: 'STARTTLS', username: '', fromAddress: '', secret: '', version: 0, hasSecret: false })
const message = ref('')
const error = ref('')
const push = usePushSubscription()

onMounted(async () => {
  channels.value = await listNotificationChannels()
  const channel = channels.value.find(item => item.type === 'SMTP')
  if (channel) {
    smtp.enabled = channel.enabled
    smtp.host = String(channel.configuration.host ?? '')
    smtp.port = Number(channel.configuration.port ?? 587)
    smtp.tlsMode = String(channel.configuration.tlsMode ?? 'STARTTLS')
    smtp.username = String(channel.configuration.username ?? '')
    smtp.fromAddress = String(channel.configuration.fromAddress ?? '')
    smtp.version = channel.version
    smtp.hasSecret = channel.hasSecret
  }
})

async function saveSmtp() {
  const updated = await updateNotificationChannel('SMTP', {
    enabled: smtp.enabled, version: smtp.version, secret: smtp.secret,
    configuration: { host: smtp.host, port: smtp.port, tlsMode: smtp.tlsMode, username: smtp.username, fromAddress: smtp.fromAddress },
  })
  smtp.version = updated.version
  smtp.hasSecret = updated.hasSecret
  smtp.secret = ''
  message.value = 'SMTP 设置已保存'
}

async function enablePush() {
  error.value = ''
  try {
    await push.enable()
    message.value = '浏览器通知已启用'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '启用浏览器通知失败'
  }
}
</script>

<template>
  <section class="st-page settings-page">
    <StPageHeader title="通知设置" description="管理浏览器通知与外部发送渠道" />
    <p v-if="message" role="status">{{ message }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <section>
      <h2>浏览器通知</h2>
      <button v-if="!push.enabled.value" :disabled="push.busy.value" @click="enablePush">启用浏览器通知</button>
      <button v-else :disabled="push.busy.value" @click="push.disable">关闭浏览器通知</button>
    </section>
    <form @submit.prevent="saveSmtp">
      <h2>SMTP</h2>
      <label><input v-model="smtp.enabled" type="checkbox" />启用 SMTP</label>
      <label>主机<input v-model="smtp.host" /></label>
      <label>端口<input v-model.number="smtp.port" type="number" /></label>
      <label>用户名<input v-model="smtp.username" /></label>
      <label>发件地址<input v-model="smtp.fromAddress" type="email" /></label>
      <label>SMTP 密码<input v-model="smtp.secret" aria-label="SMTP 密码" type="password" autocomplete="new-password" /></label>
      <p v-if="smtp.hasSecret">已保存密钥</p>
      <button type="submit">保存 SMTP</button>
    </form>
  </section>
</template>
