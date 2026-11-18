<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { marked } from 'marked'
import { generateImage as callBaiduGenerate } from '../api/creative'
import { streamChatDirect } from '../api/deepseek'
import { useUserStore } from '../stores/user'
import { saveChatHistory, getChatHistory, deleteChatHistory } from '../api/chat'
import { createGenerateTask, getTaskStatus, saveArtwork, getMyArtworks, getMyTasks, deleteArtwork, getHeritageListForAIGC } from '../api/aigc'
import { CATEGORY_OPTIONS } from '../utils/constants'
import Pagination from '../components/Pagination.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const userId = computed(() => userStore.userInfo?.id || userStore.userInfo?.username || 'default')

const activeTab = ref('chat') // 'chat' or 'aigc'

// RAG 问答状态
const chatPrompt = ref('')
const chatHistory = ref([
  { role: 'assistant', content: '你好，我是千年非遗守护人，你可以向我询问任何关于中国非物质文化遗产的知识！' }
])
const chatLoading = ref(false)

// 图片和语音相关
const selectedImage = ref(null) // 选中的图片
const imagePreview = ref('') // 图片预览
const galleryPreviewUrl = ref('') // 画廊图片放大预览
const galleryPreviewName = ref('') // 预览图片名称
const isRecording = ref(false) // 是否正在录音
let recognition = null // 浏览器语音识别实例
const showActionMenu = ref(false) // 是否显示操作菜单

// ==================== AIGC 多模态生成 ====================
const imagePrompt = ref('')
const imageLoading = ref(false)
const gallery = ref([])
const galleryCurrentPage = ref(1)
const galleryPageSize = ref(6)
const currentTaskId = ref(null) // 当前生成任务的ID

// 对话历史原始数据
const allChatHistoryItems = ref([])

// 当前对话会话ID（同一轮对话共享）
const currentSessionId = ref(null)

// ==================== 非遗关联 ====================
const heritageList = ref([])
const selectedHeritageId = ref(null)

// ==================== 搜索筛选 ====================
const filterCategory = ref('')
const filterKeyword = ref('')
const showFilterPanel = ref(false)

// ==================== 作品元数据编辑 ====================
const showMetadataModal = ref(false)
const artworkForm = ref({
  title: '',
  description: '',
  category: '',
  tags: '',
  heritageId: null
})
const pendingImageUrl = ref('') // 待保存的图片URL

async function saveToAIHistory(q, history) {
  try {
    await saveChatHistory(q, history, currentSessionId.value)
    // 同步更新画廊中的对话历史卡片
    await loadChatHistoryToGallery()
  } catch (error) {
    console.error('❌ 保存AI历史失败:', error)
    // 如果API失败，回退到localStorage
    const key = `yiyun_ai_history_${userId.value}`
    const saved = localStorage.getItem(key)
    const records = saved ? JSON.parse(saved) : []

    const now = new Date()
    const timeStr = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`

    records.unshift({
      q,
      time: timeStr,
      history: history ? JSON.parse(JSON.stringify(history)) : []
    })

    if (records.length > 20) {
      records.pop()
    }

    localStorage.setItem(key, JSON.stringify(records))
  }
}

// 新建对话
function startNewChat() {
  chatHistory.value = [
    { role: 'assistant', content: '你好，我是千年非遗守护人，你可以向我询问任何关于中国非物质文化遗产的知识！' }
  ]
  currentSessionId.value = null
  chatPrompt.value = ''
}

function restoreChatHistory() {
  const restoreKey = `yiyun_ai_restore_${userId.value}`
  const saved = localStorage.getItem(restoreKey)
  
  if (saved) {
    try {
      const data = JSON.parse(saved)
      
      if (data.history && Array.isArray(data.history) && data.history.length > 0) {
        chatHistory.value = data.history
      } else if (data.q) {
        chatHistory.value = [
          { role: 'assistant', content: '你好，我是千年非遗守护人，你可以向我询问任何关于中国非物质文化遗产的知识！' },
          { role: 'user', content: data.q }
        ]
      }
      chatPrompt.value = data.q || ''
      localStorage.removeItem(restoreKey)
      
      if (route.query.restore) {
        const query = { ...route.query }
        delete query.restore
        router.replace({ query })
      }
    } catch (e) {
      console.error('恢复对话历史失败', e)
    }
  }
}

// 恢复未完成的生成任务（切换页面回来后继续轮询）
async function recoverPendingTasks() {
  try {
    const res = await getMyTasks()
    const tasks = res.data || []

    // 已保存作品对应的 taskId，避免对旧任务重复弹窗
    const savedTaskIds = new Set(
      gallery.value
        .filter(item => item.metadata?.taskId)
        .map(item => item.metadata.taskId)
    )

    for (const task of tasks) {
      if (task.taskType !== '图像') continue

      // 只处理最近 10 分钟内的任务，避免旧数据干扰
      const updatedAt = task.updatedAt ? new Date(task.updatedAt).getTime() : 0
      const thirtyMinutesAgo = Date.now() - 30 * 60 * 1000
      if (updatedAt < thirtyMinutesAgo) continue

      // 已完成但还没保存的 —— 自动保存
      if (task.status === 'COMPLETED' && !savedTaskIds.has(task.id)) {
        console.log('[生图-恢复] 发现未保存的已完成任务 taskId:', task.id)
        try {
          await saveArtwork({
            taskId: task.id,
            title: (task.prompt || '').substring(0, 50),
            description: task.prompt || '',
            fileUrl: task.resultUrl,
            category: '',
            tags: ''
          })
          galleryCurrentPage.value = 1
          await loadMyArtworks()
          message.success('图片生成完成！')
        } catch (e) {
          console.error('自动保存失败:', e)
        }
        imageLoading.value = false
        continue
      }

      // PENDING 或 PROCESSING —— 恢复轮询
      if (task.status === 'PENDING' || task.status === 'PROCESSING') {
        console.log('[生图-恢复] 发现未完成任务 taskId:', task.id)
        const placeholderId = 'gen-' + task.id
        if (!gallery.value.find(item => item.id === placeholderId)) {
          gallery.value.unshift({
            id: placeholderId,
            url: null,
            desc: task.prompt || '生成中...',
            isTemporary: true,
            type: '图像',
            isGenerating: true
          })
        }
        if (!pendingTasks.has(task.id)) {
          pendingTasks.set(task.id, { placeholderId, prompt: task.prompt || '' })
        }
        checkTaskStatus(task.id, placeholderId, task.prompt || '').then((result) => {
          if (result === 'PENDING') {
            schedulePoll(task.id, placeholderId, task.prompt || '')
          }
        })
      }

      // FAILED / 已保存的 COMPLETED / 其他状态 → 静默跳过
    }
  } catch (e) {
    console.error('[生图-恢复] 恢复任务失败:', e)
  }
}

onMounted(async () => {
  restoreChatHistory()
  await loadMyArtworks()
  loadHeritageList()
  recoverPendingTasks()
})

watch(() => route.query.restore, (val) => {
  if (val === 'true') {
    setTimeout(() => {
      restoreChatHistory()
    }, 100)
  }
})

async function askAI() {
  const q = chatPrompt.value.trim()
  if (!q && !selectedImage.value) {
    // 如果没有输入内容，切换菜单
    toggleActionMenu()
    return
  }
  
  // 构建用户消息
  const userMessage = {
    role: 'user',
    content: q,
    image: imagePreview.value || null
  }

  // 新对话时生成会话ID
  if (!currentSessionId.value) {
    currentSessionId.value = 'sess-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
  }

  chatHistory.value.push(userMessage)
  chatPrompt.value = ''
  clearImageSelection() // 清除已选图片
  chatLoading.value = true
  
  const assistantMsgIndex = chatHistory.value.length
  chatHistory.value.push({ role: 'assistant', content: '' })

  try {
    const messages = chatHistory.value
      .slice(0, assistantMsgIndex)
      .map(msg => ({ 
        role: msg.role, 
        content: msg.content,
        image: msg.image || null
      }))

    streamChatDirect(
      messages,
      (chunk) => {
        chatLoading.value = false
        chatHistory.value[assistantMsgIndex].content += chunk
      },
      () => {
        chatLoading.value = false
        if (!chatHistory.value[assistantMsgIndex].content) {
          chatHistory.value[assistantMsgIndex].content = 'AI 服务未返回内容，请检查 API Key 配置或稍后重试'
        }
        saveToAIHistory(q, chatHistory.value)
      },
      (err) => {
        chatLoading.value = false
        chatHistory.value[assistantMsgIndex].content = '请求失败：' + (err.message || err)
        message.error('请求失败')
      }
    )
  } catch (err) {
    chatHistory.value[assistantMsgIndex].content = '请求异常：' + (err.message || err)
    message.error('请求异常')
    chatLoading.value = false
  }
}

// 处理图片选择
function handleImageSelect(event) {
  const file = event.target.files[0]
  if (!file) return
  
  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    message.error('请选择图片文件')
    return
  }
  
  // 验证文件大小（最大5MB）
  if (file.size > 5 * 1024 * 1024) {
    message.error('图片大小不能超过5MB')
    return
  }
  
  selectedImage.value = file
  
  // 生成预览
  const reader = new FileReader()
  reader.onload = (e) => {
    imagePreview.value = e.target.result
  }
  reader.readAsDataURL(file)
  
  // 关闭菜单
  showActionMenu.value = false
}

// 清除选中的图片
function clearImageSelection() {
  selectedImage.value = null
  imagePreview.value = ''
  // 重置文件输入
  const fileInput = document.getElementById('chat-image-input')
  if (fileInput) fileInput.value = ''
}

// 切换操作菜单
function toggleActionMenu() {
  showActionMenu.value = !showActionMenu.value
}

// 选择操作类型
function handleActionSelect(action) {
  showActionMenu.value = false
  
  if (action === 'image') {
    // 触发图片上传
    document.getElementById('chat-image-input').click()
  } else if (action === 'voice') {
    // 开始录音
    startRecording()
  } else if (action === 'text') {
    // 聚焦输入框
    setTimeout(() => {
      document.querySelector('.chat-input').focus()
    }, 100)
  }
}

// 开始/停止录音
let shouldSubmitAfterStop = false

async function toggleRecording() {
  if (isRecording.value) {
    stopRecording()
  } else {
    startRecording()
  }
}

function startRecording() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRecognition) {
    message.error('您的浏览器不支持语音识别，请使用 Chrome 或 Edge')
    return
  }

  // 先终止可能残留的识别实例
  if (recognition) {
    recognition.abort()
  }

  recognition = new SpeechRecognition()
  recognition.lang = 'zh-CN'
  recognition.interimResults = true
  recognition.continuous = true
  recognition.maxAlternatives = 1
  shouldSubmitAfterStop = false

  recognition.onresult = (event) => {
    let finalText = ''
    let interimText = ''
    for (let i = 0; i < event.results.length; i++) {
      const result = event.results[i]
      if (result.isFinal) {
        finalText += result[0].transcript
      } else {
        interimText += result[0].transcript
      }
    }
    chatPrompt.value = finalText || interimText
  }

  recognition.onerror = (event) => {
    message.error('语音识别失败：' + event.error)
    isRecording.value = false
    shouldSubmitAfterStop = false
  }

  recognition.onend = () => {
    isRecording.value = false
    if (shouldSubmitAfterStop && chatPrompt.value) {
      shouldSubmitAfterStop = false
      askAI()
    }
  }

  recognition.start()
  isRecording.value = true
  showActionMenu.value = false
  message.info(' 正在聆听...（再次点击结束并发送）')
}

function stopRecording() {
  if (recognition) {
    shouldSubmitAfterStop = true
    recognition.stop()
    isRecording.value = false
  }
}

// 合并画廊：作品 + 对话历史，按时间倒序
const mergedGalleryItems = computed(() => {
  const artworks = gallery.value.map(item => ({ ...item, _sortTime: item.metadata?.createdAt || '' }))
  const chats = allChatHistoryItems.value.map(item => ({ ...item, _sortTime: item.metadata?.createdAt || '' }))
  const merged = [...artworks, ...chats]
  merged.sort((a, b) => {
    if (!a._sortTime) return 1
    if (!b._sortTime) return -1
    return b._sortTime.localeCompare(a._sortTime)
  })
  return merged
})
const galleryTotal = computed(() => mergedGalleryItems.value.length)
const paginatedGallery = computed(() => {
  const start = (galleryCurrentPage.value - 1) * galleryPageSize.value
  return mergedGalleryItems.value.slice(start, start + galleryPageSize.value)
})

// 加载我的作品
async function loadMyArtworks() {
  try {
    const queryParams = {
      page: 1,
      size: 9999,
      category: filterCategory.value || undefined,
      keyword: filterKeyword.value || undefined
    }
    const res = await getMyArtworks(queryParams)
    if (res.data && res.data.records && res.data.records.length > 0) {
      gallery.value = res.data.records.map(artwork => ({
        id: artwork.id,
        url: artwork.fileUrl,
        desc: artwork.description || artwork.title || '未命名作品',
        metadata: artwork,
        type: artwork.taskType || '图像'
      }))
    } else {
      gallery.value = []
    }

    // 加载对话历史（合并到同一列表）
    if (!filterCategory.value && !filterKeyword.value) {
      loadChatHistoryToGallery()
    } else {
      allChatHistoryItems.value = []
    }
  } catch (error) {
    console.error('加载作品失败:', error)
    gallery.value = []
  }
}

// 加载对话历史到画廊（按会话分组，一次对话=一张卡片）
async function loadChatHistoryToGallery() {
  try {
    const res = await getChatHistory()
    const list = res.data || []

    // 按 sessionId 分组
    const groups = {}
    for (const item of list) {
      const sid = item.sessionId || 'legacy-' + item.id
      if (!groups[sid]) groups[sid] = []
      groups[sid].push(item)
    }

    // 每组生成一张画廊卡片
    const items = Object.values(groups)
      .sort((a, b) => {
        const timeA = a[0]?.createdAt || ''
        const timeB = b[0]?.createdAt || ''
        return timeB.localeCompare(timeA)
      })
      .map(group => {
        // 组内按时间正序
        group.sort((a, b) => (a.createdAt || '').localeCompare(b.createdAt || ''))

        // 构建完整对话内容
        const content = group.map(item =>
          '**' + (item.question || '') + '**\n\n' + (item.answer || '')
        ).join('\n\n---\n\n')

        return {
          id: 'chat-' + (group[0].sessionId || group[0].id),
          url: null,
          desc: group[0].question || '对话记录',
          content: content,
          type: '文本',
          metadata: { createdAt: group[group.length - 1]?.createdAt || group[0]?.createdAt, model: group[0].model },
          isChatHistory: true,
          sessionId: group[0].sessionId,
          rawMessages: group.map(item => ({ question: item.question, answer: item.answer }))
        }
      })

    allChatHistoryItems.value = items
  } catch (e) {
    console.error('加载对话历史到画廊失败:', e)
  }
}

// 加载非遗项目列表
async function loadHeritageList() {
  try {
    console.log('🔍 开始加载非遗项目列表...')
    const res = await getHeritageListForAIGC()
    console.log(' 非遗项目API响应:', res)
    
    // 处理不同的响应数据结构
    let dataList = []
    if (res.data) {
      // 如果返回的是分页数据 { records: [...], total: 100 }
      if (Array.isArray(res.data.records)) {
        dataList = res.data.records
      } 
      // 如果直接返回数组
      else if (Array.isArray(res.data)) {
        dataList = res.data
      }
    }
    
    heritageList.value = dataList
    console.log('✅ 加载非遗项目成功，数量:', dataList.length)
    
    if (dataList.length === 0) {
      message.warning('️ 暂无非遗项目数据，请先在系统中添加非遗项目')
    }
  } catch (error) {
    console.error('❌ 加载非遗项目失败:', error)
    message.error('加载非遗项目失败：' + (error.message || '请检查后端服务'))
  }
}

// 应用筛选
function applyFilter() {
  galleryCurrentPage.value = 1
  loadMyArtworks()
  message.success('筛选已应用')
}

// 清除筛选
function clearFilter() {
  filterCategory.value = ''
  filterKeyword.value = ''
  galleryCurrentPage.value = 1
  loadMyArtworks()
  message.info('筛选已清除')
}

const pollingTimers = new Map()
const pendingTasks = new Map() // taskId → { placeholderId, prompt }

// 检查单个任务状态，返回 'DONE' 或 'PENDING'
async function checkTaskStatus(taskId, placeholderId, prompt) {
  console.log('[生图-轮询] 检查任务状态 taskId:', taskId)
  try {
    const res = await getTaskStatus(taskId)
    console.log('[生图-轮询] 查询结果:', res)
    const task = res.data
    if (task.status === 'COMPLETED') {
      pendingTasks.delete(taskId)
      const idx = gallery.value.findIndex(item => item.id === placeholderId)
      if (idx >= 0) {
        gallery.value[idx] = {
          id: taskId,
          url: task.resultUrl,
          desc: prompt,
          isTemporary: true,
          type: '图像',
          isGenerating: false
        }
      }
      pendingImageUrl.value = task.resultUrl
      imageLoading.value = false
      message.success('✨ 图片生成完成！')
      // 自动保存，不再弹窗
      try {
        await saveArtwork({
          taskId: taskId,
          title: prompt.substring(0, 50),
          description: prompt,
          fileUrl: task.resultUrl,
          category: '',
          tags: ''
        })
        galleryCurrentPage.value = 1
        await loadMyArtworks()
      } catch (e) {
        console.error('自动保存失败:', e)
      }
      return 'DONE'
    } else if (task.status === 'FAILED') {
      pendingTasks.delete(taskId)
      gallery.value = gallery.value.filter(item => item.id !== placeholderId)
      const errMsg = task.resultText || '未知错误'
      if (errMsg.includes('CONTENT_FILTER')) {
        message.warning('⚠️ 提示词触发了内容安全过滤，请尝试调整措辞')
      } else {
        message.error('图片生成失败：' + errMsg)
      }
      imageLoading.value = false
      return 'DONE'
    }
    return 'PENDING'
  } catch (e) {
    console.error('[生图-轮询] 查询失败:', e)
    return 'PENDING'
  }
}

// 安排下一次轮询
function schedulePoll(taskId, placeholderId, prompt) {
  console.log('[生图-轮询] 安排3秒后轮询 taskId:', taskId)
  const timer = setTimeout(async () => {
    pollingTimers.delete(taskId)
    const result = await checkTaskStatus(taskId, placeholderId, prompt)
    if (result === 'PENDING') {
      schedulePoll(taskId, placeholderId, prompt)
    }
  }, 3000)
  pollingTimers.set(taskId, timer)
}

// 标签页重新可见时立即检查所有待处理任务
function onVisibilityChange() {
  console.log('[生图-Visibility] 触发, hidden:', document.hidden, 'pendingTasks:', pendingTasks.size)
  if (!document.hidden && pendingTasks.size > 0) {
    pendingTasks.forEach((data, taskId) => {
      const timer = pollingTimers.get(taskId)
      if (timer) {
        clearTimeout(timer)
        pollingTimers.delete(taskId)
      }
      checkTaskStatus(taskId, data.placeholderId, data.prompt).then((result) => {
        if (result === 'PENDING') {
          schedulePoll(taskId, data.placeholderId, data.prompt)
        }
      })
    })
  }
}

document.addEventListener('visibilitychange', onVisibilityChange)

async function generateImage() {
  const p = imagePrompt.value.trim()
  if (!p) {
    message.warning('请输入创意描述')
    return
  }
  imageLoading.value = true
  console.log('[生图-前端] 开始生成，prompt:', p)

  try {
    console.log('[生图-前端] 第1步: 创建任务...')
    const taskRes = await createGenerateTask({
      type: '图像',
      prompt: p,
      heritageId: selectedHeritageId.value
    })
    console.log('[生图-前端] 任务创建结果:', taskRes)
    const taskId = taskRes.data.id
    currentTaskId.value = taskId
    console.log('[生图-前端] taskId:', taskId)

    console.log('[生图-前端] 第2步: 触发异步生成...')
    await callBaiduGenerate(taskId, p)
    console.log('[生图-前端] 异步触发完成')

    const placeholderId = 'gen-' + taskId
    console.log('[生图-前端] 第3步: 添加占位...')
    gallery.value.unshift({
      id: placeholderId,
      url: null,
      desc: p,
      isTemporary: true,
      type: '图像',
      isGenerating: true
    })

    imagePrompt.value = ''
    selectedHeritageId.value = null
    message.info('🎨 图片正在后台生成中，完成后会通知你...')

    console.log('[生图-前端] 第4步: 开始轮询...')
    pendingTasks.set(taskId, { placeholderId, prompt: p })
    schedulePoll(taskId, placeholderId, p)
    console.log('[生图-前端] 轮询已启动')
  } catch (err) {
    console.error('[生图-前端] 出错:', err)
    imageLoading.value = false
    message.error('请求失败：' + (err?.message || '请稍后重试'))
  }
}

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  pollingTimers.forEach(timer => clearTimeout(timer))
  pollingTimers.clear()
  pendingTasks.clear()
})


// 确认保存作品
async function confirmSaveArtwork() {
  if (!artworkForm.value.title.trim()) {
    message.warning('请输入作品标题')
    return
  }
  
  try {
    // 保存到数据库
    await saveArtwork({
      taskId: currentTaskId.value,
      title: artworkForm.value.title,
      description: artworkForm.value.description,
      fileUrl: pendingImageUrl.value,
      category: artworkForm.value.category,
      tags: artworkForm.value.tags
    })
    
    message.success('✨ 作品保存成功！')
    showMetadataModal.value = false

    // 重新加载作品列表（回到第一页展示新作品）
    galleryCurrentPage.value = 1
    await loadMyArtworks()
  } catch (error) {
    message.error('保存失败：' + (error.message || '请重试'))
  }
}

// 删除作品 / 对话历史
async function handleDeleteArtwork(item) {
  const isChat = item.isChatHistory
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除${isChat ? '该对话记录' : '作品"'+item.desc+'"'}吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        if (isChat) {
          await deleteChatHistory(item.sessionId)
        } else {
          await deleteArtwork(item.id)
        }
        message.success('删除成功')
        if (paginatedGallery.value.length === 1 && galleryCurrentPage.value > 1) {
          galleryCurrentPage.value--
        }
        await loadMyArtworks()
      } catch (error) {
        message.error('删除失败')
      }
    }
  })
}

// 获取类型标签
function getTypeLabel(type) {
  const labels = {
    '图像': '🎨 图像',
    '文本': ' 文本'
  }
  return labels[type] || type
}

// 查看完整文本 / 恢复对话
function viewFullText(item) {
  if (item.isChatHistory && item.rawMessages) {
    // 对话历史：恢复到聊天框
    const messages = item.rawMessages.flatMap(msg => [
      { role: 'user', content: msg.question },
      { role: 'assistant', content: msg.answer }
    ])
    chatHistory.value = [
      { role: 'assistant', content: '你好，我是千年非遗守护人，你可以向我询问任何关于中国非物质文化遗产的知识！' },
      ...messages
    ]
    currentSessionId.value = item.sessionId || null
    activeTab.value = 'chat'
    // 定位到聊天框
    setTimeout(() => {
      const module = document.querySelector('.chat-module')
      if (module) module.scrollIntoView({ behavior: 'smooth', block: 'start' })
      const el = document.querySelector('.chat-window')
      if (el) el.scrollTop = el.scrollHeight
    }, 100)
  } else {
    Modal.info({
      title: item.desc,
      width: 760,
      class: 'markdown-modal',
      content: `<div class="chat-text markdown-content">${renderMarkdown(item.content || '')}</div>`,
      okText: '关闭'
    })
  }
}

// 画廊图片放大预览
function previewGalleryImage(item) {
  galleryPreviewUrl.value = item.url
  galleryPreviewName.value = item.desc || '未命名作品'
}

function closeGalleryPreview() {
  galleryPreviewUrl.value = ''
  galleryPreviewName.value = ''
}

// 下载预览图片
function downloadGalleryImage() {
  if (!galleryPreviewUrl.value) return
  const a = document.createElement('a')
  a.href = galleryPreviewUrl.value
  a.download = galleryPreviewName.value || 'image'
  a.target = '_blank'
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

// 渲染 Markdown 文本
function renderMarkdown(text) {
  if (!text) return ''
  // 修复 AI 返回的 "###标题" 缺少空格导致 marked 无法识别为标题的问题
  let fixed = text.replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
  // 将制表符分隔的文本块转为 Markdown 表格
  fixed = fixed.replace(/(?:^[^\t\n]*\t[^\t\n]*(?:\t[^\t\n]*)*$\n?)+/gm, (block) => {
    const lines = block.trim().split('\n')
    if (lines.length < 2) return block
    const rows = lines.map(l => '| ' + l.split('\t').join(' | ') + ' |')
    const colCount = lines[0].split('\t').length
    const sep = '| ' + Array(colCount).fill('---').join(' | ') + ' |'
    return rows[0] + '\n' + sep + '\n' + rows.slice(1).join('\n') + '\n\n'
  })
  return marked.parse(fixed, { breaks: true, gfm: true })
}
</script>

<template>
  <div class="page-container">
    <div class="container studio-page">
      <div class="studio-header glass-card">
        <h1 class="section-title">AI文创工坊 <span>(AI Studio)</span></h1>
        <p class="studio-desc">多模态非遗体验中心：向"千年守护人"提问，或将您的灵感转化为数字文创。</p>
        <div class="studio-tabs">
          <button class="tab-btn" :class="{active: activeTab==='chat'}" @click="activeTab='chat'">💬 非遗知识问答</button>
          <button class="tab-btn" :class="{active: activeTab==='aigc'}" @click="activeTab='aigc'">✨ 数字文创生成</button>
        </div>
      </div>

      <!-- RAG Chat Module -->
      <div v-if="activeTab === 'chat'" class="chat-module glass-card" @click="showActionMenu = false">
        <div class="chat-header">
          <h3>非遗知识问答</h3>
          <button class="btn-ghost" @click="startNewChat" v-if="chatHistory.length > 0">+ 新建对话</button>
        </div>
        <div class="chat-window">
          <div v-for="(msg, i) in chatHistory" :key="i" class="chat-bubble" :class="msg.role">
            <div class="chat-content">
              <!-- 显示图片 -->
              <img v-if="msg.image" :src="msg.image" class="chat-image" alt="上传的图片" />
              <!-- 显示文本，支持 Markdown 渲染 -->
              <div class="chat-text markdown-content" v-html="renderMarkdown(msg.content)"></div>
            </div>
          </div>
          <div v-if="chatLoading" class="chat-bubble assistant">
            <div class="chat-text typing-indicator"><span>.</span><span>.</span><span>.</span></div>
          </div>
        </div>
        
        <!-- 图片预览 -->
        <div v-if="imagePreview" class="image-preview">
          <img :src="imagePreview" alt="预览" />
          <button class="close-preview" @click="clearImageSelection">×</button>
        </div>
        
        <div class="chat-input-area">
          <!-- 隐藏的文件输入 -->
          <input 
            type="file" 
            id="chat-image-input"
            accept="image/*" 
            @change="handleImageSelect"
            style="display: none"
          />
          
          <input 
            v-model="chatPrompt" 
            class="chat-input" 
            placeholder="输入你想了解的非遗问题，例如：昆曲有哪些代表剧目？" 
            @keyup.enter="askAI" 
            @focus="showActionMenu = false"
          />
          
          <!-- 多功能发送按钮 -->
          <div class="send-button-wrapper" :class="{ active: showActionMenu }" @click.stop>
            <button 
              class="btn-primary send-btn" 
              @click="askAI"
              :disabled="chatLoading"
            >
              {{ isRecording ? '⏹️' : (chatLoading ? '...' : '+') }}
            </button>
            
            <!-- 弹出菜单 -->
            <div v-if="showActionMenu && !chatLoading" class="action-menu">
              <div class="menu-item" @click.stop="handleActionSelect('text')">
                <span class="menu-icon">✏️</span>
                <span class="menu-label">文本输入</span>
              </div>
              <div class="menu-item" @click.stop="handleActionSelect('image')">
                <span class="menu-icon">📷</span>
                <span class="menu-label">上传图片</span>
              </div>
              <div class="menu-item" @click.stop="handleActionSelect('voice')">
                <span class="menu-icon">🎤</span>
                <span class="menu-label">语音输入</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- AIGC Module -->
      <div v-if="activeTab === 'aigc'" class="aigc-module">

        <!-- 非遗关联选择 -->
        <div class="heritage-selector glass-card">
          <label>🏛️ 关联非遗项目（可选）</label>
          <select v-model="selectedHeritageId">
            <option :value="null">不关联</option>
            <option v-for="heritage in heritageList" :key="heritage.id" :value="heritage.id">
              {{ heritage.name }}
            </option>
          </select>
        </div>

        <!-- 筛选面板 -->
        <div class="filter-panel glass-card">
          <div class="filter-header" @click="showFilterPanel = !showFilterPanel">
            <span>🔍 作品筛选</span>
            <span class="arrow">{{ showFilterPanel ? '▲' : '▼' }}</span>
          </div>
          <div v-if="showFilterPanel" class="filter-body">
            <div class="filter-row">
              <div class="filter-group">
                <label>分类</label>
                <select v-model="filterCategory">
                  <option value="">全部分类</option>
                  <option v-for="opt in CATEGORY_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                </select>
              </div>
              <div class="filter-group">
                <label>关键词</label>
                <input v-model="filterKeyword" placeholder="搜索标题或描述" />
              </div>
              <div class="filter-actions">
                <button class="btn-primary" @click="applyFilter">应用</button>
                <button class="btn-ghost" @click="clearFilter">清除</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 图像生成 -->
        <div class="aigc-input-card glass-card">
          <h3>描绘您的创意</h3>
          <p>输入非遗元素与现代风格的融合，例如"苗族刺绣风格的现代球鞋设计"</p>

          <div class="aigc-input-row">
            <textarea v-model="imagePrompt" class="aigc-textarea" placeholder="在此输入您的文生图提示词..." rows="3"></textarea>
            <button class="btn-primary aigc-btn" @click="generateImage" :disabled="imageLoading">
              {{ imageLoading ? '生成中...' : '生成图像' }}
            </button>
          </div>
        </div>

      </div>

      <!-- 共享画廊（两个 tab 都可见） -->
      <div class="gallery-section">
      <h3 class="gallery-title">🎨 个人数字画廊</h3>

      <!-- 合并画廊（作品 + 对话历史统一分页，每页6条） -->
      <div v-if="paginatedGallery.length === 0" class="empty-gallery">
        <div class="empty-icon">🎨</div>
        <p>暂无作品，快去创作吧！</p>
      </div>
      <div v-else class="gallery-grid">
        <div v-for="item in paginatedGallery" :key="item.id" class="gallery-item glass-card card-hover">
          <div v-if="item.isGenerating" class="generating-placeholder">
            <div class="loader"></div>
            <p>正在生成中...</p>
          </div>
          <img v-else-if="item.type === '图像'" :src="item.url" :alt="item.desc" @click="previewGalleryImage(item)" />
          <div v-else-if="item.type === '文本'" class="chat-placeholder" @click="viewFullText(item)">
            <div class="chat-excerpt" v-html="renderMarkdown((item.content || '').substring(0, 300))"></div>
          </div>
          <div class="gallery-item-info">
            <div class="gallery-item-meta">
              <span class="type-badge">{{ item.isGenerating ? '⏳ 生成中' : item.isChatHistory ? '💬 问答' : getTypeLabel(item.type) }}</span>
              <span v-if="item.metadata?.heritageName" class="heritage-badge">{{ item.metadata.heritageName }}</span>
            </div>
            <div class="gallery-item-desc">{{ item.desc }}</div>
            <div class="gallery-item-actions" v-if="!item.isTemporary">
              <button class="action-btn" @click="handleDeleteArtwork(item)">🗑️ 删除</button>
            </div>
          </div>
        </div>
      </div>

      <Pagination v-if="galleryTotal > 6" v-model="galleryCurrentPage" :total="galleryTotal" :page-size="6" />
    </div>
  </div>
  </div>

  <!-- 作品元数据编辑弹窗 -->
  <div v-if="showMetadataModal" class="modal-overlay" @click.self="showMetadataModal = false">
    <div class="metadata-modal">
      <h3>📝 完善作品信息</h3>
      <div class="modal-body">
        <div class="form-group">
          <label>作品标题 *</label>
          <input v-model="artworkForm.title" placeholder="给作品起个名字吧" />
        </div>
        <div class="form-group">
          <label>作品描述</label>
          <textarea v-model="artworkForm.description" rows="3" placeholder="描述一下你的创作灵感..."></textarea>
        </div>
        <div class="form-group">
          <label>分类</label>
          <select v-model="artworkForm.category">
            <option value="">请选择分类</option>
            <option v-for="opt in CATEGORY_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>标签（用逗号分隔）</label>
          <input v-model="artworkForm.tags" placeholder="例如：苏绣,国潮,创新" />
        </div>
        <div class="preview-image">
          <img :src="pendingImageUrl" alt="预览" />
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn-ghost" @click="showMetadataModal = false">稍后保存</button>
        <button class="btn-primary" @click="confirmSaveArtwork">确认保存</button>
      </div>
    </div>
  </div>

  <!-- 画廊图片放大预览 -->
  <div v-if="galleryPreviewUrl" class="gallery-preview-overlay" @click.self="closeGalleryPreview">
    <div class="gallery-preview-container">
      <img :src="galleryPreviewUrl" :alt="galleryPreviewName" />
      <div class="gallery-preview-actions">
        <span class="gallery-preview-name">{{ galleryPreviewName }}</span>
        <button class="btn-download" @click="downloadGalleryImage">下载图片</button>
        <button class="btn-close-preview" @click="closeGalleryPreview">×</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.studio-page { padding-top: 36px; padding-bottom: 40px; display: flex; flex-direction: column; gap: 24px; max-width: 960px; margin: 0 auto; }
.studio-header { padding: 32px; text-align: center; }
.studio-desc { color: var(--text-secondary); margin: 12px 0 24px; }
.studio-tabs { display: flex; gap: 12px; justify-content: center; }
.tab-btn { padding: 10px 24px; border-radius: var(--radius-full); border: 1px solid var(--bg-glass-border); background: var(--bg-glass); color: var(--text-secondary); font-size: 15px; cursor: pointer; transition: all var(--transition-fast); }
.tab-btn.active { background: var(--color-primary); color: #fff; border-color: var(--color-primary); box-shadow: var(--shadow-glow-red); }

/* Chat Module */
.chat-module { display: flex; flex-direction: column; height: 600px; overflow: hidden; position: relative; }

/* 聊天头部 */
.chat-header { padding: 20px 24px 16px; background: rgba(255,255,255,0.8); border-bottom: 1px solid var(--bg-glass-border); display: flex; justify-content: space-between; align-items: center; }
.chat-header h3 { font-size: 18px; margin-bottom: 0; color: var(--text-primary); }
.chat-subtitle { font-size: 13px; color: var(--text-secondary); margin: 0; }

/* 聊天窗口 */
.chat-window { flex: 1; padding: 24px; overflow-y: auto; display: flex; flex-direction: column; gap: 24px; background: linear-gradient(to bottom, rgba(255,250,244,0.3), rgba(255,250,244,0.6)); }

/* 消息气泡基础样式 */
.chat-bubble { display: flex; max-width: 100%; animation: fadeIn 0.3s ease-in; }
.chat-bubble.user { align-self: flex-end; }

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 内容区域 */
.chat-content { display: flex; flex-direction: column; gap: 8px; max-width: 100%; width: 100%; }

/* 图片样式 */
.chat-image { max-width: 320px; max-height: 240px; border-radius: 12px; object-fit: cover; box-shadow: 0 4px 12px rgba(0,0,0,0.1); border: 2px solid rgba(255,255,255,0.8); }

/* 文本气泡 */
.chat-text {
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 15px;
  line-height: 1.7;
  word-wrap: break-word;
  overflow-wrap: break-word;
  word-break: break-word;
  letter-spacing: 0.3px;
  overflow-x: hidden;
}

/* Markdown 渲染样式 */
.markdown-content {
  white-space: normal;
  overflow-wrap: break-word;
  word-break: break-word;
}

.markdown-content :deep(*) {
  max-width: 100%;
  overflow-wrap: break-word;
  word-break: break-word;
}

.markdown-content :deep(p) {
  margin: 8px 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 8px 0;
  padding-left: 24px;
}

.markdown-content :deep(li) {
  margin: 4px 0;
}

.markdown-content :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.markdown-content :deep(pre) {
  background: rgba(0, 0, 0, 0.06);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.markdown-content :deep(pre code) {
  background: transparent;
  padding: 0;
}

.markdown-content :deep(blockquote) {
  border-left: 3px solid var(--color-primary);
  padding-left: 12px;
  margin: 12px 0;
  color: var(--text-secondary);
}

.markdown-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}
.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid #e0d5c7;
  padding: 8px 12px;
  text-align: left;
}
.markdown-content :deep(th) {
  background: #fdf6f0;
  font-weight: 600;
}

.markdown-content :deep(strong) {
  font-weight: bold;
}

.markdown-content :deep(em) {
  font-style: italic;
}

/* AI 助手气泡 */
.chat-bubble.assistant .chat-text { 
  background: #fff; 
  border: 1px solid rgba(201,71,44,0.15); 
  color: var(--text-primary); 
  border-top-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

/* 用户气泡 */
.chat-bubble.user .chat-text { 
  background: linear-gradient(135deg, var(--color-primary) 0%, #c9472c 100%); 
  color: #fff; 
  border-top-right-radius: 4px;
  box-shadow: 0 2px 8px rgba(201,71,44,0.3);
}

/* 图片预览 */
.image-preview { position: relative; padding: 12px 24px; background: var(--bg-card); border-bottom: 1px solid var(--bg-glass-border); }
.image-preview img { max-height: 120px; border-radius: 8px; }
.close-preview { position: absolute; top: 8px; right: 32px; width: 28px; height: 28px; border-radius: 50%; background: rgba(0,0,0,0.6); color: #fff; border: none; cursor: pointer; font-size: 18px; display: flex; align-items: center; justify-content: center; }
.close-preview:hover { background: rgba(0,0,0,0.8); }

/* 输入区域 */
.chat-input-area { padding: 18px 24px; background: rgba(255,255,255,0.95); border-top: 1px solid var(--bg-glass-border); display: flex; gap: 12px; align-items: center; backdrop-filter: blur(10px); }

.chat-input { flex: 1; padding: 13px 20px; border-radius: var(--radius-full); border: 1.5px solid var(--bg-glass-border); outline: none; background: #fff; color: var(--text-primary); font-size: 15px; transition: all 0.3s; }
.chat-input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px rgba(201,71,44,0.1); }
.chat-input::placeholder { color: var(--text-secondary); opacity: 0.7; }

/* 发送按钮包装器 */
.send-button-wrapper { position: relative; }
.send-button-wrapper.active .send-btn { transform: rotate(45deg); }

.send-btn { 
  width: 48px; 
  height: 48px; 
  border-radius: 50%; 
  background: linear-gradient(135deg, var(--color-primary) 0%, #c9472c 100%); 
  color: #fff; 
  border: none; 
  cursor: pointer; 
  font-size: 24px; 
  font-weight: bold;
  display: flex; 
  align-items: center; 
  justify-content: center; 
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px rgba(201,71,44,0.3);
}
.send-btn:hover:not(:disabled) { 
  transform: scale(1.08); 
  box-shadow: 0 4px 16px rgba(201,71,44,0.4);
}
.send-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.send-btn.recording { background: linear-gradient(135deg, #ff4444 0%, #cc0000 100%); animation: pulse 1.5s infinite; }

@keyframes pulse { 
  0%, 100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(255,68,68,0.7); } 
  50% { transform: scale(1.1); box-shadow: 0 0 0 10px rgba(255,68,68,0); } 
}

/* 操作菜单 */
.action-menu {
  position: absolute;
  bottom: 60px;
  right: 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.15);
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  animation: slideUp 0.2s ease-out;
  z-index: 100;
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.menu-item:hover {
  background: rgba(201,71,44,0.08);
}

.menu-icon {
  font-size: 20px;
}

.menu-label {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
}

/* Typing indicator */
.typing-indicator span { animation: blink 1.4s infinite both; }
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

/* AIGC Module */
.aigc-module { display: flex; flex-direction: column; gap: 24px; }

/* 共享画廊 */
.gallery-section { display: flex; flex-direction: column; gap: 24px; margin-top: 24px; }

/* 非遗关联选择 */
.heritage-selector { padding: 16px 24px; display: flex; flex-direction: column; gap: 8px; }
.heritage-selector label { font-weight: bold; font-size: 14px; color: var(--text-primary); }
.heritage-selector select { padding: 10px 14px; border: 1px solid var(--bg-glass-border); border-radius: 8px; font-size: 14px; outline: none; background: #fff; cursor: pointer; transition: all 0.3s; }
.heritage-selector select:focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px rgba(201,71,44,0.1); }

/* 筛选面板 */
.filter-panel { padding: 16px 24px; }
.filter-header { display: flex; justify-content: space-between; align-items: center; cursor: pointer; padding: 8px 0; font-weight: bold; color: var(--text-primary); }
.filter-header:hover { color: var(--color-primary); }
.filter-header .arrow { transition: transform 0.3s; }
.filter-body { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--bg-glass-border); }
.filter-row { display: flex; gap: 16px; align-items: flex-end; }
.filter-group { flex: 1; display: flex; flex-direction: column; gap: 8px; }
.filter-group label { font-size: 13px; color: var(--text-secondary); }
.filter-group select, .filter-group input { padding: 10px 14px; border: 1px solid var(--bg-glass-border); border-radius: 8px; font-size: 14px; outline: none; background: #fff; }
.filter-group select:focus, .filter-group input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px rgba(201,71,44,0.1); }
.filter-actions { display: flex; gap: 8px; }

.aigc-input-card { padding: 24px; }
.input-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.aigc-input-card h3 { margin-bottom: 8px; font-size: 18px; }
.aigc-input-card p { color: var(--text-secondary); margin-bottom: 16px; font-size: 14px; }

.aigc-input-row { display: flex; gap: 16px; align-items: flex-end; }
.aigc-textarea { flex: 1; padding: 12px; border-radius: var(--radius-md); border: 1px solid var(--bg-glass-border); outline: none; resize: none; background: #fff; font-family: inherit; color: var(--text-primary); }
.aigc-textarea:focus { border-color: var(--color-primary); }
.aigc-btn { height: 44px; padding: 0 32px; font-size: 15px; }

.gallery-title { font-size: 20px; font-family: var(--font-serif); border-bottom: 1px solid var(--bg-glass-border); padding-bottom: 12px; margin-top: 12px; }

/* 空状态 */
.empty-gallery { text-align: center; padding: 60px 20px; color: var(--text-secondary); }
.empty-gallery .empty-icon { font-size: 64px; margin-bottom: 16px; }

.gallery-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }
.gallery-item { border-radius: var(--radius-lg); overflow: hidden; display: flex; flex-direction: column; background: #fff; }
.gallery-item img { width: 100%; height: 240px; object-fit: cover; cursor: pointer; }

/* 问答卡片文本节选（与图像卡片布局一致） */
.chat-placeholder { width: 100%; height: 240px; display: flex; align-items: flex-start; padding: 16px; background: linear-gradient(135deg, #fdf8f6 0%, #fefcf9 50%, #fdf7f5 100%); cursor: pointer; overflow: hidden; }
.chat-excerpt { font-size: 13px; line-height: 1.7; color: var(--text-secondary); overflow: hidden; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 12; word-break: break-word; }
.chat-excerpt :deep(h1),
.chat-excerpt :deep(h2),
.chat-excerpt :deep(h3),
.chat-excerpt :deep(h4) { margin: 6px 0 3px; font-size: 13px; font-weight: 600; color: var(--text-primary); display: block; }
.chat-excerpt :deep(p) { margin: 3px 0; }
.chat-excerpt :deep(strong) { font-weight: 600; color: var(--text-primary); }
.chat-excerpt :deep(hr) { border: none; border-top: 1px solid #eee; margin: 6px 0; }
.chat-excerpt :deep(ul),
.chat-excerpt :deep(ol) { margin: 3px 0; padding-left: 16px; }
.chat-excerpt :deep(li) { margin: 1px 0; }
.chat-excerpt :deep(blockquote) { border-left: 2px solid var(--color-primary); padding-left: 8px; margin: 4px 0; }
.chat-excerpt :deep(table) { border-collapse: collapse; width: 100%; margin: 4px 0; font-size: 11px; }
.chat-excerpt :deep(th),
.chat-excerpt :deep(td) { border: 1px solid #e0d5c7; padding: 3px 6px; text-align: left; }
.chat-excerpt :deep(th) { background: #fdf6f0; font-weight: 600; }
.text-content .view-full { margin-top: 12px; align-self: flex-start; }

.gallery-item-info { padding: 16px; }
.gallery-item-meta { display: flex; gap: 8px; margin-bottom: 8px; }
.type-badge, .heritage-badge { padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: bold; }
.type-badge { background: rgba(201,71,44,0.1); color: var(--color-primary); }
.heritage-badge { background: rgba(52,152,219,0.1); color: #3498db; }
.gallery-item-desc { font-size: 13px; color: var(--text-secondary); margin-bottom: 8px; }
.gallery-item-actions { display: flex; gap: 8px; justify-content: flex-end; }
.action-btn { padding: 4px 12px; font-size: 12px; border: 1px solid var(--bg-glass-border); background: transparent; border-radius: 4px; cursor: pointer; transition: all 0.3s; }
.action-btn:hover { background: var(--color-primary); color: white; border-color: var(--color-primary); }

/* 警告消息样式 */
.warning-message { white-space: pre-line; line-height: 1.8; }

.loading-placeholder, .generating-placeholder { height: 240px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; color: var(--color-primary); }
.generating-placeholder p { font-size: 14px; color: var(--text-secondary); }
.loader { width: 40px; height: 40px; border: 4px solid rgba(201,71,44,0.2); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* 元数据编辑弹窗 */
.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.metadata-modal { background: white; border-radius: 16px; padding: 32px; max-width: 600px; width: 90%; max-height: 80vh; overflow-y: auto; }
.metadata-modal h3 { margin-bottom: 24px; font-size: 20px; text-align: center; }
.modal-body { display: flex; flex-direction: column; gap: 16px; }
.form-group { display: flex; flex-direction: column; gap: 8px; }
.form-group label { font-weight: bold; font-size: 14px; color: var(--text-primary); }
.form-group input, .form-group textarea, .form-group select { padding: 10px 14px; border: 1px solid var(--bg-glass-border); border-radius: 8px; font-size: 14px; outline: none; transition: all 0.3s; }
.form-group input:focus, .form-group textarea:focus, .form-group select:focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px rgba(201,71,44,0.1); }
.preview-image { margin-top: 16px; text-align: center; }
.preview-image img { max-width: 100%; max-height: 300px; border-radius: 8px; object-fit: contain; }
.modal-footer { display: flex; gap: 12px; justify-content: flex-end; margin-top: 24px; }

@media (max-width: 900px) {
  .gallery-grid { grid-template-columns: repeat(2, 1fr); }
  .aigc-input-row { flex-direction: column; align-items: stretch; }
}
@media (max-width: 600px) {
  .gallery-grid { grid-template-columns: 1fr; }
}

/* 画廊图片放大预览 */
.gallery-preview-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.85); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.gallery-preview-container { position: relative; max-width: 90vw; max-height: 90vh; display: flex; flex-direction: column; align-items: center; }
.gallery-preview-container > img { max-width: 90vw; max-height: 80vh; object-fit: contain; border-radius: 12px; box-shadow: 0 8px 40px rgba(0,0,0,0.5); }
.gallery-preview-actions { display: flex; align-items: center; gap: 16px; margin-top: 20px; }
.gallery-preview-name { color: #fff; font-size: 16px; opacity: 0.85; }
.btn-download { padding: 10px 24px; background: var(--color-primary); color: #fff; border: none; border-radius: 8px; font-size: 15px; cursor: pointer; transition: all 0.3s; }
.btn-download:hover { background: #b33a22; transform: translateY(-1px); }
.btn-close-preview { width: 36px; height: 36px; border-radius: 50%; background: rgba(255,255,255,0.15); color: #fff; border: 1px solid rgba(255,255,255,0.3); cursor: pointer; font-size: 20px; line-height: 1; transition: all 0.3s; }
.btn-close-preview:hover { background: rgba(255,255,255,0.3); }
</style>

<!-- 全局样式：弹窗中复用聊天气泡样式（Modal 渲染在组件外） -->
<style>
.markdown-modal .ant-modal-confirm-body .ant-modal-confirm-content {
  max-height: 60vh;
  overflow-y: auto;
}
/* 复制 .chat-text（聊天气泡容器） */
.markdown-modal .chat-text {
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 15px;
  line-height: 1.7;
  word-wrap: break-word;
  overflow-wrap: break-word;
  word-break: break-word;
  letter-spacing: 0.3px;
  overflow-x: hidden;
  background: #fff;
  border: 1px solid rgba(201,71,44,0.15);
  color: #333;
  border-top-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
/* 复制 .markdown-content（聊天内 Markdown 渲染） */
.markdown-modal .markdown-content {
  white-space: normal;
  overflow-wrap: break-word;
  word-break: break-word;
}
.markdown-modal .markdown-content * {
  max-width: 100%;
  overflow-wrap: break-word;
  word-break: break-word;
}
.markdown-modal .markdown-content h1,
.markdown-modal .markdown-content h2,
.markdown-modal .markdown-content h3,
.markdown-modal .markdown-content h4,
.markdown-modal .markdown-content h5,
.markdown-modal .markdown-content h6 {
  margin: 16px 0 8px 0 !important;
  font-weight: bold !important;
  line-height: 1.4 !important;
  display: block !important;
}
.markdown-modal .markdown-content h1 { font-size: 22px !important; }
.markdown-modal .markdown-content h2 { font-size: 20px !important; border-bottom: 1px solid #eee; padding-bottom: 6px; }
.markdown-modal .markdown-content h3 { font-size: 18px !important; }
.markdown-modal .markdown-content h4 { font-size: 16px !important; }
.markdown-modal .markdown-content p { margin: 8px 0; }
.markdown-modal .markdown-content ul,
.markdown-modal .markdown-content ol { margin: 8px 0; padding-left: 24px; }
.markdown-modal .markdown-content li { margin: 4px 0; }
.markdown-modal .markdown-content code {
  background: rgba(0,0,0,0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}
.markdown-modal .markdown-content pre {
  background: rgba(0,0,0,0.06);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
  white-space: pre-wrap;
  word-break: break-word;
}
.markdown-modal .markdown-content pre code {
  background: transparent;
  padding: 0;
}
.markdown-modal .markdown-content blockquote {
  border-left: 3px solid #c9472c;
  padding-left: 12px;
  margin: 12px 0;
  color: #666;
}
.markdown-modal .markdown-content strong { font-weight: bold; }
.markdown-modal .markdown-content em { font-style: italic; }
.markdown-modal .markdown-content table { border-collapse: collapse; width: 100%; margin: 12px 0; }
.markdown-modal .markdown-content th,
.markdown-modal .markdown-content td { border: 1px solid #e0d5c7; padding: 8px 12px; text-align: left; }
.markdown-modal .markdown-content th { background: #fdf6f0; font-weight: 600; }

/* 聊天框内 Markdown 标题（v-html 内容需要全局样式覆盖 Ant Design 重置） */
.chat-text.markdown-content h1 { display: block; font-size: 22px; font-weight: bold; margin: 18px 0 10px 0; line-height: 1.4; }
.chat-text.markdown-content h2 { display: block; font-size: 20px; font-weight: bold; margin: 16px 0 8px 0; line-height: 1.4; }
.chat-text.markdown-content h3 { display: block; font-size: 18px; font-weight: bold; margin: 14px 0 6px 0; line-height: 1.4; }
.chat-text.markdown-content h4 { display: block; font-size: 16px; font-weight: bold; margin: 12px 0 4px 0; line-height: 1.4; }
.chat-text.markdown-content table { border-collapse: collapse; width: 100%; margin: 12px 0; }
.chat-text.markdown-content th,
.chat-text.markdown-content td { border: 1px solid #e0d5c7; padding: 8px 12px; text-align: left; }
.chat-text.markdown-content th { background: #fdf6f0; font-weight: 600; }
.chat-text.markdown-content strong { font-weight: bold; }
.chat-text.markdown-content em { font-style: italic; }
</style>
