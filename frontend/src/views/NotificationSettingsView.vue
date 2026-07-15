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
    <p v-if="message" class="st-feedback st-feedback--success" role="status">{{ message }}</p>
    <p v-if="error" class="st-feedback st-feedback--error" role="alert">{{ error }}</p>
    <el-card shadow="never"><template #header><h2>浏览器通知</h2></template><el-button v-if="!push.enabled.value" type="primary" :loading="push.busy.value" @click="enablePush">启用浏览器通知</el-button><el-button v-else :loading="push.busy.value" @click="push.disable">关闭浏览器通知</el-button></el-card>
    <el-card shadow="never"><template #header><h2>SMTP</h2></template><el-form label-position="top" @submit.prevent="saveSmtp"><el-form-item label="启用 SMTP"><el-switch v-model="smtp.enabled" /></el-form-item><el-row :gutter="16"><el-col :xs="24" :sm="16"><el-form-item label="主机"><el-input v-model="smtp.host" /></el-form-item></el-col><el-col :xs="24" :sm="8"><el-form-item label="端口"><el-input-number v-model="smtp.port" :min="1" :max="65535" /></el-form-item></el-col><el-col :xs="24" :sm="12"><el-form-item label="用户名"><el-input v-model="smtp.username" /></el-form-item></el-col><el-col :xs="24" :sm="12"><el-form-item label="发件地址"><el-input v-model="smtp.fromAddress" type="email" /></el-form-item></el-col><el-col :span="24"><el-form-item label="SMTP 密码"><el-input v-model="smtp.secret" aria-label="SMTP 密码" type="password" autocomplete="new-password" show-password /></el-form-item></el-col></el-row><el-tag v-if="smtp.hasSecret" type="success">已保存密钥</el-tag><el-button native-type="submit" type="primary">保存 SMTP</el-button></el-form></el-card>
  </section>
</template>
