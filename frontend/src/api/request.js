import axios from 'axios'
import { message } from 'ant-design-vue'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || ''

const instance = axios.create({
  baseURL: apiBaseUrl,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' },
})

function clearSession() {
  localStorage.removeItem('yiyun_token')
  localStorage.removeItem('yiyun_user')
}

function resolveErrorMessage(err, fallback = '请求失败，请稍后重试') {
  const responseMessage = err?.response?.data?.msg || err?.response?.data?.message || ''
  if (responseMessage) return responseMessage

  if (err?.code === 'ECONNABORTED' || /timeout|timed out|超时/i.test(err?.message || '')) {
    return '请求超时，请稍后重试'
  }

  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    return '当前设备处于离线状态，请检查网络连接'
  }

  if (!err?.response) {
    return '无法连接服务，请检查网络或稍后重试'
  }

  if (err.response.status === 403) return '当前账号暂无权限执行此操作'
  if (err.response.status === 404) return '请求的内容不存在或已下线'
  if (err.response.status >= 500) return '服务暂时不可用，请稍后重试'

  return err?.message || fallback
}

instance.interceptors.request.use((config) => {
  // 登录注册请求不添加token
  const url = config.url || ''
  const isAuthRequest = url.includes('/api/user/login') || url.includes('/api/user/register')
  const token = localStorage.getItem('yiyun_token')
  if (token && !isAuthRequest) {
    config.headers.Authorization = `Bearer ${token}`
  }
  
  // 添加时间戳禁用缓存
  if (config.method?.toLowerCase() === 'get') {
    config.params = config.params || {}
    config.params._t = Date.now()
  }
  
  return config
})

instance.interceptors.response.use(
  (res) => {
    // 后端返回的是 ApiResponse 对象，code 在 res.data 中
    const data = res.data
    if (data && data.code !== undefined) {
      if (data.code !== 200) {
        if (!res.config?._silent) {
          message.error(data.msg || '请求失败')
        }
        return Promise.reject(data)
      }
    }
    return data
  },
  (err) => {
    // Skip global error toast if the request is marked as silent (caller handles errors itself)
    const isSilent = err.config?._silent
    const requestUrl = err.config?.url || ''
    const isAuthRequest = requestUrl.includes('/user/login') || requestUrl.includes('/user/register')

    if (err.response?.status === 401 && !isAuthRequest) {
      clearSession()
      message.warning('登录状态已失效，请重新登录')
      if (window.location.pathname !== '/login') window.location.href = '/login'
    } else if (!isSilent) {
      message.error(resolveErrorMessage(err, '网络错误，请稍后重试'))
    }
    return Promise.reject(err)
  }
)

export default instance
