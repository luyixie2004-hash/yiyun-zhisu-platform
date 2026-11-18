import request from './request'

// ==================== AIGC生成任务相关 ====================

/**
 * 创建生成任务
 * @param {Object} data - { type: '图像'|'文本', prompt: string, heritageId?: number }
 */
export const createGenerateTask = (data) => request.post('/api/ai/generate', data)

// ==================== AIGC作品相关 ====================

/**
 * 保存作品到数据库
 * @param {Object} data - { taskId, title, description, fileUrl, category?, tags?, heritageId? }
 */
export const saveArtwork = (data) => request.post('/api/ai/save-artwork', data)

/**
 * 获取我的作品列表（支持筛选）
 * @param {Object} params - { category?, tags?, keyword? }
 */
export const getMyArtworks = (params) => request.get('/api/ai/my-artworks', { params })

/**
 * 删除作品
 * @param {number} id - 作品ID
 */
export const deleteArtwork = (id) => request.delete(`/api/ai/artwork/${id}`)

/**
 * 获取任务状态
 * @param {number} id - 任务ID
 */
export const getTaskStatus = (id) => request.get(`/api/ai/task/${id}`)

/**
 * 获取我的生成任务列表（用于恢复未完成的轮询）
 */
export const getMyTasks = () => request.get('/api/ai/my-tasks')

// ==================== 非遗项目相关 ====================

/**
 * 获取非遗项目列表（用于关联）
 * 返回分页数据，需要处理 records 字段
 */
export const getHeritageListForAIGC = () => request.get('/api/heritage', { 
  params: { 
    page: 1,
    size: 100  // 获取最多100条数据
  } 
})
