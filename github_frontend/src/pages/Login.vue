<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { login } from '../api'

const username = ref('')
const password = ref('')
const loading = ref(false)
const router = useRouter()
const auth = useAuthStore()

async function onLogin() {
  if (!username.value || !password.value) return
  loading.value = true
  try {
    const data = await login(username.value, password.value)
    auth.setAuth(data.username, data.token)
    router.push('/chat')
  } catch (e: any) {
    alert(e?.msg || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-container">
    <div class="card auth-card">
      <h2 class="title">登录</h2>
      <div class="field"><input v-model="username" class="pill-input" placeholder="用户名" /></div>
      <div class="field"><input v-model="password" class="pill-input" type="password" placeholder="密码" /></div>
      <div class="actions">
        <button class="pill-btn" :disabled="loading" @click="onLogin">登录</button>
        <button class="pill-btn ghost" @click="$router.push('/register')">注册</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--bg);
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 28px;
  box-sizing: border-box;
}

.title {
  margin: 0 0 12px 0;
  color: var(--text);
  text-align: center;
}

.field { margin-top: 14px; }
.field .pill-input { width: 100%; }

.actions {
  margin-top: 18px;
  display: flex;
  gap: 12px;
}

.actions .pill-btn { flex: 1; }

@media (max-width: 640px) {
  .auth-card { padding: 22px; }
}
</style>