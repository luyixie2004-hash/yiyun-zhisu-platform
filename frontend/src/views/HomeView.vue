<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getHeritageList } from '../api/heritage'
import { getHeritageCategoryStats, getHomeStats } from '../api/statistics'
import { getHomeConfig } from '../api/siteConfig'
import { useUserNavigation } from '../composables/useUserNavigation'
import { CATEGORY_MAP, LEVEL_MAP, getCategoryName, getLevelName } from '../utils/constants'

const router = useRouter()
const { goToUserProfile } = useUserNavigation()
const searchQuery = ref('')
const hotTags = ['昆曲', '苗族刺绣', '皮影戏', '紫砂壶']
const allHeritages = ref([])

const siteConfig = ref({
  heroVideo: '',
  featuredStory: { title: '', subtitle: '', cover: '' },
  culturalTimeline: []
})

async function loadSiteConfig() {
  try {
    const res = await getHomeConfig()
    if (res.data) {
      siteConfig.value = {
        heroVideo: res.data.heroVideo || '',
        featuredStory: res.data.featuredStory || { title: '', subtitle: '', cover: '' },
        culturalTimeline: res.data.culturalTimeline || []
      }
    }
  } catch (error) {
    console.error('加载站点配置失败:', error)
  }
}
const featuredItems = computed(() => {
  // 按浏览量从高到低排序，取前3个
  return [...allHeritages.value]
    .sort((a, b) => (b.views || 0) - (a.views || 0))
    .slice(0, 3)
})
const activeStory = ref(null)
const randomSpotlight = ref([])

// 从非遗项目中随机生成3个专题
function generateRandomSpotlight() {
  if (allHeritages.value.length === 0) return []
  
  // 复制数组并打乱顺序
  const shuffled = [...allHeritages.value].sort(() => Math.random() - 0.5)
  const selected = shuffled.slice(0, 3)
  
  // 转换为 spotlight 格式
  return selected.map((item, index) => ({
    id: item.id,
    title: `${item.name} · ${item.desc.substring(0, 15)}...`,
    description: item.desc || `探索${item.name}的文化内涵与传承故事`,
    imageUrl: item.imageUrl || 'https://images.pexels.com/photos/7234213/pexels-photo-7234213.jpeg?auto=compress&cs=tinysrgb&w=1200',
    routeTo: `/heritage/${item.id}`,
    cta: '进入专题',
  }))
}

// 分类图标和颜色配置（10类，与后端 CATEGORY_MAP 保持一致）
const categoryConfig = {
  '民间文学': { icon: '📖', color: '#8B5E3C' },
  '传统音乐': { icon: '🎵', color: '#1E7B8C' },
  '传统舞蹈': { icon: '💃', color: '#E8657A' },
  '传统戏剧': { icon: '🎭', color: '#D4A553' },
  '曲艺': { icon: '🎪', color: '#B8467A' },
  '传统体育、游艺与杂技': { icon: '🤸', color: '#4A8B6F' },
  '传统美术': { icon: '🎨', color: '#2B5F8E' },
  '传统技艺': { icon: '⚒️', color: '#C8502A' },
  '传统医药': { icon: '🌿', color: '#7B52AB' },
  '民俗': { icon: '🏮', color: '#4A9B7F' },
}

// 分类数据（从数据库动态加载）
const categories = ref([])

// 加载分类统计数据
async function loadCategoryStats() {
  try {
    const res = await getHeritageCategoryStats()
    const stats = res.data || {}

    // 将统计数据和配置合并
    categories.value = Object.keys(categoryConfig).map(key => ({
      name: getCategoryName(key),
      icon: categoryConfig[key].icon,
      count: stats[key] || 0,
      color: categoryConfig[key].color,
      code: key
    }))
  } catch (error) {
    console.error('加载分类统计数据失败', error)
    // 如果加载失败，使用默认数据
    categories.value = Object.keys(categoryConfig).map(key => ({
      name: getCategoryName(key),
      icon: categoryConfig[key].icon,
      count: 0,
      color: categoryConfig[key].color,
      code: key
    }))
  }
}

// 首页统计基础数值（与后端 StatisticsService 保持一致）
const STAT_BASE = {
  heritage: 3847,
  inheritor: 12000,
  province: 0,
  aiConversation: 500000,
}

const stats = ref([
  { label: '收录非遗项目', target: STAT_BASE.heritage, displayVal: '0 项', suffix: ' 项', color: '#C8502A' },
  { label: '非遗传承人', target: STAT_BASE.inheritor, displayVal: '0+', suffix: '+', color: '#D4A553' },
  { label: '覆盖省份', target: 0, displayVal: '0 个', suffix: ' 个', color: '#4A9B7F' },
  { label: 'AI 对话次数', target: STAT_BASE.aiConversation, displayVal: '0+', suffix: '+', color: '#2B5F8E' },
])

async function loadStats() {
  try {
    const res = await getHomeStats()
    const data = res.data
    if (data) {
      stats.value[0].target = data.heritageCount || STAT_BASE.heritage
      stats.value[1].target = data.inheritorCount || STAT_BASE.inheritor
      stats.value[2].target = data.provinceCount || 0
      stats.value[3].target = data.aiConversationCount || STAT_BASE.aiConversation
    }
  } catch (error) {
    console.error('加载首页统计失败:', error)
  }
  // 无论 API 成功与否，都执行从 0 开始的滚动动画
  animateCount()
}

function animateCount() {
  stats.value.forEach((stat) => {
    const end = stat.target
    if (end <= 0) {
      stat.displayVal = '0' + stat.suffix
      return
    }
    let current = 0
    const step = Math.max(1, Math.ceil(end / 60))
    const timer = setInterval(() => {
      current = Math.min(current + step, end)
      stat.displayVal = current.toLocaleString('zh-CN') + stat.suffix
      if (current >= end) clearInterval(timer)
    }, 25)
  })
}

function doSearch() {
  if (searchQuery.value.trim()) {
    router.push(`/explore?q=${encodeURIComponent(searchQuery.value)}`)
  }
}

async function loadHeritages() {
  try {
    const res = await getHeritageList({ size: 100 })
    allHeritages.value = (res.data?.records || res.data || []).map(item => ({
      id: item.id,
      name: item.name,
      // 将英文枚举值转换为中文显示
      category: getCategoryName(item.category),
      region: item.region,
      // 将英文枚举值转换为中文显示
      level: getLevelName(item.level),
      desc: item.description || item.desc || item.summary || '',
      imageUrl: (item.imageUrl || item.image_url || '') && /[^\x00-\x7F]/.test(item.imageUrl || item.image_url || '') ? encodeURI(item.imageUrl || item.image_url || '') : (item.imageUrl || item.image_url || ''),
      inheritanceType: item.inheritanceType || 'INDIVIDUAL',
    inheritor: item.inheritorName || item.inheritor || '',
      userId: item.userId || null,
      keywords: item.keywords || [],
      views: item.views || 0
    }))
    
    // 加载完成后随机生成3个专题
    randomSpotlight.value = generateRandomSpotlight()
    // 设置默认选中的专题
    if (randomSpotlight.value.length > 0) {
      activeStory.value = randomSpotlight.value[0]
    }
  } catch (error) {
    console.error('加载非遗数据失败', error)
  }
}

function openHeritage(item) {
  router.push(`/heritage/${item.id}`)
}

// 获取级别标签样式类名
function getLevelTagClass(level) {
  const classMap = {
    [LEVEL_MAP.CITY]: 'city-level',
    [LEVEL_MAP.PROVINCIAL]: 'provincial-level',
    [LEVEL_MAP.NATIONAL]: 'national-level'
  }
  return classMap[level] || 'tag-gold'
}

onMounted(() => {
  loadStats()
  loadSiteConfig()
  loadHeritages()
  loadCategoryStats()
})

</script>

<template>
  <div class="home">
    <section class="hero">
      <div class="hero-video-container">
        <video class="hero-video" autoplay loop muted playsinline>
          <source src="/videos/video1.mp4" type="video/mp4" />
        </video>
        <div class="hero-overlay"></div>
      </div>
      <div class="hero-content">
        <div class="hero-badge">
          <span class="badge-dot"></span>
          AI 导览 · 智能创作 · 社区共创
        </div>
        <h1 class="hero-title">
          探寻千年华脉<br /><span class="hero-title-accent">万象华夏文脉</span>
        </h1>
        <p class="hero-subtitle">
          用 AI、知识图谱和沉浸体验，把非遗从"看见"变成"走进、理解、参与"。
        </p>
        <div class="hero-search">
          <input
            v-model="searchQuery"
            class="search-input"
            placeholder="搜索非遗项目、预约体验..."
            @keyup.enter="doSearch"
          />
          <button class="search-btn" @click="doSearch">搜索</button>
        </div>
        <div class="hero-tags">
          <span v-for="tag in hotTags" :key="tag" class="hot-tag" @click="searchQuery = tag; doSearch()">{{ tag }}</span>
        </div>
      </div>
    </section>

    <section class="stats-bar">
      <div class="container">
        <div class="stats-grid">
          <div v-for="stat in stats" :key="stat.label" class="stat-item">
            <div class="stat-num" :style="{ color: stat.color }">{{ stat.displayVal }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
        </div>
      </div>
    </section>

    <section class="story-showcase container">
      <div class="story-card glass-card">
        <div v-if="activeStory" class="story-cover" :style="{ backgroundImage: `url(${activeStory.imageUrl})` }"></div>
        <div class="story-body">
          <p class="story-kicker">{{ siteConfig.featuredStory.title }}</p>
          <h2 class="section-title">沉浸专题 <span>故事策展</span></h2>
          <p v-if="activeStory" class="story-desc">{{ activeStory.description }}</p>
          <div class="story-actions">
            <button v-if="activeStory" class="btn-primary" @click="$router.push(activeStory.routeTo)">{{ activeStory.cta }}</button>
            <button class="btn-ghost" @click="$router.push('/ai-studio')">让 AI 解读</button>
          </div>
          <div class="spotlight-grid">
            <button v-for="story in randomSpotlight" :key="story.id" class="spotlight-chip" :class="{ active: activeStory && activeStory.id === story.id }" @click="activeStory = story">
              {{ story.title }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <section class="categories-section container">
      <h2 class="section-title">探索 <span>非遗类别</span></h2>
      <p class="section-sub">从技艺、戏曲到节俗与医药，快速进入你最感兴趣的文化入口。</p>
      <div class="categories-grid">
        <router-link
          v-for="cat in categories"
          :key="cat.code"
          :to="`/explore?category=${cat.code}`"
          class="cat-card glass-card card-hover"
          :style="{ '--cat-color': cat.color }"
        >
          <div class="cat-icon">{{ cat.icon }}</div>
          <div class="cat-name">{{ cat.name }}</div>
          <div class="cat-count">{{ cat.count }} 项</div>
        </router-link>
      </div>
    </section>

    <section class="featured-section container">
      <div class="section-header">
        <h2 class="section-title">精选 <span>非遗项目</span></h2>
        <router-link to="/explore" class="view-all">查看全部 →</router-link>
      </div>
      <div class="featured-scroll">
        <div v-for="item in featuredItems" :key="item.id" class="featured-card card-hover" @click="openHeritage(item)">
          <div class="card-img-wrap">
            <img :src="item.imageUrl" :alt="item.name" loading="lazy" v-img-fallback />
            <div class="card-overlay">
              <span class="tag" :class="getLevelTagClass(item.level)">{{ item.level }}</span>
            </div>
          </div>
          <div class="card-body">
            <div class="card-meta">
              <span class="tag tag-red">{{ item.category }}</span>
              <span class="card-region">📍 {{ item.region }}</span>
            </div>
            <h3 class="card-title">{{ item.name }}</h3>
            <p class="card-desc">{{ item.desc }}</p>
            <div class="card-footer">
              <span v-if="item.inheritanceType === 'GROUP'" class="card-inheritor">传承主体：全体民众</span>
              <span v-else class="card-inheritor" :class="{ 'clickable-user': item.userId }" @click.stop="item.userId && goToUserProfile(item.userId)">传承人：{{ item.inheritor }}</span>
              <button class="btn-ghost" style="padding:5px 14px;font-size:12px">探索详情</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="timeline-section container">
      <div class="section-header stacked">
        <h2 class="section-title">文化脉络 <span>时间线</span></h2>
      </div>
      <div class="timeline-grid">
        <div v-for="item in siteConfig.culturalTimeline" :key="item.era" class="timeline-card glass-card">
          <div class="timeline-era">{{ item.era }}</div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.summary }}</p>
        </div>
      </div>
    </section>

  </div>
</template>

<style scoped>
.hero { position: relative; min-height: 100vh; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.hero-video-container { position: absolute; inset: 0; }
.hero-video { width: 100%; height: 100%; object-fit: cover; filter: brightness(0.9); }
.hero-overlay { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(255,252,247,0.4), rgba(247,239,226,0.85)); }
.hero-content { position: relative; z-index: 1; text-align: center; max-width: 820px; padding: 0 24px; }
.hero-badge { display: inline-flex; align-items: center; gap: 8px; padding: 6px 16px; background: rgba(255,255,255,0.84); border: 1px solid rgba(201, 71, 44, 0.14); border-radius: 999px; font-size: 13px; color: rgba(53, 35, 28, 0.78); margin-bottom: 28px; backdrop-filter: blur(8px); }
.badge-dot { width: 6px; height: 6px; border-radius: 50%; background: #4f9a7a; animation: liveDot 1.5s ease-in-out infinite; }
@keyframes liveDot { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
.hero-title { font-family: 'Noto Serif SC', serif; font-size: clamp(36px, 6vw, 72px); font-weight: 900; line-height: 1.05; margin-bottom: 20px; color: #35231c; }
.hero-title-accent { background: linear-gradient(135deg, #e76847, #d8a24b); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }
.hero-subtitle { font-size: 18px; color: rgba(53, 35, 28, 0.78); margin-bottom: 32px; line-height: 1.9; }
.hero-search { display: flex; gap: 12px; justify-content: center; margin-bottom: 18px; }
.search-input { width: min(540px, 100%); padding: 14px 18px; border-radius: 999px; border: 1px solid rgba(201, 71, 44, 0.18); background: rgba(255,255,255,0.78); color: #35231c; box-shadow: 0 10px 24px rgba(120, 76, 49, 0.08); font-family: inherit; font-size: 15px; }
.search-input::placeholder { color: rgba(53, 35, 28, 0.4); }
.search-btn { padding: 14px 24px; border-radius: 999px; border: none; background: linear-gradient(135deg, #c9472c, #a73720); color: #fff; cursor: pointer; font-weight: 500; transition: transform 0.2s, box-shadow 0.2s; }
.search-btn:hover { transform: translateY(-2px); box-shadow: 0 12px 28px rgba(201, 71, 44, 0.25); }
.hero-tags { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; }
.hot-tag { padding: 6px 12px; border-radius: 999px; border: 1px solid rgba(201, 71, 44, 0.14); background: rgba(255, 255, 255, 0.72); cursor: pointer; color: rgba(53, 35, 28, 0.78); font-size: 13px; transition: all 0.2s; }
.hot-tag:hover { color: #a73720; background: rgba(255, 250, 244, 0.96); border-color: rgba(201, 71, 44, 0.22); }
.stats-bar { margin-top: -68px; position: relative; z-index: 2; }
.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stat-item { background: rgba(255, 252, 247, 0.94); border: 1px solid rgba(201, 71, 44, 0.1); border-radius: 20px; padding: 24px; text-align: center; }
.stat-num { font-size: 28px; font-weight: 700; margin-bottom: 8px; }
.stat-label { font-size: 13px; color: rgba(53, 35, 28, 0.58); }
.story-showcase { padding-top: 56px; }
.story-card { display: grid; grid-template-columns: 1.1fr 1fr; overflow: hidden; background: rgba(255, 252, 247, 0.96); border: 1px solid rgba(201, 71, 44, 0.1); border-radius: 20px; }
.story-cover { min-height: 380px; background-size: cover; background-position: center; }
.story-body { padding: 34px; display: flex; flex-direction: column; gap: 18px; }
.story-kicker { color: #d8a24b; font-size: 13px; letter-spacing: 0.08em; text-transform: uppercase; }
.story-desc { color: rgba(53, 35, 28, 0.78); line-height: 1.9; }
.story-actions { display: flex; gap: 12px; flex-wrap: wrap; }
.spotlight-grid { display: flex; flex-wrap: wrap; gap: 10px; }
.spotlight-chip { padding: 10px 14px; border-radius: 12px; border: 1px solid rgba(201, 71, 44, 0.14); background: rgba(255, 255, 255, 0.84); color: rgba(53, 35, 28, 0.78); cursor: pointer; font-size: 13px; transition: all 0.2s; }
.spotlight-chip.active { color: #35231c; border-color: #c9472c; }
.categories-section, .featured-section, .timeline-section { padding-top: 64px; }
.section-sub { color: rgba(53, 35, 28, 0.58); margin-top: 8px; }
.categories-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 18px; margin-top: 24px; }
.cat-card { padding: 24px 16px; text-decoration: none; color: inherit; border-top: 3px solid var(--cat-color); background: rgba(255, 252, 247, 0.96); border-radius: 14px; border-bottom: 1px solid rgba(201, 71, 44, 0.1); border-left: 1px solid rgba(201, 71, 44, 0.1); border-right: 1px solid rgba(201, 71, 44, 0.1); transition: transform 0.2s, box-shadow 0.2s; }
.cat-card:hover { transform: translateY(-4px); box-shadow: 0 16px 38px rgba(120, 76, 49, 0.1); }
.cat-icon { font-size: 28px; margin-bottom: 12px; }
.cat-name { font-size: 16px; font-weight: 700; color: #35231c; word-break: keep-all; overflow-wrap: break-word; line-height: 1.4; }
.cat-count { color: rgba(53, 35, 28, 0.58); margin-top: 6px; font-size: 13px; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; gap: 12px; }
.section-header.stacked { display: block; }
.view-all { color: #e76847; text-decoration: none; font-size: 14px; }
.view-all:hover { text-decoration: underline; }
.featured-scroll { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; }
.featured-card { overflow: hidden; cursor: pointer; background: rgba(255, 252, 247, 0.94); border-radius: 14px; border: 1px solid rgba(201, 71, 44, 0.1); transition: transform 0.2s, box-shadow 0.2s; }
.featured-card:hover { transform: translateY(-4px); box-shadow: 0 16px 38px rgba(120, 76, 49, 0.1); }
.card-img-wrap { position: relative; height: 220px; overflow: hidden; }
.card-img-wrap img { width: 100%; height: 100%; object-fit: cover; }
.card-overlay { position: absolute; inset: 0; display: flex; align-items: flex-start; justify-content: flex-end; padding: 14px; }
.card-body { padding: 16px; }
.card-meta, .card-footer { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.card-title { font-size: 18px; margin: 10px 0; color: #35231c; font-weight: 600; }
.card-desc { color: rgba(53, 35, 28, 0.78); font-size: 13px; line-height: 1.8; min-height: 68px; }
.card-region, .card-inheritor { color: rgba(53, 35, 28, 0.58); font-size: 12px; }
.timeline-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px; }
.timeline-card { padding: 24px; background: rgba(255, 252, 247, 0.96); border: 1px solid rgba(201, 71, 44, 0.1); border-radius: 14px; }
.timeline-era { color: #d8a24b; margin-bottom: 10px; font-size: 13px; font-weight: 500; }
.timeline-card h3 { color: #35231c; margin: 0 0 10px; }
.timeline-card p { color: rgba(53, 35, 28, 0.78); margin: 0; line-height: 1.8; font-size: 14px; }
.btn-primary { padding: 12px 20px; border-radius: 12px; border: none; background: linear-gradient(135deg, #c9472c, #a73720); color: #fff; cursor: pointer; font-weight: 500; transition: transform 0.2s, box-shadow 0.2s; }
.btn-primary:hover { transform: translateY(-2px); box-shadow: 0 10px 24px rgba(201, 71, 44, 0.2); }
.btn-ghost { padding: 12px 20px; border-radius: 12px; border: 1px solid rgba(201, 71, 44, 0.2); background: transparent; color: #c9472c; cursor: pointer; font-weight: 500; transition: all 0.2s; }
.btn-ghost:hover { background: rgba(201, 71, 44, 0.05); }
.tag { padding: 4px 10px; border-radius: 8px; font-size: 12px; font-weight: 500; }
.tag-gold { background: rgba(216, 162, 75, 0.15); color: #d8a24b; }
.tag-red { background: rgba(201, 71, 44, 0.1); color: #c9472c; }

/* 市级 - 浅暖灰背景，深暖灰文字 */
.city-level {
  background: #E8E0D6;
  color: #665A4E;
  border: 1px solid #D6CFC5;
}

/* 省级 - 浅青蓝背景，深青蓝文字 */
.provincial-level {
  background: #D6E8F5;
  color: #4A7CB7;
  border: 1px solid #B8D4E8;
}

/* 国家级 - 浅红背景，深红文字 */
.national-level {
  background: #F8D7DA;
  color: #721C24;
  border: 1px solid #E5B8BC;
}

.container { max-width: 1200px; margin: 0 auto; padding: 0 24px; }

@media (max-width: 1200px) {
  .categories-grid { grid-template-columns: repeat(4, 1fr); }
}
@media (max-width: 1024px) {
  .stats-grid, .featured-scroll, .timeline-grid { grid-template-columns: repeat(2, 1fr); }
  .categories-grid { grid-template-columns: repeat(3, 1fr); }
  .story-card { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .hero-search { flex-direction: column; }
  .hero-title { font-size: clamp(28px, 8vw, 48px); }
  .stats-grid, .featured-scroll, .timeline-grid { grid-template-columns: 1fr; }
  .categories-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 480px) {
  .categories-grid { grid-template-columns: 1fr; }
}
</style>
