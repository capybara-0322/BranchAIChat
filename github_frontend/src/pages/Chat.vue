<script setup lang="ts">
import { computed, onMounted, reactive, ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { pageSessions, createSession, getTree, setActiveTurn, deleteTurn, chatStream, getLatestChain, loadHeights, getTurn, getChildren, updateSession, deleteSession } from '../api'
import { debugSSEFormat, testSSEFormats } from '../utils/sse-debug'
import MarkdownRenderer from '../components/MarkdownRenderer.vue'

const auth = useAuthStore()
const router = useRouter()
const username = computed(() => auth.username || '未登录')

// 退出登录功能
function logout() {
  auth.clear()
  router.push('/login')
}

// sessions
const sessions = ref<{ id: string; title: string }[]>([])
const page = ref(1) // 接口从1开始
const pageSize = 20
const loadingSessions = ref(false)
const noMoreSessions = ref(false)
const currentSessionId = ref<string>('')
const sessionsRef = ref<HTMLDivElement | null>(null)
const messagesRef = ref<HTMLDivElement | null>(null)

async function loadMoreSessions() {
  if (loadingSessions.value || noMoreSessions.value) return
  loadingSessions.value = true
  try {
    console.log('📋 Loading sessions, page:', page.value)
    const data = await pageSessions({ page: page.value, pageSize })
    console.log('📋 Sessions data received:', data)
    const records = data.records || []
    console.log('📋 Records:', records)
    sessions.value.push(...records.map((r: any) => ({ id: r.sid, title: r.title || '未命名会话' })))
    if (sessions.value.length >= data.total || records.length < pageSize) {
      noMoreSessions.value = true
    } else {
      page.value += 1
    }
  } catch (e) {
    console.error('❌ Failed to load sessions:', e)
    // 降级：不影响UI
  } finally {
    loadingSessions.value = false
  }
}

function onSessionsScroll(e: Event) {
  const el = e.target as HTMLDivElement
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 10) {
    loadMoreSessions()
  }
}

function scrollToBottom() {
  // 使用 nextTick 确保 DOM 更新完成后再滚动
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

async function selectSession(sid: string) {
  // 记录执行前的滚动位置
  const previousScrollTop = messagesRef.value ? messagesRef.value.scrollTop : 0
  
  currentSessionId.value = sid
  // 重置当前父节点ID
  curParentTid.value = 0
  // 清空当前消息
  messages.value = []
  // 清空缩略图
  treeRows.value = []
  // 设置加载状态
  loadingMessages.value = true

  try {
    // 1. 先执行loadHeights
    console.log('📊 Loading heights for session:', sid)
    await loadHeights(sid)
    console.log('✅ Heights loaded successfully')
    
    // 2. 获取loadLatestChain，将主链节点信息加载至缩略图最左侧一列
    console.log('📋 Loading latest chain for session:', sid)
    const chainData = await loadLatestChain(sid)
    console.log('📋 Latest chain data received:', chainData)
    
    // 将主链节点转换为缩略图格式
    const chainNodes: any[] = chainData.nodes || []
    const mainChainRows: { id: string; label: string; type: 'main'; height?: number }[][] = []
    
    // 按层级组织主链节点
    chainNodes.forEach((node, index) => {
      const rowIndex = Math.floor(index / 1) // 每行一个主链节点
      if (!mainChainRows[rowIndex]) {
        mainChainRows[rowIndex] = []
      }
      
      const nodeText = node.user_json?.text || node.ai_json?.text || `节点${node.tid}`
      mainChainRows[rowIndex].push({
        id: String(node.tid),
        label: nodeText,
        type: 'main',
        height: node.height || 0
      })
    })
    
    // 3. 根据获得到的节点链循环获取每个节点的子节点信息
    const finalRows: { id: string; label: string; type: 'main' | 'branch'; height?: number }[][] = []
    
    // 收集所有主链节点的ID，用于过滤重复的子节点
    const mainChainNodeIds = new Set(chainNodes.map(node => node.tid))
    
    // 保存上一层级的主链节点ID，用于获取当前行的子节点
    let previousMainNodeId: number | null = null
    
    for (let i = 0; i < mainChainRows.length; i++) {
      const mainRow = mainChainRows[i]
      const finalRow: { id: string; label: string; type: 'main' | 'branch'; height?: number }[] = []
      
      // 添加主链节点
      finalRow.push(...mainRow)
      
      // 获取上一层级主链节点的子节点（如果有的话）
      if (previousMainNodeId !== null && mainRow.length > 0) {
        try {
          // 先获取上一层级主链节点的子节点
          const childNodes = await getChildNodes(sid, previousMainNodeId)
          // 过滤掉已经在主链中的子节点
          const filteredChildNodes = childNodes.filter(child => !mainChainNodeIds.has(child.tid))
          
          // 将过滤后的子节点添加到同一行，与主链节点并排显示
          if (filteredChildNodes.length > 0) {
            if (filteredChildNodes.length <= 2) {
              // 展示所有过滤后的子节点
              filteredChildNodes.forEach(child => {
                finalRow.push({
                  id: String(child.tid),
                  label: child.user_json?.text || child.ai_json?.text || `节点${child.tid}`,
                  type: 'branch',
                  height: child.height || 0
                })
              })
            } else {
              // 只展示前1个子节点，其余用"..."表示
              filteredChildNodes.slice(0, 1).forEach(child => {
                finalRow.push({
                  id: String(child.tid),
                  label: child.user_json?.text || child.ai_json?.text || `节点${child.tid}`,
                  type: 'branch',
                  height: child.height || 0
                })
              })
              finalRow.push({
                id: `more-${previousMainNodeId}`,
                label: '...',
                type: 'branch',
                height: 0
              })
            }
          }
        } catch (error) {
          console.warn('Failed to load child nodes for previous main node:', previousMainNodeId, error)
        }
      }
      
      // 更新上一层级的主链节点ID为当前行的主链节点ID
      if (mainRow.length > 0) {
        previousMainNodeId = parseInt(mainRow[0].id)
      }
      
      finalRows.push(finalRow)
    }
    
    // 添加最后一行，显示主链最后一个节点的子节点
    if (previousMainNodeId !== null) {
      const finalRow: { id: string; label: string; type: 'main' | 'branch'; height?: number }[] = []
      
      try {
        // 获取主链最后一个节点的子节点
        const childNodes = await getChildNodes(sid, previousMainNodeId)
        // 过滤掉已经在主链中的子节点
        const filteredChildNodes = childNodes.filter(child => !mainChainNodeIds.has(child.tid))
        
        // 将过滤后的子节点添加到最后一行
        if (filteredChildNodes.length > 0) {
          if (filteredChildNodes.length <= 3) {
            // 展示所有过滤后的子节点
            filteredChildNodes.forEach(child => {
              finalRow.push({
                id: String(child.tid),
                label: child.user_json?.text || child.ai_json?.text || `节点${child.tid}`,
                type: 'branch',
                height: child.height || 0
              })
            })
          } else {
            // 只展示前2个子节点，其余用"..."表示
            filteredChildNodes.slice(0, 2).forEach(child => {
              finalRow.push({
                id: String(child.tid),
                label: child.user_json?.text || child.ai_json?.text || `节点${child.tid}`,
                type: 'branch',
                height: child.height || 0
              })
            })
            finalRow.push({
              id: `more-${previousMainNodeId}`,
              label: '...',
              type: 'branch',
              height: 0
            })
          }
        }
      } catch (error) {
        console.warn('Failed to load child nodes for last main node:', previousMainNodeId, error)
      }
      
      // 只有当有子节点时才添加这一行
      if (finalRow.length > 0) {
        finalRows.push(finalRow)
      }
    }
    
    treeRows.value = finalRows
    
    console.log('✅ Session loaded successfully:', { sid, finalRows })
  } catch (error) {
    console.error('❌ Failed to load session:', error)
    // 即使加载失败，也要清空加载状态
  } finally {
    loadingMessages.value = false
    // 平滑地恢复到执行前的位置，而不是滚动到最后
    nextTick(() => {
      if (messagesRef.value) {
        messagesRef.value.scrollTop = previousScrollTop
      }
    })
  }
}

async function createSessionClick() {
  try {
    const created = await createSession('新会话')
    const sid = created.sid
    sessions.value.unshift({ id: sid, title: created.title || '新会话' })
    await selectSession(sid)
  } catch (e) {
    alert('创建会话失败')
  }
}

async function updateSessionTitle(newTitle: string) {
  if (!currentSessionId.value) return
  
  try {
    console.log('🔄 Updating session title:', newTitle)
    await updateSession(currentSessionId.value, { title: newTitle })
    
    // 更新本地sessions列表中的标题
    const sessionIndex = sessions.value.findIndex(s => s.id === currentSessionId.value)
    if (sessionIndex !== -1) {
      sessions.value[sessionIndex].title = newTitle
    }
    
    console.log('✅ Session title updated successfully')
  } catch (error) {
    console.error('❌ Failed to update session title:', error)
  }
}

// thumbnails tree
const showThumbs = ref(true)
const selectedNodeId = ref<string>('root')
const pathIds = ref<string[]>(['root'])
const treeRows = ref<{ id: string; label: string; type?: 'main' | 'branch'; height?: number }[][]>([])
const curParentTid = ref<number | null>(null)

async function loadTree(sid: string) {
  try {
    const data = await getTree(sid, { depth: -1, include_payload: false })
    // 将 nodes 转换为 rows（示例：按 parentTid 层次简单聚合，仅示意）
    const nodes: any[] = data.nodes || []
    const tidToNode = new Map<number, any>()
    nodes.forEach(n => tidToNode.set(n.tid, n))
    const levels: Map<number, any[]> = new Map()
    const rootLevel = 0
    const calcLevel = (n: any): number => {
      if (n.parent_tid == null) return rootLevel
      const p = tidToNode.get(n.parent_tid)
      const pl = calcLevel(p)
      return pl + 1
    }
    nodes.forEach(n => {
      const lvl = calcLevel(n)
      const arr = levels.get(lvl) || []
      arr.push(n)
      levels.set(lvl, arr)
    })
    const rows: { id: string; label: string }[][] = []
    Array.from(levels.keys()).sort((a, b) => a - b).forEach(l => {
      rows.push((levels.get(l) || []).map(n => ({
        id: String(n.tid),
        label: n.user_json?.text || n.ai_json?.text || `节点${n.tid}`,
      })))
    })
    treeRows.value = rows
  } catch (e) {
    // 降级为空
    treeRows.value = []
  }
}

async function loadLatestChain(sid: string) {
  try {
    console.log('📋 Loading latest chain for session:', sid)
    const data = await getLatestChain(sid, { include_payload: true })
    console.log('📋 Latest chain data received:', data)
    
    // 将节点链数据转换为消息格式
    const chainNodes: any[] = data.nodes || []
    const newMessages: { id: string; role: 'user' | 'assistant'; content: string }[] = []
    
    // 按时间顺序排序（假设有 created_at 字段，如果没有则按 tid 排序）
    const sortedNodes = chainNodes.sort((a, b) => {
      if (a.created_at && b.created_at) {
        return new Date(a.created_at).getTime() - new Date(b.created_at).getTime()
      }
      return a.tid - b.tid
    })
    
    // 转换每个节点为消息
    sortedNodes.forEach(node => {
      // 用户消息
      if (node.user_json && node.user_json.text) {
        newMessages.push({
          id: `user-${node.tid}`,
          role: 'user',
          content: node.user_json.text
        })
      }
      
      // AI回复消息
      if (node.ai_json && node.ai_json.text) {
        newMessages.push({
          id: `assistant-${node.tid}`,
          role: 'assistant',
          content: node.ai_json.text
        })
      }
    })
    
    // 更新消息列表
    messages.value = newMessages
    
    // 设置当前父节点为最后一个节点的ID（如果有的话）
    if (sortedNodes.length > 0) {
      const lastNode = sortedNodes[sortedNodes.length - 1]
      curParentTid.value = lastNode.tid
    }
    
    console.log('✅ Messages loaded:', newMessages.length, 'messages')
    return data
  } catch (error) {
    console.error('❌ Failed to load latest chain:', error)
    // 保持消息列表为空
    messages.value = []
    return null
  }
}

async function getChildNodes(sid: string, parentTid: number) {
  try {
    console.log('📋 Loading child nodes for parent:', parentTid)
    const data = await getChildren(sid, parentTid, { include_payload: true })
    console.log('📋 Child nodes data received:', data)
    
    // 根据其他API的模式，可能返回 { data: [...] } 或 { nodes: [...] } 格式
    // 尝试多种可能的数据结构
    if (Array.isArray(data)) {
      return data
    } else if (data && Array.isArray(data.data)) {
      return data.data
    } else if (data && Array.isArray(data.nodes)) {
      return data.nodes
    } else {
      console.warn('Unexpected data structure for child nodes:', data)
      return []
    }
  } catch (error) {
    console.error('❌ Failed to load child nodes:', error)
    return []
  }
}

function limitedRow(row: { id: string; label: string }[]) {
  return row.slice(0, 2)
}
function isOnPath(id: string) {
  return pathIds.value.includes(id)
}
function selectNode(id: string) {
  // 处理"..."节点点击
  if (id.startsWith('more-')) {
    const parentTid = parseInt(id.replace('more-', ''))
    openMoreNodesModal(parentTid)
    return
  }
  
  selectedNodeId.value = id
  if (!pathIds.value.includes(id)) pathIds.value.push(id)
  // 更新当前父节点ID
  curParentTid.value = Number(id)
  // 选择节点后可调用 setActiveTurn(sid, { tid }) 记录最近活跃
  if (currentSessionId.value) {
    setActiveTurn(currentSessionId.value, { tid: Number(id) }).catch(() => {})
    // 重新加载当前会话以更新缩略图
    selectSession(currentSessionId.value)
  }
}

const ellipses = reactive<{ show: boolean; nodes: { id: string; label: string }[]; rowIndex: number }>({
  show: false,
  nodes: [],
  rowIndex: -1,
})

const moreNodesModal = reactive<{ show: boolean; parentTid: number; nodes: any[] }>({
  show: false,
  parentTid: 0,
  nodes: []
})

// 悬浮提示状态
const tooltip = reactive<{ 
  show: boolean; 
  x: number; 
  y: number; 
  text: string; 
  nodeId: string | null 
}>({
  show: false,
  x: 0,
  y: 0,
  text: '',
  nodeId: null
})

function openEllipsis(row: { id: string; label: string }[], idx: number) {
  ellipses.show = true
  ellipses.nodes = row
  ellipses.rowIndex = idx
}

async function openMoreNodesModal(parentTid: number) {
  if (!currentSessionId.value) return
  
  try {
    const childNodes = await getChildNodes(currentSessionId.value, parentTid)
    moreNodesModal.show = true
    moreNodesModal.parentTid = parentTid
    moreNodesModal.nodes = childNodes
  } catch (error) {
    console.error('Failed to load more nodes:', error)
  }
}

const ctxMenu = reactive<{ show: boolean; x: number; y: number; nodeId: string | null }>({
  show: false,
  x: 0,
  y: 0,
  nodeId: null,
})

// 会话右键菜单状态
const sessionCtxMenu = reactive<{ 
  show: boolean; 
  x: number; 
  y: number; 
  sessionId: string | null;
  sessionTitle: string;
}>({
  show: false,
  x: 0,
  y: 0,
  sessionId: null,
  sessionTitle: ''
})

// 编辑会话名称状态
const editSessionModal = reactive<{
  show: boolean;
  sessionId: string | null;
  newTitle: string;
}>({
  show: false,
  sessionId: null,
  newTitle: ''
})

// 删除节点确认对话框状态
const deleteNodeModal = reactive<{
  show: boolean;
  nodeId: string | null;
  nodeLabel: string;
  mode: 'node' | 'subtree';
}>({
  show: false,
  nodeId: null,
  nodeLabel: '',
  mode: 'subtree'
})
function openNodeMenu(e: MouseEvent, node: { id: string; label: string }) {
  ctxMenu.show = true
  ctxMenu.x = e.clientX
  ctxMenu.y = e.clientY
  ctxMenu.nodeId = node.id
  window.addEventListener('click', () => (ctxMenu.show = false), { once: true })
}

// 显示删除节点确认对话框
function showDeleteNodeModal(nodeId: string, nodeLabel: string, mode: 'node' | 'subtree' = 'subtree') {
  deleteNodeModal.show = true
  deleteNodeModal.nodeId = nodeId
  deleteNodeModal.nodeLabel = nodeLabel
  deleteNodeModal.mode = mode
  ctxMenu.show = false
}

// 处理删除节点点击
function handleDeleteNodeClick() {
  if (!ctxMenu.nodeId) return
  
  // 从树行中找到对应的节点标签
  let nodeLabel = `节点${ctxMenu.nodeId}`
  for (const row of treeRows.value) {
    for (const node of row) {
      if (node.id === ctxMenu.nodeId) {
        nodeLabel = node.label
        break
      }
    }
  }
  
  showDeleteNodeModal(ctxMenu.nodeId, nodeLabel, 'subtree')
}

// 会话右键菜单处理函数
function openSessionMenu(e: MouseEvent, session: { id: string; title: string }) {
  e.preventDefault()
  e.stopPropagation()
  sessionCtxMenu.show = true
  sessionCtxMenu.x = e.clientX
  sessionCtxMenu.y = e.clientY
  sessionCtxMenu.sessionId = session.id
  sessionCtxMenu.sessionTitle = session.title
  window.addEventListener('click', () => (sessionCtxMenu.show = false), { once: true })
}

// 编辑会话名称
function editSessionName() {
  if (!sessionCtxMenu.sessionId) return
  const session = sessions.value.find(s => s.id === sessionCtxMenu.sessionId)
  if (session) {
    editSessionModal.show = true
    editSessionModal.sessionId = sessionCtxMenu.sessionId
    editSessionModal.newTitle = session.title
    sessionCtxMenu.show = false
    
    // 延迟聚焦到输入框
    setTimeout(() => {
      const input = document.querySelector('input[ref="editSessionInput"]') as HTMLInputElement
      if (input) {
        input.focus()
        input.select()
      }
    }, 100)
  }
}

// 确认更改会话名称
async function confirmEditSessionName() {
  if (!editSessionModal.sessionId || !editSessionModal.newTitle.trim()) return
  
  try {
    await updateSession(editSessionModal.sessionId, { title: editSessionModal.newTitle.trim() })
    
    // 更新本地sessions列表中的标题
    const sessionIndex = sessions.value.findIndex(s => s.id === editSessionModal.sessionId)
    if (sessionIndex !== -1) {
      sessions.value[sessionIndex].title = editSessionModal.newTitle.trim()
    }
    
    editSessionModal.show = false
    console.log('✅ Session title updated successfully')
  } catch (error) {
    console.error('❌ Failed to update session title:', error)
    alert('更新会话名称失败')
  }
}

// 删除会话
async function handleDeleteSession() {
  if (!sessionCtxMenu.sessionId) return
  
  if (!confirm(`确定要删除会话"${sessionCtxMenu.sessionTitle}"吗？此操作不可撤销。`)) {
    return
  }
  
  try {
    // 调用删除会话的API
    await deleteSession(sessionCtxMenu.sessionId)
    
    // 从本地sessions列表中移除
    const sessionIndex = sessions.value.findIndex(s => s.id === sessionCtxMenu.sessionId)
    if (sessionIndex !== -1) {
      sessions.value.splice(sessionIndex, 1)
    }
    
    // 如果删除的是当前会话，清空当前会话
    if (sessionCtxMenu.sessionId === currentSessionId.value) {
      currentSessionId.value = ''
      messages.value = []
      treeRows.value = []
      curParentTid.value = null
    }
    
    sessionCtxMenu.show = false
    console.log('✅ Session deleted successfully')
  } catch (error) {
    console.error('❌ Failed to delete session:', error)
    alert('删除会话失败')
  }
}

// 悬浮提示相关函数
let tooltipTimeout: ReturnType<typeof setTimeout> | null = null

function showTooltip(e: MouseEvent, node: { id: string; label: string }) {
  // 如果文本内容较短，不需要显示悬浮提示
  if (node.label.length <= 8) {
    return
  }
  
  // 清除之前的定时器
  if (tooltipTimeout) {
    clearTimeout(tooltipTimeout)
  }
  
  // 延迟显示，避免鼠标快速移动时频繁显示
  tooltipTimeout = setTimeout(() => {
    const rect = (e.target as HTMLElement).getBoundingClientRect()
    const x = rect.left + rect.width / 2
    const y = rect.top
    
    tooltip.show = true
    tooltip.x = x
    tooltip.y = y
    tooltip.text = node.label
    tooltip.nodeId = node.id
  }, 300) // 300ms 延迟
}

function hideTooltip() {
  // 清除定时器
  if (tooltipTimeout) {
    clearTimeout(tooltipTimeout)
    tooltipTimeout = null
  }
  
  tooltip.show = false
  tooltip.text = ''
  tooltip.nodeId = null
}
// 确认删除节点
async function confirmDeleteNode() {
  if (!deleteNodeModal.nodeId || !currentSessionId.value) return
  
  try {
    console.log('🗑️ Deleting node:', deleteNodeModal.nodeId, 'mode:', deleteNodeModal.mode)
    await deleteTurn(currentSessionId.value, Number(deleteNodeModal.nodeId), deleteNodeModal.mode)
    
    // 关闭确认对话框
    deleteNodeModal.show = false
    
    // 重新加载会话以更新缩略图
    await selectSession(currentSessionId.value)
    
    console.log('✅ Node deleted successfully')
  } catch (error) {
    console.error('❌ Failed to delete node:', error)
    alert('删除节点失败，请重试')
  }
}

// 取消删除节点
function cancelDeleteNode() {
  deleteNodeModal.show = false
}

// 兼容旧版本的删除函数
function deleteSubtree(id: string | null) {
  if (!id) return
  
  // 从树行中找到对应的节点标签
  let nodeLabel = `节点${id}`
  for (const row of treeRows.value) {
    for (const node of row) {
      if (node.id === id) {
        nodeLabel = node.label
        break
      }
    }
  }
  
  showDeleteNodeModal(id, nodeLabel, 'subtree')
}

const thumbWidth = 260
function toggleThumbs() {
  const wasHidden = !showThumbs.value
  showThumbs.value = !showThumbs.value
  
  // 如果是从隐藏状态切换到显示状态，且当前有会话，则调用selectSession
  if (wasHidden && currentSessionId.value) {
    selectSession(currentSessionId.value)
  }
}

const draft = ref('')
const messages = ref<{ id: string; role: 'user' | 'assistant'; content: string }[]>([])
const loadingMessages = ref(false)
const useRAG = ref(false)
let currentController: AbortController | null = null

function send() {
  if (!draft.value || !currentSessionId.value) return
  const content = draft.value
  draft.value = ''

  const userId = 'u' + Date.now()
  const aiId = 'a' + Date.now()
  messages.value.push({ id: userId, role: 'user', content })
  messages.value.push({ id: aiId, role: 'assistant', content: '' })

  // 如果curParentTid为1，重置会话标题为当前用户消息的文本
  if (curParentTid.value === 0) {
    updateSessionTitle(content)
  }

  // cancel previous stream if any
  if (currentController) currentController.abort()

  console.log('🚀 Starting chat stream for session:', currentSessionId.value)
  console.log('📋 Current parent_tid:', curParentTid.value)

  currentController = chatStream(
    currentSessionId.value,
    {
      parent_tid: curParentTid.value !== null ? curParentTid.value : undefined,
      user_json: { text: content },
      stream_options: { include_usage: true, heartbeat_sec: 10 },
    },
    {
      onDelta: (chunk) => {
        const idx = messages.value.findIndex(m => m.id === aiId)
        if (idx !== -1) {
          messages.value[idx].content += chunk
        }
      },
      onEvent: (event, data) => {
        // 处理完成事件，获取新节点的Tid
        console.log('✅ Event:', event)
        if (event === 'done' && data) {
          curParentTid.value = data.data.tid
          console.log('✅ New node created with tid:', data.data.tid)
        }
        // optionally handle usage or other events
      },
      onDone: () => {
        console.log('✅ Done:')
        currentController = null
        // 数据发送完全后，如果缩略图显示则调用selectSession刷新界面状态
        if (currentSessionId.value && showThumbs.value) {
          selectSession(currentSessionId.value)
        }
      },
      onError: (err) => {
        console.error('❌ Chat stream error:', err)
        const idx = messages.value.findIndex(m => m.id === aiId)
        if (idx !== -1) {
          messages.value[idx].content = messages.value[idx].content || '[出错，未收到回复]'
        }
        currentController = null
      },
    },
    useRAG.value
  )
}

onMounted(() => {
  console.log('🚀 Chat component mounted')
  console.log('📊 Initial state:', {
    sessions: sessions.value,
    loadingSessions: loadingSessions.value,
    noMoreSessions: noMoreSessions.value,
    page: page.value
  })
  
  loadMoreSessions()
  
  // 添加示例缩略图节点数据用于演示
  addSampleThumbnailNodes()
  
  // 将调试函数暴露到全局，方便在控制台调用
  if (typeof window !== 'undefined') {
    (window as any).debugSessions = () => {
      console.log('📊 Current sessions state:', {
        sessions: sessions.value,
        loadingSessions: loadingSessions.value,
        noMoreSessions: noMoreSessions.value,
        page: page.value
      })
    }
    (window as any).addSampleNodes = addSampleThumbnailNodes
    console.log('🔧 调试工具已加载，可在控制台使用:')
    console.log('- debugSessions() - 查看会话状态')
    console.log('- addSampleNodes() - 添加示例缩略图节点')
  }
})

// 添加示例缩略图节点数据
function addSampleThumbnailNodes() {
  treeRows.value = [
    // 第一层：根节点
    [
      { id: '1', label: '你好', type: 'main', height: 3 },
    ],
    // 第二层：主路径和分支
    [
      { id: '2', label: '这里是主路径', type: 'main', height: 2 },
      { id: '3', label: '这里是分支', type: 'branch', height: 1 },
    ],
    // 第三层：主路径和分支
    [
      { id: '5', label: '你可以切换到分支', type: 'main', height: 1 },
      { id: '6', label: '也可以删除', type: 'branch', height: 0 },
    ],
    // 第四层：主路径和分支
    [
      { id: '7', label: '点击分支', type: 'main', height: 0 },
      { id: '8', label: '可以转换对话状态', type: 'branch', height: 0 },
      { id: '9', label: '从新的节点开始对话', type: 'branch', height: 0 },
    ]
  ]
  
  // 设置默认选中第一个节点
  selectedNodeId.value = '1'
  pathIds.value = ['1']
  curParentTid.value = 1
  
  console.log('✅ 示例缩略图节点已添加，共', treeRows.value.length, '层')
}
</script>

<template>
  <div class="chat-layout">
    <header class="chat-header card">
      <div class="left">
        <span>{{ username }}</span>
        <button class="logout-btn" @click="logout" title="退出登录">退出</button>
      </div>
      <div class="center">
        {{ sessions.find(s => s.id === currentSessionId)?.title || '未选择会话' }}
        <span v-if="loadingMessages" class="loading-dot">●</span>
      </div>
      <div class="right">当前父节点: {{ curParentTid || '无' }}</div>
    </header>

    <div class="chat-body">
      <aside class="sessions card">
        <div class="sessions-header">
          <div class="button-group">
            <button class="pill-btn small" @click="createSessionClick">新建会话</button>
            <button class="pill-btn small" @click="loadMoreSessions">刷新会话</button>
          </div>
        </div>
        <div class="sessions-list" @scroll="onSessionsScroll" ref="sessionsRef">
          <!-- 调试信息 -->
          <div class="debug-info" style="font-size: 12px; color: #666; padding: 8px; border-bottom: 1px solid #eee;">
            <div>会话数: {{ sessions.length }}</div>
            <div>加载中: {{ loadingSessions }}</div>
            <div>无更多: {{ noMoreSessions }}</div>
            <div>当前页: {{ page }}</div>
          </div>
          
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{active: s.id === currentSessionId}"
            @click="selectSession(s.id)"
            @contextmenu.prevent="openSessionMenu($event, s)"
          >
            {{ s.title }}
          </div>
          <div v-if="loadingSessions" class="muted">加载中...</div>
          <div v-if="noMoreSessions && sessions.length === 0" class="muted">暂无会话</div>
          <div v-if="noMoreSessions && sessions.length > 0" class="muted">没有更多</div>
        </div>
      </aside>

      <main class="chat-window card">
        <div class="messages" ref="messagesRef">
          <!-- 加载状态指示器 -->
          <div v-if="loadingMessages" class="loading-indicator">
            <div class="loading-spinner"></div>
            <span>正在加载历史消息...</span>
          </div>
          
          <!-- 消息列表 -->
          <div v-for="m in messages" :key="m.id" class="message" :class="m.role">
            <div class="bubble">
              <MarkdownRenderer :content="m.content" />
            </div>
          </div>
          
          <!-- 空状态 -->
          <div v-if="!loadingMessages && messages.length === 0" class="empty-state">
            <div class="empty-icon">💬</div>
            <p>暂无历史消息</p>
            <p class="empty-hint">开始新的对话吧！</p>
          </div>
        </div>
        <div class="composer">
          <input v-model="draft" class="pill-input" placeholder="输入内容..." @keyup.enter="send" />
          <label class="rag-checkbox">
            <input type="checkbox" v-model="useRAG" />
            <span>RAG</span>
          </label>
          <button class="pill-btn" @click="send">发送</button>
          <button class="pill-btn ghost small toggle" @click="toggleThumbs">
            {{ showThumbs ? '隐藏缩略图' : '显示缩略图' }}
          </button>
        </div>
      </main>

      <aside class="thumbs card" v-show="showThumbs">
        <div class="thumbs-inner" @contextmenu.prevent>
          <div v-for="(row, idx) in treeRows" :key="idx" class="thumb-row">
            <div class="nodes-container">
              <!-- 所有节点并排显示 -->
              <template v-for="(node, nIdx) in row" :key="node.id">
                <div
                  class="node"
                  :class="[
                    node.type === 'main' ? 'main-node' : 'branch-node',
                    { selected: node.id === selectedNodeId, onpath: isOnPath(node.id) }
                  ]"
                  @click="selectNode(node.id)"
                  @contextmenu.prevent="openNodeMenu($event, node)"
                  @mouseenter="showTooltip($event, node)"
                  @mouseleave="hideTooltip"
                >
                  <div class="node-content-wrapper">
                    <div v-if="node.height !== undefined && node.height > 0" class="height-circle">
                      {{ node.height }}
                    </div>
                    <div class="node-text">
                      <div class="text-line">{{ node.label }}</div>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </div>

        </div>
      </aside>
    </div>

    <div v-if="ellipses.show" class="modal-mask" @click.self="ellipses.show = false">
      <div class="card modal">
        <h4>更多节点</h4>
        <div class="nodes-list">
          <div v-for="n in ellipses.nodes" :key="n.id" class="node" @click="selectNode(n.id)">
            {{ n.label }}
          </div>
        </div>
      </div>
    </div>

    <div v-if="moreNodesModal.show" class="modal-mask" @click.self="moreNodesModal.show = false">
      <div class="card modal more-nodes-modal">
        <div class="modal-header">
          <h4>更多子节点</h4>
          <button class="close-btn" @click="moreNodesModal.show = false">×</button>
        </div>
        <div class="modal-content">
          <div class="nodes-grid">
            <div v-for="node in moreNodesModal.nodes" :key="node.tid" class="modal-node" @click="selectNode(String(node.tid)); moreNodesModal.show = false">
              <div class="modal-node-content">
                <div v-if="node.height !== undefined && node.height > 0" class="modal-height-circle">
                  {{ node.height }}
                </div>
                <div class="modal-node-text">
                  {{ node.user_json?.text || node.ai_json?.text || `节点${node.tid}` }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <ul
      v-if="ctxMenu.show"
      class="ctx-menu card"
      :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
    >
      <li @click="handleDeleteNodeClick" class="danger">删除节点及子树</li>
    </ul>

    <!-- 会话右键菜单 -->
    <ul
      v-if="sessionCtxMenu.show"
      class="ctx-menu card session-ctx-menu"
      :style="{ left: sessionCtxMenu.x + 'px', top: sessionCtxMenu.y + 'px' }"
    >
      <li @click="editSessionName">重命名</li>
      <li @click="handleDeleteSession" class="danger">删除会话</li>
    </ul>

    <!-- 编辑会话名称模态框 -->
    <div v-if="editSessionModal.show" class="modal-mask" @click.self="editSessionModal.show = false">
      <div class="card modal">
        <div class="modal-header">
          <h4>重命名会话</h4>
          <button class="close-btn" @click="editSessionModal.show = false">×</button>
        </div>
        <div class="modal-content">
          <div class="form-group">
            <label>会话名称</label>
            <input 
              v-model="editSessionModal.newTitle" 
              class="pill-input" 
              placeholder="输入新的会话名称"
              @keyup.enter="confirmEditSessionName"
              ref="editSessionInput"
            />
          </div>
          <div class="modal-actions">
            <button class="pill-btn ghost" @click="editSessionModal.show = false">取消</button>
            <button class="pill-btn" @click="confirmEditSessionName" :disabled="!editSessionModal.newTitle.trim()">确认</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 删除节点确认对话框 -->
    <div v-if="deleteNodeModal.show" class="modal-mask" @click.self="cancelDeleteNode">
      <div class="card modal">
        <div class="modal-header">
          <h4>确认删除</h4>
          <button class="close-btn" @click="cancelDeleteNode">×</button>
        </div>
        <div class="modal-content">
          <div class="delete-warning">
            <div class="warning-text">
              <p><strong>确定要删除节点吗？</strong></p>
              <p>节点内容：<span class="node-content">{{ deleteNodeModal.nodeLabel }}</span></p>
              <p v-if="deleteNodeModal.mode === 'subtree'" class="warning-detail">
                此操作将删除该节点及其所有子节点，此操作不可撤销。
              </p>
              <p v-else class="warning-detail">
                此操作将删除该节点，此操作不可撤销。
              </p>
            </div>
          </div>
          <div class="modal-actions">
            <button class="pill-btn ghost" @click="cancelDeleteNode">取消</button>
            <button class="pill-btn danger" @click="confirmDeleteNode">确认删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 悬浮提示 -->
    <div
      v-if="tooltip.show"
      class="tooltip"
      :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }"
    >
      <div class="tooltip-content">
        {{ tooltip.text }}
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 聊天界面布局 */
.chat-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg);
}

/* 头部样式 */
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border);
  background: var(--card);
  border-radius: 0;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}

.chat-header .left {
  font-weight: 600;
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 12px;
}

.logout-btn {
  background: #e74c3c;
  color: white;
  border: none;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-weight: 500;
}

.logout-btn:hover {
  background: #c0392b;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(231, 76, 60, 0.3);
}

.logout-btn:active {
  transform: translateY(0);
  box-shadow: 0 1px 4px rgba(231, 76, 60, 0.3);
}

.chat-header .center {
  color: var(--muted);
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.loading-dot {
  color: var(--accent);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

/* 主体布局 */
.chat-body {
  display: flex;
  flex: 1;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
}

/* 会话列表样式 */
.sessions {
  width: 280px;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  padding: 0;
  overflow: hidden;
}

.sessions-header {
  padding: 16px;
  border-bottom: 1px solid var(--border);
  background: var(--card);
}

.button-group {
  display: flex;
  gap: 8px;
  justify-content: space-between;
}

.button-group .pill-btn {
  flex: 1;
  min-width: 0;
}

.sessions-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  padding: 12px 16px;
  margin: 4px 0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
  background: transparent;
}

.session-item:hover {
  background: var(--accent-weak);
  border-color: var(--accent);
}

.session-item.active {
  background: var(--accent);
  color: white;
  border-color: var(--accent);
}

.muted {
  color: var(--muted);
  font-size: 14px;
  text-align: center;
  padding: 16px;
}

/* 聊天窗口样式 */
.chat-window {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  padding: 0;
  overflow: hidden;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message {
  display: flex;
  margin-bottom: 16px;
}

.message.user {
  justify-content: flex-end;
}

.message.assistant {
  justify-content: flex-start;
}

.bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 18px;
  word-wrap: break-word;
  line-height: 1.4;
  overflow-wrap: break-word;
}

.bubble :deep(.markdown-content) {
  margin: 0;
}

.bubble :deep(.markdown-content > *:first-child) {
  margin-top: 0;
}

.bubble :deep(.markdown-content > *:last-child) {
  margin-bottom: 0;
}

.message.user .bubble {
  background: var(--accent);
  color: white;
  border-bottom-right-radius: 4px;
}

.message.assistant .bubble {
  background: var(--card);
  color: var(--text);
  border: 1px solid var(--border);
  border-bottom-left-radius: 4px;
}

/* 输入区域样式 */
.composer {
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid var(--border);
  background: var(--card);
  align-items: center;
}

.composer .pill-input {
  flex: 1;
  margin: 0;
}

.composer .pill-btn {
  white-space: nowrap;
}

.composer .pill-btn.toggle {
  font-size: 12px;
  padding: 8px 12px;
}

.rag-checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s ease;
  border: 1px solid var(--border);
  background: var(--card);
  white-space: nowrap;
  font-size: 14px;
  color: var(--text);
}

.rag-checkbox:hover {
  background: var(--accent-weak);
  border-color: var(--accent);
}

.rag-checkbox input[type="checkbox"] {
  margin: 0;
  cursor: pointer;
  width: 16px;
  height: 16px;
  accent-color: var(--accent);
}

.rag-checkbox input[type="checkbox"]:checked + span {
  font-weight: 500;
  color: var(--accent);
}

/* 缩略图样式 */
.thumbs {
  width: auto;
  border-radius: 12px;
  padding: 0;
  overflow: hidden;
  position: relative;
}

.thumbs-inner {
  height: 100%;
  overflow-y: auto;
  padding: 16px;
  position: relative;
}

.thumb-row {
  margin-bottom: 12px;
  position: relative;
  height: 70px;
}

.nodes-container {
  display: flex;
  align-items: center;
  height: 100%;
  gap: 8px;
  padding: 0 8px;
  flex-wrap: wrap;
}

.node {
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s ease;
  min-width: 60px;
  max-width: 140px;
  text-align: center;
  border: 1px solid;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.main-node {
  background: #4285F4;
  color: white;
  border-color: #4285F4;
  width: 110px;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(66, 133, 244, 0.3);
}

.main-node:hover {
  background: #3367D6;
  border-color: #3367D6;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(66, 133, 244, 0.4);
}

.main-node.selected {
  background: #3367D6;
  border-color: #3367D6;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.3);
}

.branch-node {
  background: white;
  color: #333;
  border-color: #ddd;
  width: 110px;
  flex-shrink: 1;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.branch-node:hover {
  background: #f5f5f5;
  border-color: #bbb;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.branch-node.selected {
  background: #f0f8ff;
  border-color: #4285F4;
  color: #4285F4;
  box-shadow: 0 0 0 2px rgba(66, 133, 244, 0.3);
}

.node.onpath {
  border-color: #007AFF;
}

.node.more {
  background: var(--muted);
  color: white;
  border-color: var(--muted);
}

.node-content-wrapper {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: 100%;
  height: 100%;
  padding: 0 8px;
  gap: 8px;
}

.height-circle {
  position: relative;
  top: auto;
  right: auto;
  width: 24px;
  height: 24px;
  background: #B2E0B2;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: bold;
  flex-shrink: 0;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.node-text {
  flex: 1;
  text-align: left;
  line-height: 1.3;
  font-size: 14px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 32px;
  overflow: hidden;
}

.main-node .node-text {
  color: white;
}

.branch-node .node-text {
  color: #333;
}

.text-line {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  width: 100%;
}

.height-circle.small {
  position: relative;
  top: auto;
  right: auto;
  width: 16px;
  height: 16px;
  font-size: 9px;
  margin-left: 8px;
}

.node-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.node-text {
  flex: 1;
  text-align: left;
}

.connectors {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
  z-index: 1;
}

/* 模态框样式 */
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  max-width: 400px;
  width: 90%;
  max-height: 80vh;
  overflow-y: auto;
}

.modal h4 {
  margin: 0 0 16px 0;
  color: var(--text);
}

.nodes-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nodes-list .node {
  cursor: pointer;
  text-align: left;
}

/* 更多节点弹窗样式 */
.more-nodes-modal {
  max-width: 600px;
  width: 95%;
  max-height: 70vh;
  padding: 0;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
  background: var(--card);
}

.modal-header h4 {
  margin: 0;
  color: var(--text);
  font-size: 18px;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  color: var(--muted);
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: var(--accent-weak);
  color: var(--text);
}

.modal-content {
  padding: 20px 24px;
  max-height: calc(70vh - 80px);
  overflow-y: auto;
}

.nodes-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.modal-node {
  background: white;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.modal-node:hover {
  background: var(--accent-weak);
  border-color: var(--accent);
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.modal-node-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.modal-height-circle {
  width: 20px;
  height: 20px;
  background: #B2E0B2;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: bold;
  flex-shrink: 0;
}

.modal-node-text {
  flex: 1;
  color: var(--text);
  font-size: 14px;
  line-height: 1.4;
  word-wrap: break-word;
}

/* 右键菜单样式 */
.ctx-menu {
  position: fixed;
  z-index: 1001;
  padding: 8px 0;
  min-width: 120px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.ctx-menu li {
  padding: 8px 16px;
  cursor: pointer;
  transition: background 0.2s ease;
  list-style: none;
  color: var(--text);
}

.ctx-menu li:hover {
  background: var(--accent-weak);
}

.ctx-menu li.danger {
  color: #e74c3c;
}

.ctx-menu li.danger:hover {
  background: #fdf2f2;
  color: #c0392b;
}

/* 会话右键菜单样式 */
.session-ctx-menu {
  min-width: 140px;
}

/* 编辑会话名称模态框样式 */
.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
  color: var(--text);
}

.modal-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 20px;
}

.modal-actions .pill-btn {
  min-width: 80px;
}

.modal-actions .pill-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 删除节点确认对话框样式 */
.delete-warning {
  padding: 20px 0;
}

.warning-text {
  color: var(--text);
}

.warning-text p {
  margin: 8px 0;
  line-height: 1.5;
}

.warning-text p:first-child {
  font-size: 16px;
  margin-bottom: 12px;
}

.node-content {
  background: var(--accent-weak);
  padding: 4px 8px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 14px;
  color: var(--accent);
  word-break: break-all;
}

.warning-detail {
  color: var(--muted);
  font-size: 14px;
  margin-top: 12px !important;
}

.modal-actions .pill-btn.danger {
  background: #e74c3c;
  color: white;
  border-color: #e74c3c;
}

.modal-actions .pill-btn.danger:hover {
  background: #c0392b;
  border-color: #c0392b;
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(231, 76, 60, 0.3);
}

/* 悬浮提示样式 */
.tooltip {
  position: fixed;
  z-index: 1002;
  pointer-events: none;
  max-width: 300px;
  transform: translate(-50%, -100%);
  margin-top: -8px;
  animation: tooltipFadeIn 0.2s ease-out;
}

@keyframes tooltipFadeIn {
  from {
    opacity: 0;
    transform: translate(-50%, -100%) scale(0.9);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -100%) scale(1);
  }
}

.tooltip-content {
  background: rgba(0, 0, 0, 0.9);
  color: white;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.4;
  word-wrap: break-word;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  position: relative;
  backdrop-filter: blur(4px);
}

.tooltip-content::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border: 5px solid transparent;
  border-top-color: rgba(0, 0, 0, 0.9);
}

/* 响应式悬浮提示 */
@media (max-width: 768px) {
  .tooltip {
    max-width: 250px;
    font-size: 12px;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .chat-body {
    flex-direction: column;
    gap: 12px;
  }
  
  .sessions, .thumbs {
    width: 100%;
    height: 200px;
  }
  
  .chat-window {
    flex: 1;
    min-height: 400px;
  }
  
  .bubble {
    max-width: 85%;
  }
  
  .composer {
    flex-wrap: wrap;
  }
  
  .composer .pill-btn.toggle {
    order: -1;
    width: 100%;
    margin-bottom: 8px;
  }
}

/* 加载状态样式 */
.loading-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 20px;
  color: var(--muted);
  font-size: 14px;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--border);
  border-top: 2px solid var(--accent);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 空状态样式 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
  color: var(--muted);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.6;
}

.empty-state p {
  margin: 8px 0;
  font-size: 16px;
}

.empty-hint {
  font-size: 14px;
  opacity: 0.8;
}

/* 滚动条样式 */
.sessions-list::-webkit-scrollbar,
.messages::-webkit-scrollbar,
.thumbs-inner::-webkit-scrollbar {
  width: 6px;
}

.sessions-list::-webkit-scrollbar-track,
.messages::-webkit-scrollbar-track,
.thumbs-inner::-webkit-scrollbar-track {
  background: transparent;
}

.sessions-list::-webkit-scrollbar-thumb,
.messages::-webkit-scrollbar-thumb,
.thumbs-inner::-webkit-scrollbar-thumb {
  background: var(--border);
  border-radius: 3px;
}

.sessions-list::-webkit-scrollbar-thumb:hover,
.messages::-webkit-scrollbar-thumb:hover,
.thumbs-inner::-webkit-scrollbar-thumb:hover {
  background: var(--muted);
}
</style>