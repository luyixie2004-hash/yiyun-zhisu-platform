<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '../stores/user'
import { getExperienceList } from '../api/experience'
import { getExperienceCount, getInheritorCount } from '../api/statistics'
import { createBooking } from '../api/booking'
import StarRating from '../components/StarRating.vue'
import { regionData } from '../data/regionData'
import { useUserNavigation } from '../composables/useUserNavigation'
import { EXPERIENCE_TYPE_MAP, EXPERIENCE_TYPE_NAME_TO_CODE } from '../utils/constants'
import Pagination from '../components/Pagination.vue'
import dayjs from 'dayjs'

const { goToUserProfile } = useUserNavigation()


const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const allWorkshops = ref([])
const loading = ref(false)

// 数据库真实总数
const totalWorkshopsCount = ref(0)
const totalMastersCount = ref(0)

const typeMap = EXPERIENCE_TYPE_MAP

// ========== 筛选状态 ==========
const allTypes = ['全部', ...Object.values(EXPERIENCE_TYPE_MAP)]
const typeNameToCode = EXPERIENCE_TYPE_NAME_TO_CODE
const activeType = ref('全部')
const selectedProvince = ref('')
const selectedCity = ref('')
const selectedDistrict = ref('')
const keyword = ref('')
const sortBy = ref('default')
const sortOrder = ref('asc')

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

// 当前有效的地区筛选值（最具体的层级）
const activeLocation = computed(() => {
  return selectedDistrict.value || selectedCity.value || selectedProvince.value || '全部'
})

// 地区显示文本：省·市·区
const locationLabel = computed(() => {
  const parts = [selectedProvince.value, selectedCity.value, selectedDistrict.value].filter(Boolean)
  return parts.length > 0 ? parts.join('·') : '全部'
})

const hasActiveFilters = computed(() =>
  activeType.value !== '全部' || activeLocation.value !== '全部' || keyword.value.trim() !== ''
)

const currentSortLabel = computed(() => {
  const labels = { default: '默认排序', price: '价格', rating: '评分', stock: '剩余名额' }
  const arrow = sortOrder.value === 'asc' ? ' ↑' : ' ↓'
  return sortBy.value === 'default' ? labels.default : labels[sortBy.value] + arrow
})

function handleSortMenuClick({ key }) {
  if (sortBy.value === key) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortBy.value = key
    sortOrder.value = key === 'price' || key === 'stock' ? 'asc' : 'desc'
  }
}

function resetFilters() {
  activeType.value = '全部'
  selectedProvince.value = ''
  selectedCity.value = ''
  selectedDistrict.value = ''
  keyword.value = ''
  sortBy.value = 'default'
  sortOrder.value = 'asc'
  loadWorkshops()
}

function mapExperienceToWorkshop(item) {
  const total = Number(item.quota || 10)
  const booked = Number(item.bookedNum || 0)
  const remaining = total - booked
  // 转换类型为中文
  const typeChinese = typeMap[item.type] || item.type || '非遗体验'

  return {
    id: item.id,
    name: item.name,
    location: item.location || '待定',
    date: item.schedule || '具体时间以预约页为准',
    time: '可选时段',
    price: Number(item.price || 0),
    total: total,  // 总人数
    booked: booked,  // 已预约人数
    stock: remaining,  // 剩余人数（计算得出）
    master: item.inheritorName || item.type || '非遗传承人',
    masterId: item.userId || null,
    img: item.imageUrl || 'https://picsum.photos/800/500?random=' + item.id,
    tags: item.type ? [typeChinese] : ['非遗体验'],
    desc: item.description || '沉浸式体验传统非遗文化。',
    highlight: remaining <= 3 ? '🔥 名额紧张' : '',
    rating: Number(item.rating || 5.0),
    typeCode: item.type,
    startTime: item.startTime || null,
    endTime: item.endTime || null,
    schedule: item.schedule || '',
  }
}

async function loadWorkshops(keywordParam = null) {
  loading.value = true
  try {
    const params = {}
    if (keywordParam) params.keyword = keywordParam
    const res = await getExperienceList(params)
    allWorkshops.value = (res.data || []).map(mapExperienceToWorkshop).sort((a, b) => b.id - a.id)
  } catch (error) {
    console.error('加载工坊数据失败', error)
    message.error('加载工坊数据失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 筛选后的工坊列表
const filteredWorkshops = computed(() => {
  let list = allWorkshops.value

  if (activeType.value !== '全部') {
    const typeCode = typeNameToCode[activeType.value]
    list = list.filter(w => w.typeCode === typeCode)
  }

  if (activeLocation.value !== '全部') {
    list = list.filter(w => w.location && (w.location.includes(activeLocation.value) || activeLocation.value.includes(w.location)))
  }

  if (keyword.value.trim()) {
    const kw = keyword.value.trim().toLowerCase()
    list = list.filter(w => w.name.toLowerCase().includes(kw) || w.desc.toLowerCase().includes(kw))
  }

  if (sortBy.value !== 'default') {
    list = [...list].sort((a, b) => {
      let va, vb
      switch (sortBy.value) {
        case 'price': va = a.price; vb = b.price; break
        case 'rating': va = a.rating; vb = b.rating; break
        case 'stock': va = a.stock; vb = b.stock; break
        default: return 0
      }
      return sortOrder.value === 'asc' ? va - vb : vb - va
    })
  }

  return list
})

// 筛选条件变化时重置到第一页
watch([activeType, selectedProvince, selectedCity, selectedDistrict, keyword, sortBy, sortOrder], () => {
  currentPage.value = 1
})

onMounted(async () => {
  const queryKeyword = route.query.keyword
  if (queryKeyword) {
    keyword.value = queryKeyword
    loadWorkshops(queryKeyword)
  } else {
    loadWorkshops()
  }

  // 获取数据库真实总数
  try {
    const [expRes, inhRes] = await Promise.all([
      getExperienceCount(),
      getInheritorCount()
    ])
    totalWorkshopsCount.value = expRes.data || 0
    totalMastersCount.value = inhRes.data || 0
  } catch (e) {
    console.error('获取统计数据失败', e)
  }
})

// 分页
const currentPage = ref(1)
const pageSize = 6
const pagedWorkshops = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredWorkshops.value.slice(start, start + pageSize)
})

// 解析 schedule 文本，提取星期和时间段
function parseSchedule(text) {
  if (!text) return null
  const dayMap = { '日': 0, '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6 }
  const result = { days: [], timeRange: null }
  const dayPart = text.match(/每\s*周\s*([一-鿿、，,\s]+)/)
  if (dayPart) {
    const tokens = dayPart[1].split(/[、，,\s]+/).filter(Boolean)
    for (const token of tokens) {
      const m = token.match(/周?([一-鿿])/)
      if (m && dayMap[m[1]] !== undefined) result.days.push(dayMap[m[1]])
    }
  }
  const timeMatch = text.match(/(\d{1,2}:\d{2})\s*[-–—至]\s*(\d{1,2}:\d{2})/)
  if (timeMatch) result.timeRange = `${timeMatch[1].padStart(5, '0')}-${timeMatch[2].padStart(5, '0')}`
  return result.days.length > 0 || result.timeRange ? result : null
}

// 弹窗中的日期选择（仅线下工坊使用）
const modalBookingDate = ref(null)

const isOnline = computed(() => selectedWorkshop.value?.typeCode === '线上活动')

// 禁用不可预约的日期
function disabledDate(current) {
  if (!selectedWorkshop.value || isOnline.value) return false
  const ws = selectedWorkshop.value
  const parsed = parseSchedule(ws.schedule)
  const allowedDays = parsed ? parsed.days : []
  let disabled = false
  if (allowedDays.length > 0 && !allowedDays.includes(current.day())) disabled = true
  if (ws.startTime) {
    const start = dayjs(ws.startTime).startOf('day')
    if (current.isBefore(start)) disabled = true
  }
  if (ws.endTime) {
    const end = dayjs(ws.endTime).startOf('day')
    if (current.isAfter(end)) disabled = true
  }
  if (current.isBefore(dayjs().startOf('day'))) disabled = true
  return disabled
}

// 预约弹窗相关
const selectedWorkshop = ref(null)
const detailModalVisible = ref(false)

// 预约表单
const bookingForm = ref({
  name: '',
  phone: '',
  people: 1,
  selectedTime: ''
})

// 可预约时间段（线上活动无时段选择，线下工坊从 schedule 解析）
const availableTimeSlots = computed(() => {
  if (!selectedWorkshop.value) return []
  if (isOnline.value) return []
  const parsed = parseSchedule(selectedWorkshop.value.schedule)
  if (parsed && parsed.timeRange) {
    return [{ label: parsed.timeRange, value: parsed.timeRange }]
  }
  return [
    { label: '上午 09:00-12:00', value: '09:00-12:00' },
    { label: '下午 14:00-17:00', value: '14:00-17:00' },
  ]
})

function viewDetail(ws) {
  selectedWorkshop.value = ws
  detailModalVisible.value = true
  modalBookingDate.value = null
  bookingForm.value = {
    name: '',
    phone: '',
    people: 1,
    selectedTime: ''
  }
}

function closeDetailModal() {
  detailModalVisible.value = false
  selectedWorkshop.value = null
}

// AI智能问答 - 跳转到AIGC工坊
function aiChat(ws) {
  router.push(`/ai-studio?topic=${encodeURIComponent(ws.name)}`)
}

// 从个人中心获取手机号并填充
function fillPhoneFromProfile() {
  if (userStore.userInfo?.phone) {
    bookingForm.value.phone = userStore.userInfo.phone
    message.success('已自动填充手机号')
  } else {
    message.warning('个人中心暂无手机号信息，请手动输入')
  }
}

async function submitBooking() {
  if (!bookingForm.value.name.trim()) {
    message.warning('请输入姓名')
    return
  }

  if (!bookingForm.value.phone.trim()) {
    message.warning('请输入手机号')
    return
  }

  const phonePattern = /^1\d{10}$/
  if (!phonePattern.test(bookingForm.value.phone)) {
    message.warning('请输入正确的11位手机号')
    return
  }

  // 线下工坊需要选择日期
  if (!isOnline.value && !modalBookingDate.value) {
    message.warning('请选择预约日期')
    return
  }

  // 线下工坊需要选择时间段
  if (!isOnline.value && !bookingForm.value.selectedTime) {
    message.warning('请选择预约时间段')
    return
  }

  if (bookingForm.value.people > 6) {
    message.warning('预约人数最多为6人，请调整人数')
    return
  }

  if (!userStore.isLoggedIn) {
    message.warning('请先登录后再预约')
    router.push('/login')
    return
  }

  // 线上活动：使用固定 schedule 作为预约时间
  const bookingTime = isOnline.value
    ? (selectedWorkshop.value.schedule || '具体时间以预约页为准')
    : bookingForm.value.selectedTime

  // 线下工坊：使用选中的日期；线上活动：使用当天日期
  const bookingDate = isOnline.value
    ? dayjs().format('YYYY-MM-DD')
    : modalBookingDate.value.format('YYYY-MM-DD')

  try {
    await createBooking({
      experienceId: selectedWorkshop.value.id,
      bookingDate,
      bookingTime,
      people: bookingForm.value.people,
      contactName: bookingForm.value.name,
      contactPhone: bookingForm.value.phone,
      remark: ''
    })

    message.success('预约成功！')
    closeDetailModal()
    // 预约成功后重新加载工坊数据，更新剩余名额
    await loadWorkshops()
  } catch (error) {
    console.error('预约失败:', error)
    const errorMsg = error.response?.data?.message || error.message || '预约失败，请稍后重试'
    message.error(errorMsg)
  }
}

const stockPercent = (ws) => Math.round((ws.stock / Math.max(ws.total || 1, 1)) * 100)

const avgRating = computed(() => {
  const rated = allWorkshops.value.filter(w => w.rating > 0)
  if (rated.length === 0) return 5.0
  const sum = rated.reduce((s, w) => s + w.rating, 0)
  return Math.round((sum / rated.length) * 10) / 10
})

const ratingPercent = computed(() => Math.round((avgRating.value / 5) * 100))
</script>

<template>
  <div class="page-container">
    <div class="container booking-page">
      <section class="booking-hero glass-card">
        <div class="hero-decor">🏮</div>
        <div class="hero-inner">
          <div class="hero-badge">🎫 沉浸式非遗体验 · 限额预约</div>
          <h1 class="section-title">非遗体验工坊预约</h1>
          <p class="hero-desc">走进传承人的工作室，用双手触碰千年技艺。每场限额开放，先到先得。</p>
          <div class="hero-stats">
            <div class="hs-item"><strong>{{ totalWorkshopsCount || allWorkshops.length }}</strong><span>精选工坊</span></div>
            <div class="hs-item"><strong>{{ totalMastersCount || 0 }}</strong><span>传承大师</span></div>
            <div class="hs-item"><strong>{{ ratingPercent }}%</strong><span>好评率</span></div>
          </div>
        </div>
      </section>

      <!-- 筛选栏 -->
      <section class="filter-bar glass-card">
        <div class="filter-group">
          <span class="filter-label">分类：</span>
          <a-select v-model:value="activeType" style="width:100px" :options="allTypes.map(t => ({ value: t, label: t }))" />
        </div>

        <div class="filter-group">
          <span class="filter-label">地区：</span>
          <a-select v-model:value="selectedProvince" placeholder="选择省份" style="width:100px" allowClear @change="selectedCity = ''; selectedDistrict = ''" :options="provinces" />
          <a-select v-if="selectedProvince" v-model:value="selectedCity" placeholder="选择城市" style="width:100px" allowClear @change="selectedDistrict = ''" :options="cities" />
          <a-select v-if="selectedCity && districts.length > 0" v-model:value="selectedDistrict" placeholder="选择区县" style="width:100px" allowClear :options="districts" />
        </div>

        <div class="filter-group">
          <span class="filter-label">搜索：</span>
          <a-input v-model:value="keyword" placeholder="搜索工坊..." allowClear style="width: 200px" />
        </div>

        <button class="filter-reset" @click="resetFilters">重置筛选</button>
      </section>

      <!-- 激活的筛选标签 + 排序 -->
      <div class="active-filters">
        <div class="af-tags" v-if="hasActiveFilters">
          <span v-if="activeType !== '全部'" class="tag tag-blue" @click="activeType = '全部'" style="cursor: pointer;">
            {{ activeType }}
          </span>
          <span v-if="activeLocation !== '全部'" class="tag tag-blue" @click="selectedProvince = ''; selectedCity = ''; selectedDistrict = ''" style="cursor: pointer;">
            {{ locationLabel }}
          </span>
          <span v-if="keyword" class="tag tag-blue" @click="keyword = ''" style="cursor: pointer;">
            {{ keyword }}
          </span>
        </div>

        <div class="af-sort">
          <a-dropdown>
            <button class="filter-tag sort-btn">{{ currentSortLabel }}</button>
            <template #overlay>
              <a-menu @click="handleSortMenuClick">
                <a-menu-item key="default">默认排序</a-menu-item>
                <a-menu-item key="price">价格 {{ sortBy === 'price' ? (sortOrder === 'asc' ? '↑' : '↓') : '' }}</a-menu-item>
                <a-menu-item key="rating">评分 {{ sortBy === 'rating' ? '↓' : '' }}</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </div>

      <section class="ws-grid" v-if="!loading && pagedWorkshops.length > 0">
        <div v-for="ws in pagedWorkshops" :key="ws.id" class="ws-card glass-card card-hover" :class="{ soldout: ws.stock <= 0 }">
          <div class="ws-img-wrap">
            <img :src="ws.img" :alt="ws.name" />
            <div class="ws-price-badge">¥{{ ws.price }}<small>/人</small></div>
            <div v-if="ws.highlight" class="ws-highlight">{{ ws.highlight }}</div>
            <div v-if="ws.stock <= 0" class="ws-soldout-overlay">已约满</div>
          </div>
          <div class="ws-body">
            <div class="ws-tags"><span v-for="t in ws.tags" :key="t" class="tag tag-red">{{ t }}</span></div>
            <h3 class="ws-name">{{ ws.name }}</h3>
            <p class="ws-desc">{{ ws.desc }}</p>
            <div class="ws-meta">
              <span>📍 {{ ws.location }}</span><span>📅 {{ ws.date }}</span>
              <span>🕐 {{ ws.time }}</span><span :class="{ 'clickable-user': ws.masterId }" @click.stop="ws.masterId && goToUserProfile(ws.masterId)">👤 {{ ws.master }}</span>
              <span class="ws-rating"><StarRating :model-value="ws.rating" :disabled="true" :size="14" /></span>
            </div>
            <div class="ws-stock-row">
              <div class="ws-stock-bar"><div class="ws-stock-fill" :style="{ width: stockPercent(ws)+'%' }" :class="{ low: ws.stock <= 5 && ws.stock > 0 }"></div></div>
              <span class="ws-stock-text" :class="{ danger: ws.stock <= 5 }">剩余 {{ ws.stock }}/{{ ws.total }}</span>
            </div>
            <div class="ws-actions" style="display: flex; gap: 8px; margin-top: 16px;">
              <button class="btn-primary" style="flex: 1" @click="viewDetail(ws)">
                📖 详情
              </button>
              <button class="btn-ghost" style="flex: 1" @click="aiChat(ws)">
                AI智能问答
              </button>
            </div>
          </div>
        </div>
      </section>

      <div v-if="loading" class="empty-state glass-card" style="padding: 48px; text-align: center; color: var(--text-muted);">工坊加载中...</div>
      <div v-else-if="pagedWorkshops.length === 0 && !loading" class="empty-state glass-card" style="padding: 48px; text-align: center; color: var(--text-muted);">
        <p style="font-size: 16px; margin-bottom: 12px;">没有找到匹配的工坊</p>
        <button class="filter-tag" @click="resetFilters" style="cursor: pointer;">重置筛选条件</button>
      </div>

      <!-- 分页 -->
      <Pagination v-if="filteredWorkshops.length > 6" v-model="currentPage" :total="filteredWorkshops.length" :page-size="6" />

      <!-- 预约弹窗 -->
      <a-modal
        v-model:open="detailModalVisible"
        :title="selectedWorkshop ? selectedWorkshop.name : '预约体验'"
        width="600px"
        @cancel="closeDetailModal"
        :footer="null"
      >
        <div v-if="selectedWorkshop" class="booking-modal-content">
          <!-- 工坊信息 -->
          <div class="activity-info">
            <img
              :src="selectedWorkshop.img"
              :alt="selectedWorkshop.name"
              class="activity-image"
            />
            <div class="activity-details">
              <h3>{{ selectedWorkshop.name }}</h3>
              <div class="activity-meta">
                <span>📍 {{ selectedWorkshop.location || '待定' }}</span>
                <span>⏱️ {{ selectedWorkshop.time || '2小时' }}</span>
                <span :class="{ 'clickable-user': selectedWorkshop.masterId }" @click="selectedWorkshop.masterId && goToUserProfile(selectedWorkshop.masterId)">👤 {{ selectedWorkshop.master || '传承人' }}</span>
              </div>
              <p class="activity-price">¥{{ selectedWorkshop.price || 0 }}</p>
            </div>
          </div>

          <!-- 预约时间 -->
          <div class="time-slots-section">
            <!-- 线上活动：固定时间 -->
            <template v-if="isOnline">
              <h4>活动时间</h4>
              <div class="fixed-schedule">
                <span>📅 {{ selectedWorkshop.schedule || '具体时间待定' }}</span>
              </div>
            </template>

            <!-- 线下工坊：日期选择 + 时段选择 -->
            <template v-else>
              <h4>选择预约日期</h4>
              <a-date-picker
                v-model:value="modalBookingDate"
                :disabled-date="disabledDate"
                format="YYYY-MM-DD"
                placeholder="请选择日期"
                style="width: 100%; margin-bottom: 12px;"
              />
              <h4>选择预约时段</h4>
              <a-radio-group v-model:value="bookingForm.selectedTime" class="time-slots">
                <a-radio-button
                  v-for="slot in availableTimeSlots"
                  :key="slot.value"
                  :value="slot.value"
                >
                  {{ slot.label }}
                </a-radio-button>
              </a-radio-group>
            </template>
          </div>

          <!-- 预约表单 -->
          <div class="booking-form">
            <h4>预约信息</h4>

            <a-form layout="vertical">
              <a-form-item label="姓名" required>
                <a-input
                  v-model:value="bookingForm.name"
                  placeholder="请填写预约人姓名（建议使用真实姓名，便于签到核对）"
                  :maxlength="20"
                />
              </a-form-item>
              <a-form-item label="手机号" required>
                <div class="phone-input-wrapper">
                  <a-input
                    v-model:value="bookingForm.phone"
                    placeholder="请输入11位手机号"
                    :maxlength="11"
                    style="flex: 1"
                  />
                  <a-button
                    type="default"
                    class="fill-phone-btn"
                    @click="fillPhoneFromProfile"
                  >
                    一键填充
                  </a-button>
                </div>
              </a-form-item>
              <a-form-item label="预约人数" required>
                <div class="people-input-wrapper">
                  <a-input-number
                    v-model:value="bookingForm.people"
                    :min="1"
                    :max="6"
                    style="flex: 1"
                  />
                  <span class="people-tip">（最多6人）</span>
                </div>
              </a-form-item>
            </a-form>
          </div>

          <!-- 操作按钮 -->
          <div class="modal-actions">
            <button class="btn-ghost" @click="closeDetailModal">取消</button>
            <button class="btn-primary" @click="submitBooking">提交预约</button>
          </div>
        </div>

        <!-- 没有工坊时的提示 -->
        <div v-else class="no-activity-tip">
          <p>暂无可预约的工坊，请稍后再试</p>
          <button class="btn-primary" @click="closeDetailModal">关闭</button>
        </div>
      </a-modal>
    </div>

  </div>
</template>

<style scoped>
.booking-page { padding-top: 32px; padding-bottom: 48px; max-width: 1024px; margin: 0 auto; }
.booking-hero { padding: 40px 36px; text-align: center; margin-bottom: 32px; background: linear-gradient(135deg, rgba(201,71,44,0.06), rgba(212,165,83,0.04)); position: relative; overflow: hidden; }
.hero-decor { position: absolute; right: 40px; top: -10px; font-size: 120px; opacity: 0.06; pointer-events: none; }
.hero-inner { position: relative; max-width: 680px; margin: 0 auto; }
.hero-badge { display: inline-block; padding: 6px 18px; border-radius: 20px; background: rgba(201,71,44,0.1); color: var(--color-primary); font-size: 13px; margin-bottom: 14px; }
.hero-desc { color: var(--text-secondary); margin-top: 10px; line-height: 1.8; }
.hero-stats { display: flex; justify-content: center; gap: 40px; margin-top: 20px; }
.hs-item { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.hs-item strong { font-size: 26px; color: var(--color-primary); }
.hs-item span { font-size: 12px; color: var(--text-muted); }

.ws-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px; }
.ws-card { overflow: hidden; display: flex; flex-direction: column; transition: transform 0.3s, box-shadow 0.3s; }
.ws-card:hover { transform: translateY(-4px); }
.ws-card.soldout { opacity: 0.65; }
.ws-img-wrap { position: relative; height: 160px; overflow: hidden; }
.ws-img-wrap img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.5s; }
.ws-card:hover .ws-img-wrap img { transform: scale(1.06); }
.ws-price-badge { position: absolute; top: 10px; right: 10px; padding: 4px 12px; background: rgba(201,71,44,0.92); color: #fff; border-radius: 20px; font-weight: 700; font-size: 14px; }
.ws-price-badge small { font-size: 10px; font-weight: 400; opacity: 0.8; }
.ws-highlight { position: absolute; top: 10px; left: 10px; padding: 3px 10px; background: rgba(212,165,83,0.9); color: #fff; border-radius: 12px; font-size: 11px; font-weight: 600; }
.ws-soldout-overlay { position: absolute; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; font-size: 22px; font-weight: 700; color: #fff; letter-spacing: 6px; }
.ws-body { padding: 14px; flex: 1; display: flex; flex-direction: column; gap: 6px; }
.ws-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.ws-name { font-family: var(--font-serif); font-size: 16px; font-weight: 700; }
.ws-desc { font-size: 12px; color: var(--text-secondary); line-height: 1.5; flex: 1; }
.ws-meta { display: flex; flex-wrap: wrap; gap: 8px; font-size: 11px; color: var(--text-muted); }
.ws-rating { display: inline-flex; align-items: center; }
.ws-stock-row { display: flex; align-items: center; gap: 10px; }
.ws-stock-bar { flex: 1; height: 5px; background: rgba(201,71,44,0.1); border-radius: 3px; overflow: hidden; }
.ws-stock-fill { height: 100%; background: linear-gradient(90deg, #4f9a7a, #3dbd7d); border-radius: 3px; transition: width 0.5s; }
.ws-stock-fill.low { background: linear-gradient(90deg, #c9472c, #e05a3a); animation: pulse-bar 1.5s infinite; }
@keyframes pulse-bar { 0%,100%{opacity:1} 50%{opacity:0.6} }
.ws-stock-text { font-size: 12px; color: var(--text-secondary); white-space: nowrap; }
.ws-stock-text.danger { color: #c9472c; font-weight: 600; }
.ws-book-btn { width: 100%; margin-top: 4px; font-size: 13px; padding: 8px; }


/* 筛选栏 */
.filter-bar { display: flex; align-items: center; gap: 20px; padding: 16px 20px; margin-bottom: 20px; flex-wrap: wrap; }
.filter-group { display: flex; align-items: center; gap: 10px; }
.filter-label { font-size: 14px; color: var(--text-secondary); white-space: nowrap; flex-shrink: 0; }
.filter-tag { padding: 5px 14px; border: 1px solid var(--bg-glass-border); border-radius: 16px; background: #fff; cursor: pointer; font-size: 13px; color: var(--text-secondary); transition: all 0.2s; white-space: nowrap; }
.filter-tag:hover { color: var(--color-primary); border-color: var(--color-primary); }
.filter-tag.active { color: #fff; background: var(--color-primary); border-color: var(--color-primary); }
.sort-btn { min-width: 90px; background: transparent; border-color: transparent; }
.sort-btn:hover { background: rgba(0,0,0,0.04); border-color: var(--bg-glass-border); }
.filter-reset { margin-left: auto; padding: 5px 14px; border: 1px solid var(--bg-glass-border); border-radius: 16px; background: #fff; cursor: pointer; font-size: 13px; color: var(--text-muted); transition: all 0.2s; white-space: nowrap; flex-shrink: 0; }
.filter-reset:hover { color: #c9472c; border-color: #c9472c; }

.active-filters { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.af-tags { display: flex; gap: 8px; flex-wrap: wrap; }
.af-sort { margin-left: auto; display: flex; align-items: center; gap: 8px; flex-shrink: 0; }

/* 详情弹窗样式 */
.booking-modal-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.activity-info {
  display: flex;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--bg-glass-border);
}

.activity-image {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: var(--radius-md);
  flex-shrink: 0;
}

.activity-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.activity-details h3 {
  margin: 0;
  font-size: 18px;
  font-family: var(--font-serif);
  color: var(--text-primary);
}

.activity-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: var(--text-secondary);
}

.activity-price {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-primary);
}

.time-slots-section h4,
.booking-form h4 {
  margin: 0 0 12px 0;
  font-size: 16px;
  color: var(--text-primary);
}

.time-slots {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
}

.time-slots :deep(.ant-radio-button-wrapper) {
  margin-bottom: 8px;
}

.fixed-schedule {
  padding: 12px 16px;
  background: rgba(201, 71, 44, 0.05);
  border-radius: var(--radius-md);
  font-size: 15px;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.booking-form {
  padding-top: 16px;
  border-top: 1px solid var(--bg-glass-border);
}

/* 手机号输入框包装器 */
.phone-input-wrapper {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* 预约人数包装器 */
.people-input-wrapper {
  display: flex;
  gap: 8px;
  align-items: center;
}

.people-tip {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--text-muted);
  white-space: nowrap;
}

.fill-phone-btn {
  flex-shrink: 0;
  background: #f5f5f5;
  border: 1px solid var(--bg-glass-border);
  color: var(--text-secondary);
  font-size: 13px;
  padding: 6px 16px;
  border-radius: 6px;
  transition: all 0.2s;
}

.fill-phone-btn:hover {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--bg-glass-border);
}

.no-activity-tip {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-muted);
}

.no-activity-tip p {
  margin-bottom: 20px;
  font-size: 15px;
}

/* 响应式 */
@media (max-width: 900px) {
  .ws-grid { grid-template-columns: 1fr; }
}
</style>
