import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173, // 设置本地开发端口号
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
