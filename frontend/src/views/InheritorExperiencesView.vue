<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUserStore } from '../stores/user'
import { getMyExperiences, createExperience, updateExperience, deleteExperience, deactivateExperience } from '../api/experience'
import { getMyHeritageList } from '../api/heritage'
import { uploadFile } from '../api/file'
import { PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined, StopOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import { Empty } from 'ant-design-vue'
import { EXPERIENCE_TYPE_OPTIONS, APPROVAL_STATUS_MAP } from '../utils/constants'
import Pagination from '../components/Pagination.vue'
import dayjs from 'dayjs'
import { regionData } from '../data/regionData'

const userStore = useUserStore()
const loading = ref(false)
const modalVisible = ref(false)
const editingId = ref(null)
const allExperiences = ref([])
const filterStatus = ref('ALL')
const viewModalVisible = ref(false)
const currentRecord = ref(null)
const currentPage = ref(1)
const pageSize = 5

const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE

const formData = ref({
  name: '',
  type: '',
  heritageId: null,
  location: '',
  price: 0,
  quota: 10,
  description: '',
  scheduleDate: null,
  scheduleTime: null,
})

const imageList = ref([])
const uploading = ref(false)
const maxImages = 9

const heritageList = ref([])

const selectedProvince = ref('')
const selectedCity = ref('')
const selectedDistrict = ref('')

const provinceList = [
  { value: '全国', label: '全国（跨省项目）' },
  ...regionData.map(p => ({ value: p.value, label: p.label }))
]

const cityList = computed(() => {
  if (!selectedProvince.value || selectedProvince.value === '全国') return []
  const province = regionData.find(p => p.value === selectedProvince.value)
  if (!province) return []
  return province.children.map(c => ({ value: c.value, label: c.label }))
})

const districtList = computed(() => {
  if (!selectedProvince.value || selectedProvince.value === '全国' || !selectedCity.value) return []
  const province = regionData.find(p => p.value === selectedProvince.value)
  if (!province) return []
  const city = province.children.find(c => c.value === selectedCity.value)
  if (!city) return []
  return city.children.map(d => ({ value: d.value, label: d.label }))
})

const fullRegion = computed(() => {
  const parts = []
  if (selectedProvince.value) parts.push(selectedProvince.value)
  if (selectedCity.value) parts.push(selectedCity.value)
  if (selectedDistrict.value) parts.push(selectedDistrict.value)
  return parts.join('')
})

watch(selectedProvince, () => {
  selectedCity.value = ''
  selectedDistrict.value = ''
})

watch(selectedCity, () => {
  selectedDistrict.value = ''
})

const typeOptions = EXPERIENCE_TYPE_OPTIONS

const filterOptions = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: APPROVAL_STATUS_MAP.PENDING },
  { value: 'APPROVED', label: APPROVAL_STATUS_MAP.APPROVED },
  { value: 'REJECTED', label: APPROVAL_STATUS_MAP.REJECTED },
  { value: 'INACTIVE', label: APPROVAL_STATUS_MAP.INACTIVE },
]

const totalCount = computed(() => allExperiences.value.length)
const pendingCount = computed(() => allExperiences.value.filter(e => e.status === 'PENDING').length)
const approvedCount = computed(() => allExperiences.value.filter(e => e.status === 'APPROVED').length)
const rejectedCount = computed(() => allExperiences.value.filter(e => e.status === 'REJECTED').length)
const inactiveCount = computed(() => allExperiences.value.filter(e => e.status === 'INACTIVE').length)

const filteredExperiences = computed(() => {
  if (filterStatus.value === 'ALL') return allExperiences.value
  return allExperiences.value.filter(e => e.status === filterStatus.value)
})

const pagedExperiences = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredExperiences.value.slice(start, start + pageSize)
})

function setFilter(status) {
  filterStatus.value = status
  currentPage.value = 1
}

function getStatusColor(status) {
  const map = { PENDING: 'orange', APPROVED: 'green', REJECTED: 'red', INACTIVE: 'default' }
  return map[status] || 'default'
}

function getStatusText(status) {
  return APPROVAL_STATUS_MAP[status] || status
}

function getTypeLabel(type) {
  return typeOptions.find(t => t.value === type)?.label || type
}

function getHeritageName(heritageId) {
  const h = heritageList.value.find(item => item.id === heritageId)
  return h ? h.name : ''
}

async function loadHeritageList() {
  try {
    const res = await getMyHeritageList({ size: 100 })
    heritageList.value = res.data?.records || res.data || []
  } catch (error) {
    console.error('加载非遗列表失败', error)
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getMyExperiences({ page: 1, size: 1000 })
    if (res.data?.records) {
      allExperiences.value = res.data.records
    } else if (Array.isArray(res.data)) {
      allExperiences.value = res.data
    } else {
      allExperiences.value = []
    }
  } catch (error) {
    message.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  formData.value = {
    name: '',
    type: '',
    heritageId: null,
    location: '',
    price: 0,
    quota: 10,
    description: '',
    scheduleDate: null,
    scheduleTime: null,
  }
  imageList.value = []
  selectedProvince.value = ''
  selectedCity.value = ''
  selectedDistrict.value = ''
}

function handleAdd() {
  editingId.value = null
  resetForm()
  modalVisible.value = true
}

function parseRegion(region) {
  selectedProvince.value = ''
  selectedCity.value = ''
  selectedDistrict.value = ''
  if (!region) return
  if (region === '全国') {
    selectedProvince.value = '全国'
    return
  }
  for (const province of regionData) {
    if (region.startsWith(province.value)) {
      selectedProvince.value = province.value
      const remaining = region.substring(province.value.length)
      for (const city of province.children) {
        if (remaining.startsWith(city.value)) {
          selectedCity.value = city.value
          const districtPart = remaining.substring(city.value.length)
          for (const district of city.children) {
            if (districtPart === district.value) {
              selectedDistrict.value = district.value
              return
            }
          }
          return
        }
      }
      return
    }
  }
}

function handleEdit(record) {
  editingId.value = record.id
  parseRegion(record.location)
  formData.value = {
    name: record.name,
    type: record.type,
    heritageId: record.heritageId,
    location: record.location,
    price: record.price || 0,
    quota: record.quota || 10,
    description: record.description || '',
    scheduleDate: record.schedule ? dayjs(record.schedule) : null,
    scheduleTime: null,
  }
  if (record.imageUrl) {
    imageList.value = record.imageUrl.split(',').filter(url => url.trim())
  } else {
    imageList.value = []
  }
  modalVisible.value = true
}

function handleDelete(record) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除「${record.name}」吗？此操作不可撤销。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteExperience(record.id)
        message.success('删除成功')
        loadData()
      } catch (error) {
        message.error('删除失败')
      }
    }
  })
}

function handleDeactivate(record) {
  Modal.confirm({
    title: '确认下架',
    content: `下架后该体验将不再对外展示。确定要下架「${record.name}」吗？`,
    okText: '确认下架',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deactivateExperience(record.id)
        message.success('已下架')
        loadData()
      } catch (error) {
        message.error('操作失败')
      }
    }
  })
}

function handleReactivate(record) {
  Modal.confirm({
    title: '确认上架',
    content: `确定要重新上架「${record.name}」吗？将提交管理员审核。`,
    okText: '确认上架',
    cancelText: '取消',
    onOk: async () => {
      try {
        await updateExperience(record.id, { ...record, status: 'PENDING' })
        message.success('已提交审核')
        loadData()
      } catch (error) {
        message.error('操作失败')
      }
    }
  })
}

function handleView(record) {
  currentRecord.value = record
  viewModalVisible.value = true
}

function formatDate(date) {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

async function handleSubmit() {
  if (!formData.value.name.trim()) {
    message.error('请输入体验名称')
    return
  }
  if (!formData.value.type) {
    message.error('请选择体验类型')
    return
  }
  if (!selectedProvince.value || (selectedProvince.value !== '全国' && !selectedCity.value)) {
    message.error('请选择省份和城市')
    return
  }
  if (formData.value.price < 0) {
    message.error('请输入有效价格')
    return
  }
  if (!formData.value.description.trim()) {
    message.error('请输入体验描述')
    return
  }

  const scheduleParts = []
  if (formData.value.scheduleDate) {
    scheduleParts.push(dayjs(formData.value.scheduleDate).format('YYYY-MM-DD'))
  }
  if (formData.value.scheduleTime) {
    const t = formData.value.scheduleTime
    if (typeof t === 'string') {
      scheduleParts.push(t)
    } else {
      scheduleParts.push(dayjs(t).format('HH:mm'))
    }
  }

  const submitData = {
    name: formData.value.name.trim(),
    type: formData.value.type,
    heritageId: formData.value.heritageId || null,
    location: fullRegion.value,
    price: formData.value.price,
    quota: formData.value.quota,
    description: formData.value.description.trim(),
    imageUrl: imageList.value.join(','),
    schedule: scheduleParts.join(' '),
  }

  try {
    if (editingId.value) {
      await updateExperience(editingId.value, submitData)
      message.success('更新成功，已提交审核')
    } else {
      await createExperience(submitData)
      message.success('创建成功，已提交审核')
    }
    modalVisible.value = false
    loadData()
  } catch (error) {
    message.error(editingId.value ? '更新失败' : '创建失败')
  }
}

async function handleImageUpload(e) {
  const files = e.target.files
  if (!files || files.length === 0) return

  if (imageList.value.length >= maxImages) {
    message.warning(`最多上传${maxImages}张图片`)
    e.target.value = ''
    return
  }

  for (const file of files) {
    if (imageList.value.length >= maxImages) {
      message.warning(`最多上传${maxImages}张图片`)
      break
    }
    if (!file.type.startsWith('image/')) {
      message.warning(`${file.name} 不是图片文件，已跳过`)
      continue
    }
    if (file.size > 5 * 1024 * 1024) {
      message.warning(`${file.name} 超过5MB限制，已跳过`)
      continue
    }
    uploading.value = true
    try {
      const res = await uploadFile(file)
      imageList.value.push(res.data.url)
      message.success(`${file.name} 上传成功`)
    } catch (error) {
      message.error(`${file.name} 上传失败`)
    } finally {
      uploading.value = false
    }
  }
  e.target.value = ''
}

function removeImage(index) {
  imageList.value.splice(index, 1)
}

onMounted(async () => {
  await userStore.refreshUser()
  loadHeritageList()
  loadData()
})
</script>

<template>
  <div class="inheritor-experiences page-container">
    <div class="container">
      <div class="page-header">
        <div class="page-title">
          <h1>体验管理</h1>
          <p class="page-subtitle">管理您的非遗体验项目，包括工坊、课程、文化之旅和主题活动</p>
        </div>
        <a-button type="primary" size="large" @click="handleAdd" class="add-button">
          <PlusOutlined />
          新增体验项目
        </a-button>
      </div>

      <div class="stats-row">
        <div
          v-for="opt in filterOptions"
          :key="opt.value"
          class="stat-card"
          :class="{ active: filterStatus === opt.value }"
          @click="setFilter(opt.value)"
        >
          <div class="stat-icon">
            <template v-if="opt.value === 'ALL'">📋</template>
            <template v-else-if="opt.value === 'PENDING'">⏳</template>
            <template v-else-if="opt.value === 'APPROVED'">✅</template>
            <template v-else-if="opt.value === 'REJECTED'">❌</template>
            <template v-else-if="opt.value === 'INACTIVE'">📦</template>
          </div>
          <div class="stat-info">
            <div class="stat-label">{{ opt.label }}</div>
            <div class="stat-value">
              {{
                opt.value === 'ALL' ? totalCount :
                opt.value === 'PENDING' ? pendingCount :
                opt.value === 'APPROVED' ? approvedCount :
                opt.value === 'REJECTED' ? rejectedCount :
                inactiveCount
              }}
            </div>
          </div>
        </div>
      </div>

      <a-table
        :columns="columns"
        :data-source="pagedExperiences"
        :loading="loading"
        row-key="id"
        :pagination="false"
        class="experience-table"
      >
        <template #emptyText>
          <div style="padding: 60px 0; text-align: center;">
            <a-empty :image="simpleImage">
              <template #description>
                <span style="color: var(--text-secondary); font-size: 14px;">
                  暂无体验项目，点击右上角「新增体验项目」创建你的第一个体验吧！
                </span>
              </template>
            </a-empty>
          </div>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'imageUrl'">
            <div v-if="record.imageUrl" class="table-images">
              <img
                v-for="(img, index) in record.imageUrl.split(',').filter(url => url.trim()).slice(0, 3)"
                :key="index"
                :src="img"
                :alt="record.name"
                class="table-image"
              />
            </div>
            <a-empty v-else :image="simpleImage" description="无图片" :style="{ margin: '10px 0' }" />
          </template>
          <template v-else-if="column.key === 'type'">
            <a-tag>{{ getTypeLabel(record.type) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'price'">
            ¥{{ record.price }}
          </template>
          <template v-else-if="column.key === 'quota'">
            <span>{{ record.bookedNum || 0 }} / {{ record.quota }}</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)">
              {{ getStatusText(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'heritageId'">
            {{ getHeritageName(record.heritageId) || '-' }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <template v-if="record.status === 'PENDING'">
                <a-button type="link" size="small" @click="handleView(record)">
                  <EyeOutlined /> 查看
                </a-button>
              </template>
              <template v-else-if="record.status === 'APPROVED'">
                <a-button type="link" size="small" @click="handleEdit(record)">
                  <EditOutlined /> 编辑
                </a-button>
                <a-button type="link" size="small" danger @click="handleDeactivate(record)">
                  <StopOutlined /> 下架
                </a-button>
                <a-button type="link" size="small" @click="handleView(record)">
                  <EyeOutlined /> 查看
                </a-button>
              </template>
              <template v-else-if="record.status === 'INACTIVE'">
                <a-button type="link" size="small" @click="handleEdit(record)">
                  <EditOutlined /> 编辑
                </a-button>
                <a-button type="link" size="small" @click="handleReactivate(record)">
                  <PlayCircleOutlined /> 上架
                </a-button>
                <a-button type="link" size="small" @click="handleView(record)">
                  <EyeOutlined /> 查看
                </a-button>
              </template>
              <template v-else-if="record.status === 'REJECTED'">
                <a-button type="link" size="small" @click="handleEdit(record)">
                  <EditOutlined /> 修改重提
                </a-button>
                <a-button type="link" size="small" @click="handleView(record)">
                  <EyeOutlined /> 查看
                </a-button>
              </template>
              <a-button type="link" size="small" danger @click="handleDelete(record)">
                <DeleteOutlined /> 删除
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <Pagination v-if="filteredExperiences.length > 5" v-model="currentPage" :total="filteredExperiences.length" :page-size="5" />
    </div>

    <a-modal
      v-model:open="modalVisible"
      :title="editingId ? '编辑体验项目' : '新增体验项目'"
      @ok="handleSubmit"
      width="650px"
      :mask-closable="false"
    >
      <a-form :model="formData" layout="vertical">
        <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px;">
          <a-form-item label="体验名称" required>
            <a-input v-model:value="formData.name" placeholder="请输入体验名称" />
          </a-form-item>

          <a-form-item label="体验类型" required>
            <a-select v-model:value="formData.type" :options="typeOptions" placeholder="请选择类型" />
          </a-form-item>
        </div>

        <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px;">
          <a-form-item label="关联非遗项目">
            <a-select
              v-model:value="formData.heritageId"
              placeholder="选择关联的非遗项目（可选）"
              allow-clear
              :options="heritageList.map(h => ({ value: h.id, label: h.name }))"
            />
          </a-form-item>

          <a-form-item label="价格（元）" required>
            <a-input-number v-model:value="formData.price" :min="0" :precision="2" style="width: 100%" />
          </a-form-item>
        </div>

        <a-form-item label="地点" required>
          <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px;">
            <a-select v-model:value="selectedProvince" :options="provinceList" placeholder="选择省" />
            <a-select v-model:value="selectedCity" :options="cityList" placeholder="选择市" :disabled="!selectedProvince || selectedProvince === '全国'" />
            <a-select v-model:value="selectedDistrict" :options="districtList" placeholder="选择区（可选）" :disabled="!selectedCity || selectedProvince === '全国'" />
          </div>
        </a-form-item>

        <a-form-item label="名额上限" required>
          <a-input-number v-model:value="formData.quota" :min="1" style="width: 200px" />
        </a-form-item>

        <a-form-item label="时间安排">
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px;">
            <a-date-picker
              v-model:value="formData.scheduleDate"
              style="width: 100%"
              placeholder="选择日期"
              format="YYYY-MM-DD"
            />
            <a-time-picker
              v-model:value="formData.scheduleTime"
              style="width: 100%"
              placeholder="选择时间"
              format="HH:mm"
            />
          </div>
        </a-form-item>

        <a-form-item label="体验图片">
          <div class="image-grid">
            <div v-for="(img, index) in imageList" :key="index" class="image-item">
              <img :src="img" alt="体验图片" />
              <button class="remove-btn" @click="removeImage(index)" :disabled="uploading">×</button>
            </div>
            <label v-if="imageList.length < maxImages" class="upload-placeholder">
              <input
                type="file"
                accept="image/*"
                multiple
                @change="handleImageUpload"
                class="file-input"
                :disabled="uploading"
              />
              <div class="upload-content">
                <span class="upload-icon">+</span>
                <span>{{ uploading ? '上传中...' : '上传图片' }}</span>
                <span class="upload-tip">单张≤5MB</span>
              </div>
            </label>
          </div>
        </a-form-item>

        <a-form-item label="体验描述" required>
          <a-textarea v-model:value="formData.description" :rows="4" placeholder="请描述体验内容、流程及注意事项" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="viewModalVisible"
      title="体验详情"
      :footer="null"
      width="650px"
    >
      <div v-if="currentRecord" class="view-detail">
        <div class="detail-row">
          <span class="detail-label">体验名称：</span>
          <span class="detail-value">{{ currentRecord.name }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">审核状态：</span>
          <span class="detail-value">
            <a-tag :color="getStatusColor(currentRecord.status)">
              {{ getStatusText(currentRecord.status) }}
            </a-tag>
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">体验类型：</span>
          <span class="detail-value">
            <a-tag>{{ getTypeLabel(currentRecord.type) }}</a-tag>
          </span>
        </div>
        <div class="detail-row" v-if="currentRecord.heritageId">
          <span class="detail-label">关联非遗：</span>
          <span class="detail-value">{{ getHeritageName(currentRecord.heritageId) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">地点：</span>
          <span class="detail-value">{{ currentRecord.location }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">价格：</span>
          <span class="detail-value">¥{{ currentRecord.price }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">名额：</span>
          <span class="detail-value">{{ currentRecord.bookedNum || 0 }} / {{ currentRecord.quota }}（已约/上限）</span>
        </div>
        <div class="detail-row" v-if="currentRecord.schedule">
          <span class="detail-label">时间安排：</span>
          <span class="detail-value">{{ currentRecord.schedule }}</span>
        </div>
        <div class="detail-row" v-if="currentRecord.createdAt">
          <span class="detail-label">创建时间：</span>
          <span class="detail-value">{{ formatDate(currentRecord.createdAt) }}</span>
        </div>
        <div class="detail-row" v-if="currentRecord.imageUrl">
          <span class="detail-label">体验图片：</span>
          <div class="detail-images">
            <img
              v-for="(img, index) in currentRecord.imageUrl.split(',').filter(url => url.trim())"
              :key="index"
              :src="img"
              :alt="currentRecord.name"
              class="detail-image"
            />
          </div>
        </div>
        <div class="detail-row">
          <span class="detail-label">体验描述：</span>
          <span class="detail-value detail-description">{{ currentRecord.description }}</span>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script>
const columns = [
  { title: '图片', dataIndex: 'imageUrl', key: 'imageUrl', width: 110 },
  { title: '体验名称', dataIndex: 'name', key: 'name' },
  { title: '类型', dataIndex: 'type', key: 'type', width: 100 },
  { title: '关联非遗', dataIndex: 'heritageId', key: 'heritageId', width: 120 },
  { title: '地点', dataIndex: 'location', key: 'location', width: 140 },
  { title: '价格', dataIndex: 'price', key: 'price', width: 90 },
  { title: '预约/名额', dataIndex: 'quota', key: 'quota', width: 100 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '时间安排', dataIndex: 'schedule', key: 'schedule', width: 140 },
  { title: '操作', key: 'actions', width: 220, fixed: 'right' },
]
</script>

<style scoped>
.inheritor-experiences {
  min-height: 100vh;
  overflow-y: auto;
}

.inheritor-experiences .container {
  padding: 24px 24px 40px;
  max-width: 1400px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 20px;
  background: linear-gradient(135deg, rgba(200,80,42,0.05), rgba(200,80,42,0.02));
  border-radius: 12px;
  border: 1px solid rgba(200,80,42,0.1);
}

.page-title h1 {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.add-button {
  padding: 0 24px;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(200,80,42,0.3);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: linear-gradient(135deg, #fff, rgba(255,250,244,0.8));
  border-radius: 10px;
  border: 1px solid rgba(200,80,42,0.1);
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  transition: all 0.3s ease;
  cursor: pointer;
  user-select: none;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(200,80,42,0.15);
}

.stat-card.active {
  background: linear-gradient(135deg, rgba(200,80,42,0.1), rgba(200,80,42,0.05));
  border: 2px solid rgba(200,80,42,0.3);
  box-shadow: 0 4px 12px rgba(200,80,42,0.2);
}

.stat-icon {
  font-size: 24px;
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(200,80,42,0.1);
  border-radius: 10px;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 2px;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.experience-table {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.table-images {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.table-image {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid rgba(200,80,42,0.1);
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  width: 100%;
}

.image-item {
  position: relative;
  width: 100%;
  height: 160px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(200,80,42,0.15);
  box-shadow: 0 2px 6px rgba(0,0,0,0.08);
  transition: all 0.2s;
  background: #fff;
}

.image-item:hover {
  box-shadow: 0 4px 12px rgba(200,80,42,0.15);
  transform: translateY(-2px);
}

.image-item img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.remove-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.65);
  color: white;
  border: none;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  opacity: 0;
}

.image-item:hover .remove-btn {
  opacity: 1;
}

.remove-btn:hover {
  background: rgba(220, 53, 69, 0.9);
  transform: scale(1.15);
}

.remove-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.upload-placeholder {
  position: relative;
  width: 100%;
  height: 160px;
  border: 2px dashed rgba(200,80,42,0.25);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(200,80,42,0.02), rgba(200,80,42,0.05));
  cursor: pointer;
  transition: all 0.3s;
}

.upload-placeholder:hover {
  border-color: rgba(200,80,42,0.5);
  background: linear-gradient(135deg, rgba(200,80,42,0.05), rgba(200,80,42,0.08));
  box-shadow: 0 2px 8px rgba(200,80,42,0.1);
}

.upload-placeholder:has(:disabled) {
  cursor: not-allowed;
  opacity: 0.6;
}

.file-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.file-input:disabled {
  cursor: not-allowed;
}

.upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
  font-size: 14px;
  pointer-events: none;
}

.upload-icon {
  font-size: 28px;
  line-height: 1;
}

.upload-tip {
  font-size: 12px;
  color: var(--text-muted);
}

.view-detail {
  padding: 16px 0;
}

.detail-row {
  margin-bottom: 16px;
  display: flex;
  align-items: flex-start;
}

.detail-label {
  font-weight: 600;
  color: var(--text-secondary);
  min-width: 100px;
  font-size: 14px;
  flex-shrink: 0;
}

.detail-value {
  flex: 1;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.6;
}

.detail-description {
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-images {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-image {
  width: 120px;
  height: 90px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid rgba(200,80,42,0.1);
}

</style>
