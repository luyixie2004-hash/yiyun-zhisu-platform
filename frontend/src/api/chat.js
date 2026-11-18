import request from './request'

// 保存对话历史
export const saveChatHistory = (question, history, sessionId) =>
  request.post('/api/ai/history', { question, history, sessionId })

// 获取对话会话列表
export const getChatSessions = (userId) => request.get('/api/ai/sessions', { params: { userId } })

// 保存对话会话
export const saveChatSession = (data) => request.post('/api/ai/sessions', data)

// 获取对话历史
export const getChatHistory = () => request.get('/api/ai/history')

// 删除对话历史（按会话）
export const deleteChatHistory = (sessionId) => request.delete(`/api/ai/history/${sessionId}`)

// 流式对话（SSE）
export async function streamChat(data, onChunk, onDone, onError) {
  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''
  try {
    const token = localStorage.getItem('yiyun_token')
    const headers = { 'Content-Type': 'application/json' }
    if (token) headers['Authorization'] = `Bearer ${token}`

    const res = await fetch(`${API_BASE_URL}/api/ai/stream`, {
      method: 'POST',
      headers,
      body: JSON.stringify(data),
    })

    if (!res.ok) {
      const errText = await res.text()
      throw new Error(`请求失败 (${res.status}): ${errText}`)
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        onDone?.()
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const content = line.substring(5)
          const data = content.startsWith(' ') ? content.substring(1) : content
          if (data === '[DONE]') {
            onDone?.()
            return
          }
          if (data.startsWith('[ERROR]')) {
            onError?.(new Error(data.substring(7).trim() || '服务器错误'))
            return
          }
          onChunk?.(data)
        }
      }
    }
  } catch (err) {
    onError?.(err)
  }
}