import { createRouter, createWebHistory } from 'vue-router'
import { message } from 'ant-design-vue'

const routes = [
  { path: '/', name: 'Landing', component: () => import('../views/LandingView.vue'), meta: { title: '遗韵智溯 — 融合大模型与AIGC技术的非遗全景探索引擎', requiresAuth: false, layout: 'blank' } },
  { path: '/home', name: 'Home', component: () => import('../views/HomeView.vue'), meta: { title: '首页 · 遗韵智溯', requiresAuth: true } },
  { path: '/explore', name: 'Explore', component: () => import('../views/ExploreView.vue'), meta: { title: '非遗图文库 · 遗韵智溯', requiresAuth: true } },
  { path: '/heritage/:id', name: 'HeritageDetail', component: () => import('../views/HeritageDetailView.vue'), meta: { title: '非遗详情 · 遗韵智溯', requiresAuth: true } },
  { path: '/city-heritage', name: 'CityHeritage', component: () => import('../views/CityHeritageView.vue'), meta: { title: '市级非遗项目 · 遗韵智溯', requiresAuth: true } },

  { path: '/inheritor', name: 'Inheritor', component: () => import('../views/InheritorsView.vue'), meta: { title: '传承人 · 遗韵智溯', requiresAuth: true } },
  { path: '/inheritor/heritages', name: 'InheritorHeritages', component: () => import('../views/InheritorHeritagesView.vue'), meta: { title: '内容管理 · 遗韵智溯', requiresAuth: true, requiresInheritor: true } },
  { path: '/inheritor/experiences', name: 'InheritorExperiences', component: () => import('../views/InheritorExperiencesView.vue'), meta: { title: '体验管理 · 遗韵智溯', requiresAuth: true, requiresInheritor: true } },
  { path: '/inheritor/bookings', name: 'InheritorBookings', component: () => import('../views/InheritorBookingView.vue'), meta: { title: '预约管理 · 遗韵智溯', requiresAuth: true, requiresInheritor: true } },
  { path: '/ai-studio', name: 'AIStudio', component: () => import('../views/AIStudioView.vue'), meta: { title: 'AI 文创工坊 · 遗韵智溯', requiresAuth: true } },
  { path: '/booking', name: 'Booking', component: () => import('../views/BookingView.vue'), meta: { title: '工坊预约 · 遗韵智溯', requiresAuth: true } },
  { path: '/graph', name: 'Graph', component: () => import('../views/GraphView.vue'), meta: { title: '知识图谱 · 遗韵智溯', requiresAuth: true } },
  { path: '/dashboard', name: 'Dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '数据大屏 · 遗韵智溯', requiresAuth: true, requiresAdmin: true } },
  { path: '/dashboard/explore', name: 'DashboardExplore', component: () => import('../views/DashboardPublicView.vue'), meta: { title: '非遗数据大屏 · 遗韵智溯', requiresAuth: true, requiresNonAdmin: true } },
  { path: '/profile', name: 'Profile', component: () => import('../views/ProfileView.vue'), meta: { title: '个人中心 · 遗韵智溯', requiresAuth: true } },
  { path: '/user/:id', name: 'UserProfile', component: () => import('../views/ProfileView.vue'), meta: { title: '用户主页 · 遗韵智溯', requiresAuth: true } },
  { path: '/login', name: 'Login', component: () => import('../views/LoginView.vue'), meta: { title: '登录 · 遗韵智溯', requiresAuth: false, layout: 'blank' } },
  { path: '/admin-login', name: 'AdminLogin', component: () => import('../views/AdminLoginView.vue'), meta: { title: '管理员登录 · 遗韵智溯', requiresAuth: false, layout: 'blank' } },
  { path: '/register', name: 'Register', component: () => import('../views/RegisterView.vue'), meta: { title: '注册 · 遗韵智溯', requiresAuth: false, layout: 'blank' } },
  { path: '/admin/heritage', name: 'AdminHeritage', component: () => import('../views/admin/AdminHeritage.vue'), meta: { title: '非遗项目审核 · 遗韵智溯', requiresAuth: true, requiresAdmin: true } },
  { path: '/admin/experience', name: 'AdminExperience', component: () => import('../views/admin/AdminWorkshop.vue'), meta: { title: '体验项目审核 · 遗韵智溯', requiresAuth: true, requiresAdmin: true } },
  { path: '/admin/bookings', name: 'AdminBookings', component: () => import('../views/admin/AdminBooking.vue'), meta: { title: '订单与内容管理 · 遗韵智溯', requiresAuth: true, requiresAdmin: true } },
  { path: '/admin/settings', name: 'AdminSettings', component: () => import('../views/admin/AdminSettings.vue'), meta: { title: '用户与认证管理 · 遗韵智溯', requiresAuth: true, requiresAdmin: true } },
  { path: '/guide', name: 'Guide', component: () => import('../views/GuideView.vue'), meta: { title: '导览 · 遗韵智溯', requiresAuth: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0, behavior: 'smooth' }
    }
  },
})

router.beforeEach(async (to, from, next) => {
  document.title = to.meta.title || '遗韵智溯'

  const token = localStorage.getItem('yiyun_token')

  // 检查登录状态
  if (to.meta.requiresAuth !== false && !token) {
    next('/login')
    return
  }

  // 检查管理员权限
  if (to.meta.requiresAdmin) {
    const userInfo = JSON.parse(localStorage.getItem('yiyun_user') || 'null')
    const isAdmin = userInfo?.role === 'ADMIN' || userInfo?.username === 'admin'

    if (!isAdmin) {
      message.warning('无权访问，仅限管理员查看')
      next('/home')
      return
    }
  }

  // 检查非管理员权限（普通用户/传承人专属页面）
  if (to.meta.requiresNonAdmin) {
    const userInfo = JSON.parse(localStorage.getItem('yiyun_user') || 'null')
    const isAdmin = userInfo?.role === 'ADMIN' || userInfo?.username === 'admin'

    if (isAdmin) {
      message.info('管理员请使用管理数据大屏')
      next('/dashboard')
      return
    }
  }

  // 检查传承人权限
  if (to.meta.requiresInheritor) {
    const userInfo = JSON.parse(localStorage.getItem('yiyun_user') || 'null')
    let isInheritor = userInfo?.role === 'INHERITOR'

    // localStorage 角色可能过期，从后端刷新
    if (!isInheritor && token) {
      try {
        const { getUserInfo } = await import('../api/user')
        const res = await getUserInfo()
        if (res.data) {
          localStorage.setItem('yiyun_user', JSON.stringify(res.data))
          isInheritor = res.data?.role === 'INHERITOR'
        }
      } catch {
        // 网络失败时降级使用 localStorage 判断
      }
    }

    if (!isInheritor) {
      message.warning('无权访问，仅限传承人查看')
      next('/home')
      return
    }
  }

  next()
})

export default router
