<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import { getAdminExperiences, approveExperience, rejectExperience } from '../../api/experience'
import { APPROVAL_STATUS_MAP, EXPERIENCE_TYPE_MAP } from '../../utils/constants'
import Pagination from '../../components/Pagination.vue'

const experiences = ref([])
const loading = ref(false)
const filterStatus = ref('')
const detailVisible = ref(false)
const detailItem = ref(null)
const rejectReason = ref('')
const rejectLoading = ref(false)
const approveLoading = ref(false)
const showRejectInput = ref(false)

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: APPROVAL_STATUS_MAP.PENDING },
  { value: 'APPROVED', label: APPROVAL_STATUS_MAP.APPROVED },
  { value: 'REJECTED', label: APPROVAL_STATUS_MAP.REJECTED },
  { value: 'INACTIVE', label: APPROVAL_STATUS_MAP.INACTIVE },
]

const statusMap = {
  PENDING: { label: APPROVAL_STATUS_MAP.PENDING, color: 'orange' },
  APPROVED: { label: APPROVAL_STATUS_MAP.APPROVED, color: 'green' },
  REJECTED: { label: APPROVAL_STATUS_MAP.REJECTED, color: 'red' },
  INACTIVE: { label: APPROVAL_STATUS_MAP.INACTIVE, color: 'default' },
}

const typeMap = EXPERIENCE_TYPE_MAP

const filteredList = computed(() => {
  if (!filterStatus.value) return experiences.value
  return experiences.value.filter(e => e.status === filterStatus.value)
})

const currentPage = ref(1)
const pageSize = 10

const paginatedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredList.value.slice(start, start + pageSize)
})

watch(filterStatus, () => {
  currentPage.value = 1
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '体验名称', dataIndex: 'name', key: 'name' },
  { title: '类型', key: 'type', width: 90 },
  { title: '地点', dataIndex: 'location', key: 'location', width: 110 },
  { title: '价格', key: 'price', width: 80 },
  { title: '名额', key: 'quota', width: 80 },
  { title: '审核状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 160 },
]

async function loadExperiences() {
  loading.value = true
  try {
    const res = await getAdminExperiences({ size: 200 })
    const records = res.data?.records || res.data || []
    experiences.value = records.map(item => ({
      id: item.id,
      name: item.name,
      type: item.type || '线下工坊',
      location: item.location || '',
      price: item.price || 0,
      quota: item.quota || 0,
      bookedNum: item.bookedNum || 0,
      imageUrl: item.imageUrl || '',
      description: item.description || '',
      schedule: item.schedule || '',
      startTime: item.startTime || '',
      endTime: item.endTime || '',
      rating: item.rating || 0,
      inheritorName: item.inheritorName || '',
      heritageName: item.heritageName || '',
      status: item.status || 'PENDING',
    }))
  } catch (error) {
    console.error('加载体验数据失败', error)
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

function showDetail(record) {
  detailItem.value = record
  rejectReason.value = ''
  showRejectInput.value = false
  detailVisible.value = true
}

async function doApprove() {
  if (!detailItem.value) return
  approveLoading.value = true
  try {
    await approveExperience(detailItem.value.id)
    detailItem.value.status = 'APPROVED'
    const listItem = experiences.value.find(e => e.id === detailItem.value.id)
    if (listItem) listItem.status = 'APPROVED'
    message.success('已通过审核')
    detailVisible.value = false
  } catch (error) {
    message.error('操作失败')
  } finally {
    approveLoading.value = false
  }
}

async function doReject() {
  if (!detailItem.value) return
  if (!rejectReason.value.trim()) {
    message.warning('请输入驳回原因')
    return
  }
  rejectLoading.value = true
  try {
    await rejectExperience(detailItem.value.id, { reason: rejectReason.value.trim() })
    detailItem.value.status = 'REJECTED'
    const listItem = experiences.value.find(e => e.id === detailItem.value.id)
    if (listItem) listItem.status = 'REJECTED'
    message.success('已驳回')
    detailVisible.value = false
  } catch (error) {
    message.error('操作失败')
  } finally {
    rejectLoading.value = false
  }
}

function getStatusLabel(status) {
  return statusMap[status]?.label || status
}

function getStatusColor(status) {
  return statusMap[status]?.color || 'default'
}

function getTypeLabel(type) {
  return typeMap[type] || type || '非遗体验'
}

onMounted(() => {
  loadExperiences()
})
</script>

<template>
  <div class="admin-page page-container">
    <div class="container glass-card" style="padding:32px; min-height:600px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:24px">
        <div>
          <h1 class="section-title">体验项目审核</h1>
          <p style="color:var(--text-secondary);margin-top:8px">审核传承人发布的体验项目，管理项目状态。</p>
        </div>
      </div>

      <div style="display:flex;gap:12px;margin-bottom:24px;align-items:center">
        <span style="font-size:14px;color:var(--text-secondary)">状态筛选：</span>
        <a-radio-group v-model:value="filterStatus" button-style="solid">
          <a-radio-button v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-radio-button>
        </a-radio-group>
      </div>

      <a-table :dataSource="paginatedList" :columns="columns" rowKey="id" :loading="loading"
        :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag color="purple">{{ getTypeLabel(record.type) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'price'">
            ¥{{ record.price }}
          </template>
          <template v-else-if="column.key === 'quota'">
            {{ record.bookedNum || 0 }}/{{ record.quota || 0 }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)">{{ getStatusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <div style="display:flex;gap:6px">
              <a-button size="small" @click="showDetail(record)">查看</a-button>
            </div>
          </template>
        </template>
      </a-table>

      <Pagination v-model="currentPage" :total="filteredList.length" :page-size="pageSize" />
    </div>

    <!-- 体验详情弹窗 -->
    <a-modal v-model:visible="detailVisible" title="体验项目详情" :footer="null" width="680px" v-if="detailItem">
      <div class="detail-modal">
        <div class="detail-hero" v-if="detailItem.imageUrl">
          <img :src="detailItem.imageUrl" class="detail-cover" />
        </div>
        <div class="detail-body">
          <h2 class="detail-name">{{ detailItem.name }}</h2>
          <div class="detail-tags">
            <a-tag color="purple">{{ getTypeLabel(detailItem.type) }}</a-tag>
            <a-tag :color="getStatusColor(detailItem.status)">{{ getStatusLabel(detailItem.status) }}</a-tag>
          </div>
          <div class="detail-grid">
            <div class="detail-field" v-if="detailItem.location">
              <span class="detail-label">地点</span>
              <span class="detail-content">{{ detailItem.location }}</span>
            </div>
            <div class="detail-field">
              <span class="detail-label">价格</span>
              <span class="detail-content" style="color:#e65100">¥{{ detailItem.price }}</span>
            </div>
            <div class="detail-field">
              <span class="detail-label">名额/已预约</span>
              <span class="detail-content">{{ detailItem.bookedNum || 0 }} / {{ detailItem.quota || 0 }} 人</span>
            </div>
            <div class="detail-field" v-if="detailItem.rating">
              <span class="detail-label">评分</span>
              <span class="detail-content">{{ detailItem.rating }} 分</span>
            </div>
            <div class="detail-field" v-if="detailItem.inheritorName">
              <span class="detail-label">创建传承人</span>
              <span class="detail-content">{{ detailItem.inheritorName }}</span>
            </div>
            <div class="detail-field" v-if="detailItem.heritageName">
              <span class="detail-label">关联非遗项目</span>
              <span class="detail-content">{{ detailItem.heritageName }}</span>
            </div>
          </div>
          <div class="detail-section" v-if="detailItem.schedule">
            <h4>时间安排</h4>
            <p>{{ detailItem.schedule }}</p>
          </div>
          <div class="detail-section" v-if="detailItem.startTime">
            <h4>活动周期</h4>
            <p>{{ detailItem.startTime }} ~ {{ detailItem.endTime }}</p>
          </div>
          <div class="detail-section" v-if="detailItem.description">
            <h4>详细描述</h4>
            <p class="detail-desc">{{ detailItem.description }}</p>
          </div>

          <!-- PENDING 状态：审核操作 -->
          <div v-if="detailItem.status === 'PENDING'" class="audit-actions">
            <div class="audit-divider"></div>
            <h4 style="margin:0 0 12px">审核操作</h4>
            <div v-if="showRejectInput" class="audit-reject-section">
              <a-textarea v-model:value="rejectReason" placeholder="请输入驳回原因（必填）" :rows="3" style="margin-bottom:12px" />
              <div style="display:flex;gap:10px">
                <a-button @click="showRejectInput = false">取消</a-button>
                <a-button danger :loading="rejectLoading" :disabled="!rejectReason.trim()" @click="doReject">确认驳回</a-button>
              </div>
            </div>
            <div v-else style="display:flex;gap:10px">
              <a-button type="primary" :loading="approveLoading" @click="doApprove">审核通过</a-button>
              <a-button danger @click="showRejectInput = true">驳回</a-button>
            </div>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.admin-page { padding-top: calc(var(--header-height) + 32px); padding-bottom: 32px; background: var(--bg-base); min-height: 100vh; }

.detail-modal { }
.detail-hero { border-radius: 8px 8px 0 0; overflow: hidden; margin: 0 -24px; }
.detail-cover { width: 100%; max-height: 320px; object-fit: cover; display: block; }
.detail-body { padding-top: 20px; }
.detail-name { font-size: 20px; font-weight: 700; margin: 0 0 12px; }
.detail-tags { display: flex; gap: 8px; margin-bottom: 20px; flex-wrap: wrap; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px; }
.detail-field { background: #f8f9fa; border-radius: 6px; padding: 12px; }
.detail-label { display: block; font-size: 12px; color: var(--text-muted); margin-bottom: 4px; }
.detail-content { font-size: 14px; font-weight: 500; color: var(--text-primary); }
.detail-section { margin-bottom: 16px; }
.detail-section h4 { font-size: 13px; font-weight: 600; color: var(--text-secondary); margin: 0 0 6px; }
.detail-section p { font-size: 14px; color: var(--text-primary); line-height: 1.7; margin: 0; }
.detail-desc { white-space: pre-wrap; }

.audit-actions { margin-top: 4px; }
.audit-divider { border-top: 1px solid var(--border-color); margin-bottom: 16px; }
.audit-reject-section { }
</style>
