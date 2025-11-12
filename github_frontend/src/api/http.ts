import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

let bearerToken = ''

export function setToken(token: string) {
  bearerToken = token || ''
}

export function getToken() {
  return bearerToken
}

api.interceptors.request.use((config) => {
  console.log('📤 API Request:', {
    method: config.method?.toUpperCase(),
    url: config.url,
    baseURL: config.baseURL,
    fullURL: `${config.baseURL}${config.url}`,
    hasToken: !!bearerToken
  })
  
  if (bearerToken) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${bearerToken}`
  }
  return config
})

api.interceptors.response.use(
  (resp) => {
    // 统一处理 code=0
    const data = resp.data
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 0) return data
      console.error('❌ API Error:', data)
      return Promise.reject(data)
    }
    return resp
  },
  (err) => {
    console.error('❌ HTTP Error:', err)
    return Promise.reject(err)
  }
)

export default api