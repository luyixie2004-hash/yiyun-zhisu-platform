import request from './request'

export const getInheritors = () => request.get('/api/inheritor/list')
export const applyInheritor = (data) => request.post('/api/inheritor/apply', data)
export const getMyApplication = () => request.get('/api/inheritor/my-application')
export const getInheritorPublicApplication = (userId) => request.get(`/api/inheritor/public-application/${userId}`, { _silent: true })
export const getInheritorPosts = (id) => request.get(`/api/inheritor/${id}/posts`, { _silent: true })
export const getApplications = (params) => request.get('/api/inheritor/applications', { params })
export const approveInheritor = (id) => request.put(`/api/inheritor/application/${id}/approve`)
export const rejectInheritor = (id, data) => request.put(`/api/inheritor/application/${id}/reject`, data)