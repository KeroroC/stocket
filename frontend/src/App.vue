<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuth } from './auth/useAuth'

const { state, bootstrap, login, logout, passwordChanged, initialize } = useAuth()

const username = ref('')
const password = ref('')

onMounted(() => {
  bootstrap()
})
</script>

<template>
  <main class="app-shell">
    <!-- checking-setup -->
    <section v-if="state.kind === 'checking-setup'" class="auth-card">
      <p>正在检查身份状态...</p>
    </section>

    <!-- setup-required -->
    <section v-else-if="state.kind === 'setup-required'" class="auth-card">
      <h1>初始化家庭</h1>
      <p>首次使用需要创建家庭和管理员账户</p>
    </section>

    <!-- anonymous (login) -->
    <section v-else-if="state.kind === 'anonymous'" class="auth-card">
      <h1>登录</h1>
      <form @submit.prevent="login({ username, password })">
        <div class="form-field">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="username"
            type="text"
            autocomplete="username"
          />
        </div>
        <div class="form-field">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="password"
            type="password"
            autocomplete="current-password"
          />
        </div>
        <button type="submit">登录</button>
      </form>
    </section>

    <!-- password-change-required -->
    <section v-else-if="state.kind === 'password-change-required'" class="auth-card">
      <h1>修改密码</h1>
      <p>请修改初始密码后再继续使用</p>
    </section>

    <!-- authenticated -->
    <section v-else-if="state.kind === 'authenticated'" class="auth-card">
      <h1>{{ state.account.displayName }}</h1>
      <button @click="logout()">退出登录</button>
    </section>
  </main>
</template>
