import request from './request'

// 关注相关
export const followUser = (targetId) => request.post(`/api/follow/${targetId}`)
export const unfollowUser = (targetId) => request.delete(`/api/follow/${targetId}`)
export const checkFollow = (targetId) => request.get(`/api/follow/check/${targetId}`, { _silent: true })
export const getFollowing = () => request.get('/api/follow/following')
export const getFollowers = () => request.get('/api/follow/followers')
export const getUserFollowCounts = (userId) => request.get(`/api/follow/counts/${userId}`, { _silent: true })
export const getUserFollowing = (userId) => request.get(`/api/follow/following/${userId}`, { _silent: true })
export const getUserFollowers = (userId) => request.get(`/api/follow/followers/${userId}`, { _silent: true })
