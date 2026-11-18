<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '../stores/user'
import { getMyHeritageList, createHeritage, updateHeritage, deleteHeritage, toggleHeritageOnline, withdrawFromHeritage } from '../api/heritage'
import { getMyApplication, getInheritors } from '../api/inheritor'
import { PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined, SwitcherOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'
import { Empty, Modal } from 'ant-design-vue'
import { regionData } from '../data/regionData'
import { uploadFile } from '../api/file'
import { CATEGORY_OPTIONS, LEVEL_OPTIONS, DYNASTY_MAP } from '../utils/constants'
import Pagination from '../components/Pagination.vue'

const userStore = useUserStore()
const loading = ref(false)
const modalVisible = ref(false)
const editingId = ref(null)
const heritages = ref([])
const filterStatus = ref('ALL') // 筛选状态:ALL/PENDING/APPROVED/REJECTED
const currentPage = ref(1)
const pageSize = 5
const viewModalVisible = ref(false)
const currentRecord = ref(null)
const myApplication = ref(null)
const coInheritorTags = ref([])
const associatedRegionTags = ref([])
const inheritorOptions = ref([])

async function loadInheritorOptions() {
  try {
    const res = await getInheritors()
    inheritorOptions.value = (res.data || []).map(u => ({
      value: u.nickname || u.username,
      label: `${u.nickname || u.username}${u.realName ? ' — ' + u.realName : ''}`
    }))
  } catch (err) {
    console.error('加载传承人列表失败:', err)
  }
}
const enableAssociatedRegions = ref(false)

const formData = ref({
  name: '',
  region: '',
  description: '',
  imageUrl: '',
  inheritanceType: 'INDIVIDUAL',
  inheritorName: '',
  coInheritors: '',
  category: '',
  level: '',
  originDynasty: '',
  associatedRegions: '',
})

// 关联地区可选列表（省市二级扁平化）
const associatedRegionOptions = computed(() => {
  const options = []
  regionData.forEach(p => {
    options.push({ value: p.value, label: p.label })
    if (p.children) {
      p.children.forEach(c => {
        options.push({ value: p.value + c.value, label: p.value + c.value })
      })
    }
  })
  return options
})

// 省市区三级联动选择
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

// 监听省份变化,重置市和区
watch(selectedProvince, () => {
  selectedCity.value = ''
  selectedDistrict.value = ''
})

// 监听城市变化,重置区
watch(selectedCity, () => {
  selectedDistrict.value = ''
})

// 监听级别变化，控制关联地区显示
watch(() => formData.value.level, (newLevel) => {
  if (newLevel === '市级') {
    enableAssociatedRegions.value = false
    associatedRegionTags.value = []
  }
})

// 监听传承类型变化
watch(() => formData.value.inheritanceType, (newType) => {
  if (newType === 'GROUP') {
    formData.value.inheritorName = '全体民众'
    coInheritorTags.value = []
  } else {
    const app = myApplication.value
    formData.value.inheritorName = app?.realName || userStore.userInfo?.nickname || userStore.userInfo?.username || ''
  }
})

// 是否显示传承人字段（仅个人传承）
const showInheritorFields = computed(() => formData.value.inheritanceType === 'INDIVIDUAL')

// 关联地区是否显示
const showAssociatedRegions = computed(() => {
  if (formData.value.level === '市级') return enableAssociatedRegions.value
  if (formData.value.level === '省级' || formData.value.level === '国家级') return true
  return false
})

// 计算完整地区
const fullRegion = computed(() => {
  const parts = []
  if (selectedProvince.value) parts.push(selectedProvince.value)
  if (selectedCity.value) parts.push(selectedCity.value)
  if (selectedDistrict.value) parts.push(selectedDistrict.value)
  return parts.join('')
})

const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE

// 分类选项
const categoryOptions = CATEGORY_OPTIONS

// 级别选项
const levelOptions = LEVEL_OPTIONS

const dynastyOptions = Object.entries(DYNASTY_MAP).map(([value, label]) => ({ value, label }))

// 图片上传相关
const imageList = ref([]) // 已上传的图片URL列表
const uploading = ref(false) // 上传中状态
const maxImages = 9 // 最多上传9张图片

const myHeritages = computed(() => {
  // 前端按状态筛选
  if (filterStatus.value === 'ALL') {
    return heritages.value
  }
  return heritages.value.filter(h => h.status === filterStatus.value)
})

const totalCount = computed(() => {
  return heritages.value.length
})

const pendingCount = computed(() => {
  return heritages.value.filter(h => h.status === 'PENDING').length
})

const approvedCount = computed(() => {
  return heritages.value.filter(h => h.status === 'APPROVED').length
})

const rejectedCount = computed(() => {
  return heritages.value.filter(h => h.status === 'REJECTED').length
})

const paginatedHeritages = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return myHeritages.value.slice(start, start + pageSize)
})

function setFilter(status) {
  filterStatus.value = status
  currentPage.value = 1
  // 不重新加载数据，前端直接筛选
}

function handleView(record) {
  currentRecord.value = record
  viewModalVisible.value = true
}

async function handleToggleOnline(record) {
  try {
    const res = await toggleHeritageOnline(record.id)
    const action = res.data.isOnline ? '上架' : '下架'
    message.success(`已${action}: ${record.name}`)
    loadData()
  } catch (error) {
    message.error('操作失败，请稍后重试')
  }
}

function handleViewRejectReason(record) {
  message.warning(`查看驳回理由: ${record.rejectReason || '暂无驳回理由'}`)
  // TODO: 实现查看驳回理由弹窗
}

// 解析联合传承人显示
function parseCoInheritors(val) {
  if (!val) return ''
  try {
    const arr = JSON.parse(val)
    return Array.isArray(arr) ? arr.join('、') : val
  } catch {
    return val
  }
}

// 解析关联地区显示
function parseAssociatedRegions(val) {
  if (!val) return ''
  try {
    const arr = JSON.parse(val)
    return Array.isArray(arr) ? arr.join('、') : val
  } catch {
    return val
  }
}

// 解析地区字符串
function parseRegion(region) {
  selectedProvince.value = ''
  selectedCity.value = ''
  selectedDistrict.value = ''
  if (!region) return

  // 全国跨省项目
  if (region === '全国') {
    selectedProvince.value = '全国'
    return
  }

  // 尝试匹配省市区
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

async function loadMyApplication() {
  try {
    const res = await getMyApplication()
    if (res.data) {
      myApplication.value = res.data
    }
  } catch (error) {
    // 尚未申请认证，忽略
  }
}

async function loadData() {
  loading.value = true
  try {
    // 始终加载所有数据，不传status参数
    const res = await getMyHeritageList({ size: 100 })
    heritages.value = res.data?.records || res.data || []
  } catch (error) {
    message.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

async function handleAdd() {
  editingId.value = null
  imageList.value = []
  coInheritorTags.value = []
  associatedRegionTags.value = []
  enableAssociatedRegions.value = false

  // 从认证信息获取默认值
  const app = myApplication.value
  const defaultName = app?.realName || userStore.userInfo?.nickname || userStore.userInfo?.username || ''
  const defaultRegion = app?.region || ''

  // 解析默认地区到省市区
  if (defaultRegion && defaultRegion !== '未填写') {
    parseRegion(defaultRegion)
  } else {
    selectedProvince.value = ''
    selectedCity.value = ''
    selectedDistrict.value = ''
  }

  formData.value = {
    name: '',
    region: defaultRegion,
    description: '',
    imageUrl: '',
    inheritanceType: 'INDIVIDUAL',
    inheritorName: defaultName,
    coInheritors: '',
    category: '',
    level: '',
    originDynasty: '',
    associatedRegions: '',
  }
  loadInheritorOptions()
  modalVisible.value = true
}

function handleEdit(record) {
  editingId.value = record.id
  parseRegion(record.region)

  // 解析已有的图片
  if (record.imageUrl) {
    imageList.value = record.imageUrl.split(',').filter(url => url.trim())
  } else {
    imageList.value = []
  }

  // 解析联合传承人
  try {
    coInheritorTags.value = record.coInheritors ? JSON.parse(record.coInheritors) : []
  } catch {
    coInheritorTags.value = record.coInheritors ? record.coInheritors.split(',') : []
  }

  // 解析关联地区
  try {
    associatedRegionTags.value = record.associatedRegions ? JSON.parse(record.associatedRegions) : []
  } catch {
    associatedRegionTags.value = record.associatedRegions ? record.associatedRegions.split(',') : []
  }

  // 如果有关联地区且级别为市级，自动开启
  if (associatedRegionTags.value.length > 0 && record.level === '市级') {
    enableAssociatedRegions.value = true
  } else {
    enableAssociatedRegions.value = false
  }

  formData.value = {
    name: record.name,
    region: record.region,
    description: record.description || '',
    imageUrl: record.imageUrl || '',
    inheritanceType: record.inheritanceType || 'INDIVIDUAL',
    inheritorName: record.inheritorName || '',
    coInheritors: record.coInheritors || '',
    category: record.category || '',
    level: record.level || '',
    originDynasty: record.originDynasty || '',
    associatedRegions: record.associatedRegions || '',
  }
  loadInheritorOptions()
  modalVisible.value = true
}

function handleDelete(record) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除「${record.name}」吗？删除后不可恢复。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteHeritage(record.id)
        message.success('删除成功')
        loadData()
      } catch (error) {
        message.error('删除失败')
      }
    }
  })
}

async function handleWithdraw(record) {
  Modal.confirm({
    title: '申请退出',
    content: `确定要退出「${record.name}」的联合传承吗？退出后您的姓名将从联合传承人列表中移除。`,
    okText: '确认退出',
    cancelText: '取消',
    onOk: async () => {
      try {
        await withdrawFromHeritage(record.id)
        message.success('已退出联合传承')
        loadData()
      } catch (error) {
        message.error('操作失败')
      }
    }
  })
}

async function handleSubmit() {
  // 验证必填字段
  if (!formData.value.name.trim()) {
    message.error('请输入项目名称')
    return
  }
  if (!selectedProvince.value || (selectedProvince.value !== '全国' && !selectedCity.value)) {
    message.error('请选择省份和城市')
    return
  }
  if (!formData.value.category) {
    message.error('请选择项目类别')
    return
  }
  if (!formData.value.level) {
    message.error('请选择项目级别')
    return
  }
  if (!formData.value.description.trim()) {
    message.error('请输入项目描述')
    return
  }

  try {
    const submitData = {
      ...formData.value,
      region: fullRegion.value,
      imageUrl: imageList.value.join(','), // 多张图片用逗号分隔
      userId: userStore.userInfo?.id,
      coInheritors: JSON.stringify(coInheritorTags.value),
      associatedRegions: JSON.stringify(associatedRegionTags.value),
    }
    
    console.log('提交的数据:', submitData)
    console.log('当前用户ID:', userStore.userInfo?.id)
    
    if (editingId.value) {
      await updateHeritage(editingId.value, submitData)
      message.success('更新成功')
    } else {
      await createHeritage(submitData)
      message.success('创建成功')
    }
    modalVisible.value = false
    
    // 刷新数据
    await loadData()
    console.log('加载的数据:', heritages.value)
    console.log('过滤后的我的项目:', myHeritages.value)
  } catch (error) {
    console.error('提交错误:', error)
    message.error(editingId.value ? '更新失败' : '创建失败')
  }
}

// 图片上传处理
async function handleImageUpload(e) {
  const files = e.target.files
  if (!files || files.length === 0) return
  
  // 检查是否超过最大数量
  if (imageList.value.length >= maxImages) {
    message.warning(`最多只能上传${maxImages}张图片`)
    e.target.value = ''
    return
  }
  
  for (const file of files) {
    // 再次检查数量
    if (imageList.value.length >= maxImages) {
      message.warning(`最多只能上传${maxImages}张图片`)
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
  
  // 清空input，允许重复选择同一文件
  e.target.value = ''
}

// 删除图片
function removeImage(index) {
  imageList.value.splice(index, 1)
}

onMounted(async () => {
  await userStore.refreshUser()
  loadData()
  loadMyApplication()
})
</script>

<template>
  <div class="inheritor-heritages page-container">
    <div class="container">
    <div class="page-header">
      <div class="page-title">
        <h1>📚 内容管理</h1>
        <p class="page-subtitle">管理您的非遗项目,包括发布、编辑和删除</p>
      </div>
      <a-button type="primary" size="large" @click="handleAdd" class="add-button">
        <PlusOutlined />
        新增非遗项目
      </a-button>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card" :class="{ active: filterStatus === 'ALL' }" @click="setFilter('ALL')">
        <div class="stat-icon">📝</div>
        <div class="stat-info">
          <div class="stat-label">项目总数</div>
          <div class="stat-value">{{ totalCount }}</div>
        </div>
      </div>
      <div class="stat-card" :class="{ active: filterStatus === 'PENDING' }" @click="setFilter('PENDING')">
        <div class="stat-icon">⏳</div>
        <div class="stat-info">
          <div class="stat-label">待审核</div>
          <div class="stat-value">{{ pendingCount }}</div>
        </div>
      </div>
      <div class="stat-card" :class="{ active: filterStatus === 'APPROVED' }" @click="setFilter('APPROVED')">
        <div class="stat-icon">✅</div>
        <div class="stat-info">
          <div class="stat-label">审核通过</div>
          <div class="stat-value">{{ approvedCount }}</div>
        </div>
      </div>
      <div class="stat-card" :class="{ active: filterStatus === 'REJECTED' }" @click="setFilter('REJECTED')">
        <div class="stat-icon">❌</div>
        <div class="stat-info">
          <div class="stat-label">审核驳回</div>
          <div class="stat-value">{{ rejectedCount }}</div>
        </div>
      </div>
    </div>

    <a-table
      :columns="columns"
      :data-source="paginatedHeritages"
      :loading="loading"
      row-key="id"
      :pagination="false"
      class="heritage-table"
    >
      <template #emptyText>
        <div style="padding: 60px 0; text-align: center;">
          <a-empty :image="simpleImage">
            <template #description>
              <span style="color: var(--text-secondary); font-size: 14px;">
                暂无项目数据，点击右上角「新增非遗项目」创建你的第一个非遗项目吧！
              </span>
            </template>
          </a-empty>
        </div>
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'imageUrl'">
          <div v-if="record.imageUrl" class="table-images">
            <img 
              v-for="(img, index) in record.imageUrl.split(',').filter(url => url.trim())" 
              :key="index" 
              :src="img" 
              :alt="record.name" 
              class="table-image"
            />
          </div>
          <a-empty v-else :image="simpleImage" description="无图片" :style="{ margin: '10px 0' }" />
        </template>
        <template v-else-if="column.key === 'ownership'">
          <a-tag :color="record.ownership === 'OWNER' ? 'blue' : 'purple'">
            {{ record.ownership === 'OWNER' ? '主传承人' : '联合传承人' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'isOnline'">
          <template v-if="record.status === 'APPROVED'">
            <a-tag :color="record.isOnline !== 0 ? 'green' : 'default'">
              {{ record.isOnline !== 0 ? '已上架' : '已下架' }}
            </a-tag>
          </template>
          <span v-else style="color: var(--text-muted); font-size: 12px;">—</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 'PENDING' ? 'orange' : record.status === 'APPROVED' ? 'green' : 'red'">
            {{ record.status === 'PENDING' ? '待审核' : record.status === 'APPROVED' ? '审核通过' : '审核驳回' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <!-- ===== 主传承人 (OWNER) 操作 ===== -->
            <template v-if="record.ownership === 'OWNER'">
              <!-- 待审核状态 -->
              <template v-if="record.status === 'PENDING'">
                <a-button type="link" size="small" @click="handleView(record)">
                  <EyeOutlined />查看详情
                </a-button>
              </template>
              <!-- 审核通过状态 -->
              <template v-else-if="record.status === 'APPROVED'">
                <a-button type="link" size="small" @click="handleEdit(record)">
                  <EditOutlined />编辑
                </a-button>
                <a-button type="link" size="small" @click="handleToggleOnline(record)">
                  <SwitcherOutlined />{{ record.isOnline !== 0 ? '下架' : '上架' }}
                </a-button>
                <a-button type="link" size="small" @click="handleView(record)">
                  <EyeOutlined />查看
                </a-button>
                <a-button type="link" size="small" danger @click="handleDelete(record)">
                  <DeleteOutlined />删除
                </a-button>
              </template>
              <!-- 审核驳回状态 -->
              <template v-else>
                <a-button type="link" size="small" @click="handleEdit(record)">
                  <EditOutlined />修改重新提交
                </a-button>
                <a-button type="link" size="small" @click="handleViewRejectReason(record)">
                  <InfoCircleOutlined />驳回理由
                </a-button>
              </template>
            </template>

            <!-- ===== 联合传承人 (JOINT) 操作 ===== -->
            <template v-else>
              <a-button type="link" size="small" @click="handleView(record)">
                <EyeOutlined />查看
              </a-button>
              <a-button type="link" size="small" danger @click="handleWithdraw(record)">
                <DeleteOutlined />申请退出
              </a-button>
            </template>
          </a-space>
        </template>
      </template>
    </a-table>

    <Pagination v-if="myHeritages.length > 5" v-model="currentPage" :total="myHeritages.length" :page-size="5" />

    <a-modal
      v-model:open="modalVisible"
      :title="editingId ? '✏️ 编辑非遗项目' : '✨ 新增非遗项目'"
      @ok="handleSubmit"
      width="650px"
      :mask-closable="false"
    >
      <a-form :model="formData" layout="vertical">
        <a-form-item label="传承类型" required>
          <a-radio-group v-model:value="formData.inheritanceType" button-style="solid">
            <a-radio-button value="INDIVIDUAL">个人传承</a-radio-button>
            <a-radio-button value="GROUP">群体 / 全民传承</a-radio-button>
          </a-radio-group>
        </a-form-item>

        <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px;">
          <a-form-item label="项目名称" required>
            <a-input v-model:value="formData.name" placeholder="请输入项目名称" />
          </a-form-item>

          <a-form-item v-if="showInheritorFields" label="主传承人">
            <a-input v-model:value="formData.inheritorName" disabled placeholder="本人" />
            <div style="font-size:11px;color:var(--text-muted);margin-top:4px">主传承人固定为本人，由认证信息自动填入</div>
          </a-form-item>
          <a-form-item v-else label="传承主体">
            <a-input value="全体民众 / 全民共同传承" disabled />
            <div style="font-size:11px;color:var(--text-muted);margin-top:4px">群体/全民传承项目，无需指定个人传承人</div>
          </a-form-item>
        </div>

        <a-form-item v-if="showInheritorFields" label="联合传承人">
          <a-select
            v-model:value="coInheritorTags"
            mode="tags"
            placeholder="请选择或输入联合传承人姓名"
            :options="inheritorOptions"
            :filter-option="(input, option) => option.label.includes(input)"
            show-search
          />
        </a-form-item>

        <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px;">
          <a-form-item label="项目类别" required>
            <a-select v-model:value="formData.category" :options="categoryOptions" placeholder="请选择分类" />
          </a-form-item>
          <a-form-item label="项目级别" required>
            <a-select v-model:value="formData.level" :options="levelOptions" placeholder="请选择级别" />
          </a-form-item>
        </div>

        <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px;">
          <a-form-item label="起源朝代">
            <a-select v-model:value="formData.originDynasty" :options="dynastyOptions" placeholder="请选择起源朝代" allow-clear />
          </a-form-item>
        </div>

        <a-form-item label="所属地区" required>
          <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px;">
            <a-select v-model:value="selectedProvince" :options="provinceList" placeholder="选择省" />
            <a-select v-model:value="selectedCity" :options="cityList" placeholder="选择市" :disabled="!selectedProvince || selectedProvince === '全国'" />
            <a-select v-model:value="selectedDistrict" :options="districtList" placeholder="选择区（可选）" :disabled="!selectedCity || selectedProvince === '全国'" />
          </div>
        </a-form-item>

        <!-- 关联地区（市级默认隐藏，省级/国家级默认显示） -->
        <a-form-item v-if="formData.level === '市级'" label="关联地区">
          <div v-if="!enableAssociatedRegions" style="display:flex;align-items:center;gap:8px">
            <a-switch v-model:checked="enableAssociatedRegions" size="small" />
            <span style="font-size:13px;color:var(--text-secondary)">启用关联地区（市级项目默认仅主地区）</span>
          </div>
          <a-select v-else v-model:value="associatedRegionTags" mode="multiple" :options="associatedRegionOptions" placeholder="请选择关联地区" />
        </a-form-item>
        <a-form-item v-else-if="formData.level === '省级' || formData.level === '国家级'" label="关联地区">
          <a-select v-model:value="associatedRegionTags" mode="multiple" :options="associatedRegionOptions" :placeholder="formData.level === '国家级' ? '请选择关联地区（可多选全国范围）' : '请选择关联地区（可多选）'" />
        </a-form-item>

        <a-form-item label="项目图片">
          <div class="image-upload-container">
            <!-- 已上传的图片列表和上传按钮 -->
            <div class="image-grid">
              <div v-for="(img, index) in imageList" :key="index" class="image-item">
                <img :src="img" alt="项目图片" />
                <button class="remove-btn" @click="removeImage(index)" :disabled="uploading">×</button>
              </div>
              
              <!-- 上传按钮 - 未达到最大数量时显示 -->
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
                  <span class="upload-icon"></span>
                  <span>{{ uploading ? '上传中...' : '点击上传图片' }}</span>
                  <span class="upload-tip">支持多图 · 单张≤5MB</span>
                </div>
              </label>
            </div>
          </div>
        </a-form-item>

        <a-form-item label="项目描述" required>
          <a-textarea v-model:value="formData.description" :rows="5" placeholder="请详细描述项目的历史渊源、技艺特点、传承现状等" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 查看详情弹窗 -->
    <a-modal
      v-model:open="viewModalVisible"
      title="📄 项目详情"
      :footer="null"
      width="650px"
    >
      <div v-if="currentRecord" class="view-detail">
        <div class="detail-row">
          <span class="detail-label">项目名称：</span>
          <span class="detail-value">{{ currentRecord.name }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">审核状态：</span>
          <span class="detail-value">
            <a-tag :color="currentRecord.status === 'PENDING' ? 'orange' : currentRecord.status === 'APPROVED' ? 'green' : 'red'">
              {{ currentRecord.status === 'PENDING' ? '待审核' : currentRecord.status === 'APPROVED' ? '审核通过' : '审核驳回' }}
            </a-tag>
          </span>
        </div>
        <div class="detail-row" v-if="currentRecord.category">
          <span class="detail-label">项目类别：</span>
          <span class="detail-value">{{ currentRecord.category }}</span>
        </div>
        <div class="detail-row" v-if="currentRecord.level">
          <span class="detail-label">项目级别：</span>
          <span class="detail-value">
            <a-tag :color="currentRecord.level === '国家级' ? 'red' : currentRecord.level === '省级' ? 'blue' : 'default'">
              {{ currentRecord.level }}
            </a-tag>
          </span>
        </div>
        <div class="detail-row" v-if="currentRecord.status === 'REJECTED'">
          <span class="detail-label">驳回理由：</span>
          <span class="detail-value" style="color: #f56c6c;">{{ currentRecord.rejectReason || '暂无' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">所属地区：</span>
          <span class="detail-value">{{ currentRecord.region }}</span>
        </div>
        <div class="detail-row" v-if="currentRecord.inheritanceType === 'GROUP'">
          <span class="detail-label">传承主体：</span>
          <span class="detail-value">全体民众 / 全民共同传承</span>
        </div>
        <div class="detail-row" v-if="currentRecord.inheritorName && currentRecord.inheritanceType !== 'GROUP'">
          <span class="detail-label">主传承人：</span>
          <span class="detail-value">{{ currentRecord.inheritorName }}</span>
        </div>
        <div class="detail-row" v-if="currentRecord.coInheritors && currentRecord.inheritanceType !== 'GROUP'">
          <span class="detail-label">联合传承人：</span>
          <span class="detail-value">{{ parseCoInheritors(currentRecord.coInheritors) }}</span>
        </div>
        <div class="detail-row" v-if="currentRecord.associatedRegions">
          <span class="detail-label" v-if="currentRecord.inheritanceType === 'GROUP'">主要流传地：</span>
          <span class="detail-label" v-else>关联地区：</span>
          <span class="detail-value">{{ parseAssociatedRegions(currentRecord.associatedRegions) }}</span>
        </div>
        <div class="detail-row" v-if="currentRecord.imageUrl">
          <span class="detail-label">项目图片：</span>
          <div class="detail-image">
            <img :src="currentRecord.imageUrl" :alt="currentRecord.name" />
          </div>
        </div>
        <div class="detail-row">
          <span class="detail-label">项目描述：</span>
          <span class="detail-value detail-description">{{ currentRecord.description }}</span>
        </div>
      </div>
    </a-modal>
    </div>
  </div>
</template>

<script>
const columns = [
  { title: '图片', dataIndex: 'imageUrl', key: 'imageUrl', width: 100 },
  { title: '项目名称', dataIndex: 'name', key: 'name' },
  { title: '类别', dataIndex: 'category', key: 'category', width: 100 },
  { title: '级别', dataIndex: 'level', key: 'level', width: 100 },
  { title: '归属', dataIndex: 'ownership', key: 'ownership', width: 100 },
  { title: '审核状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '上架', dataIndex: 'isOnline', key: 'isOnline', width: 70 },
  { title: '操作', key: 'actions', width: 200, fixed: 'right' },
]
</script>

<style scoped>
.inheritor-heritages {
  min-height: 100vh;
  overflow-y: auto;
}

.inheritor-heritages .container {
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

/* 统计卡片 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: linear-gradient(135deg, #fff, rgba(255,250,244,0.8));
  border-radius: 12px;
  border: 1px solid rgba(200,80,42,0.1);
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  transition: all 0.3s ease;
  cursor: pointer;
  user-select: none;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 6px 16px rgba(200,80,42,0.15);
}

.stat-card.active {
  background: linear-gradient(135deg, rgba(200,80,42,0.1), rgba(200,80,42,0.05));
  border: 2px solid rgba(200,80,42,0.3);
  box-shadow: 0 4px 12px rgba(200,80,42,0.2);
}

.stat-icon {
  font-size: 32px;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(200,80,42,0.1);
  border-radius: 12px;
}

.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.heritage-table {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.table-images {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.table-image {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid rgba(200,80,42,0.1);
}

.image-preview {
  margin-top: 12px;
  border: 2px dashed rgba(200,80,42,0.2);
  border-radius: 8px;
  overflow: hidden;
}

.image-preview img {
  width: 100%;
  max-height: 200px;
  object-fit: cover;
  display: block;
}

/* 图片上传容器 */
.image-upload-container {
  width: 100%;
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
  height: 220px;
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
  display: block;
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
  height: 220px;
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
  gap: 6px;
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

/* 查看详情弹窗样式 */
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

.detail-image {
  flex: 1;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(200,80,42,0.1);
}

.detail-image img {
  width: 100%;
  max-height: 300px;
  object-fit: cover;
  display: block;
}

</style>
