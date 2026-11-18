import { useUserStore } from '../stores/user'

function getUserKey(baseKey) {
  const userStore = useUserStore()
  const userId = userStore.userInfo?.id || userStore.userInfo?.username || 'anonymous'
  return `${baseKey}_${userId}`
}

export function getUserStorage(key, defaultValue = null) {
  const userKey = getUserKey(key)
  const saved = localStorage.getItem(userKey)
  if (saved === null) {
    return defaultValue
  }
  try {
    return JSON.parse(saved)
  } catch {
    return defaultValue
  }
}

export function setUserStorage(key, value) {
  const userKey = getUserKey(key)
  localStorage.setItem(userKey, JSON.stringify(value))
}