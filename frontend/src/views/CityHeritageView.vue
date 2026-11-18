<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getHeritageList } from '../api/heritage'
import { message } from 'ant-design-vue'
import { useUserNavigation } from '../composables/useUserNavigation'
import { CATEGORY_MAP, LEVEL_MAP } from '../utils/constants'

const router = useRouter()
const { goToUserProfile } = useUserNavigation()
const loading = ref(false)
const cityHeritages = ref([])
const currentPage = ref(1)
const pageSize = ref(12)
const total = ref(0)

// 加载市级非遗项目
async function loadCityHeritages(page = 1) {
  loading.value = true
  try {
    const res = await getHeritageList({ 
      page, 
      size: pageSize.value, 
      level: '市级' // 只获取市级非遗项目
    })
    
    const records = res.data?.records || res.data || []
    cityHeritages.value = records.map(item => ({
      id: item.id,
      name: item.name,
      category: getCategoryName(item.category),
      region: item.region,
      level: getLevelName(item.level),
      description: item.description || item.summary || '',
      imageUrl: item.imageUrl || '/uploads/heritage/default.svg',
      inheritanceType: item.inheritanceType || 'INDIVIDUAL',
      inheritorName: item.inheritorName || '未知传承人',
      userId: item.userId || null,
      views: item.views || 0
    }))
    
    total.value = res.data?.total || 0
    currentPage.value = page
  } catch (error) {
    console.error('加载市级非遗项目失败', error)
    message.error('加载市级非遗项目失败，请稍后重试')
    cityHeritages.value = []
  } finally {
    loading.value = false
  }
}

function getCategoryName(category) {
  return CATEGORY_MAP[category] || category
}

function getLevelName(level) {
  return LEVEL_MAP[level] || level
}

// 获取级别样式类名
function getLevelClass(level) {
  const classMap = {
    [LEVEL_MAP.CITY]: 'city-level',
    [LEVEL_MAP.PROVINCIAL]: 'provincial-level',
    [LEVEL_MAP.NATIONAL]: 'national-level'
  }
  return classMap[level] || 'city-level'
}

// 跳转到详情页
function openDetail(item) {
  router.push(`/heritage/${item.id}`)
}

// 分页处理
function handlePageChange(page) {
  loadCityHeritages(page)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  loadCityHeritages()
})
</script>

<template>
  <div class="city-heritage-page">
    <div class="container">
      <!-- 页面标题 -->
      <div class="page-header">
        <h1 class="page-title">
          <span class="title-icon">🏛️</span>
          市级非遗项目
        </h1>
        <p class="page-subtitle">探索各地市级非物质文化遗产，感受地方文化的独特魅力</p>
        <div class="result-count">共 {{ total }} 项市级非遗项目</div>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>正在加载市级非遗项目...</p>
      </div>

      <!-- 非遗项目列表 -->
      <div v-else-if="cityHeritages.length > 0" class="heritage-grid">
        <div 
          v-for="item in cityHeritages" 
          :key="item.id" 
          class="heritage-card glass-card card-hover"
          @click="openDetail(item)"
        >
          <div class="card-image">
            <img :src="item.imageUrl" :alt="item.name" loading="lazy" v-img-fallback />
            <div class="card-overlay">
              <span class="level-tag" :class="getLevelClass(item.level)">{{ item.level }}</span>
            </div>
          </div>
          <div class="card-content">
            <div class="card-meta">
              <span class="category-tag">{{ item.category }}</span>
              <span class="region-tag">📍 {{ item.region }}</span>
            </div>
            <h3 class="card-title">{{ item.name }}</h3>
            <p class="card-description">{{ item.description }}</p>
            <div class="card-footer">
              <span v-if="item.inheritanceType === 'GROUP'" class="inheritor">传承主体：全体民众</span>
              <span v-else class="inheritor" :class="{ 'clickable-user': item.userId }" @click.stop="item.userId && goToUserProfile(item.userId)">传承人：{{ item.inheritorName }}</span>
              <span class="views">👁️ {{ item.views }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <div class="empty-icon">🏛️</div>
        <h3>暂无市级非遗项目</h3>
        <p>目前还没有收录市级非遗项目，敬请期待</p>
        <button class="btn-primary" @click="$router.push('/explore')">查看全部非遗项目</button>
      </div>

      <!-- 分页 -->
      <div v-if="total > pageSize && !loading" class="pagination">
        <a-pagination 
          :current="currentPage" 
          :total="total" 
          :pageSize="pageSize"
          @change="handlePageChange"
          show-size-changer
          :show-total="(total) => `共 ${total} 项`"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.city-heritage-page {
  padding: 40px 0;
  min-height: calc(100vh - 200px);
  background: linear-gradient(180deg, #F9F5F0 0%, #FFF 100%);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
}

/* 页面头部 */
.page-header {
  text-align: center;
  margin-bottom: 40px;
}

.page-title {
  font-family: 'Noto Serif SC', serif;
  font-size: 36px;
  font-weight: 700;
  color: #35231c;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.title-icon {
  font-size: 40px;
}

.page-subtitle {
  font-size: 16px;
  color: rgba(53, 35, 28, 0.68);
  margin-bottom: 16px;
  line-height: 1.8;
}

.result-count {
  display: inline-block;
  padding: 6px 16px;
  background: rgba(201, 71, 44, 0.08);
  border-radius: 20px;
  color: #c9472c;
  font-size: 14px;
  font-weight: 500;
}

/* 加载状态 */
.loading-state {
  text-align: center;
  padding: 80px 20px;
}

.loading-spinner {
  width: 50px;
  height: 50px;
  margin: 0 auto 20px;
  border: 4px solid rgba(201, 71, 44, 0.1);
  border-top-color: #c9472c;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-state p {
  color: rgba(53, 35, 28, 0.68);
  font-size: 15px;
}

/* 非遗项目网格 */
.heritage-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 40px;
}

.heritage-card {
  overflow: hidden;
  cursor: pointer;
  background: rgba(255, 252, 247, 0.96);
  border: 1px solid rgba(201, 71, 44, 0.1);
  border-radius: 16px;
  transition: all 0.3s ease;
}

.heritage-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 16px 40px rgba(120, 76, 49, 0.15);
  border-color: rgba(201, 71, 44, 0.2);
}

.card-image {
  position: relative;
  height: 200px;
  overflow: hidden;
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.heritage-card:hover .card-image img {
  transform: scale(1.05);
}

.card-overlay {
  position: absolute;
  top: 12px;
  right: 12px;
}

.level-tag {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  backdrop-filter: blur(8px);
}

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

.card-content {
  padding: 20px;
}

.card-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.category-tag {
  padding: 4px 10px;
  background: rgba(216, 162, 75, 0.12);
  color: #d8a24b;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
}

.region-tag {
  font-size: 12px;
  color: rgba(53, 35, 28, 0.58);
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #35231c;
  margin: 0 0 10px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-description {
  font-size: 13px;
  color: rgba(53, 35, 28, 0.68);
  line-height: 1.7;
  margin: 0 0 16px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 66px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid rgba(201, 71, 44, 0.08);
}

.inheritor {
  font-size: 12px;
  color: rgba(53, 35, 28, 0.58);
}

.views {
  font-size: 12px;
  color: rgba(53, 35, 28, 0.58);
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 80px 20px;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 20px;
  opacity: 0.5;
}

.empty-state h3 {
  font-size: 20px;
  color: #35231c;
  margin-bottom: 10px;
}

.empty-state p {
  font-size: 14px;
  color: rgba(53, 35, 28, 0.58);
  margin-bottom: 24px;
}

/* 按钮 */
.btn-primary {
  padding: 12px 24px;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #c9472c, #a73720);
  color: #fff;
  cursor: pointer;
  font-weight: 500;
  font-size: 14px;
  transition: all 0.2s;
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(201, 71, 44, 0.25);
}

/* 分页 */
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 40px;
}

/* 响应式 */
@media (max-width: 768px) {
  .page-title {
    font-size: 28px;
  }
  
  .heritage-grid {
    grid-template-columns: 1fr;
  }
  
  .card-image {
    height: 180px;
  }
}
</style>
