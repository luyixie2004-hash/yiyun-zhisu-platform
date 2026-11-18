import request from './request'

export const createBooking = (data) => request.post('/api/booking', data)
export const getMyBookings = (status) => request.get('/api/booking/my', { params: status ? { status } : {} })
export const cancelBooking = (id) => request.put(`/api/booking/${id}/cancel`)
export const getAdminBookings = (params) => request.get('/api/booking/admin/list', { params })
export const approveBooking = (id) => request.put(`/api/booking/admin/${id}/approve`)
export const rejectBooking = (id, data) => request.put(`/api/booking/admin/${id}/reject`, data)

// 传承人预约管理API
export const getInheritorBookings = (params) => request.get('/api/booking/inheritor/list', { params })
export const getInheritorBookingStats = () => request.get('/api/booking/inheritor/stats')
export const inheritorApproveBooking = (id) => request.put(`/api/booking/inheritor/${id}/approve`)
export const inheritorRejectBooking = (id, data) => request.put(`/api/booking/inheritor/${id}/reject`, data)
export const completeBooking = (id) => request.put(`/api/booking/inheritor/${id}/complete`)
export const rateBooking = (id, data) => request.put(`/api/booking/${id}/rate`, data)
