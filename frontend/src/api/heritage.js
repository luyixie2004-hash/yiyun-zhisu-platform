import request from './request'

// 非遗项目列表
export const getHeritageList = (params) => request.get('/api/heritage', { params, _silent: true })

// 我的非遗项目列表(传承人)
export const getMyHeritageList = (params) => request.get('/api/heritage/my', { params })

// 非遗项目详情
export const getHeritageDetail = (id) => request.get(`/api/heritage/${id}`, { _silent: true })

// 获取非遗图片列表
export const getHeritageImages = (id) => request.get(`/api/heritage/${id}/images`, { _silent: true })

// 获取非遗关联体验项目
export const getHeritageExperiences = (id) => request.get(`/api/heritage/${id}/experiences`, { _silent: true })

// 创建非遗项目
export const createHeritage = (data) => request.post('/api/heritage', data)

// 更新非遗项目
export const updateHeritage = (id, data) => request.put(`/api/heritage/${id}`, data)

// 删除非遗项目
export const deleteHeritage = (id) => request.delete(`/api/heritage/${id}`)

// 上架/下架切换
export const toggleHeritageOnline = (id) => request.put(`/api/heritage/${id}/toggle-online`)

// 联合传承人申请退出
export const withdrawFromHeritage = (id) => request.post(`/api/heritage/${id}/withdraw`)

// 管理员获取列表
export const getAdminHeritageList = (params) => request.get('/api/heritage/admin/list', { params })

// 管理员审核通过
export const approveHeritage = (id) => request.put(`/api/heritage/admin/${id}/approve`)

// 管理员拒绝
export const rejectHeritage = (id, data) => request.put(`/api/heritage/admin/${id}/reject`, data)