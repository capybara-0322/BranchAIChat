<template>
  <div class="markdown-content" v-html="renderedMarkdown"></div>
</template>

<script setup lang="ts">
import { computed, onMounted, nextTick } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

interface Props {
  content: string
}

const props = defineProps<Props>()

// 配置marked
marked.setOptions({
  highlight: function(code: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (err) {
        console.warn('Code highlighting error:', err)
      }
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true,
  gfm: true,
  pedantic: false,
  sanitize: false,
  smartLists: true,
  smartypants: false
})

const renderedMarkdown = computed(() => {
  if (!props.content) return ''
  return marked(props.content)
})

// 确保代码高亮在内容更新后重新应用
onMounted(async () => {
  await nextTick()
  highlightCodeBlocks()
})

// 监听内容变化，重新高亮代码
const highlightCodeBlocks = () => {
  const codeBlocks = document.querySelectorAll('.markdown-content pre code')
  codeBlocks.forEach((block) => {
    hljs.highlightElement(block as HTMLElement)
  })
}

// 当内容变化时重新高亮
import { watch } from 'vue'
watch(() => props.content, async () => {
  await nextTick()
  highlightCodeBlocks()
})
</script>

<style scoped>
.markdown-content {
  line-height: 1.6;
  color: var(--text);
}

/* 标题样式 */
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 1.5em 0 0.5em 0;
  font-weight: 600;
  line-height: 1.25;
  color: var(--text);
}

.markdown-content :deep(h1) {
  font-size: 1.5em;
  border-bottom: 1px solid var(--border);
  padding-bottom: 0.3em;
}

.markdown-content :deep(h2) {
  font-size: 1.3em;
  border-bottom: 1px solid var(--border);
  padding-bottom: 0.3em;
}

.markdown-content :deep(h3) {
  font-size: 1.1em;
}

/* 段落样式 */
.markdown-content :deep(p) {
  margin: 0.8em 0;
}

/* 列表样式 */
.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0.8em 0;
  padding-left: 1.5em;
}

.markdown-content :deep(li) {
  margin: 0.3em 0;
}

/* 引用样式 */
.markdown-content :deep(blockquote) {
  margin: 1em 0;
  padding: 0 1em;
  border-left: 4px solid var(--accent);
  background: var(--accent-weak);
  color: var(--muted);
  font-style: italic;
}

/* 代码样式 */
.markdown-content :deep(code) {
  background: var(--accent-weak);
  color: var(--accent);
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
}

.markdown-content :deep(pre) {
  background: #f6f8fa;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 1em;
  margin: 1em 0;
  overflow-x: auto;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
  line-height: 1.4;
}

.markdown-content :deep(pre code) {
  background: none;
  color: inherit;
  padding: 0;
  border-radius: 0;
  font-size: inherit;
}

/* 表格样式 */
.markdown-content :deep(table) {
  border-collapse: collapse;
  margin: 1em 0;
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: hidden;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid var(--border);
  padding: 0.6em 1em;
  text-align: left;
}

.markdown-content :deep(th) {
  background: var(--accent-weak);
  font-weight: 600;
  color: var(--accent);
}

.markdown-content :deep(tr:nth-child(even)) {
  background: #f8f9fa;
}

/* 链接样式 */
.markdown-content :deep(a) {
  color: var(--accent);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: all 0.2s ease;
}

.markdown-content :deep(a:hover) {
  border-bottom-color: var(--accent);
  text-decoration: none;
}

/* 分割线样式 */
.markdown-content :deep(hr) {
  border: none;
  height: 1px;
  background: var(--border);
  margin: 2em 0;
}

/* 强调样式 */
.markdown-content :deep(strong) {
  font-weight: 600;
  color: var(--text);
}

.markdown-content :deep(em) {
  font-style: italic;
  color: var(--muted);
}

/* 删除线样式 */
.markdown-content :deep(del) {
  text-decoration: line-through;
  color: var(--muted);
}

/* 内联代码在用户消息中的特殊样式 */
.message.user .markdown-content :deep(code) {
  background: rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.9);
}

.message.user .markdown-content :deep(pre) {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.9);
}

.message.user .markdown-content :deep(pre code) {
  background: none;
  color: inherit;
}

/* 响应式表格 */
@media (max-width: 768px) {
  .markdown-content :deep(table) {
    font-size: 0.9em;
  }
  
  .markdown-content :deep(th),
  .markdown-content :deep(td) {
    padding: 0.4em 0.6em;
  }
}
</style>
