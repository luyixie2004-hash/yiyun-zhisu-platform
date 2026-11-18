import request from './request'

// 用户认证
export const login = (data) => request.post('/api/user/login', data, { _silent: true })
export const register = (data) => request.post('/api/user/register', data, { _silent: true })
export const getUserInfo = () => request.get('/api/user/info', { _silent: true })
export const getUserById = (id) => request.get(`/api/user/${id}`, { _silent: true })

// 唯一性检查
export const checkUsername = (username) => request.get(`/api/user/check/username?username=${username}`, { _silent: true })
export const checkPhone = (phone) => request.get(`/api/user/check/phone?phone=${phone}`, { _silent: true })
export const checkEmail = (email) => request.get(`/api/user/check/email?email=${email}`, { _silent: true })

// 用户信息
export const changePassword = (data) => request.put('/api/user/password', data)
export const updateUserInfo = (data) => request.put('/api/user/info', data)
export const getUserList = () => request.get('/api/user/list')

// 管理员操作
export const banUser = (id, data) => request.put(`/api/user/${id}/ban`, data)
export const unbanUser = (id) => request.put(`/api/user/${id}/unban`)

// 点赞相关
export const toggleLike = (targetType, targetId) => request.post('/api/likes', {}, { params: { targetType, targetIdStr: String(targetId) } })
export const checkLike = (targetType, targetId) => request.get('/api/likes/check', { params: { targetType: targetType, targetIdStr: String(targetId) }, _silent: true })
export const getLikeCount = (targetType, targetId) => request.get('/api/likes/count', { params: { targetType, targetId }, _silent: true })

// 获取我的点赞列表
export const getMyLikes = () => request.get('/api/likes/my')

// 收藏相关
export const getMyFavorites = () => request.get('/api/favorite')
export const addFavorite = (heritageId) => request.post(`/api/favorite/${heritageId}`)
export const removeFavorite = (heritageId) => request.delete(`/api/favorite/${heritageId}`)
export const checkFavorite = (heritageId) => request.get(`/api/favorite/${heritageId}/check`, { _silent: true })