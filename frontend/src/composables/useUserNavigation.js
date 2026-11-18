import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

export function useUserNavigation() {
  const router = useRouter()
  const userStore = useUserStore()

  function goToUserProfile(userId) {
    if (!userId) return
    if (String(userId) === String(userStore.userInfo?.id)) {
      router.push('/profile')
    } else {
      router.push(`/user/${userId}`)
    }
  }

  return { goToUserProfile }
}
