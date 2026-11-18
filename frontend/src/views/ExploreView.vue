<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getHeritageList } from '../api/heritage'
import { message } from 'ant-design-vue'
import { useUserStore } from '../stores/user'
import { addFavorite, removeFavorite, getMyFavorites, getLikeCount } from '../api/user'
import { regionData } from '../data/regionData'
import { useUserNavigation } from '../composables/useUserNavigation'
import { CATEGORY_CODE_TO_NAME, CATEGORY_NAME_TO_CODE, LEVEL_CODE_TO_NAME, LEVEL_NAME_TO_CODE } from '../utils/constants'
import Pagination from '../components/Pagination.vue'

const userStore = useUserStore()
const { goToUserProfile } = useUserNavigation()
const route = useRoute()
const router = useRouter()
const loading = ref(false)

const categoryNames = Object.values(CATEGORY_CODE_TO_NAME)
const allCategories = ['全部', ...categoryNames]
const allLevels = ['全部', '国家级', '省级', '市级']
const categoryCodeToName = CATEGORY_CODE_TO_NAME
const categoryNameToCode = CATEGORY_NAME_TO_CODE
const levelCodeToName = LEVEL_CODE_TO_NAME
const levelNameToCode = LEVEL_NAME_TO_CODE

const categories = allCategories
const levels = allLevels

const activeCategory = ref('全部')
const selectedProvince = ref('')
const selectedCity = ref('')
const selectedDistrict = ref('')
const activeLevel = ref('全部')
const activeDynasty = ref('全部')
const keyword = ref('')

const dynastyOptions = ['全部', '先秦', '秦汉', '魏晋南北朝', '隋唐', '宋代', '元代', '明代', '清代', '近现代', '当代']

// 级联地区选项
const provinces = regionData.map(p => ({ value: p.value, label: p.label }))
const cities = computed(() => {
  if (!selectedProvince.value) return []
  const province = regionData.find(p => p.value === selectedProvince.value)
  return province ? province.children.map(c => ({ value: c.value, label: c.label })) : []
})
const districts = computed(() => {
  if (!selectedProvince.value || !selectedCity.value) return []
  const province = regionData.find(p => p.value === selectedProvince.value)
  if (!province) return []
  const city = province.children.find(c => c.value === selectedCity.value)
  return city ? (city.children || []).map(d => ({ value: d.value, label: d.label })) : []
})

// 当前有效的地区筛选值
const activeRegion = computed(() => {
  return selectedDistrict.value || selectedCity.value || selectedProvince.value || '全部'
})

// 地区显示文本：省·市·区
const locationLabel = computed(() => {
  const parts = [selectedProvince.value, selectedCity.value, selectedDistrict.value].filter(Boolean)
  return parts.length > 0 ? parts.join('·') : '全部'
})
const sortBy = ref('default') // 排序方式：default(默认), views(浏览量), likes(点赞数), createdAt(创建时间)
const sortOrder = ref('asc') // 排序顺序：asc(升序), desc(降序)
const filteredList = ref([])

const currentPage = ref(1)
const pageSize = 6
const pagedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredList.value.slice(start, start + pageSize)
})

const isInitialLoad = ref(true)

watch(filteredList, () => {
  if (!isInitialLoad.value) {
    currentPage.value = 1
  }
})

const activeFilters = computed(() => [activeCategory.value, locationLabel.value !== '全部' ? locationLabel.value : '', activeLevel.value, activeDynasty.value, keyword.value].filter((item) => item && item !== '全部'))

// 当前排序标签
const currentSortLabel = computed(() => {
  const labels = {
    'default': '默认排序',
    'views': '浏览量',
    'likes': '点赞数',
    'createdAt': '创建时间',
  }
  const arrow = sortOrder.value === 'asc' ? ' ↑' : ' ↓'
  return sortBy.value === 'default' ? labels.default : labels[sortBy.value] + arrow
})

function normalizeHeritage(item) {
  if (!item) return null
  return {
    id: item.id,
    name: item.name,
    // 将英文枚举值转换为中文显示
    category: categoryCodeToName[item.category] || item.category,
    region: item.region,
    // 将英文枚举值转换为中文显示
    level: levelCodeToName[item.level] || item.level,
    summary: item.summary || item.desc || item.description || '',
    description: item.description || item.desc || item.summary || '',
    desc: item.description || item.desc || item.summary || '',
    imageUrl: item.imageUrl || item.image_url || '',
    videoUrl: item.videoUrl || item.video_url || '',
    inheritanceType: item.inheritanceType || 'INDIVIDUAL',
    inheritor: item.inheritorName || item.inheritor || '',
    inheritorName: item.inheritorName || item.inheritor || '',
    userId: item.userId || null,
    originDynasty: item.originDynasty || '',
    views: item.views || 0,
    keywords: item.keywords || [],
  }
}

// 应用排序：views/createdAt 由后端排序需重新请求，likes 客户端排序
function applySorting() {
  if (sortBy.value !== 'likes') {
    fetchList()
    return
  }

  const sorted = [...filteredList.value]
  const orderMultiplier = sortOrder.value === 'desc' ? -1 : 1
  const likeCountMap = {}
  likes.value.forEach(f => {
    likeCountMap[f.id] = (likeCountMap[f.id] || 0) + 1
  })
  sorted.sort((a, b) => ((likeCountMap[a.id] || 0) - (likeCountMap[b.id] || 0)) * orderMultiplier)
  filteredList.value = sorted
}

// 处理排序菜单点击
function handleSortMenuClick({ key }) {
  if (sortBy.value === key) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortBy.value = key
    sortOrder.value = key === 'default' || key === 'createdAt' ? 'asc' : 'desc'
  }
  applySorting()
}



async function fetchList() {
  loading.value = true
  
  // 将中文分类名称转换为英文枚举值发送给后端
  const categoryCode = categoryNameToCode[activeCategory.value] || activeCategory.value
  // 将中文级别名称转换为英文枚举值发送给后端
  const levelCode = levelNameToCode[activeLevel.value] || activeLevel.value
  
  const params = {
    page: 1,
    size: 100,
    category: activeCategory.value === '全部' ? undefined : categoryCode,
    region: activeRegion.value === '全部' ? undefined : activeRegion.value,
    level: activeLevel.value === '全部' ? undefined : levelCode,
    originDynasty: activeDynasty.value === '全部' ? undefined : activeDynasty.value,
    keyword: keyword.value || undefined,
    sortBy: sortBy.value === 'likes' ? undefined : sortBy.value,
    sortOrder: sortBy.value === 'likes' ? undefined : sortOrder.value,
  }

  try {
    const res = await getHeritageList(params)
    const records = res.data?.records || res.data || []
    let result = records.map(normalizeHeritage)

    if (sortBy.value === 'likes') {
      applySortingOnData(result)
    }

    filteredList.value = result
    loadLikeCounts(result)
  } catch (error) {
    console.error('加载非遗列表失败', error)
    message.error('加载非遗列表失败，请稍后重试')
    filteredList.value = []
  } finally {
    loading.value = false
  }
}

// 对数据进行排序（不改变原始数组）
function applySortingOnData(data) {
  if (sortBy.value !== 'likes') return

  const orderMultiplier = sortOrder.value === 'desc' ? -1 : 1
  data.sort((a, b) => ((getItemLikeCount(a) || 0) - (getItemLikeCount(b) || 0)) * orderMultiplier)
}

function syncQuery() {
  router.replace({
    path: '/explore',
    query: {
      category: activeCategory.value !== '全部' ? activeCategory.value : undefined,
      region: activeRegion.value !== '全部' ? activeRegion.value : undefined,
      level: activeLevel.value !== '全部' ? activeLevel.value : undefined,
      dynasty: activeDynasty.value !== '全部' ? activeDynasty.value : undefined,
      q: keyword.value || undefined,
    },
  })
}

function updateAndFetch() {
  syncQuery()
  fetchList()
}

function resetFilters() {
  activeCategory.value = '全部'
  selectedProvince.value = ''
  selectedCity.value = ''
  selectedDistrict.value = ''
  activeLevel.value = '全部'
  activeDynasty.value = '全部'
  keyword.value = ''
  updateAndFetch()
}

function openDetail(item) {
  router.push({
    path: `/heritage/${item.id}`,
    query: {
      from: 'explore',
      page: currentPage.value,
      category: activeCategory.value !== '全部' ? activeCategory.value : undefined,
      region: activeRegion.value !== '全部' ? activeRegion.value : undefined,
      level: activeLevel.value !== '全部' ? activeLevel.value : undefined,
      dynasty: activeDynasty.value !== '全部' ? activeDynasty.value : undefined,
      q: keyword.value || undefined,
    }
  })
}

function askAI(item) {
  router.push(`/ai-studio?topic=${encodeURIComponent(item.name)}`)
}

const favoritedIds = ref(new Set())
const likeCounts = ref({})

async function loadFavorites() {
  if (!userStore.isLoggedIn) return
  try {
    const res = await getMyFavorites()
    favoritedIds.value = new Set((res.data || []).map(h => h.id))
  } catch (err) {
    console.error('加载收藏列表失败:', err)
  }
}

async function loadLikeCounts(items) {
  if (!items.length) return
  try {
    const results = await Promise.allSettled(
      items.map(item => getLikeCount('HERITAGE', item.id))
    )
    const counts = {}
    results.forEach((r, i) => {
      counts[items[i].id] = r.status === 'fulfilled' ? (r.value.data?.count || 0) : 0
    })
    likeCounts.value = counts
  } catch (err) {
    console.error('加载点赞数失败:', err)
  }
}

function getItemLikeCount(item) {
  return likeCounts.value[item.id] || 0
}

function isFavorited(item) {
  return favoritedIds.value.has(item.id)
}

async function toggleFavorite(item) {
  if (!userStore.isLoggedIn) {
    message.warning('请先登录后再收藏')
    router.push('/login')
    return
  }
  try {
    if (favoritedIds.value.has(item.id)) {
      await removeFavorite(item.id)
      favoritedIds.value.delete(item.id)
      message.success('已取消收藏')
    } else {
      await addFavorite(item.id)
      favoritedIds.value.add(item.id)
      message.success('收藏成功')
    }
    favoritedIds.value = new Set(favoritedIds.value)
  } catch (err) {
    const msg = err?.response?.data?.msg || err?.msg || '操作失败'
    message.error(msg)
  }
}

onMounted(async () => {
  loadFavorites()

  // 处理分类参数：可能是英文枚举值或中文
  const categoryParam = route.query.category || '全部'
  if (categoryCodeToName[categoryParam]) {
    // 如果是英文枚举值，转换为中文
    activeCategory.value = categoryCodeToName[categoryParam]
  } else {
    activeCategory.value = categoryParam
  }
  
  activeLevel.value = route.query.level || '全部'
  activeDynasty.value = route.query.dynasty || '全部'
  keyword.value = route.query.q || ''

  if (route.query.page) {
    const pageNum = parseInt(route.query.page)
    currentPage.value = pageNum
  } else {
    currentPage.value = 1
  }

  await fetchList()

  isInitialLoad.value = false
})

watch(() => route.query, (query) => {
  if (query.category && query.category !== activeCategory.value) {
    activeCategory.value = query.category
    fetchList()
  }
  if (query.q && query.q !== keyword.value) {
    keyword.value = query.q
    fetchList()
  }
})
</script>

<template>
  <div class="explore page-container">
    <!-- 用户视图：左侧筛选栏 + 右侧卡片列表 -->
    <div class="explore-layout container">
      <aside class="sidebar glass-card">
        <h3 class="sidebar-title">筛选条件</h3>
        <div class="filter-group">
          <label>分类</label>
          <a-select v-model:value="activeCategory" style="width:100%" @change="updateAndFetch" :options="categories.map(c => ({ value: c, label: c }))" />
        </div>
        <div class="filter-group">
          <label>地区</label>
          <a-select v-model:value="selectedProvince" placeholder="选择省份" style="width:100%; margin-bottom: 8px;" allowClear @change="selectedCity = ''; selectedDistrict = ''; updateAndFetch()" :options="provinces" />
          <a-select v-if="selectedProvince" v-model:value="selectedCity" placeholder="选择城市" style="width:100%; margin-bottom: 8px;" allowClear @change="selectedDistrict = ''; updateAndFetch()" :options="cities" />
          <a-select v-if="selectedCity && districts.length > 0" v-model:value="selectedDistrict" placeholder="选择区县" style="width:100%;" allowClear @change="updateAndFetch" :options="districts" />
        </div>
        <div class="filter-group">
          <label>级别</label>
          <a-select v-model:value="activeLevel" style="width:100%" @change="updateAndFetch" :options="levels.map(l => ({ value: l, label: l }))" />
        </div>
        <div class="filter-group">
          <label>朝代</label>
          <a-select v-model:value="activeDynasty" placeholder="全部" style="width:100%" @change="updateAndFetch" :options="dynastyOptions.map(d => ({ value: d, label: d }))" />
        </div>
        <div class="filter-group">
          <label>关键词</label>
          <a-input v-model:value="keyword" placeholder="输入关键词..." @pressEnter="updateAndFetch" allow-clear @clear="updateAndFetch" />
        </div>
        <button class="btn-ghost" style="width:100%;margin-top:8px" @click="resetFilters">重置筛选</button>
      </aside>

      <main class="explore-main">
        <div class="explore-header">
          <div>
            <h1 class="section-title">非遗 <span>探索</span></h1>
            <p class="result-count">共 {{ filteredList.length }} 项结果</p>
          </div>
          <div style="display: flex; gap: 16px; align-items: center;">
            <div class="active-filter-wrap" v-if="activeFilters.length">
              <span v-if="activeCategory !== '全部'" class="tag tag-blue" @click="activeCategory = '全部'; updateAndFetch()" style="cursor: pointer;">{{ activeCategory }}</span>
              <span v-if="activeRegion !== '全部'" class="tag tag-blue" @click="selectedProvince = ''; selectedCity = ''; selectedDistrict = ''; updateAndFetch()" style="cursor: pointer;">{{ locationLabel }}</span>
              <span v-if="activeLevel !== '全部'" class="tag tag-blue" @click="activeLevel = '全部'; updateAndFetch()" style="cursor: pointer;">{{ activeLevel }}</span>
              <span v-if="activeDynasty !== '全部'" class="tag tag-blue" @click="activeDynasty = '全部'; updateAndFetch()" style="cursor: pointer;">{{ activeDynasty }}</span>
              <span v-if="keyword" class="tag tag-blue" @click="keyword = ''; updateAndFetch()" style="cursor: pointer;">{{ keyword }}</span>
            </div>
            <!-- 排序下拉框 -->
            <a-dropdown trigger="click" placement="bottomRight">
              <div class="sort-select" style="cursor: pointer; padding: 6px 14px;">
                {{ currentSortLabel }}
              </div>
              <template #overlay>
                <a-menu @click="handleSortMenuClick">
                  <a-menu-item key="default">
                    <span>默认排序</span>
                  </a-menu-item>
                  <a-menu-item key="views">
                    <span>浏览量 {{ sortBy === 'views' ? (sortOrder === 'asc' ? '↑' : '↓') : '' }}</span>
                  </a-menu-item>
                  <a-menu-item key="likes">
                    <span>点赞数 {{ sortBy === 'likes' ? (sortOrder === 'asc' ? '↑' : '↓') : '' }}</span>
                  </a-menu-item>
                  <a-menu-item key="createdAt">
                    <span>创建时间 {{ sortBy === 'createdAt' ? (sortOrder === 'asc' ? '↑' : '↓') : '' }}</span>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>

          </div>
        </div>

        <div v-if="loading" class="empty-state">
          <div style="font-size:44px">⏳</div>
          <p>正在加载非遗项目...</p>
        </div>

        <div v-else class="heritage-grid">
          <div v-for="item in pagedList" :key="item.id" class="heritage-card card-hover glass-card" style="cursor: pointer;" @click="openDetail(item)">
            <div class="hcard-img">
              <img :src="item.imageUrl" :alt="item.name" loading="eager" v-img-fallback />
              <div class="hcard-level-left" :data-level="item.level">{{ item.level }}</div>
              <button class="hcard-fav-right" @click.stop="toggleFavorite(item)">
                <span v-if="isFavorited(item)" style="color: #f5a623;">⭐</span>
                <span v-else style="color: white; opacity: 0.9;">☆</span>
              </button>
            </div>
            <div class="hcard-body">
              <div class="hcard-meta">
                <span class="tag tag-red">{{ item.category }}</span>
              </div>
              <h3 class="hcard-name">{{ item.name }}</h3>
              <p class="hcard-desc">{{ item.desc }}</p>
              <div class="hcard-footer">
                <div class="hcard-info-row">
                  <span class="hcard-region">📍 {{ item.region }}</span>
                  <span v-if="item.originDynasty" class="hcard-dynasty">🏯 {{ item.originDynasty }}</span>
                  <span v-if="item.inheritanceType === 'GROUP'" class="hcard-inheritor">👥 全体民众</span>
                  <span v-else class="hcard-inheritor" :class="{ 'clickable-user': item.userId }" @click.stop="item.userId && goToUserProfile(item.userId)">👤 {{ item.inheritor || '待补充' }}</span>
                </div>
                <div class="hcard-stats-row">
                  <span class="hcard-stats">🔥 {{ item.views || 0 }} 浏览</span>
                  <span class="hcard-stats">👍 {{ getItemLikeCount(item) }}</span>
                </div>
                <div class="hcard-actions hcard-actions-right">
                  <button class="btn-ghost" style="padding:5px 12px;font-size:12px" @click.stop="askAI(item)">AI智能问答</button>
                  <button class="btn-primary" style="padding:5px 12px;font-size:12px" @click.stop="openDetail(item)">详情</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <Pagination v-if="filteredList.length > 6" v-model="currentPage" :total="filteredList.length" :page-size="6" style="margin-top: 32px" />

        <div v-if="!loading && filteredList.length === 0" class="empty-state">
          <div style="font-size:48px;margin-bottom:16px">🔍</div>
          <p>未找到相关非遗项目，请调整筛选条件</p>
        </div>
      </main>
    </div>

  </div>
</template>

<style scoped>
.explore { padding-bottom: 20px; }
.explore-layout { display: grid; grid-template-columns: 260px 1fr; gap: 28px; padding-top: 32px; }
.sidebar { padding: 24px; height: fit-content; position: sticky; top: calc(var(--header-height) + 20px); }
.sidebar-title { font-family: var(--font-serif); font-size: 16px; font-weight: 700; color: var(--text-primary); margin-bottom: 20px; padding-bottom: 12px; border-bottom: 1px solid var(--bg-glass-border); }
.filter-group { margin-bottom: 20px; }
.filter-group label { display: block; font-size: 13px; font-weight: 700; color: var(--text-secondary); letter-spacing: 0.03em; margin-bottom: 10px; }
.filter-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.filter-tag { padding: 6px 12px; border-radius: var(--radius-full); font-size: 13px; color: var(--text-secondary); background: var(--bg-glass); border: 1px solid var(--bg-glass-border); cursor: pointer; transition: all var(--transition-fast); }
.filter-tag.active, .filter-tag:hover { background: rgba(200,80,42,0.15); border-color: var(--color-primary); color: var(--color-primary-light); }
.explore-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; gap: 16px; }
.result-count { font-size: 14px; color: var(--text-secondary); margin-top: 8px; }
.active-filter-wrap { display: flex; gap: 8px; flex-wrap: wrap; }
/* 排序控制容器 */
.sort-control {
  display: flex;
  align-items: center;
  gap: 8px;
}
/* 排序下拉框样式 - 与已选标签风格一致 */
.sort-select {
  padding: 6px 14px;
  border-radius: var(--radius-full);
  font-size: 13px;
  color: #5A4E3F;
  background: #F5F0EB;
  border: 1px solid #E8E0D6;
  cursor: pointer;
  transition: all var(--transition-fast);
  outline: none;
  white-space: nowrap;
}
.sort-select:hover {
  background: #EDE7DF;
  border-color: #D6CFC5;
}
.sort-select:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(200, 80, 42, 0.1);
}
.heritage-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }
.heritage-card { overflow: hidden; }
.hcard-img { position: relative; height: 180px; overflow: hidden; }
.hcard-img img { width: 100%; height: 100%; object-fit: cover; transition: transform var(--transition-slow); }
.heritage-card:hover .hcard-img img { transform: scale(1.05); }
.hcard-level-left { 
  position: absolute; 
  top: 10px; 
  left: 10px; 
  padding: 4px 12px; 
  border-radius: var(--radius-full); 
  font-size: 12px; 
  font-weight: 700;
}

/* 市级 - 浅暖灰背景，深暖灰文字 */
.hcard-level-left[data-level="市级"] {
  background: #E8E0D6;
  color: #665A4E;
  border: 1px solid #D6CFC5;
}

/* 省级 - 浅青蓝背景，深青蓝文字 */
.hcard-level-left[data-level="省级"] {
  background: #D6E8F5;
  color: #4A7CB7;
  border: 1px solid #B8D4E8;
}

/* 国家级 - 浅红背景，深红文字 */
.hcard-level-left[data-level="国家级"] {
  background: #F8D7DA;
  color: #721C24;
  border: 1px solid #E5B8BC;
}

.hcard-fav-right { position: absolute; top: 10px; right: 10px; width: 32px; height: 32px; border-radius: 50%; background: rgba(0,0,0,0.3); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; border: 1px solid rgba(255,255,255,0.2); cursor: pointer; transition: all 0.2s; font-size: 14px; }
.hcard-fav-right:hover { background: rgba(0,0,0,0.5); transform: scale(1.1); }
.hcard-body { padding: 16px; }
.hcard-meta { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.hcard-region { font-size: 12px; color: var(--text-secondary); }
.hcard-dynasty { font-size: 12px; color: #8b5cf6; }
.hcard-name { font-family: var(--font-serif); font-size: 17px; font-weight: 700; color: var(--text-primary); margin-bottom: 8px; }
.hcard-desc { font-size: 13px; color: var(--text-secondary); line-height: 1.7; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; margin-bottom: 12px; min-height: 66px; }
.hcard-footer { 
  display: flex; 
  flex-direction: column;
  gap: 12px; 
}
/* 信息行：地区、传承人、浏览量、点赞数 */
.hcard-info-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--text-secondary);
  flex-wrap: wrap;
}
.hcard-info-row .hcard-region,
.hcard-info-row .hcard-inheritor,
.hcard-info-row .hcard-dynasty {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
.hcard-stats-row {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 4px;
}
.hcard-stats {
  font-weight: 500;
}
.hcard-inheritor { font-size: 12px; color: var(--text-secondary); }
.hcard-actions { display: flex; gap: 6px; }
.hcard-actions-right {
  justify-content: flex-end;
}
.empty-state { text-align: center; padding: 80px 20px; color: var(--text-muted); font-size: 14px; }
@media (max-width: 1100px) { .heritage-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 768px) { .explore-layout { grid-template-columns: 1fr; } .heritage-grid { grid-template-columns: 1fr; } .explore-header { display:block; } }

/* 管理员布局样式 */

/* 弹窗表单样式 */
.heritage-form .form-row { display: flex; gap: 16px; }
.heritage-form .form-col { flex: 1; margin-bottom: 0; }
.heritage-form :deep(.ant-form-item) { margin-bottom: 16px; }
.heritage-form :deep(.ant-form-item-label) { font-weight: 500; color: var(--text-primary); }
.heritage-form :deep(.ant-form-item-label > label.ant-form-item-required::before) { 
  color: #ff4d4f; 
  margin-right: 4px; 
}
.region-row { display: flex; gap: 12px; }
</style>
