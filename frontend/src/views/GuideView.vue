<script setup>
import { marked } from 'marked'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getChatSessions, saveChatSession, streamChat } from '../api/chat'
import { useUserStore } from '../stores/user'

// 配置marked启用XSS防护
marked.setOptions({
  breaks: true,
  gfm: true
})

const route = useRoute()
const userStore = useUserStore()
const messages = ref([])
const inputText = ref(route.query.topic || '')
const isStreaming = ref(false)
const messagesEl = ref(null)
const inputEl = ref(null)
const sessions = ref([])
const currentSession = ref(null)
const currentSessionTitle = ref('AI 非遗文化导游')
const serviceError = ref('')
const loadingSessions = ref(false)

const quickPrompts = ['推荐苗族文化游览路线', '昆曲的历史渊源是什么？', '非遗体验工坊预约', '江南地区非遗推荐', '中医针灸的传承历史']
const suggestionCards = computed(() => [
  { title: '读懂当前主题', desc: '让 AI 从历史、技艺与地域文化三个维度解释。', prompt: `帮我系统介绍一下${route.query.topic || '一个非遗项目'}。` },
  { title: '生成体验建议', desc: '把介绍变成可落地的体验清单与打卡路径。', prompt: `围绕${route.query.topic || '非遗主题'}，推荐适合游客的一日体验安排。` },
  { title: '做路线延伸', desc: '把单个项目扩展成完整行程或专题探索路线。', prompt: `以${route.query.topic || '非遗主题'}为核心，规划一条 3 天游览路线。` },
])

function renderMarkdown(text) {
  if (!text) return ''
  const html = marked.parse(text || '')
  // 移除危险的HTML标签和事件属性，防止XSS攻击
  return html
    .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
    .replace(/<iframe\b[^<]*(?:(?!<\/iframe>)<[^<]*)*<\/iframe>/gi, '')
    .replace(/\s*on\w+\s*=\s*(['"])[^'"]*\1/gi, '')
    .replace(/javascript:/gi, '')
    .replace(/data:/gi, '')
}

async function scrollToBottom() {
  await nextTick()
  if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight
}

function autoResize() {
  if (!inputEl.value) return
  inputEl.value.style.height = 'auto'
  inputEl.value.style.height = `${Math.min(inputEl.value.scrollHeight, 140)}px`
}

function buildSessionTitle(text) {
  return text.length > 18 ? `${text.slice(0, 18)}...` : text
}

function normalizeErrorMessage(error) {
  const raw = error?.response?.data?.msg || error?.message || ''
  if (!raw) return 'AI 服务暂时不可用，请稍后再试。'
  if (/尚未配置|api key/i.test(raw)) return 'AI 服务尚未配置完成，当前无法发起对话。'
  if (/timeout|timed out|超时/i.test(raw)) return 'AI 响应超时，请稍后重试。'
  if (/network error|failed to fetch|连接失败|无法连接服务/i.test(raw)) return '当前网络连接异常，暂时无法连接 AI 服务。'
  if (/登录状态已失效|401/.test(raw)) return '登录状态已失效，请重新登录后继续。'
  return raw
}

async function persistSession() {
  if (!userStore.userInfo?.id || !messages.value.length) return
  const payload = {
    id: currentSession.value,
    userId: userStore.userInfo.id,
    title: currentSessionTitle.value,
    messages: JSON.stringify(messages.value.map(({ role, content }) => ({ role, content }))),
  }
  try {
    await saveChatSession(payload)
  } catch {
    localStorage.setItem('yiyun_chat_sessions', JSON.stringify(sessions.value))
  }
}

async function loadSessions() {
  if (!userStore.userInfo?.id) return
  loadingSessions.value = true
  serviceError.value = ''
  try {
    const res = await getChatSessions(userStore.userInfo.id)
    sessions.value = (res.data || []).map((item) => ({
      id: item.id,
      title: item.title,
      time: item.updatedAt || item.createdAt || '刚刚',
      messages: (() => {
        try { return JSON.parse(item.messages || '[]') } catch { return [] }
      })(),
    }))
    if (sessions.value[0]) {
      currentSession.value = sessions.value[0].id
      currentSessionTitle.value = sessions.value[0].title
      messages.value = sessions.value[0].messages
    }
  } catch (error) {
    const localSessions = JSON.parse(localStorage.getItem('yiyun_chat_sessions') || '[]')
    sessions.value = localSessions
    if (!localSessions.length) {
      serviceError.value = normalizeErrorMessage(error)
    }
  } finally {
    loadingSessions.value = false
  }
}

async function sendMessage(text) {
  inputText.value = text
  await handleSend()
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || isStreaming.value) return

  serviceError.value = ''

  if (!currentSession.value) {
    currentSession.value = Date.now()
    currentSessionTitle.value = buildSessionTitle(text)
    sessions.value.unshift({ id: currentSession.value, title: currentSessionTitle.value, time: '刚刚', messages: [] })
  }

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  if (inputEl.value) inputEl.value.style.height = 'auto'
  await scrollToBottom()
  isStreaming.value = true

  const aiMsg = { role: 'assistant', content: '', streaming: true, isError: false }
  messages.value.push(aiMsg)
  const payload = { messages: messages.value.filter((item) => !item.streaming).map(({ role, content }) => ({ role, content })) }

  streamChat(
    payload,
    (chunk) => {
      aiMsg.content += chunk
      scrollToBottom()
    },
    async () => {
      aiMsg.streaming = false
      isStreaming.value = false
      const current = sessions.value.find((item) => item.id === currentSession.value)
      if (current) {
        current.messages = messages.value.map(({ role, content, isError }) => ({ role, content, ...(isError ? { isError: true } : {}) }))
        current.title = currentSessionTitle.value
      }
      await persistSession()
    },
    async (err) => {
      const normalizedMessage = normalizeErrorMessage(err)
      aiMsg.streaming = false
      aiMsg.isError = true
      aiMsg.content = normalizedMessage
      isStreaming.value = false
      serviceError.value = normalizedMessage
      await scrollToBottom()
    }
  )
}

function openSession(session) {
  currentSession.value = session.id
  currentSessionTitle.value = session.title
  messages.value = [...(session.messages || [])]
}

function newChat() {
  currentSession.value = null
  currentSessionTitle.value = 'AI 非遗文化导游'
  messages.value = []
  inputText.value = route.query.topic || ''
}

watch(() => route.query.topic, (topic) => {
  if (topic && !messages.value.length) inputText.value = topic
})

onMounted(async () => {
  await userStore.hydrateUser()
  await loadSessions()
  autoResize()
})
</script>

<template>
  <div class="guide-page page-container">
    <div class="guide-layout">
      <aside class="session-sidebar glass-card">
        <button class="btn-primary new-chat-btn" @click="newChat">
          <span>＋</span> 新建对话
        </button>
        <div class="session-list">
          <div v-for="s in sessions" :key="s.id" class="session-item" :class="{active: currentSession===s.id}" @click="openSession(s)">
            <span class="session-icon">💬</span>
            <div class="session-info">
              <span class="session-title">{{ s.title }}</span>
              <span class="session-time">{{ s.time }}</span>
            </div>
          </div>
        </div>
        <div class="quick-prompts">
          <h4>快速提问</h4>
          <div v-for="p in quickPrompts" :key="p" class="quick-prompt" @click="sendMessage(p)">{{ p }}</div>
        </div>
      </aside>

      <main class="chat-main">
        <div class="chat-header glass-card">
          <div class="chat-model-badge">
            <span class="model-dot animate-live-dot"></span>
            遗韵 AI · 非遗文化专家
          </div>
          <span class="chat-title">{{ currentSessionTitle }}</span>
        </div>

        <div v-if="serviceError" class="service-status glass-card">
          <div class="service-status-title">当前 AI 服务不可用</div>
          <p>{{ serviceError }}</p>
        </div>

        <div class="guide-insights" v-if="!messages.length && !serviceError">
          <div class="insight-card glass-card" v-for="card in suggestionCards" :key="card.title" @click="sendMessage(card.prompt)">
            <h3>{{ card.title }}</h3>
            <p>{{ card.desc }}</p>
          </div>
        </div>

        <div class="messages-area" ref="messagesEl">
          <div v-if="messages.length === 0" class="welcome-area">
            <div class="welcome-icon">🏛️</div>
            <h2>您好，我是「遗韵」</h2>
            <p>可以帮您理解非遗项目、制定路线、推荐体验方式，也能把当前页面内容继续展开。</p>
          </div>

          <div v-for="(msg, idx) in messages" :key="idx" class="message-row" :class="[msg.role, { error: msg.isError }]">
            <div v-if="msg.role==='assistant'" class="avatar ai-avatar">华</div>
            <div class="bubble" :class="[msg.role, { error: msg.isError }]">
              <div class="bubble-content" v-html="renderMarkdown(msg.content)"></div>
              <div v-if="msg.streaming" class="typing-cursor"></div>
            </div>
            <div v-if="msg.role==='user'" class="avatar user-avatar">
              <img :src="userStore.userInfo?.avatar || 'https://api.dicebear.com/9.x/adventurer/svg?seed=user'" alt="用户" />
            </div>
          </div>
        </div>

        <div class="input-area glass-card">
          <div class="input-tools">
            <button class="tool-btn" title="插入当前主题" @click="inputText = route.query.topic || inputText">🏷️</button>
            <button class="tool-btn" title="路线建议" @click="sendMessage('请为我推荐一条 3 天游览路线')">🗺️</button>
          </div>
          <textarea
            ref="inputEl"
            v-model="inputText"
            class="chat-input"
            placeholder="输入您的问题，例如：昆曲为什么被称为百戏之祖？"
            rows="1"
            @keydown.enter.exact.prevent="handleSend"
            @keydown.shift.enter.exact="inputText += '\n'"
            @input="autoResize"
          ></textarea>
          <button class="send-btn" :class="{ loading: isStreaming }" :disabled="isStreaming || !inputText.trim()" @click="handleSend">
            <span v-if="!isStreaming">发送</span>
            <span v-else class="loading-dots"><i></i><i></i><i></i></span>
          </button>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.guide-page { height: 100vh; }
.guide-layout { display: grid; grid-template-columns: 260px 1fr; height: calc(100vh - var(--header-height)); }
.session-sidebar { margin: 16px 0 16px 16px; padding: 16px; display: flex; flex-direction: column; gap: 12px; overflow-y: auto; border-radius: var(--radius-lg); }
.new-chat-btn { width: 100%; justify-content: center; gap: 8px; font-size: 14px; }
.session-list { flex: 1; display: flex; flex-direction: column; gap: 4px; overflow-y: auto; }
.session-item { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-radius: var(--radius-md); cursor: pointer; transition: background var(--transition-fast); }
.session-item:hover, .session-item.active { background: var(--bg-glass-hover); }
.session-icon { font-size: 16px; }
.session-info { flex: 1; overflow: hidden; }
.session-title { display: block; font-size: 13px; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.session-time { font-size: 11px; color: var(--text-muted); }
.quick-prompts h4 { font-size: 11px; color: var(--text-muted); font-weight: 600; letter-spacing: 0.08em; margin-bottom: 8px; }
.quick-prompt { font-size: 12px; color: var(--text-secondary); padding: 7px 10px; border-radius: var(--radius-sm); cursor: pointer; border: 1px solid transparent; transition: all var(--transition-fast); }
.quick-prompt:hover { background: var(--bg-glass); border-color: var(--bg-glass-border); color: var(--text-primary); }
.chat-main { display: flex; flex-direction: column; height: 100%; padding: 16px 16px 16px 0; gap: 12px; overflow: hidden; }
.chat-header { padding: 12px 20px; display: flex; align-items: center; justify-content: space-between; border-radius: var(--radius-lg); flex-shrink: 0; }
.chat-model-badge { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-secondary); }
.model-dot { width: 7px; height: 7px; border-radius: 50%; background: #4A9B7F; }
.chat-title { font-size: 15px; font-weight: 600; color: var(--text-primary); font-family: var(--font-serif); }
.service-status { padding: 16px 18px; border-radius: var(--radius-lg); border: 1px solid rgba(201, 71, 44, 0.18); background: rgba(255, 249, 245, 0.96); }
.service-status-title { font-size: 14px; font-weight: 700; color: var(--color-primary-dark); margin-bottom: 6px; }
.service-status p { color: var(--text-secondary); line-height: 1.7; }
.guide-insights { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.insight-card { padding: 18px; cursor: pointer; }
.insight-card p { color: var(--text-secondary); margin-top: 8px; line-height: 1.7; }
.messages-area { flex: 1; overflow-y: auto; padding: 12px 0; display: flex; flex-direction: column; gap: 16px; }
.welcome-area { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; color: var(--text-secondary); }
.welcome-icon { font-size: 56px; margin-bottom: 16px; }
.welcome-area h2 { font-family: var(--font-serif); font-size: 24px; color: var(--text-primary); margin-bottom: 10px; }
.welcome-area p { font-size: 14px; line-height: 1.8; }
.message-row { display: flex; align-items: flex-start; gap: 10px; }
.message-row.user { flex-direction: row-reverse; }
.avatar { width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0; }
.ai-avatar { background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)); display: flex; align-items: center; justify-content: center; font-family: var(--font-serif); font-size: 16px; font-weight: 700; color: #fff; }
.user-avatar img { width: 36px; height: 36px; border-radius: 50%; }
.bubble { max-width: 72%; padding: 12px 16px; border-radius: var(--radius-lg); line-height: 1.7; font-size: 14px; position: relative; }
.bubble.assistant { background: var(--bg-card); border: 1px solid var(--bg-glass-border); color: var(--text-primary); border-bottom-left-radius: 4px; }
.bubble.assistant.error { background: rgba(255, 247, 243, 0.98); border-color: rgba(201, 71, 44, 0.24); color: var(--color-primary-dark); }
.bubble.user { background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)); color: #fff; border-bottom-right-radius: 4px; }
.bubble-content :deep(h2), .bubble-content :deep(h3) { font-family: var(--font-serif); margin: 10px 0 6px; }
.bubble-content :deep(p) { margin-bottom: 8px; }
.bubble-content :deep(ul), .bubble-content :deep(ol) { padding-left: 20px; margin-bottom: 8px; }
.typing-cursor { display: inline-block; width: 2px; height: 16px; background: var(--color-accent); margin-left: 2px; vertical-align: text-bottom; animation: blink 0.75s step-end infinite; }
.input-area { display: flex; align-items: flex-end; gap: 8px; padding: 12px 16px; border-radius: var(--radius-lg); flex-shrink: 0; }
.input-tools { display: flex; flex-direction: column; gap: 6px; }
.tool-btn { width: 32px; height: 32px; background: var(--bg-glass); border: 1px solid var(--bg-glass-border); border-radius: 8px; cursor: pointer; font-size: 14px; }
.chat-input { flex: 1; background: transparent; border: none; outline: none; color: var(--text-primary); font-size: 14px; font-family: var(--font-sans); resize: none; line-height: 1.6; max-height: 140px; }
.send-btn { padding: 10px 20px; background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)); color: #fff; border: none; border-radius: var(--radius-full); font-size: 14px; cursor: pointer; min-width: 72px; }
.send-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.loading-dots { display: flex; gap: 4px; align-items: center; justify-content: center; }
.loading-dots i { width: 5px; height: 5px; border-radius: 50%; background: #fff; animation: liveDot 0.8s ease-in-out infinite; }
.loading-dots i:nth-child(2) { animation-delay: 0.15s; }
.loading-dots i:nth-child(3) { animation-delay: 0.3s; }
@media (max-width: 900px) { .guide-insights { grid-template-columns: 1fr; } }
@media (max-width: 768px) { .guide-layout { grid-template-columns: 1fr; } .session-sidebar { display: none; } .chat-main { padding: 12px; } }
</style>
