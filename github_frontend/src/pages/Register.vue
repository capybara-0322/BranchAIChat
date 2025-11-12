<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api'
import { useAuthStore } from '../stores/auth'

const username = ref('')
const password = ref('')
const confirm = ref('')
const email = ref('')
const phone = ref('')
const loading = ref(false)
const router = useRouter()
const auth = useAuthStore()

async function onRegister() {
  if (!username.value || !password.value) return
  if (confirm.value && confirm.value !== password.value) {
    alert('两次密码不一致')
    return
  }
  loading.value = true
  try {
    const data = await register({
      username: username.value,
      password: password.value,
      confirmPassword: confirm.value || undefined,
      email: email.value || undefined,
      phone: phone.value || undefined,
      autoLogin: true,
    })
    // autoLogin=true 时返回 token
    if (data?.token) {
      auth.setAuth(data.username, data.token)
      router.push('/chat')
    } else {
      router.push('/login')
    }
  } catch (e: any) {
    alert(e?.msg || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-container">
    <div class="card auth-card">
      <h2 class="title">注册</h2>
      <div class="field"><input v-model="username" class="pill-input" placeholder="用户名" /></div>
      <div class="field"><input v-model="password" type="password" class="pill-input" placeholder="密码" /></div>
      <div class="field"><input v-model="confirm" type="password" class="pill-input" placeholder="确认密码" /></div>
      <div class="field"><input v-model="email" class="pill-input" placeholder="邮箱（可选）" /></div>
      <div class="field"><input v-model="phone" class="pill-input" placeholder="手机（可选）" /></div>
      <div class="actions">
        <button class="pill-btn" :disabled="loading" @click="onRegister">注册</button>
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
  max-width: 520px;
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
  justify-content: center;
}

.actions .pill-btn { min-width: 160px; }

@media (max-width: 640px) {
  .auth-card { padding: 22px; }
}
</style>