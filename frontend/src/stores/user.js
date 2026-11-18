import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getUserInfo, login as loginApi, register as registerApi } from '../api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('yiyun_token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('yiyun_user') || 'null'))
  const hydrated = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN' || userInfo.value?.username === 'admin')
  const isInheritor = computed(() => userInfo.value?.role === 'INHERITOR')
  const displayName = computed(() => userInfo.value?.nickname || userInfo.value?.username || '文化旅行者')

  function persistSession(nextToken, nextUser) {
    token.value = nextToken || ''
    userInfo.value = nextUser || null
    if (nextToken) localStorage.setItem('yiyun_token', nextToken)
    else localStorage.removeItem('yiyun_token')
    if (nextUser) localStorage.setItem('yiyun_user', JSON.stringify(nextUser))
    else localStorage.removeItem('yiyun_user')
  }

  async function loginAction(credentials) {
    const res = await loginApi(credentials)
    const nextUser = res.data?.user || {
      id: res.data?.userId,
      username: res.data?.username,
      nickname: res.data?.nickname,
      avatar: res.data?.avatar,
      role: res.data?.role,
    }
    persistSession(res.data?.token, nextUser)
    hydrated.value = true
    return res
  }

  async function registerAction(payload) {
    return registerApi(payload)
  }

  async function hydrateUser() {
    if (hydrated.value) return userInfo.value
    hydrated.value = true
    if (!token.value) return null
    if (token.value === 'admin-token') return userInfo.value
    try {
      const res = await getUserInfo()
      if (res.data) {
        persistSession(token.value, res.data)
        return res.data
      }
      logout()
    } catch {
      logout()
    }
    return null
  }

  async function refreshUser() {
    if (!token.value || token.value === 'admin-token') return userInfo.value
    try {
      const res = await getUserInfo()
      if (res.data) {
        persistSession(token.value, res.data)
        hydrated.value = true
        return res.data
      }
    } catch {
      // keep existing data on failure
    }
    return userInfo.value
  }

  function logout() {
    hydrated.value = false
    persistSession('', null)
  }

  return {
    token,
    userInfo,
    hydrated,
    isLoggedIn,
    isAdmin,
    isInheritor,
    displayName,
    persistSession,
    loginAction,
    registerAction,
    hydrateUser,
    refreshUser,
    logout,
  }
})
