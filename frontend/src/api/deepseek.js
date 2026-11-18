/**
 * DeepSeek API 客户端
 * 通过后端代理调用 DeepSeek API，支持真实 SSE 流式输出
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

const SYSTEM_PROMPT = `你是「华脉万象」平台的 AI 非遗文化导游，名叫"华脉"。你是中国非物质文化遗产领域的专家，同时也是一位出色的文化旅行规划师。

你的核心能力：
1. 深入介绍中国各类非遗项目（传统技艺、民间艺术、曲艺戏剧、民俗节庆、传统医药、民间音乐等）
2. 讲解非遗项目的历史脉络、代表技艺、文化内涵和当代传承
3. 推荐非遗相关的旅行路线、体验工坊和文化活动
4. 介绍各地非遗传承人的故事和匠心精神

回答风格：
- 知识丰富但语言平实易懂，像一位热情的文化导游
- 回答结构清晰，适当使用 Markdown 格式（标题、列表、加粗等）
- 融入文化故事和细节，让内容生动有趣
- 适时推荐相关的非遗体验和路线
- 用中文回答所有问题`

/**
 * 流式对话 - 通过后端 SSE 代理，实时逐字输出
 */
export async function streamChatDirect(messages, onChunk, onDone, onError) {
  try {
    const res = await fetch(`${API_BASE_URL}/api/deepseek/chat/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ messages, systemPrompt: SYSTEM_PROMPT }),
    })

    if (!res.ok) {
      const errText = await res.text()
      throw new Error(`API 请求失败 (${res.status}): ${errText}`)
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let dataLines = []

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        // flush remaining data lines
        if (dataLines.length > 0) {
          const joined = dataLines.join('\n')
          dataLines = []
          if (joined.startsWith('[ERROR]')) {
            onError?.(new Error(joined.substring(7).trim() || '服务器错误'))
            return
          }
          onChunk?.(joined)
        }
        onDone?.()
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const content = line.substring(5)
          // content may have leading space per SSE spec
          const data = content.startsWith(' ') ? content.substring(1) : content
          if (data === '[DONE]') {
            if (dataLines.length > 0) {
              onChunk?.(dataLines.join('\n'))
              dataLines = []
            }
            onDone?.()
            return
          }
          if (data.startsWith('[ERROR]')) {
            if (dataLines.length > 0) {
              onChunk?.(dataLines.join('\n'))
              dataLines = []
            }
            onError?.(new Error(data.substring(7).trim() || '服务器错误'))
            return
          }
          dataLines.push(data)
        } else if (line.trim() === '') {
          // empty line = end of SSE event, flush accumulated data
          if (dataLines.length > 0) {
            onChunk?.(dataLines.join('\n'))
            dataLines = []
          }
        }
        // ignore other lines (event:, id:, etc.)
      }
    }
  } catch (err) {
    onError?.(err)
  }
}
