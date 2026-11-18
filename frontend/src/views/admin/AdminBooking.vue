<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getAdminBookings, approveBooking, rejectBooking } from '../../api/booking'
import { ORDER_STATUS_MAP } from '../../utils/constants'
import Pagination from '../../components/Pagination.vue'

const allOrders = ref([])
const statusFilter = ref('')
const loading = ref(false)
const currentPage = ref(1)
const pageSize = 10

const paginatedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return allOrders.value.slice(start, start + pageSize)
})

const statusOptions = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: ORDER_STATUS_MAP.PENDING },
  { value: 'CONFIRMED', label: ORDER_STATUS_MAP.CONFIRMED },
  { value: 'REJECTED', label: ORDER_STATUS_MAP.REJECTED },
  { value: 'COMPLETED', label: ORDER_STATUS_MAP.COMPLETED },
  { value: 'CANCELLED', label: ORDER_STATUS_MAP.CANCELLED },
]

const statusMap = {
  PENDING: { label: ORDER_STATUS_MAP.PENDING, color: 'blue' },
  CONFIRMED: { label: ORDER_STATUS_MAP.CONFIRMED, color: 'green' },
  REJECTED: { label: ORDER_STATUS_MAP.REJECTED, color: 'red' },
  COMPLETED: { label: ORDER_STATUS_MAP.COMPLETED, color: 'default' },
  CANCELLED: { label: ORDER_STATUS_MAP.CANCELLED, color: 'red' },
}

async function loadOrders() {
  loading.value = true
  currentPage.value = 1
  try {
    const res = await getAdminBookings(statusFilter.value ? { status: statusFilter.value } : {})
    allOrders.value = (res.data?.records || res.data || []).map(o => ({
      id: o.id,
      orderNo: o.orderNo || o.id,
      username: o.userName || o.username || o.contactName || '',
      workshop: o.experienceName || o.workshop || o.name || '',
      date: o.bookingDate || o.date || '',
      time: o.bookingTime || o.time || '',
      people: o.people || o.participants || 1,
      usedPeople: o.usedPeople || o.usedParticipants || 0,
      contactName: o.contactName || '',
      contactPhone: o.contactPhone || '',
      status: o.status || 'PENDING',
      price: o.total || o.price || 0,
    }))
  } catch (error) {
    console.error('加载订单失败:', error)
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

function getStatusLabel(status) {
  return statusMap[status]?.label || status
}

function getStatusColor(status) {
  return statusMap[status]?.color || 'default'
}

const orderStats = computed(() => ({
  all: allOrders.value.length,
  pending: allOrders.value.filter(o => o.status === 'PENDING').length,
  confirmed: allOrders.value.filter(o => o.status === 'CONFIRMED').length,
  completed: allOrders.value.filter(o => o.status === 'COMPLETED').length,
  rejected: allOrders.value.filter(o => o.status === 'REJECTED').length,
  cancelled: allOrders.value.filter(o => o.status === 'CANCELLED').length,
}))

async function handleApprove(record) {
  try {
    await approveBooking(record.id)
    record.status = 'CONFIRMED'
    message.success('已通过该预约')
  } catch (error) {
    message.error('操作失败')
  }
}

async function handleReject(record) {
  try {
    await rejectBooking(record.id, { reason: '管理员驳回' })
    record.status = 'REJECTED'
    message.success('已驳回该预约')
  } catch (error) {
    message.error('操作失败')
  }
}

const columns = [
  { title: '订单号', dataIndex: 'orderNo', key: 'orderNo', width: 100 },
  { title: '预约用户', dataIndex: 'username', key: 'username', width: 90 },
  { title: '体验项目', dataIndex: 'workshop', key: 'workshop' },
  { title: '体验时间', key: 'datetime', width: 160 },
  { title: '人数', dataIndex: 'people', key: 'people', width: 60 },
  { title: '金额', key: 'price', width: 70 },
  { title: '状态', key: 'status', width: 80 },
  { title: '操作', key: 'action', width: 130 },
]

onMounted(() => {
  loadOrders()
})
</script>

<template>
  <div class="admin-page page-container">
    <div class="container glass-card" style="padding:32px; min-height:600px">
      <h1 class="section-title">订单与内容管理</h1>
      <p style="color:var(--text-secondary);margin-bottom:24px">查看全平台预约订单，处理异常订单。</p>

      <div style="display:flex;gap:12px;margin-bottom:24px;align-items:center;flex-wrap:wrap">
        <span style="font-size:14px;color:var(--text-secondary)">状态筛选：</span>
        <a-radio-group v-model:value="statusFilter" button-style="solid" @change="loadOrders">
          <a-radio-button value="">全部 ({{ orderStats.all }})</a-radio-button>
          <a-radio-button value="PENDING">待审核 ({{ orderStats.pending }})</a-radio-button>
          <a-radio-button value="CONFIRMED">已通过 ({{ orderStats.confirmed }})</a-radio-button>
          <a-radio-button value="COMPLETED">已完成 ({{ orderStats.completed }})</a-radio-button>
          <a-radio-button value="REJECTED">已驳回 ({{ orderStats.rejected }})</a-radio-button>
          <a-radio-button value="CANCELLED">已取消 ({{ orderStats.cancelled }})</a-radio-button>
        </a-radio-group>
      </div>

      <a-table :dataSource="paginatedOrders" :columns="columns" rowKey="id" :loading="loading"
        :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'datetime'">
            {{ record.date }} {{ record.time }}
          </template>
          <template v-else-if="column.key === 'price'">
            ¥{{ record.price }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)">{{ getStatusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <div v-if="record.status === 'PENDING'" style="display:flex;gap:6px">
              <a-button size="small" type="primary" @click="handleApprove(record)">通过</a-button>
              <a-button size="small" danger @click="handleReject(record)">驳回</a-button>
            </div>
            <span v-else style="color:var(--text-muted);font-size:12px">-</span>
          </template>
        </template>
      </a-table>

      <Pagination v-model="currentPage" :total="allOrders.length" :page-size="pageSize" />
    </div>
  </div>
</template>

<style scoped>
.admin-page { padding-top: calc(var(--header-height) + 32px); padding-bottom: 32px; background: var(--bg-base); min-height: 100vh; }
</style>
