import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    username: '' as string,
    token: '' as string,
  }),
  actions: {
    setAuth(username: string, token: string) {
      this.username = username
      this.token = token
    },
    clear() {
      this.username = ''
      this.token = ''
    },
  },
})