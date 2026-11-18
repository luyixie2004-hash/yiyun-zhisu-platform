import request from './request'

// 获取体验项目列表
export const getExperienceList = (params) => request.get('/api/experiences', { params, _silent: true })

// 创建体验项目
export const createExperience = (data) => request.post('/api/experiences', data)

// 更新体验项目
export const updateExperience = (id, data) => request.put(`/api/experiences/${id}`, data)

// 删除体验项目
export const deleteExperience = (id) => request.delete(`/api/experiences/${id}`)

// 获取我的体验项目（传承人）
export const getMyExperiences = (params) => request.get('/api/experiences/my', { params })

// 下架体验项目
export const deactivateExperience = (id) => request.put(`/api/experiences/${id}/deactivate`)

// 获取指定用户发布的体验项目
export const getUserExperiences = (userId, params) => request.get(`/api/experiences/user/${userId}`, { params, _silent: true })

// 管理员获取体验列表
export const getAdminExperiences = (params) => request.get('/api/experiences/admin/list', { params })

// 管理员审核通过
export const approveExperience = (id) => request.put(`/api/experiences/admin/${id}/approve`)

// 管理员拒绝
export const rejectExperience = (id, data) => request.put(`/api/experiences/admin/${id}/reject`, data)