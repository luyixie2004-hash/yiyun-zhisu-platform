import request from './request'

// 获取非遗项目评论列表
export const getCommentsByHeritage = (heritageId, params) => request.get(`/api/comment/heritage/${heritageId}`, { params, _silent: true })

// 获取评论回复
export const getCommentReplies = (id) => request.get(`/api/comment/${id}/replies`, { _silent: true })

// 创建评论
export const createComment = (data) => request.post('/api/comment', data)

// 回复评论
export const replyComment = (id, data) => request.post(`/api/comment/${id}/reply`, data)

// 删除评论
export const deleteComment = (id) => request.delete(`/api/comment/${id}`)

// 点赞评论
export const likeComment = (id) => request.post(`/api/comment/${id}/like`)

// 取消点赞评论
export const unlikeComment = (id) => request.delete(`/api/comment/${id}/like`)
