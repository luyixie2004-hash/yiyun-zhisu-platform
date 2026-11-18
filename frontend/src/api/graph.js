import request from './request'

// 获取全量图谱数据
export const getFullGraph = () => request.get('/api/graph/full', { _silent: true })

// 获取节点及其邻居（局部子图）
export const getNodeNeighbors = (nodeId) => request.get(`/api/graph/node/${nodeId}`, { _silent: true })

// 按关键词搜索节点
export const searchGraphNodes = (keyword) => request.get('/api/graph/search', { params: { keyword }, _silent: true })