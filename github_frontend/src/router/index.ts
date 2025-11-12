import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router'
import Login from '../pages/Login.vue'
import Register from '../pages/Register.vue'
import Chat from '../pages/Chat.vue'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'Login', component: Login },
  { path: '/register', name: 'Register', component: Register },
  { path: '/chat', name: 'Chat', component: Chat },
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
})