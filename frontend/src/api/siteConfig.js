import request from './request'

export function getHomeConfig() {
  return request.get('/api/site-config/home')
}
