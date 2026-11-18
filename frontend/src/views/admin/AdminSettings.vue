<script setup>
import { ref, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { getUserList, banUser, unbanUser } from '../../api/user'
import { getApplications, approveInheritor, rejectInheritor } from '../../api/inheritor'
import { ROLE_MAP, APPROVAL_STATUS_MAP } from '../../utils/constants'

const users = ref([])
const applications = ref([])
const appLoading = ref(false)
const certPreviewVisible = ref(false)
const certPreviewUrls = ref([])

async function loadUsers() {
  try {
    const res = await getUserList()
    if (res.data) {
      users.value = res.data.map(u => ({
        id: u.id,
        username: u.username,
        role: ROLE_MAP[u.role] || u.role,
        roleRaw: u.role,
        status: u.status || 'ACTIVE',
      }))
    }
  } catch (error) {
    console.error('加载用户列表失败:', error)
    message.error('加载用户列表失败')
  }
}

async function loadApplications() {
  appLoading.value = true
  try {
    const res = await getApplications({ size: 200 })
    applications.value = (res.data?.records || res.data || []).map(a => ({
      id: a.id,
      realName: a.realName || '',
      idCard: a.idCard || '',
      skill: a.skill || a.heritage || '',
      level: a.level || '',
      years: a.years || '',
      description: a.description || '',
      certificates: a.certificates || '[]',
      status: a.status || 'PENDING',
      userId: a.userId,
      createdAt: a.createdAt || '',
    }))
  } catch (error) {
    console.error('加载认证申请失败:', error)
  } finally {
    appLoading.value = false
  }
}

// 用户管理分页
const userCurrentPage = ref(1)
const userPageSize = 10
const userJumpPage = ref('')

const userTotalPages = computed(() => Math.ceil(users.value.length / userPageSize))

const paginatedUsers = computed(() => {
  const start = (userCurrentPage.value - 1) * userPageSize
  return users.value.slice(start, start + userPageSize)
})

const userVisiblePages = computed(() => {
  const current = userCurrentPage.value
  const total = userTotalPages.value
  if (total <= 6) return Array.from({ length: total }, (_, i) => i + 1)
  if (current <= 3) return [1, 2, 3, 4, '...', total]
  if (current >= total - 2) return [1, '...', total - 3, total - 2, total - 1, total]
  return [1, '...', current - 1, current, current + 1, '...', total]
})

function goUserPage(p) {
  if (p >= 1 && p <= userTotalPages.value) {
    userCurrentPage.value = p
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

function handleUserJump() {
  const p = parseInt(userJumpPage.value)
  if (p && p >= 1 && p <= userTotalPages.value) {
    goUserPage(p)
    userJumpPage.value = ''
  }
}

// 认证申请分页
const appCurrentPage = ref(1)
const appPageSize = 10
const appJumpPage = ref('')

const appTotalPages = computed(() => Math.ceil(applications.value.length / appPageSize))

const paginatedApps = computed(() => {
  const start = (appCurrentPage.value - 1) * appPageSize
  return applications.value.slice(start, start + appPageSize)
})

const appVisiblePages = computed(() => {
  const current = appCurrentPage.value
  const total = appTotalPages.value
  if (total <= 6) return Array.from({ length: total }, (_, i) => i + 1)
  if (current <= 3) return [1, 2, 3, 4, '...', total]
  if (current >= total - 2) return [1, '...', total - 3, total - 2, total - 1, total]
  return [1, '...', current - 1, current, current + 1, '...', total]
})

function goAppPage(p) {
  if (p >= 1 && p <= appTotalPages.value) {
    appCurrentPage.value = p
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

function handleAppJump() {
  const p = parseInt(appJumpPage.value)
  if (p && p >= 1 && p <= appTotalPages.value) {
    goAppPage(p)
    appJumpPage.value = ''
  }
}

async function handleBan(record) {
  if (record.username === 'admin') {
    message.warning('不能操作管理员账号')
    return
  }
  Modal.confirm({
    title: '确认封禁',
    content: `确定要封禁用户「${record.username}」吗？`,
    okText: '封禁',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await banUser(record.id, { reason: '管理员封禁' })
        record.status = 'BANNED'
        message.success(`已封禁用户 ${record.username}`)
      } catch (error) {
        message.error('操作失败')
      }
    },
  })
}

async function handleUnban(record) {
  try {
    await unbanUser(record.id)
    record.status = 'ACTIVE'
    message.success(`已解封用户 ${record.username}`)
  } catch (error) {
    message.error('操作失败')
  }
}

async function handleApproveApp(app) {
  Modal.confirm({
    title: '确认通过认证',
    content: `确定要通过「${app.realName}」的传承人认证申请吗？通过后该用户将获得传承人身份。`,
    okText: '通过',
    cancelText: '取消',
    onOk: async () => {
      try {
        await approveInheritor(app.id)
        app.status = 'APPROVED'
        message.success('已通过认证申请')
        loadUsers()
      } catch (error) {
        message.error('操作失败')
      }
    },
  })
}

async function handleRejectApp(app) {
  Modal.confirm({
    title: '确认驳回',
    content: `确定要驳回「${app.realName}」的认证申请吗？`,
    okText: '驳回',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await rejectInheritor(app.id, { reason: '管理员审核驳回' })
        app.status = 'REJECTED'
        message.success('已驳回认证申请')
      } catch (error) {
        message.error('操作失败')
      }
    },
  })
}

function maskIdCard(idCard) {
  if (!idCard || idCard.length < 10) return idCard
  return idCard.substring(0, 6) + '********' + idCard.substring(idCard.length - 4)
}

function getAppStatusLabel(status) {
  return APPROVAL_STATUS_MAP[status] || status
}

function getAppStatusColor(status) {
  const map = { PENDING: 'orange', APPROVED: 'green', REJECTED: 'red' }
  return map[status] || 'default'
}

function parseCertUrls(certificates) {
  if (!certificates) return []
  try {
    return JSON.parse(certificates)
  } catch {
    return []
  }
}

function getFileName(url) {
  if (!url) return '未知文件'
  const parts = url.split('/')
  return parts[parts.length - 1] || '未知文件'
}

function isImageUrl(url) {
  return /\.(jpg|jpeg|png|gif|webp|bmp|svg)/i.test(url)
}

function isPdfUrl(url) {
  return /\.pdf$/i.test(url) || url.toLowerCase().includes('.pdf')
}

function showCertPreview(urls) {
  certPreviewUrls.value = urls
  certPreviewVisible.value = true
}

const userColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '角色', dataIndex: 'role', key: 'role', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '操作', key: 'action', width: 100 },
]

const appColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '姓名', dataIndex: 'realName', key: 'realName', width: 90 },
  { title: '身份证号', key: 'idCard', width: 160 },
  { title: '传承项目', dataIndex: 'skill', key: 'skill', width: 100 },
  { title: '级别', dataIndex: 'level', key: 'level', width: 80 },
  { title: '从艺年限', key: 'years', width: 80 },
  { title: '认证材料', key: 'certificates', width: 120 },
  { title: '状态', key: 'status', width: 80 },
  { title: '操作', key: 'action', width: 140 },
]

onMounted(() => {
  loadUsers()
  loadApplications()
})
</script>

<template>
  <div class="admin-page page-container">
    <div class="container glass-card" style="padding:32px; min-height:600px">
      <h1 class="section-title">用户与认证管理</h1>
      <p style="color:var(--text-secondary);margin-bottom:24px">管理平台用户权限，审核传承人认证申请。</p>

      <a-tabs default-active-key="users">
        <a-tab-pane key="users" tab="用户管理">
          <a-table :dataSource="paginatedUsers" :columns="userColumns" rowKey="id" :pagination="false">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'red'">{{ record.status === 'ACTIVE' ? '正常' : '已封禁' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button v-if="record.status === 'ACTIVE'" size="small" danger :disabled="record.username==='admin'" @click="handleBan(record)">封禁</a-button>
                <a-button v-else size="small" type="primary" @click="handleUnban(record)">解封</a-button>
              </template>
            </template>
          </a-table>

          <div v-if="userTotalPages > 1" class="pagination">
            <button class="page-btn" :disabled="userCurrentPage <= 1" @click="goUserPage(userCurrentPage - 1)">‹ 上一页</button>
            <template v-for="(p, index) in userVisiblePages" :key="index">
              <button v-if="p !== '...'" class="page-btn" :class="{ active: p === userCurrentPage }" @click="goUserPage(p)">{{ p }}</button>
              <span v-else class="page-ellipsis">...</span>
            </template>
            <button class="page-btn" :disabled="userCurrentPage >= userTotalPages" @click="goUserPage(userCurrentPage + 1)">下一页 ›</button>
            <span class="page-info">共 {{ users.length }} 项</span>
            <div class="page-jump">
              <span>前往</span>
              <input type="number" v-model="userJumpPage" @keyup.enter="handleUserJump" min="1" :max="userTotalPages" />
              <span>页</span>
            </div>
          </div>
        </a-tab-pane>

        <a-tab-pane key="certification" tab="传承人认证审核">
          <a-table :dataSource="paginatedApps" :columns="appColumns" rowKey="id" :loading="appLoading"
            :pagination="false">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'idCard'">
                {{ maskIdCard(record.idCard) }}
              </template>
              <template v-else-if="column.key === 'years'">
                {{ record.years ? record.years + ' 年' : '-' }}
              </template>
              <template v-else-if="column.key === 'certificates'">
                <template v-if="parseCertUrls(record.certificates).length > 0">
                  <a-button size="small" type="link" @click="showCertPreview(parseCertUrls(record.certificates))">
                    查看 ({{ parseCertUrls(record.certificates).length }})
                  </a-button>
                </template>
                <span v-else style="color:var(--text-muted);font-size:12px">-</span>
              </template>
              <template v-else-if="column.key === 'status'">
                <a-tag :color="getAppStatusColor(record.status)">{{ getAppStatusLabel(record.status) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'action'">
                <div v-if="record.status === 'PENDING'" style="display:flex;gap:6px">
                  <a-button size="small" type="primary" @click="handleApproveApp(record)">通过</a-button>
                  <a-button size="small" danger @click="handleRejectApp(record)">驳回</a-button>
                </div>
                <span v-else style="color:var(--text-muted);font-size:12px">-</span>
              </template>
            </template>
          </a-table>

          <div v-if="appTotalPages > 1" class="pagination">
            <button class="page-btn" :disabled="appCurrentPage <= 1" @click="goAppPage(appCurrentPage - 1)">‹ 上一页</button>
            <template v-for="(p, index) in appVisiblePages" :key="index">
              <button v-if="p !== '...'" class="page-btn" :class="{ active: p === appCurrentPage }" @click="goAppPage(p)">{{ p }}</button>
              <span v-else class="page-ellipsis">...</span>
            </template>
            <button class="page-btn" :disabled="appCurrentPage >= appTotalPages" @click="goAppPage(appCurrentPage + 1)">下一页 ›</button>
            <span class="page-info">共 {{ applications.length }} 项</span>
            <div class="page-jump">
              <span>前往</span>
              <input type="number" v-model="appJumpPage" @keyup.enter="handleAppJump" min="1" :max="appTotalPages" />
              <span>页</span>
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </div>

    <!-- 认证材料预览弹窗 -->
    <a-modal v-model:visible="certPreviewVisible" title="认证材料预览" :footer="null" width="720px">
      <div v-if="certPreviewUrls.length === 0" style="text-align:center;padding:40px;color:var(--text-muted)">暂无材料</div>
      <div v-else style="display:flex;flex-direction:column;gap:16px">
        <div v-for="(url, idx) in certPreviewUrls" :key="idx" class="cert-file-card">
          <!-- 图片类型：直接预览 -->
          <template v-if="isImageUrl(url)">
            <div class="cert-file-label">材料 {{ idx + 1 }}：{{ getFileName(url) }}</div>
            <img :src="url" class="cert-image-preview" />
          </template>
          <!-- PDF 类型：显示图标 + 文件名 + 打开按钮 -->
          <template v-else-if="isPdfUrl(url)">
            <div class="cert-file-row">
              <div class="cert-file-icon pdf-icon">PDF</div>
              <div class="cert-file-info">
                <div class="cert-file-name">{{ getFileName(url) }}</div>
                <div class="cert-file-hint">PDF 文档 — 点击下方按钮在新标签页中查看</div>
              </div>
              <a :href="url" target="_blank" class="cert-file-btn">打开文件</a>
            </div>
          </template>
          <!-- 其他文件类型 -->
          <template v-else>
            <div class="cert-file-row">
              <div class="cert-file-icon other-icon">FILE</div>
              <div class="cert-file-info">
                <div class="cert-file-name">{{ getFileName(url) }}</div>
                <div class="cert-file-hint">点击下方按钮查看文件</div>
              </div>
              <a :href="url" target="_blank" class="cert-file-btn">打开文件</a>
            </div>
          </template>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.admin-page { padding-top: calc(var(--header-height) + 32px); padding-bottom: 32px; background: var(--bg-base); min-height: 100vh; }

.cert-file-card { border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; }
.cert-file-label { padding: 8px 12px; background: #fafafa; font-size: 13px; color: var(--text-secondary); border-bottom: 1px solid var(--border-color); }
.cert-image-preview { width: 100%; max-height: 500px; object-fit: contain; background: #f5f5f5; display: block; }

.cert-file-row { display: flex; align-items: center; gap: 16px; padding: 20px; }
.cert-file-icon { width: 52px; height: 52px; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700; flex-shrink: 0; }
.cert-file-icon.pdf-icon { background: #FFF0F0; color: #D32F2F; }
.cert-file-icon.other-icon { background: #F0F0FF; color: #4A4AD0; }
.cert-file-info { flex: 1; min-width: 0; }
.cert-file-name { font-size: 14px; font-weight: 500; color: var(--text-primary); word-break: break-all; }
.cert-file-hint { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
.cert-file-btn { flex-shrink: 0; padding: 6px 16px; border: 1px solid var(--primary); border-radius: 6px; color: var(--primary); font-size: 13px; text-decoration: none; transition: all 0.2s; }
.cert-file-btn:hover { background: var(--primary); color: #fff; }

.pagination { display: flex; align-items: center; justify-content: center; gap: 8px; margin-top: 24px; flex-wrap: wrap; }
.page-btn { padding: 8px 16px; border: 1px solid var(--bg-glass-border); border-radius: 8px; background: #fff; cursor: pointer; font-size: 14px; color: var(--text-secondary); transition: all 0.2s; }
.page-btn:hover:not(:disabled) { color: #fff; background: var(--color-primary); border-color: var(--color-primary); }
.page-btn.active { color: #fff; background: var(--color-primary); border-color: var(--color-primary); font-weight: 600; }
.page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.page-info { font-size: 13px; color: var(--text-muted); margin-left: 12px; }
.page-ellipsis { padding: 8px; color: var(--text-muted); letter-spacing: 2px; }
.page-jump { display: flex; align-items: center; gap: 8px; margin-left: 16px; font-size: 13px; color: var(--text-secondary); }
.page-jump input { width: 50px; padding: 6px; border: 1px solid var(--bg-glass-border); border-radius: 6px; text-align: center; outline: none; transition: border-color 0.2s; }
.page-jump input:focus { border-color: var(--color-primary); }
</style>
