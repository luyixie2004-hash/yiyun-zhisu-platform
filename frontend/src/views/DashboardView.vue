<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import * as echarts from 'echarts'
import { message } from 'ant-design-vue'
import {
  ReloadOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  BulbOutlined,
  BulbFilled
} from '@ant-design/icons-vue'
import { CATEGORY_MAP, AIGC_TYPE_MAP } from '../utils/constants'
import {
  getDashboardStats,
  getHeritageCount,
  getUserCount,
  getArtworkCount,
  getBookingCount,
  getHeritageCategoryStats,
  getAIGCTypeStats,
  getTopHeritages,
  getBookingTrend,
  getDailyActiveStats
} from '../api/statistics.js'

// ── 状态 ──────────────────────────────────────────

// 今日实时数据
const todayActive = ref(0)
const todayGenerate = ref(0)
const todayArtwork = ref(0)
const todayBooking = ref(0)
const todayFavorite = ref(0)

// 核心指标
const heritageCount = ref(0)
const userCount = ref(0)
const artworkCount = ref(0)
const bookingCount = ref(0)

// 加载/错误状态
const todayLoading = ref(true)
const todayError = ref(false)
const metricsLoading = ref(true)
const metricsError = ref(false)

const chartStates = ref({
  category: { loading: true, error: false },
  aigcType: { loading: true, error: false },
  bookingTrend: { loading: true, error: false },
  topHeritage: { loading: true, error: false },
  dailyActive: { loading: true, error: false }
})

// 主题
const darkMode = ref(false)
const isFullscreen = ref(false)
const isRefreshing = ref(false)
const lastRefreshTime = ref('')

// 计时器
let refreshTimer = null
const REFRESH_INTERVAL = 60_000 // 60秒自动刷新

// 图表 refs
const categoryPieRef = ref(null)
const aigcTypeBarRef = ref(null)
const bookingTrendRef = ref(null)
const topHeritageRef = ref(null)
const dailyActiveRef = ref(null)

let categoryChart = null
let aigcTypeChart = null
let bookingTrendChart = null
let topHeritageChart = null
let dailyActiveChart = null

// ── 格式化时间 ─────────────────────────────────────

function formatTime() {
  const now = new Date()
  return now.toLocaleTimeString('zh-CN', { hour12: false })
}

// ── 今日实时数据 ───────────────────────────────────

const loadTodayStats = async () => {
  todayError.value = false
  todayLoading.value = true
  try {
    const res = await getDashboardStats()
    const data = res.data || {}
    todayActive.value = data.userActive || 0
    todayGenerate.value = data.totalGenerate || 0
    todayArtwork.value = data.totalArtwork || 0
    todayBooking.value = data.totalBooking || 0
    todayFavorite.value = data.totalFavorite || 0
  } catch (error) {
    console.error('加载今日数据失败:', error)
    todayError.value = true
  } finally {
    todayLoading.value = false
  }
}

// ── 核心指标 ───────────────────────────────────────

const loadCoreMetrics = async () => {
  metricsError.value = false
  metricsLoading.value = true
  try {
    const [heritage, user, artwork, booking] = await Promise.all([
      getHeritageCount(),
      getUserCount(),
      getArtworkCount(),
      getBookingCount()
    ])
    heritageCount.value = heritage.data || 0
    userCount.value = user.data || 0
    artworkCount.value = artwork.data || 0
    bookingCount.value = booking.data || 0
  } catch (error) {
    console.error('加载核心指标失败:', error)
    metricsError.value = true
  } finally {
    metricsLoading.value = false
  }
}

// ── 图表实例存取辅助 ─────────────────────────────

function getChartByKey(key) {
  const map = { category: categoryChart, aigcType: aigcTypeChart, bookingTrend: bookingTrendChart, topHeritage: topHeritageChart, dailyActive: dailyActiveChart }
  return map[key]
}

function setChartByKey(key, value) {
  if (key === 'category') categoryChart = value
  else if (key === 'aigcType') aigcTypeChart = value
  else if (key === 'bookingTrend') bookingTrendChart = value
  else if (key === 'topHeritage') topHeritageChart = value
  else if (key === 'dailyActive') dailyActiveChart = value
}

function disposeChartByKey(key) {
  const inst = getChartByKey(key)
  if (inst) {
    inst.dispose()
    setChartByKey(key, null)
  }
}

// ── 图表初始化辅助：先加载数据，再渲染图表 ──────────

function safeInit(key, fetchData, renderChart) {
  return async () => {
    chartStates.value[key].error = false
    chartStates.value[key].loading = true
    // 先销毁旧实例，因为 loading=true 会通过 v-if 移除旧 DOM
    disposeChartByKey(key)
    try {
      const data = await fetchData()
      // 数据加载完成后先标记完成，让 DOM 渲染出可见容器
      chartStates.value[key].loading = false
      await nextTick()
      renderChart(data)
    } catch (error) {
      console.error(`图表 ${key} 加载失败:`, error)
      chartStates.value[key].error = true
      chartStates.value[key].loading = false
      disposeChartByKey(key)
    }
  }
}

// ── 非遗分类饼图 ───────────────────────────────────

const initCategoryPie = safeInit('category',
  () => getHeritageCategoryStats().then(res => res.data),
  (data) => {
    if (!categoryChart) categoryChart = echarts.init(categoryPieRef.value)
    const isDark = darkMode.value
    categoryChart.setOption({
      title: {
        text: '非遗分类占比',
        left: 'center',
        textStyle: { color: isDark ? '#e0e0e0' : '#1a1a1a', fontSize: 16, fontWeight: 600 }
      },
      tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
      legend: {
        orient: 'vertical', right: 10, top: 'center',
        textStyle: { color: isDark ? '#aaa' : '#666' }
      },
      series: [{
        name: '非遗分类', type: 'pie',
        radius: ['45%', '72%'], center: ['38%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: isDark ? '#2a2a2a' : '#fff', borderWidth: 3 },
        label: { show: true, formatter: '{b}\n{d}%', color: isDark ? '#ccc' : undefined },
        emphasis: { label: { show: true, fontSize: 15, fontWeight: 'bold' } },
        data: Object.entries(data).map(([name, value]) => ({
          name: CATEGORY_MAP[name] || name, value
        }))
      }]
    })
  }
)

// ── AIGC类型柱状图 ─────────────────────────────────

const initAIGCTypeBar = safeInit('aigcType',
  () => getAIGCTypeStats().then(res => res.data),
  (data) => {
    if (!aigcTypeChart) aigcTypeChart = echarts.init(aigcTypeBarRef.value)
    const isDark = darkMode.value
    const types = Object.keys(data)
    const values = Object.values(data)
    aigcTypeChart.setOption({
      title: {
        text: 'AIGC 生成类型统计',
        left: 'center',
        textStyle: { color: isDark ? '#e0e0e0' : '#1a1a1a', fontSize: 16, fontWeight: 600 }
      },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '4%', bottom: '3%', top: 60, containLabel: true },
      xAxis: { type: 'category', data: types.map(t => AIGC_TYPE_MAP[t] || t), axisLabel: { color: isDark ? '#aaa' : '#666' } },
      yAxis: { type: 'value', axisLabel: { color: isDark ? '#aaa' : '#666' }, splitLine: { lineStyle: { color: isDark ? '#333' : undefined } } },
      series: [{
        name: '生成数量', type: 'bar', data: values, barWidth: '50%',
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#667eea' }, { offset: 1, color: '#764ba2' }
          ])
        }
      }]
    })
  }
)

// ── 月度预约趋势 ───────────────────────────────────

const initBookingTrend = safeInit('bookingTrend',
  () => getBookingTrend().then(res => res.data),
  (data) => {
    if (!bookingTrendChart) bookingTrendChart = echarts.init(bookingTrendRef.value)
    const isDark = darkMode.value
    const months = data.map(item => item.month)
    const counts = data.map(item => item.count)
    bookingTrendChart.setOption({
      title: {
        text: '月度预约趋势',
        left: 'center',
        textStyle: { color: isDark ? '#e0e0e0' : '#1a1a1a', fontSize: 16, fontWeight: 600 }
      },
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '3%', top: 60, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: months, axisLabel: { color: isDark ? '#aaa' : '#666' } },
      yAxis: { type: 'value', axisLabel: { color: isDark ? '#aaa' : '#666' }, splitLine: { lineStyle: { color: isDark ? '#333' : undefined } } },
      series: [{
        name: '预约数', type: 'line', smooth: true, data: counts,
        symbol: 'circle', symbolSize: 8,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(58,77,233,0.25)' }, { offset: 1, color: 'rgba(58,77,233,0.02)' }
          ])
        },
        lineStyle: { width: 3, color: '#3a4de9' },
        itemStyle: { color: '#3a4de9', borderWidth: 2, borderColor: isDark ? '#2a2a2a' : '#fff' }
      }]
    })
  }
)

// ── 热门非遗TOP5 ───────────────────────────────────

const initTopHeritage = safeInit('topHeritage',
  () => getTopHeritages().then(res => res.data),
  (data) => {
    if (!topHeritageChart) topHeritageChart = echarts.init(topHeritageRef.value)
    const isDark = darkMode.value
    const names = data.map(item => item.name).reverse()
    const views = data.map(item => item.views).reverse()
    topHeritageChart.setOption({
      title: {
        text: '热门非遗前5',
        left: 'center',
        textStyle: { color: isDark ? '#e0e0e0' : '#1a1a1a', fontSize: 16, fontWeight: 600 }
      },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '8%', bottom: '3%', top: 60, containLabel: true },
      xAxis: { type: 'value', axisLabel: { color: isDark ? '#aaa' : '#666' }, splitLine: { lineStyle: { color: isDark ? '#333' : undefined } } },
      yAxis: { type: 'category', data: names, axisLabel: { color: isDark ? '#aaa' : '#666' } },
      series: [{
        name: '浏览量', type: 'bar', data: views, barWidth: '55%',
        itemStyle: {
          borderRadius: [0, 6, 6, 0],
          color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
            { offset: 0, color: '#f5576c' }, { offset: 1, color: '#ffa502' }
          ])
        },
        label: { show: true, position: 'right', color: isDark ? '#aaa' : '#666' }
      }]
    })
  }
)

// ── 近7天日活&生成量 ──────────────────────────────

const initDailyActive = safeInit('dailyActive',
  () => getDailyActiveStats().then(res => res.data),
  (data) => {
    if (!dailyActiveChart) dailyActiveChart = echarts.init(dailyActiveRef.value)
    const isDark = darkMode.value
    const dates = data.map(item => item.date)
    const userActive = data.map(item => item.userActive)
    const totalGenerate = data.map(item => item.totalGenerate)
    dailyActiveChart.setOption({
      title: {
        text: '近7天平台活跃度',
        left: 'center',
        textStyle: { color: isDark ? '#e0e0e0' : '#1a1a1a', fontSize: 16, fontWeight: 600 }
      },
      tooltip: { trigger: 'axis' },
      legend: { data: ['活跃用户', '生成量'], top: 28, textStyle: { color: isDark ? '#aaa' : '#666' } },
      grid: { left: '3%', right: '6%', bottom: '3%', top: 70, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: dates, axisLabel: { color: isDark ? '#aaa' : '#666' } },
      yAxis: [
        { type: 'value', name: '活跃用户', axisLabel: { color: isDark ? '#aaa' : '#666' }, splitLine: { lineStyle: { color: isDark ? '#333' : undefined } } },
        { type: 'value', name: '生成量', axisLabel: { color: isDark ? '#aaa' : '#666' }, splitLine: { show: false } }
      ],
      series: [
        { name: '活跃用户', type: 'line', smooth: true, data: userActive, itemStyle: { color: '#5470c6' }, lineStyle: { width: 3 } },
        { name: '生成量', type: 'line', smooth: true, yAxisIndex: 1, data: totalGenerate, itemStyle: { color: '#91cc75' }, lineStyle: { width: 3 } }
      ]
    })
  }
)

// ── 全部图表初始化 ─────────────────────────────────

const loadAllCharts = async () => {
  await Promise.all([
    initCategoryPie(),
    initAIGCTypeBar(),
    initBookingTrend(),
    initTopHeritage(),
    initDailyActive()
  ])
}

// ── 重试单个图表 ──────────────────────────────────

const retryChart = (key) => {
  const fnMap = {
    category: initCategoryPie,
    aigcType: initAIGCTypeBar,
    bookingTrend: initBookingTrend,
    topHeritage: initTopHeritage,
    dailyActive: initDailyActive
  }
  fnMap[key]?.()
}

// ── 全屏切换 ──────────────────────────────────────

const toggleFullscreen = async () => {
  const el = document.documentElement
  if (!isFullscreen.value) {
    try {
      await el.requestFullscreen()
      isFullscreen.value = true
    } catch (e) {
      message.warning('当前浏览器不支持全屏')
    }
  } else {
    await document.exitFullscreen()
    isFullscreen.value = false
  }
}

const onFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

// ── 主题切换 ──────────────────────────────────────

const toggleDarkMode = () => {
  darkMode.value = !darkMode.value
  applyTheme()
  // 重绘所有图表以适配新主题
  loadAllCharts()
}

const applyTheme = () => {
  document.documentElement.classList.toggle('dashboard-dark', darkMode.value)
}

// ── 手动刷新 ──────────────────────────────────────

const handleRefresh = async () => {
  if (isRefreshing.value) return
  isRefreshing.value = true
  await Promise.all([loadTodayStats(), loadCoreMetrics(), loadAllCharts()])
  lastRefreshTime.value = formatTime()
  isRefreshing.value = false
  message.success('数据已刷新')
}

// ── resize 处理 ────────────────────────────────────

const handleResize = () => {
  categoryChart?.resize()
  aigcTypeChart?.resize()
  bookingTrendChart?.resize()
  topHeritageChart?.resize()
  dailyActiveChart?.resize()
}

// ── 生命周期 ──────────────────────────────────────

onMounted(async () => {
  await Promise.all([loadTodayStats(), loadCoreMetrics()])
  await loadAllCharts()
  lastRefreshTime.value = formatTime()
  window.addEventListener('resize', handleResize)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  // 自动刷新
  refreshTimer = setInterval(async () => {
    await Promise.all([loadTodayStats(), loadCoreMetrics(), loadAllCharts()])
    lastRefreshTime.value = formatTime()
  }, REFRESH_INTERVAL)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  if (refreshTimer) clearInterval(refreshTimer)
  categoryChart?.dispose()
  aigcTypeChart?.dispose()
  bookingTrendChart?.dispose()
  topHeritageChart?.dispose()
  dailyActiveChart?.dispose()
})
</script>

<template>
  <div class="dashboard-page page-container" :class="{ 'dark': darkMode }">
    <div class="container">
      <!-- 顶部标题区 -->
      <div class="dash-hero">
        <div class="dash-hero-left">
          <h1 class="dash-title">数据大屏</h1>
          <p class="dash-subtitle">平台运营数据实时监控</p>
        </div>
        <div class="dash-hero-right">
          <span v-if="lastRefreshTime" class="refresh-time">最近刷新：{{ lastRefreshTime }}</span>
          <div class="hero-actions">
            <a-button
              :loading="isRefreshing"
              size="small"
              @click="handleRefresh"
              title="刷新数据"
            >
              <template #icon><ReloadOutlined /></template>
              刷新
            </a-button>
            <a-button
              size="small"
              @click="toggleDarkMode"
              :title="darkMode ? '切换浅色模式' : '切换深色模式'"
            >
              <template #icon><component :is="darkMode ? BulbFilled : BulbOutlined" /></template>
            </a-button>
            <a-button
              size="small"
              @click="toggleFullscreen"
              :title="isFullscreen ? '退出全屏' : '全屏模式'"
            >
              <template #icon><component :is="isFullscreen ? FullscreenExitOutlined : FullscreenOutlined" /></template>
            </a-button>
          </div>
        </div>
      </div>

      <!-- 今日实时数据条 -->
      <div v-if="todayError" class="error-strip">
        <span>⚠️ 今日数据加载失败</span>
        <a-button size="small" type="link" @click="loadTodayStats">点击重试</a-button>
      </div>
      <div class="today-strip" v-else>
        <!-- 骨架 -->
        <template v-if="todayLoading">
          <div v-for="i in 5" :key="'ts'+i" class="today-item skeleton-box">
            <div class="skeleton-avatar"></div>
            <div class="skeleton-lines">
              <div class="skeleton-line w-60"></div>
              <div class="skeleton-line w-40 mt-6"></div>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="today-item">
            <div class="today-icon" style="background: linear-gradient(135deg, #667eea, #764ba2)">👤</div>
            <div class="today-info">
              <div class="today-value">{{ todayActive }}</div>
              <div class="today-desc">今日活跃用户</div>
            </div>
          </div>
          <div class="today-item">
            <div class="today-icon" style="background: linear-gradient(135deg, #f093fb, #f5576c)">⚡</div>
            <div class="today-info">
              <div class="today-value">{{ todayGenerate }}</div>
              <div class="today-desc">今日生成任务</div>
            </div>
          </div>
          <div class="today-item">
            <div class="today-icon" style="background: linear-gradient(135deg, #4facfe, #00f2fe)">🎨</div>
            <div class="today-info">
              <div class="today-value">{{ todayArtwork }}</div>
              <div class="today-desc">今日新增作品</div>
            </div>
          </div>
          <div class="today-item">
            <div class="today-icon" style="background: linear-gradient(135deg, #fa709a, #fee140)">📅</div>
            <div class="today-info">
              <div class="today-value">{{ todayBooking }}</div>
              <div class="today-desc">今日预约</div>
            </div>
          </div>
          <div class="today-item">
            <div class="today-icon" style="background: linear-gradient(135deg, #a18cd1, #fbc2eb)">⭐</div>
            <div class="today-info">
              <div class="today-value">{{ todayFavorite }}</div>
              <div class="today-desc">今日收藏</div>
            </div>
          </div>
        </template>
      </div>

      <!-- 核心指标卡片 -->
      <div v-if="metricsError" class="error-strip">
        <span>⚠️ 核心指标加载失败</span>
        <a-button size="small" type="link" @click="loadCoreMetrics">点击重试</a-button>
      </div>
      <section class="metrics-section" v-else>
        <template v-if="metricsLoading">
          <div v-for="i in 4" :key="'mc'+i" class="metric-card skeleton-box">
            <div class="skeleton-avatar large"></div>
            <div class="skeleton-lines">
              <div class="skeleton-line w-80 large"></div>
              <div class="skeleton-line w-50 mt-6"></div>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="metric-card">
            <div class="metric-icon" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%)">📚</div>
            <div class="metric-content">
              <div class="metric-value">{{ heritageCount.toLocaleString() }}</div>
              <div class="metric-label">非遗项目总数</div>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%)">👥</div>
            <div class="metric-content">
              <div class="metric-value">{{ userCount.toLocaleString() }}</div>
              <div class="metric-label">平台总用户数</div>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)">🎨</div>
            <div class="metric-content">
              <div class="metric-value">{{ artworkCount.toLocaleString() }}</div>
              <div class="metric-label">AIGC作品总数</div>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon" style="background: linear-gradient(135deg, #fa709a 0%, #fee140 100%)">📅</div>
            <div class="metric-content">
              <div class="metric-value">{{ bookingCount.toLocaleString() }}</div>
              <div class="metric-label">体验预约总数</div>
            </div>
          </div>
        </template>
      </section>

      <!-- 图表：第一行 -->
      <section class="charts-row">
        <div class="chart-container">
          <div v-if="chartStates.category.loading" class="chart-placeholder">
            <div class="skeleton-chart"></div>
          </div>
          <div v-else-if="chartStates.category.error" class="chart-error">
            <span>⚠️ 加载失败</span>
            <a-button size="small" type="link" @click="retryChart('category')">重试</a-button>
          </div>
          <div v-else ref="categoryPieRef" class="chart-box"></div>
        </div>
        <div class="chart-container">
          <div v-if="chartStates.aigcType.loading" class="chart-placeholder">
            <div class="skeleton-chart"></div>
          </div>
          <div v-else-if="chartStates.aigcType.error" class="chart-error">
            <span>⚠️ 加载失败</span>
            <a-button size="small" type="link" @click="retryChart('aigcType')">重试</a-button>
          </div>
          <div v-else ref="aigcTypeBarRef" class="chart-box"></div>
        </div>
      </section>

      <!-- 图表：第二行 预约趋势 -->
      <section class="charts-row">
        <div class="chart-container full-width">
          <div v-if="chartStates.bookingTrend.loading" class="chart-placeholder">
            <div class="skeleton-chart"></div>
          </div>
          <div v-else-if="chartStates.bookingTrend.error" class="chart-error">
            <span>⚠️ 加载失败</span>
            <a-button size="small" type="link" @click="retryChart('bookingTrend')">重试</a-button>
          </div>
          <div v-else ref="bookingTrendRef" class="chart-box"></div>
        </div>
      </section>

      <!-- 图表：第三行 -->
      <section class="charts-row charts-row-3">
        <div class="chart-container">
          <div v-if="chartStates.topHeritage.loading" class="chart-placeholder">
            <div class="skeleton-chart"></div>
          </div>
          <div v-else-if="chartStates.topHeritage.error" class="chart-error">
            <span>⚠️ 加载失败</span>
            <a-button size="small" type="link" @click="retryChart('topHeritage')">重试</a-button>
          </div>
          <div v-else ref="topHeritageRef" class="chart-box"></div>
        </div>
        <div class="chart-container">
          <div v-if="chartStates.dailyActive.loading" class="chart-placeholder">
            <div class="skeleton-chart"></div>
          </div>
          <div v-else-if="chartStates.dailyActive.error" class="chart-error">
            <span>⚠️ 加载失败</span>
            <a-button size="small" type="link" @click="retryChart('dailyActive')">重试</a-button>
          </div>
          <div v-else ref="dailyActiveRef" class="chart-box"></div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* ── 基础 ──────────────────────────────── */
.dashboard-page {
  padding-top: calc(var(--header-height) + 24px);
  padding-bottom: 40px;
  background: linear-gradient(180deg, #f0f2f5 0%, #e8ecf1 100%);
  min-height: 100vh;
  transition: background 0.3s, color 0.3s;
}

.dashboard-page.dark {
  background: linear-gradient(180deg, #141414 0%, #1a1a2e 100%);
}

.dashboard-page .container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
}

/* ── 顶部标题区 ────────────────────────── */
.dash-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 24px 32px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  transition: background 0.3s, box-shadow 0.3s;
}

.dark .dash-hero {
  background: #1e1e2d;
  box-shadow: 0 2px 12px rgba(0,0,0,0.3);
}

.dash-title {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0 0 4px 0;
  transition: color 0.3s;
}

.dark .dash-title { color: #e8e8e8; }

.dash-subtitle {
  font-size: 14px;
  color: #999;
  margin: 0;
}

.dash-hero-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.refresh-time {
  font-size: 12px;
  color: #999;
  white-space: nowrap;
}

.hero-actions {
  display: flex;
  gap: 8px;
}

/* ── 错误条 ─────────────────────────────── */
.error-strip {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 12px;
  margin-bottom: 24px;
  color: #cf1322;
  font-size: 14px;
}

.dark .error-strip {
  background: #2a1215;
  border-color: #5c1d1d;
}

/* ── 今日实时数据条 ──────────────────────── */
.today-strip {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.today-item {
  background: white;
  border-radius: 14px;
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  transition: transform 0.2s, box-shadow 0.2s, background 0.3s;
}

.dark .today-item { background: #1e1e2d; box-shadow: 0 2px 8px rgba(0,0,0,0.2); }

.today-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0,0,0,0.1);
}

.today-icon {
  width: 48px; height: 48px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; flex-shrink: 0;
}

.today-info { flex: 1; min-width: 0; }

.today-value {
  font-size: 26px; font-weight: 700;
  color: #1a1a1a; line-height: 1.2;
  transition: color 0.3s;
}

.dark .today-value { color: #e8e8e8; }

.today-desc { font-size: 12px; color: #999; margin-top: 2px; }

/* ── 核心指标 ────────────────────────────── */
.metrics-section {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.metric-card {
  background: white;
  border-radius: 14px;
  padding: 24px;
  display: flex; align-items: center; gap: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  transition: transform 0.2s, box-shadow 0.2s, background 0.3s;
}

.dark .metric-card { background: #1e1e2d; box-shadow: 0 2px 8px rgba(0,0,0,0.2); }

.metric-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0,0,0,0.1);
}

.metric-icon {
  width: 60px; height: 60px;
  border-radius: 14px;
  display: flex; align-items: center; justify-content: center;
  font-size: 28px; color: white; flex-shrink: 0;
}

.metric-content { flex: 1; }

.metric-value {
  font-size: 32px; font-weight: 700;
  color: #1a1a1a; margin-bottom: 4px;
  transition: color 0.3s;
}

.dark .metric-value { color: #e8e8e8; }

.metric-label { font-size: 14px; color: #888; }

/* ── 图表行 ────────────────────────────── */
.charts-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin-bottom: 20px;
}

.charts-row-3 {
  grid-template-columns: 1fr 1fr;
}

.chart-container {
  background: white;
  border-radius: 14px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  transition: background 0.3s, box-shadow 0.3s;
  position: relative;
}

.dark .chart-container { background: #1e1e2d; box-shadow: 0 2px 8px rgba(0,0,0,0.2); }

.chart-container.full-width { grid-column: 1 / -1; }

.chart-box { width: 100%; height: 360px; }

/* ── 图表骨架/占位 ──────────────────────── */
.chart-placeholder,
.chart-error {
  width: 100%; height: 360px;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 12px;
}

.chart-error {
  color: #cf1322; font-size: 14px;
}

.dark .chart-error { color: #ff7875; }

.skeleton-chart {
  width: 80%; height: 70%;
  background: linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 8px;
}

.dark .skeleton-chart {
  background: linear-gradient(90deg, #2a2a3a 25%, #333 50%, #2a2a3a 75%);
  background-size: 200% 100%;
}

/* ── 骨架动画 ────────────────────────────── */
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

.skeleton-box { cursor: default; pointer-events: none; }

.skeleton-avatar {
  width: 48px; height: 48px;
  border-radius: 12px; flex-shrink: 0;
  background: linear-gradient(90deg, #e8e8e8 25%, #f0f0f0 50%, #e8e8e8 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-avatar.large { width: 60px; height: 60px; border-radius: 14px; }

.dark .skeleton-avatar {
  background: linear-gradient(90deg, #2a2a3a 25%, #3a3a4a 50%, #2a2a3a 75%);
  background-size: 200% 100%;
}

.skeleton-lines { flex: 1; }

.skeleton-line {
  height: 14px; border-radius: 7px;
  background: linear-gradient(90deg, #e8e8e8 25%, #f0f0f0 50%, #e8e8e8 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.skeleton-line.large { height: 28px; border-radius: 8px; }

.dark .skeleton-line {
  background: linear-gradient(90deg, #2a2a3a 25%, #3a3a4a 50%, #2a2a3a 75%);
  background-size: 200% 100%;
}

.w-40 { width: 40%; } .w-50 { width: 50%; } .w-60 { width: 60%; } .w-80 { width: 80%; }
.mt-6 { margin-top: 6px; }

/* ── 响应式 ─────────────────────────────── */
@media (max-width: 1400px) {
  .today-strip { grid-template-columns: repeat(5, 1fr); gap: 12px; }
  .today-item { padding: 16px; gap: 12px; }
  .today-value { font-size: 22px; }
}

@media (max-width: 1200px) {
  .metrics-section { grid-template-columns: repeat(2, 1fr); }
  .today-strip { grid-template-columns: repeat(3, 1fr); }
  .charts-row-3 { grid-template-columns: 1fr; }
}

@media (max-width: 768px) {
  .metrics-section { grid-template-columns: 1fr; }
  .today-strip { grid-template-columns: repeat(2, 1fr); }
  .charts-row { grid-template-columns: 1fr; }
  .chart-box { height: 300px; }
  .chart-placeholder, .chart-error { height: 300px; }
  .dash-hero { flex-direction: column; align-items: flex-start; gap: 12px; }
  .dash-hero-right { width: 100%; justify-content: space-between; flex-wrap: wrap; }
}
</style>
