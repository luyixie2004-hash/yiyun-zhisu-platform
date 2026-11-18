<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { getAdminHeritageList, approveHeritage, rejectHeritage, updateHeritage } from '../../api/heritage'
import { CATEGORY_MAP, CATEGORY_OPTIONS, LEVEL_MAP, LEVEL_OPTIONS, APPROVAL_STATUS_MAP } from '../../utils/constants'
import Pagination from '../../components/Pagination.vue'

const heritageList = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const detailItem = ref(null)
const rejectReason = ref('')
const rejectLoading = ref(false)
const approveLoading = ref(false)
const showRejectInput = ref(false)
const searchKeyword = ref('')
const filterCategory = ref('')
const filterRegion = ref('')
const filterLevel = ref('')
const filterStatus = ref('')

const categoryMap = CATEGORY_MAP
const levelMap = LEVEL_MAP

const categoryOptions = [
  { value: '', label: '全部分类' },
  ...CATEGORY_OPTIONS,
]

const levelOptions = [
  { value: '', label: '全部级别' },
  ...LEVEL_OPTIONS,
]

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

const filteredList = computed(() => {
  return heritageList.value.filter(item => {
    if (searchKeyword.value && !item.name.includes(searchKeyword.value)) return false
    if (filterCategory.value && item.category !== filterCategory.value) return false
    if (filterRegion.value && item.region && !item.region.includes(filterRegion.value)) return false
    if (filterLevel.value && item.level !== filterLevel.value) return false
    if (filterStatus.value && item.status !== filterStatus.value) return false
    return true
  })
})

const currentPage = ref(1)
const pageSize = 10

const paginatedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredList.value.slice(start, start + pageSize)
})

watch([searchKeyword, filterCategory, filterLevel, filterStatus], () => {
  currentPage.value = 1
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '项目名称', dataIndex: 'name', key: 'name' },
  { title: '分类', dataIndex: 'category', key: 'category', width: 90 },
  { title: '地区', dataIndex: 'region', key: 'region', width: 100 },
  { title: '级别', dataIndex: 'level', key: 'level', width: 80 },
  { title: '传承人', dataIndex: 'inheritorName', key: 'inheritorName', width: 100 },
  { title: '审核状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 180 },
]

async function loadHeritages() {
  loading.value = true
  try {
    const res = await getAdminHeritageList({ size: 200 })
    const records = res.data?.records || res.data || []
    heritageList.value = records.map(item => ({
      id: item.id,
      name: item.name,
      category: item.category,
      region: item.region,
      level: item.level,
      inheritanceType: item.inheritanceType || 'INDIVIDUAL',
      inheritorName: item.inheritorName || item.inheritor || '',
      originDynasty: item.originDynasty || '',
      imageUrl: item.imageUrl || item.image_url || '',
      summary: item.summary || '',
      description: item.description || item.desc || '',
      views: item.views || 0,
      status: item.status || 'PENDING',
      auditNote: item.auditNote || '',
      createdAt: item.createdAt || '',
    }))
  } catch (error) {
    console.error('加载非遗数据失败', error)
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function handleShelve(record) {
  Modal.confirm({
    title: '确认下架',
    content: `确定要下架「${record.name}」吗？下架后用户将不可见。`,
    okText: '下架',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await updateHeritage(record.id, { status: 'INACTIVE' })
        record.status = 'INACTIVE'
        message.success('已下架')
      } catch (error) {
        message.error('操作失败')
      }
    },
  })
}

async function handleUnshelve(record) {
  try {
    await updateHeritage(record.id, { status: 'APPROVED' })
    record.status = 'APPROVED'
    message.success('已上架')
  } catch (error) {
    message.error('操作失败')
  }
}

function getStatusLabel(status) {
  return statusMap[status]?.label || status
}

function getStatusColor(status) {
  return statusMap[status]?.color || 'default'
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
    await approveHeritage(detailItem.value.id)
    detailItem.value.status = 'APPROVED'
    const listItem = heritageList.value.find(h => h.id === detailItem.value.id)
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
    await rejectHeritage(detailItem.value.id, { reason: rejectReason.value.trim() })
    detailItem.value.status = 'REJECTED'
    detailItem.value.auditNote = rejectReason.value.trim()
    const listItem = heritageList.value.find(h => h.id === detailItem.value.id)
    if (listItem) {
      listItem.status = 'REJECTED'
      listItem.auditNote = rejectReason.value.trim()
    }
    message.success('已驳回')
    detailVisible.value = false
  } catch (error) {
    message.error('操作失败')
  } finally {
    rejectLoading.value = false
  }
}

onMounted(() => {
  loadHeritages()
})
</script>

<template>
  <div class="admin-page page-container">
    <div class="container glass-card" style="padding:32px; min-height:600px">
      <div class="admin-header" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:24px">
        <div>
          <h1 class="section-title">非遗项目审核</h1>
          <p style="color:var(--text-secondary);margin-top:8px">审核传承人提交的非遗项目，管理项目上下架状态。</p>
        </div>
      </div>

      <div style="display:flex;gap:12px;margin-bottom:24px;flex-wrap:wrap;align-items:center">
        <a-input v-model:value="searchKeyword" placeholder="搜索项目名称..." style="width:220px" allow-clear />
        <a-select v-model:value="filterCategory" :options="[{value:'',label:'全部分类'},...categoryOptions]" style="width:130px" />
        <a-select v-model:value="filterLevel" :options="[{value:'',label:'全部级别'},...levelOptions]" style="width:120px" />
        <a-select v-model:value="filterStatus" :options="statusOptions" style="width:110px" />
        <button class="btn-ghost" style="margin-left:auto;padding:8px 16px" @click="filterCategory='';filterLevel='';filterStatus='';searchKeyword=''">重置筛选</button>
      </div>

      <a-table :dataSource="paginatedList" :columns="columns" rowKey="id" :loading="loading"
        :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'category'">
            {{ categoryMap[record.category] || record.category }}
          </template>
          <template v-else-if="column.key === 'level'">
            <a-tag :color="record.level==='国家级'?'red':(record.level==='省级'?'orange':'blue')">{{ levelMap[record.level] || record.level }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)">{{ getStatusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <div style="display:flex;gap:6px">
              <a-button size="small" @click="showDetail(record)">查看</a-button>
              <template v-if="record.status === 'APPROVED'">
                <a-button size="small" danger @click="handleShelve(record)">下架</a-button>
              </template>
              <template v-else-if="record.status === 'REJECTED' || record.status === 'INACTIVE'">
                <a-button size="small" type="primary" @click="handleUnshelve(record)">上架</a-button>
              </template>
            </div>
          </template>
        </template>
      </a-table>

      <Pagination v-model="currentPage" :total="filteredList.length" :page-size="pageSize" />
    </div>

    <!-- 项目详情弹窗 -->
    <a-modal v-model:visible="detailVisible" title="非遗项目详情" :footer="null" width="680px" v-if="detailItem">
      <div class="detail-modal">
        <div class="detail-hero" v-if="detailItem.imageUrl">
          <img :src="detailItem.imageUrl" class="detail-cover" />
        </div>
        <div class="detail-body">
          <h2 class="detail-name">{{ detailItem.name }}</h2>
          <div class="detail-tags">
            <a-tag color="purple">{{ categoryMap[detailItem.category] || detailItem.category }}</a-tag>
            <a-tag :color="detailItem.level==='国家级'?'red':(detailItem.level==='省级'?'orange':'blue')">{{ levelMap[detailItem.level] || detailItem.level }}</a-tag>
            <a-tag :color="getStatusColor(detailItem.status)">{{ getStatusLabel(detailItem.status) }}</a-tag>
          </div>
          <div class="detail-grid">
            <div class="detail-field" v-if="detailItem.region">
              <span class="detail-label">所在地区</span>
              <span class="detail-content">{{ detailItem.region }}</span>
            </div>
            <div class="detail-field" v-if="detailItem.inheritanceType === 'GROUP'">
              <span class="detail-label">传承主体</span>
              <span class="detail-content">全体民众</span>
            </div>
            <div class="detail-field" v-else-if="detailItem.inheritorName">
              <span class="detail-label">传承人</span>
              <span class="detail-content">{{ detailItem.inheritorName }}</span>
            </div>
            <div class="detail-field" v-if="detailItem.originDynasty">
              <span class="detail-label">起源朝代</span>
              <span class="detail-content">{{ detailItem.originDynasty }}</span>
            </div>
            <div class="detail-field">
              <span class="detail-label">浏览量</span>
              <span class="detail-content">{{ detailItem.views || 0 }} 次</span>
            </div>
          </div>
          <div class="detail-section" v-if="detailItem.summary">
            <h4>简介</h4>
            <p>{{ detailItem.summary }}</p>
          </div>
          <div class="detail-section" v-if="detailItem.description">
            <h4>详细描述</h4>
            <p class="detail-desc">{{ detailItem.description }}</p>
          </div>
          <div class="detail-section" v-if="detailItem.auditNote">
            <h4>审核备注</h4>
            <p style="color:#d32f2f">{{ detailItem.auditNote }}</p>
          </div>
          <div class="detail-section" v-if="detailItem.createdAt">
            <h4>提交时间</h4>
            <p>{{ detailItem.createdAt }}</p>
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
