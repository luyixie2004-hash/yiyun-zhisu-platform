<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'
import { getHeritageList } from '../../api/heritage'

const router = useRouter()
const userStore = useUserStore()
const isScrolled = ref(false)
const isHidden = ref(false)
const menuOpen = ref(false)
const searchOpen = ref(false)
const searchQuery = ref('')
const allHeritages = ref([])
let lastScrollY = 0

const userNavItems = [
  { path: '/home', label: '首页' },
  { path: '/explore', label: '非遗探索' },
  { path: '/booking', label: '体验预约' },
  { path: '/ai-studio', label: 'AIGC工坊' },
  { path: '/dashboard/explore', label: '数据大屏' },
]

const inheritorNavItems = [
  { path: '/home', label: '首页' },
  { path: '/explore', label: '非遗探索' },
  { path: '/booking', label: '体验预约' },
  { path: '/ai-studio', label: 'AIGC工坊' },
  { path: '/inheritor/heritages', label: '内容管理' },
  { path: '/inheritor/experiences', label: '体验管理' },
  { path: '/inheritor/bookings', label: '预约管理' },
  { path: '/dashboard/explore', label: '数据大屏' },
]

const adminNavItems = [
  { path: '/admin/heritage', label: '非遗项目审核' },
  { path: '/admin/experience', label: '体验项目审核' },
  { path: '/admin/bookings', label: '订单管理' },
  { path: '/admin/settings', label: '用户与认证' },
  { path: '/dashboard', label: '数据大屏' },
]

const navItems = computed(() => {
  if (userStore.isAdmin) return adminNavItems
  if (userStore.isInheritor) return inheritorNavItems
  return userNavItems
})

const userAvatar = computed(() => userStore.userInfo?.avatar || 'https://api.dicebear.com/9.x/adventurer/svg?seed=heritage')
const userName = computed(() => userStore.displayName)

const searchResults = computed(() => {
  const q = searchQuery.value.trim()
  if (!q) return []
  return allHeritages.value.filter((h) =>
    h.name.includes(q) || h.category.includes(q) || h.desc.includes(q)
    || h.region.includes(q) || h.inheritor.includes(q)
    || (h.keywords && h.keywords.some((k) => k.includes(q)))
  ).slice(0, 8)
})

function onScroll() {
  const currentScrollY = window.scrollY
  isScrolled.value = currentScrollY > 30
  
  if (currentScrollY > 80 && currentScrollY > lastScrollY) {
    isHidden.value = true // 向下滚动，隐藏
  } else if (currentScrollY < lastScrollY || currentScrollY <= 80) {
    isHidden.value = false // 向上滚动或接近顶部，显示
  }
  
  lastScrollY = currentScrollY
}

function doSearch() {
  const q = searchQuery.value.trim()
  if (!q) return
  searchOpen.value = false
  router.push(`/explore?q=${encodeURIComponent(q)}`)
  searchQuery.value = ''
}

function selectResult(item) {
  searchOpen.value = false
  searchQuery.value = ''
  router.push(`/heritage/${item.id}`)
}

function goProfile() { router.push('/profile') }
function logout() { userStore.logout(); router.push('/login') }

async function loadHeritages() {
  try {
    const res = await getHeritageList({ size: 100 })
    allHeritages.value = (res.data?.records || res.data || []).map(item => ({
      id: item.id,
      name: item.name,
      category: item.category,
      region: item.region,
      level: item.level,
      desc: item.description || item.desc || item.summary || '',
      imageUrl: item.imageUrl || item.image_url || '',
      inheritor: item.inheritorName || item.inheritor || '',
      keywords: item.keywords || []
    }))
  } catch (error) {
    console.error('加载非遗数据失败', error)
  }
}

onMounted(() => {
  window.addEventListener('scroll', onScroll)
  loadHeritages()
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <header class="app-header" :class="{ scrolled: isScrolled, hidden: isHidden }">
    <div class="header-inner">
      <router-link to="/home" class="logo">
        <span class="logo-icon">华</span>
        <span class="logo-text">遗韵智溯</span>
      </router-link>

      <nav class="nav-links" :class="{ open: menuOpen }">
        <router-link v-for="item in navItems" :key="item.path" :to="item.path" class="nav-link" @click="menuOpen = false">
          {{ item.label }}
        </router-link>
      </nav>

      <div class="header-actions">
        <!-- 已登录 -->
        <template v-if="userStore.isLoggedIn">
          <a-dropdown>
            <div class="user-chip">
              <div class="avatar-wrap">
                <img :src="userAvatar" class="user-avatar" :alt="userName" />
                <span v-if="userStore.isInheritor" class="cert-icon" title="已认证非遗传承人">🏅</span>
              </div>
            </div>
            <template #overlay>
              <a-menu class="user-menu">
                <div class="user-menu-header">
                  <img :src="userAvatar" class="menu-avatar" />
                  <div>
                    <div class="menu-name">{{ userName }}</div>
                    <div class="menu-role">{{ userStore.isAdmin ? '系统管理员' : userStore.isInheritor ? '非遗传承人' : '文化旅行者' }}</div>
                  </div>
                </div>
                <template v-if="!userStore.isAdmin">
                  <a-menu-divider />
                  <a-menu-item @click="$router.push('/profile')"><span class="menu-icon">👤</span> 个人中心</a-menu-item>
                  <a-menu-item @click="$router.push('/profile?tab=orders')"><span class="menu-icon">🎫</span> 预约订单</a-menu-item>
                  <a-menu-item @click="$router.push('/profile?tab=favorites')"><span class="menu-icon">⭐</span> 我的收藏</a-menu-item>
                  <a-menu-item @click="$router.push('/profile?tab=likes')"><span class="menu-icon">👍</span> 我的点赞</a-menu-item>
                  <a-menu-item @click="$router.push('/profile?tab=following')"><span class="menu-icon">👥</span> 我的关注</a-menu-item>
                  <a-menu-item @click="$router.push('/profile?tab=followers')"><span class="menu-icon">🌟</span> 我的粉丝</a-menu-item>
                  <a-menu-item @click="$router.push('/profile?tab=creations')"><span class="menu-icon">🎨</span> 我的创作</a-menu-item>
                  <a-menu-item v-if="!userStore.isInheritor" @click="$router.push('/profile?tab=certification')"><span class="menu-icon">🏅</span> 传承人认证</a-menu-item>
                  <template v-if="userStore.isInheritor">
                    <a-menu-divider />
                    <a-menu-item @click="$router.push('/inheritor/heritages')"><span class="menu-icon">📚</span> 内容管理</a-menu-item>
                    <a-menu-item @click="$router.push('/inheritor/experiences')"><span class="menu-icon">🎯</span> 体验管理</a-menu-item>
                    <a-menu-item @click="$router.push('/inheritor/bookings')"><span class="menu-icon">📋</span> 预约管理</a-menu-item>
                  </template>
                  <a-menu-divider />
                  <a-menu-item @click="$router.push('/ai-studio')"><span class="menu-icon">✨</span> AIGC工坊</a-menu-item>
                </template>
                <a-menu-divider />
                <a-menu-item style="color:var(--color-primary);font-weight:600" @click="logout"><span class="menu-icon">🚪</span> 退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <template v-else>
          <router-link to="/login" class="btn-ghost" style="padding:7px 18px;font-size:13px">登录</router-link>
          <router-link to="/register" class="btn-primary" style="padding:7px 18px;font-size:13px">注册</router-link>
        </template>
        <button class="burger" @click="menuOpen = !menuOpen" :class="{active: menuOpen}">
          <span/><span/><span/>
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: fixed; top: 0; left: 0; right: 0;
  height: var(--header-height); z-index: 1000;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), background var(--transition-normal), box-shadow var(--transition-normal);
  background: rgba(255, 252, 247, 0.86);
  backdrop-filter: var(--blur-glass); -webkit-backdrop-filter: var(--blur-glass);
  border-bottom: 1px solid rgba(201, 71, 44, 0.08);
}
.app-header.hidden {
  transform: translateY(-100%);
}
.app-header.scrolled { background: rgba(255, 250, 244, 0.96); backdrop-filter: var(--blur-heavy); box-shadow: 0 10px 24px rgba(120, 76, 49, 0.08); }
.header-inner { max-width: 1320px; margin: 0 auto; padding: 0 24px; height: 100%; display: flex; align-items: center; gap: 32px; }
.logo { display: flex; align-items: center; gap: 10px; text-decoration: none; flex-shrink: 0; }
.logo-icon { width: 38px; height: 38px; background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)); border-radius: 10px; display: flex; align-items: center; justify-content: center; font-family: var(--font-serif); font-size: 18px; font-weight: 900; color: var(--text-on-primary); box-shadow: var(--shadow-glow-red); }
.logo-text { font-family: var(--font-serif); font-size: 20px; font-weight: 700; color: var(--text-primary); letter-spacing: 0.08em; }
.nav-links { display: flex; align-items: center; justify-content: center; gap: 6px; flex: 1; }
.nav-link { padding: 8px 18px; border-radius: var(--radius-full); color: var(--text-secondary); text-decoration: none; font-size: 14px; font-weight: 500; transition: all 0.25s ease; }
.nav-link:hover { color: #fff; background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)); box-shadow: 0 4px 14px rgba(201,71,44,0.25); transform: translateY(-1px); }
.nav-link.router-link-exact-active { color: #fff; background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)); box-shadow: 0 4px 14px rgba(201,71,44,0.3); font-weight: 600; }
.header-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.icon-btn { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,0.82); border: 1px solid var(--bg-glass-border); border-radius: 50%; color: var(--text-secondary); cursor: pointer; transition: all var(--transition-fast); }
.icon-btn:hover { color: var(--color-secondary); background: var(--bg-soft-green); border-color: rgba(47,119,92,0.22); }
/* 搜索 */
.search-wrap { position: relative; }
.search-overlay { position: fixed; inset: 0; z-index: 998; }
.search-popover { position: absolute; top: 44px; right: 0; width: 360px; padding: 12px; z-index: 999; border-radius: var(--radius-lg); box-shadow: 0 16px 48px rgba(0,0,0,0.12); animation: fadeInDown 0.2s ease; }
@keyframes fadeInDown { from { opacity: 0; transform: translateY(-8px); } to { opacity: 1; transform: translateY(0); } }
.search-input { width: 100%; padding: 10px 14px; border: 1px solid var(--bg-glass-border); border-radius: var(--radius-full); background: var(--bg-glass); color: var(--text-primary); font-size: 14px; outline: none; }
.search-input:focus { border-color: var(--color-primary); }
.search-results { margin-top: 8px; display: flex; flex-direction: column; gap: 2px; max-height: 320px; overflow-y: auto; }
.search-item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: var(--radius-md); cursor: pointer; transition: background var(--transition-fast); }
.search-item:hover { background: rgba(200,80,42,0.08); }
.sr-name { font-size: 14px; font-weight: 600; color: var(--text-primary); flex: 1; }
.sr-region { font-size: 12px; color: var(--text-muted); }
.search-empty { padding: 16px; text-align: center; color: var(--text-muted); font-size: 13px; }
/* 用户 */
.user-chip { display: flex; align-items: center; gap: 8px; padding: 3px; border-radius: 999px; background: rgba(255,255,255,0.82); border: 1px solid var(--bg-glass-border); cursor: pointer; transition: all var(--transition-fast); }
.user-chip:hover { border-color: var(--color-primary); box-shadow: 0 0 0 3px rgba(200,80,42,0.08); }
.avatar-wrap { position: relative; display: inline-flex; }
.user-avatar { width: 32px; height: 32px; border-radius: 50%; border: 2px solid var(--color-primary); background: #fff; display: block; }
.cert-icon { position: absolute; bottom: -3px; right: -5px; font-size: 13px; line-height: 1; filter: drop-shadow(0 1px 2px rgba(0,0,0,0.15)); pointer-events: none; }
.user-name { font-size: 13px; color: var(--text-primary); max-width: 92px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.user-menu { background:#fffaf4; border:1px solid rgba(201,71,44,0.12); border-radius: 14px; padding: 6px; min-width: 200px; }
.user-menu-header { display: flex; align-items: center; gap: 10px; padding: 10px 12px; }
.menu-avatar { width: 40px; height: 40px; border-radius: 50%; border: 2px solid var(--color-primary); }
.menu-name { font-size: 14px; font-weight: 700; color: var(--text-primary); }
.menu-role { font-size: 11px; color: var(--text-muted); margin-top: 2px; }
.menu-icon { margin-right: 6px; }
.burger { display: none; flex-direction: column; gap: 5px; background: none; border: none; cursor: pointer; padding: 4px; }
.burger span { display: block; width: 22px; height: 2px; background: var(--text-secondary); border-radius: 2px; transition: all 0.3s; }
@media (max-width: 900px) {
  .nav-links { display: none; }
  .nav-links.open { display: flex; flex-direction: column; position: fixed; top: var(--header-height); left: 0; right: 0; background: rgba(255,250,244,0.98); backdrop-filter: var(--blur-heavy); padding: 16px; gap: 4px; border-bottom: 1px solid rgba(201,71,44,0.12); }
  .user-name { display: none; }
  .burger { display: flex; }
  .search-popover { width: 280px; right: -40px; }
}
</style>
