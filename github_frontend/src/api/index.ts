import api, { setToken, getToken } from './http'

// Auth
export async function login(username: string, password: string) {
  const res = await api.post('/login', { username, password })
  // res.data = { userId, username, token }
  if (res?.data?.token) setToken(res.data.token)
  return res.data
}

export async function register(payload: {
  username: string
  password: string
  confirmPassword?: string
  email?: string
  phone?: string
  autoLogin?: boolean
}) {
  const body = { autoLogin: true, ...payload }
  const res = await api.post('/register', body)
  if (body.autoLogin && res?.data?.token) setToken(res.data.token)
  return res.data
}

export async function getMe() {
  const res = await api.get('/user/me')
  return res.data
}

// Sessions
export async function createSession(title?: string) {
  const res = await api.post('/sessions/create', title ? { title } : {})
  return res.data
}

export async function pageSessions(params: { title?: string; page: number; pageSize: number }) {
  const res = await api.get('/v1/sessions/page', { params })
  return res.data // { total, records: [...] }
}

export async function getSession(sid: string) {
  const res = await api.get(`/v1/sessions/${sid}`)
  return res.data
}

export async function updateSession(sid: string, body: { title?: string; last_active_tid?: number; last_active_tuid?: string }) {
  const res = await api.patch(`/v1/sessions/${sid}`, body)
  return res.data
}

export async function deleteSession(sid: string) {
  const res = await api.delete(`/v1/sessions/${sid}`)
  return res.data
}

// Turns / Nodes
export async function createTurn(sid: string, body: { parent_tid?: number; user_json: object; ai_json?: object }) {
  const res = await api.post(`/v1/sessions/${sid}/turns`, body)
  return res.data
}

export async function getTurn(sid: string, tid: number, params?: { include_children?: boolean; include_payload?: boolean }) {
  const res = await api.get(`/v1/sessions/${sid}/turns/${tid}`, { params })
  return res.data
}

export async function pageTurns(sid: string, params: {
  order_by?: 'last_accessed_at' | 'created_at'
  desc?: boolean
  page: number
  pageSize: number
  include_payload?: boolean
}) {
  const res = await api.get(`/v1/sessions/${sid}/turns/page`, { params })
  return res.data
}

export async function deleteTurn(sid: string, tid: number, mode: 'subtree' | 'node' = 'subtree') {
  const res = await api.delete(`/v1/sessions/${sid}/turns/${tid}`, { params: { mode } })
  return res.data
}

export async function setActiveTurn(sid: string, body: { tid?: number; tuid?: string }) {
  const res = await api.post(`/v1/sessions/${sid}/active`, body)
  return res.data
}

// Height Management
export async function loadHeights(sid: string) {
  const res = await api.post(`/v1/sessions/${sid}/turns/load-heights`)
  return res.data
}

export async function getNodeHeights(sid: string, tid: number) {
  const res = await api.get(`/v1/sessions/${sid}/turns/${tid}/heights`)
  return res.data
}

// Tree & Path
export async function getTree(sid: string, params?: { root_tid?: number; depth?: number; include_payload?: boolean }) {
  const res = await api.get(`/v1/sessions/${sid}/tree`, { params })
  return res.data
}

export async function getPathTo(sid: string, tid: number, params?: { include_payload?: boolean }) {
  const res = await api.get(`/v1/sessions/${sid}/paths/${tid}`, { params })
  return res.data
}

export async function getLatestChain(sid: string, params?: { include_payload?: boolean }) {
  const res = await api.get(`/v1/sessions/${sid}/latest-chain`, { params })
  return res.data
}

export async function getChildren(sid: string, tid: number, params?: { include_payload?: boolean }) {
  const res = await api.get(`/v1/sessions/${sid}/turns/${tid}/children`, { params })
  return res.data
}

// Search & Resolve
export async function searchTurns(sid: string, params: { q: string; page?: number; pageSize?: number }) {
  const res = await api.get(`/v1/sessions/${sid}/search`, { params })
  return res.data
}

export async function resolveTuid(tuid: string) {
  const res = await api.get(`/v1/turns/resolve/${tuid}`)
  return res.data
}

// Chat SSE (return EventSource for streaming)
export function chatSSE(sid: string, body: {
  parent_tid?: number
  user_json: object
  gen_config?: { temperature?: number; max_tokens?: number }
  stream_options?: { include_usage?: boolean; heartbeat_sec?: number }
  client_event_id?: string
}) {
  const token = (typeof window !== 'undefined') ? (document.cookie.match(/(?<=token=)[^;]+/)?.[0] || '') : ''
  // 推荐使用 axios + fetch+ReadableStream，但这里简化用 EventSource polyfill 方案
  const url = new URL(`/api/v1/sessions/${sid}/chat`, window.location.origin)
  // 由于标准 EventSource 不支持自定义 header，实际项目可使用 fetch+ReadableStream 或 SSE polyfill
  // 这里仅示意：前端还需通过后端反代把 Authorization 注入

  const es = new EventSource(url.toString())
  return es
}

// Chat SSE via POST + fetch ReadableStream
// Returns an AbortController so caller can cancel if needed
export function chatStream(
  sid: string,
  body: {
    parent_tid?: number
    user_json: object
    gen_config?: { temperature?: number; max_tokens?: number }
    stream_options?: { include_usage?: boolean; heartbeat_sec?: number }
    client_event_id?: string
  },
  handlers: {
    onDelta?: (chunk: string) => void
    onEvent?: (event: string, data: any) => void
    onDone?: () => void
    onError?: (err: any) => void
  } = {},
  useRAG: boolean = false
) {
  const controller = new AbortController()
  const token = getToken()

  // 根据 useRAG 参数选择不同的端点
  const endpoint = useRAG ? `/api/v1/sessions/${sid}/RAGchat` : `/api/v1/sessions/${sid}/chat`
  
  console.log('🚀 Starting SSE stream for session:', sid, useRAG ? '(RAG mode)' : '(normal mode)')

  fetch(endpoint, {
    method: 'POST',
    signal: controller.signal,
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  }).then(async (res) => {
    console.log('📡 SSE Response status:', res.status, res.statusText)
    
    if (!res.ok || !res.body) {
      throw new Error(`HTTP ${res.status}: ${res.statusText}`)
    }
    
    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let eventCount = 0
    
    while (true) {
      const { done, value } = await reader.read()
      
      if (done) {
        console.log('✅ SSE stream completed')
        break
      }
      
      const chunk = decoder.decode(value, { stream: true })
      buffer += chunk
      
      // 正规化换行，兼容 CRLF 和 LF
      buffer = buffer.replace(/\r\n/g, '\n')
      
      // 以双空行分割事件块
      let sep = '\n\n'
      let idx
      while ((idx = buffer.indexOf(sep)) !== -1) {
        const rawBlock = buffer.slice(0, idx)
        buffer = buffer.slice(idx + sep.length)
        eventCount++

        // 解析标准 SSE：event:/data:
        const lines = rawBlock.split('\n')
        let eventName = 'message'
        const dataLines: string[] = []
        
        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            dataLines.push(line.slice(5))
          }
        }
        
        let outerData = dataLines.join('\n').trim()

        // 尝试直接 JSON 解析
        let parsed: any = outerData
        let parsedOk = false
        try { 
          parsed = JSON.parse(outerData)
          parsedOk = true 
        } catch (e) {
          // JSON解析失败，保持原始数据
        }

        // 兼容"data:event: xxx / data:data: {...}"嵌套格式
        if (!parsedOk && /(^|\n)event:\s*\w+/.test(outerData)) {
          // 将外层 data 内容再次按行解析为内层 SSE 语义
          const innerLines = outerData.split('\n')
          let innerEvent = eventName
          const innerDataLines: string[] = []
          for (const l of innerLines) {
            const t = l.startsWith('data:') ? l.slice(5) : l
            if (t.startsWith('event:')) innerEvent = t.slice(6).trim()
            else if (t.startsWith('data:')) innerDataLines.push(t.slice(5))
          }
          const innerDataStr = innerDataLines.join('\n').trim()
          let innerParsed: any = innerDataStr
          try { 
            innerParsed = JSON.parse(innerDataStr)
            parsedOk = true 
          } catch (e) {
            // 嵌套JSON解析失败，保持原始数据
          }
          eventName = innerEvent
          parsed = innerParsed
        }

        // 处理你提供的具体格式：data:event: chunk 和 data:data: {...}
        if (!parsedOk && outerData.includes('event:')) {
          const lines = outerData.split('\n')
          let chunkEvent = 'chunk'
          let chunkData = ''
          
          for (const line of lines) {
            if (line.startsWith('event:')) {
              chunkEvent = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              chunkData = line.slice(5).trim()
            }
          }
          
          if (chunkData) {
            try {
              parsed = JSON.parse(chunkData)
              parsedOk = true
              eventName = chunkEvent
            } catch (e) {
              console.error(`SSE JSON parse failed:`, e.message)
            }
          }
        }

        // 分发事件
        if (eventName === 'delta' || eventName === 'message' || eventName === 'chunk') {
          const deltaText = typeof parsed === 'string' ? parsed : (parsed?.delta ?? parsed?.text ?? parsed?.content ?? '')
          if (deltaText && handlers.onDelta) {
            handlers.onDelta(deltaText)
          }
        }
        
        if (handlers.onEvent) {
          handlers.onEvent(eventName, parsed)
        }
        
        if (eventName === 'done') {
          if (handlers.onDone) handlers.onDone()
        }
      }
    }
    
    // flush remainder if any
    if (buffer) {
      if (handlers.onEvent) handlers.onEvent('message', buffer)
    }
    if (handlers.onDone) handlers.onDone()
  }).catch((err) => {
    console.error('❌ SSE stream error:', err)
    if (handlers.onError) handlers.onError(err)
  })

  return controller
}