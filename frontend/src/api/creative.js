import request from './request'

/**
 * 触发后端异步生成图片（立即返回，通过 /api/ai/task/{id} 查询进度）
 * @param {number} taskId 任务ID
 * @param {string} prompt 提示词
 * @returns {Promise<{taskId: number, status: string}>}
 */
export async function generateImage(taskId, prompt) {
  const res = await request.post('/api/ai/generate-image', { taskId, prompt }, { _silent: true, timeout: 180000 })
  return res.data
}
