<template>
  <div class="page-container">
    <div class="container inheritor-booking-page">
      <!-- 页面标题 -->
      <div class="page-header">
        <h1 class="page-title">预约管理</h1>
        <p class="page-desc">查看和管理您发布的体验项目预约订单</p>
      </div>

      <!-- 统计卡片 -->
      <div class="stats-cards">
        <div class="stat-card">
          <div class="stat-icon pending">📋</div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.pending }}</div>
            <div class="stat-label">待审核</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon confirmed">✓</div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.confirmed }}</div>
            <div class="stat-label">已确认</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon completed">⭐</div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.completed }}</div>
            <div class="stat-label">已完成</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon total">👥</div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalPeople }}</div>
            <div class="stat-label">总预约人数</div>
          </div>
        </div>
      </div>

      <!-- 筛选器 -->
      <div class="filter-bar">
        <a-radio-group v-model:value="statusFilter" button-style="solid">
          <a-radio-button value="all">全部 ({{ orderStats.all }})</a-radio-button>
          <a-radio-button value="PENDING">待审核 ({{ orderStats.pending }})</a-radio-button>
          <a-radio-button value="CONFIRMED">已确认 ({{ orderStats.confirmed }})</a-radio-button>
          <a-radio-button value="COMPLETED">已完成 ({{ orderStats.completed }})</a-radio-button>
        </a-radio-group>
      </div>

      <!-- 预约列表 -->
      <div class="booking-list">
        <a-spin :spinning="loading">
          <div v-if="bookings.length === 0 && !loading" class="empty-state">
            <div class="empty-icon">📭</div>
            <p>暂无预约订单</p>
          </div>

          <div v-for="order in bookings" :key="order.id" class="booking-card glass-card">
            <div class="booking-header">
              <div class="order-info">
                <span class="order-no">订单号: {{ order.orderNo }}</span>
                <span class="order-date">{{ formatDate(order.createdAt) }}</span>
              </div>
              <a-tag :color="getStatusColor(order.status)">
                {{ getStatusText(order.status) }}
              </a-tag>
            </div>

            <div class="booking-body">
              <div class="info-row">
                <span class="label">体验项目:</span>
                <span class="value">{{ order.experienceName || '未知项目' }}</span>
              </div>
              <div class="info-row">
                <span class="label">预约时间:</span>
                <span class="value">{{ order.bookingDate }} {{ order.bookingTime }}</span>
              </div>
              <div class="info-row">
                <span class="label">预约人数:</span>
                <span class="value highlight">{{ order.people }} 人</span>
              </div>
              <div class="info-row">
                <span class="label">联系人:</span>
                <span class="value">{{ order.contactName }} {{ order.contactPhone }}</span>
              </div>
              <div class="info-row" v-if="order.remark">
                <span class="label">备注:</span>
                <span class="value">{{ order.remark }}</span>
              </div>
            </div>

            <div class="booking-actions">
              <template v-if="order.status === 'PENDING'">
                <a-button type="primary" size="small" @click="handleApprove(order)">
                  ✓ 通过
                </a-button>
                <a-button danger size="small" @click="handleReject(order)">
                  ✗ 拒绝
                </a-button>
              </template>
              <template v-else-if="order.status === 'CONFIRMED'">
                <a-button type="success" size="small" @click="handleComplete(order)">
                  ⭐ 核销完成
                </a-button>
              </template>
              <template v-else-if="order.status === 'COMPLETED'">
                <span class="completed-badge">✓ 已核销</span>
              </template>
            </div>
          </div>
        </a-spin>

        <!-- 分页 -->
        <Pagination v-model="currentPage" :total="total" :page-size="pageSize" @page-change="loadBookings" />
      </div>
    </div>

    <!-- 拒绝原因弹窗 -->
    <a-modal
      v-model:open="rejectModalVisible"
      title="拒绝预约"
      @ok="confirmReject"
      okText="确定"
      cancelText="取消"
    >
      <a-textarea
        v-model:value="rejectReason"
        placeholder="请输入拒绝原因（可选）"
        :rows="4"
      />
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { useUserStore } from '../stores/user'
import { getInheritorBookings, getInheritorBookingStats, inheritorApproveBooking, inheritorRejectBooking, completeBooking } from '../api/booking'
import Pagination from '../components/Pagination.vue'

const userStore = useUserStore()
const loading = ref(false)
const bookings = ref([])
const statusFilter = ref('all')
const currentPage = ref(1)
const pageSize = 5
const total = ref(0)

const rejectModalVisible = ref(false)
const rejectReason = ref('')
const currentRejectOrder = ref(null)

// 统计数据（从全部数据中获取，不受分页影响）
const stats = ref({
  pending: 0,
  confirmed: 0,
  completed: 0,
  totalPeople: 0
})

const orderStats = computed(() => {
  return {
    all: stats.value.pending + stats.value.confirmed + stats.value.completed,
    pending: stats.value.pending,
    confirmed: stats.value.confirmed,
    completed: stats.value.completed
  }
})

async function loadStats() {
  try {
    const res = await getInheritorBookingStats()
    if (res.data) {
      stats.value = res.data
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

async function loadBookings() {
  loading.value = true
  try {
    const res = await getInheritorBookings({
      page: currentPage.value,
      size: pageSize,
      status: statusFilter.value === 'all' ? undefined : statusFilter.value
    })
    
    if (res.data && res.data.records) {
      bookings.value = res.data.records
      total.value = res.data.total || 0
    }
  } catch (error) {
    console.error('加载预约列表失败:', error)
    message.error('加载预约列表失败')
  } finally {
    loading.value = false
  }
}

function handleApprove(order) {
  message.confirm({
    title: '确认通过',
    content: `确定要通过「${order.contactName}」的预约吗？`,
    onOk: async () => {
      try {
        await inheritorApproveBooking(order.id)
        message.success('已通过预约')
        loadStats()
        loadBookings()
      } catch (error) {
        message.error('操作失败')
      }
    }
  })
}

function handleReject(order) {
  currentRejectOrder.value = order
  rejectReason.value = ''
  rejectModalVisible.value = true
}

async function confirmReject() {
  try {
    await inheritorRejectBooking(currentRejectOrder.value.id, {
      reason: rejectReason.value
    })
    message.success('已拒绝预约')
    rejectModalVisible.value = false
    loadStats()
    loadBookings()
  } catch (error) {
    message.error('操作失败')
  }
}

function handleComplete(order) {
  message.confirm({
    title: '确认核销',
    content: `确定要核销「${order.contactName}」的预约吗？核销后将无法撤销。`,
    onOk: async () => {
      try {
        await completeBooking(order.id)
        message.success('已核销预约')
        loadStats()
        loadBookings()
      } catch (error) {
        message.error('操作失败')
      }
    }
  })
}

function formatDate(date) {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

function getStatusColor(status) {
  const colors = {
    PENDING: 'orange',
    CONFIRMED: 'blue',
    COMPLETED: 'green',
    CANCELLED: 'default'
  }
  return colors[status] || 'default'
}

function getStatusText(status) {
  const texts = {
    PENDING: '待审核',
    CONFIRMED: '已确认',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }
  return texts[status] || status
}

watch(statusFilter, () => {
  currentPage.value = 1
  loadBookings()
})

onMounted(async () => {
  await userStore.refreshUser()
  loadStats()
  loadBookings()
})
</script>

<style scoped>
.inheritor-booking-page {
  padding-bottom: 40px;
}

.page-header {
  margin-bottom: 32px;
}

.page-title {
  font-family: var(--font-serif);
  font-size: 28px;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.page-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

/* 统计卡片 */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: linear-gradient(135deg, rgba(255,255,255,0.9) 0%, rgba(255,255,255,0.7) 100%);
  border-radius: var(--radius-lg);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  transition: transform 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-icon.pending { background: rgba(255,152,0,0.1); }
.stat-icon.confirmed { background: rgba(33,150,243,0.1); }
.stat-icon.completed { background: rgba(76,175,80,0.1); }
.stat-icon.total { background: rgba(156,39,176,0.1); }

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
}

/* 筛选器 */
.filter-bar {
  margin-bottom: 24px;
}

/* 预约列表 */
.booking-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-muted);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.booking-card {
  padding: 20px;
  transition: all 0.3s;
}

.booking-card:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
}

.booking-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--bg-glass-border);
}

.order-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.order-no {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.order-date {
  font-size: 12px;
  color: var(--text-muted);
}

.booking-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  align-items: center;
  font-size: 14px;
}

.label {
  min-width: 90px;
  color: var(--text-secondary);
  font-weight: 500;
}

.value {
  flex: 1;
  color: var(--text-primary);
}

.value.highlight {
  color: var(--color-primary);
  font-weight: 600;
}

.booking-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--bg-glass-border);
}

.completed-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: rgba(76,175,80,0.1);
  color: #4caf50;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 768px) {
  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .booking-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .booking-actions {
    justify-content: stretch;
  }
  
  .booking-actions button {
    flex: 1;
  }
}
</style>
