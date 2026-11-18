<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { message, Modal } from 'ant-design-vue'
import { getUserStorage, setUserStorage } from '../utils/storage'
import { getMyBookings, cancelBooking, rateBooking } from '../api/booking'
import StarRating from '../components/StarRating.vue'
import { updateUserInfo, getUserInfo, getUserById } from '../api/user'
import { getMyArtworks, deleteArtwork } from '../api/aigc'
import { ORDER_STATUS_MAP, AIGC_TYPE_MAP } from '../utils/constants'
import Pagination from '../components/Pagination.vue'
import { getMyLikes, toggleLike, getMyFavorites, removeFavorite } from '../api/user'
import { getFollowing, getFollowers, unfollowUser, followUser, checkFollow, getUserFollowCounts, getUserFollowing, getUserFollowers } from '../api/follow'
import { applyInheritor, getMyApplication, getInheritorPublicApplication, getInheritorPosts } from '../api/inheritor'
import { getUserExperiences } from '../api/experience'
import { getHeritageDetail } from '../api/heritage'
import { uploadFileWithProgress } from '../api/file'
import { regionData } from '../data/regionData'
import { useUserNavigation } from '../composables/useUserNavigation'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { goToUserProfile } = useUserNavigation()
const activeTab = ref('orders')

// 判断是否为查看自己的个人中心
const isOwnProfile = computed(() => {
  return !route.params.id || String(route.params.id) === String(userStore.userInfo?.id)
})

// 查看其他用户时的数据
const profileUser = ref(null)
const profileFollowCounts = ref({ followerCount: 0, followingCount: 0 })
const isFollowing = ref(false)
const followLoading = ref(false)
const profileFollowers = ref([])
const profileFollowing = ref([])

// 查看传承人主页时的数据
const isInheritorProfile = computed(() => profileUser.value?.role === 'INHERITOR')
const profileActiveTab = ref('cert')
const inheritorCertInfo = ref(null)
const inheritorHeritages = ref([])
const inheritorExperiences = ref([])
const inheritorDataLoading = ref(false)

// 查看他人主页时的分页
const profileFollowerCurrentPage = ref(1)
const profileFollowerPageSize = 5

const paginatedProfileFollowers = computed(() => {
  const start = (profileFollowerCurrentPage.value - 1) * profileFollowerPageSize
  return profileFollowers.value.slice(start, start + profileFollowerPageSize)
})

const inheritorHeritageCurrentPage = ref(1)
const inheritorHeritagePageSize = 6

const paginatedInheritorHeritages = computed(() => {
  const start = (inheritorHeritageCurrentPage.value - 1) * inheritorHeritagePageSize
  return inheritorHeritages.value.slice(start, start + inheritorHeritagePageSize)
})

const inheritorExperienceCurrentPage = ref(1)
const inheritorExperiencePageSize = 6

const paginatedInheritorExperiences = computed(() => {
  const start = (inheritorExperienceCurrentPage.value - 1) * inheritorExperiencePageSize
  return inheritorExperiences.value.slice(start, start + inheritorExperiencePageSize)
})

// 头像列表供选择
const avatarSeeds = ['heritage','felix','coco','mimi','sunny','panda','jade','lotus','orchid','bamboo','dragon','phoenix']
const showAvatarPicker = ref(false)
const currentAvatar = ref('')

// 设置弹窗
const showSettingsModal = ref(false)
const showAvatarGrid = ref(false)
const settingsSaving = ref(false)
const editForm = ref({
  avatar: '',
  nickname: '',
  bio: '',
  phone: '',
  email: '',
  oldPwd: '',
  newPwd: '',
  confirmPwd: ''
})

function openSettings() {
  editForm.value = {
    avatar: currentAvatar.value || userStore.userInfo?.avatar || '',
    nickname: userStore.userInfo?.nickname || userStore.userInfo?.username || '',
    bio: userBio.value,
    phone: userStore.userInfo?.phone || '',
    email: userStore.userInfo?.email || '',
    oldPwd: '',
    newPwd: '',
    confirmPwd: ''
  }
  showAvatarGrid.value = false
  showSettingsModal.value = true
}

function selectSettingsAvatar(seed) {
  const url = `https://api.dicebear.com/9.x/adventurer/svg?seed=${seed}`
  editForm.value.avatar = url
}

async function handleSettingsAvatarUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  try {
    const res = await uploadFileWithProgress(file)
    const url = res.data?.url || res.url || ''
    if (!url) { message.error('头像上传失败'); return }
    editForm.value.avatar = url
  } catch (err) {
    console.error('头像上传失败:', err)
    message.error('头像上传失败，请稍后重试')
  }
}

async function saveSettings() {
  const updates = {}
  const tasks = []

  // 头像
  if (editForm.value.avatar && editForm.value.avatar !== (currentAvatar.value || userStore.userInfo?.avatar)) {
    currentAvatar.value = editForm.value.avatar
    setUserStorage('yiyun_avatar', editForm.value.avatar)
    updates.avatar = editForm.value.avatar
  }

  // 昵称
  if (editForm.value.nickname.trim() && editForm.value.nickname.trim() !== (userStore.userInfo?.nickname || userStore.userInfo?.username)) {
    updates.nickname = editForm.value.nickname.trim()
  }

  // 简介
  if (editForm.value.bio.trim() && editForm.value.bio.trim() !== userBio.value) {
    userBio.value = editForm.value.bio.trim()
    setUserStorage('yiyun_bio', editForm.value.bio.trim())
    updates.bio = editForm.value.bio.trim()
  }

  // 手机号
  if (editForm.value.phone.trim() && editForm.value.phone.trim() !== userStore.userInfo?.phone) {
    const phonePattern = /^1\d{10}$/
    if (!phonePattern.test(editForm.value.phone.trim())) {
      message.warning('请输入正确的11位手机号')
      return
    }
    updates.phone = editForm.value.phone.trim()
  }

  // 邮箱
  if (editForm.value.email.trim() && editForm.value.email.trim() !== userStore.userInfo?.email) {
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailPattern.test(editForm.value.email.trim())) {
      message.warning('请输入正确的邮箱地址')
      return
    }
    updates.email = editForm.value.email.trim()
  }

  // 密码
  if (editForm.value.newPwd) {
    if (editForm.value.newPwd.length < 6) {
      message.warning('新密码至少6位')
      return
    }
    if (editForm.value.newPwd !== editForm.value.confirmPwd) {
      message.warning('两次密码不一致')
      return
    }
    if (!editForm.value.oldPwd) {
      message.warning('请输入原密码')
      return
    }
  }

  settingsSaving.value = true
  try {
    if (Object.keys(updates).length > 0) {
      await updateUserInfo(updates)
      if (userStore.userInfo) {
        userStore.persistSession(userStore.token, { ...userStore.userInfo, ...updates })
      }
    }

    if (editForm.value.newPwd) {
      const { changePassword } = await import('../api/user')
      await changePassword({
        oldPassword: editForm.value.oldPwd,
        newPassword: editForm.value.newPwd
      })
    }

    message.success('设置已保存')
    showSettingsModal.value = false
  } catch (error) {
    const errMsg = error?.response?.data?.msg || error?.response?.data?.message || error?.message || '保存失败'
    message.error(errMsg)
  } finally {
    settingsSaving.value = false
  }
}

const userAvatar = computed(() => {
  if (!isOwnProfile.value && profileUser.value) {
    return profileUser.value.avatar || `https://api.dicebear.com/9.x/adventurer/svg?seed=${profileUser.value.username || profileUser.value.id}`
  }
  return currentAvatar.value || userStore.userInfo?.avatar || 'https://api.dicebear.com/9.x/adventurer/svg?seed=heritage'
})
const userName = computed(() => {
  if (!isOwnProfile.value && profileUser.value) {
    return profileUser.value.nickname || profileUser.value.username || '文化旅行者'
  }
  return userStore.userInfo?.nickname || userStore.displayName || '文化旅行者'
})
const displayRole = computed(() => {
  if (!isOwnProfile.value && profileUser.value) {
    const role = profileUser.value.role
    if (role === 'INHERITOR') return '🏅 非遗传承人'
    if (role === 'ADMIN') return '🛡️ 平台管理员'
    return '🏛️ 文化旅行者 · 非遗守护人'
  }
  return '🏛️ 文化旅行者 · 非遗守护人'
})
const displayBio = computed(() => {
  if (!isOwnProfile.value && profileUser.value) {
    return profileUser.value.bio || '在文化的长河中，每一次探索都是一次心灵的归乡'
  }
  return userBio.value
})

// 个人简介
const userBio = ref('在文化的长河中，每一次探索都是一次心灵的归乡')
const isEditingBio = ref(false)
const editBioText = ref('')

// 昵称编辑
const isEditingName = ref(false)
const editNameText = ref('')

// 手机号编辑
const isEditingPhone = ref(false)
const editPhoneText = ref('')

// 邮箱编辑
const isEditingEmail = ref(false)
const editEmailText = ref('')

function startEditName() {
  editNameText.value = userName.value
  isEditingName.value = true
}

async function saveName() {
  if (!editNameText.value.trim()) {
    message.warning('昵称不能为空')
    return
  }
  
  try {
    // 同步到后端
    const res = await updateUserInfo({ nickname: editNameText.value.trim() })
    
    // 更新本地状态
    if (userStore.userInfo) {
      userStore.persistSession(userStore.token, { ...userStore.userInfo, nickname: editNameText.value.trim() })
    }
    isEditingName.value = false
    message.success('昵称已更新')
  } catch (error) {
    console.error('❌ 更新昵称失败:', error)
    message.error('更新昵称失败')
  }
}

function cancelEditName() {
  isEditingName.value = false
  editNameText.value = ''
}

// 手机号编辑
function startEditPhone() {
  editPhoneText.value = userStore.userInfo?.phone || ''
  isEditingPhone.value = true
}

async function savePhone() {
  if (!editPhoneText.value.trim()) {
    message.warning('手机号不能为空')
    return
  }
  
  const phonePattern = /^1\d{10}$/
  if (!phonePattern.test(editPhoneText.value.trim())) {
    message.warning('请输入正确的11位手机号')
    return
  }
  
  try {
    await updateUserInfo({ phone: editPhoneText.value.trim() })
    
    if (userStore.userInfo) {
      userStore.persistSession(userStore.token, { ...userStore.userInfo, phone: editPhoneText.value.trim() })
    }
    
    isEditingPhone.value = false
    message.success('手机号已更新')
  } catch (error) {
    console.error(' 更新手机号失败:', error)
    const errorMsg = error.response?.data?.message || error.message || '更新手机号失败'
    message.error(errorMsg)
  }
}

function cancelEditPhone() {
  isEditingPhone.value = false
  editPhoneText.value = ''
}

// 邮箱编辑
function startEditEmail() {
  editEmailText.value = userStore.userInfo?.email || ''
  isEditingEmail.value = true
}

async function saveEmail() {
  if (!editEmailText.value.trim()) {
    message.warning('邮箱不能为空')
    return
  }
  
  const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailPattern.test(editEmailText.value.trim())) {
    message.warning('请输入正确的邮箱地址')
    return
  }
  
  try {
    await updateUserInfo({ email: editEmailText.value.trim() })
    
    if (userStore.userInfo) {
      userStore.persistSession(userStore.token, { ...userStore.userInfo, email: editEmailText.value.trim() })
    }
    
    isEditingEmail.value = false
    message.success('邮箱已更新')
  } catch (error) {
    console.error(' 更新邮箱失败:', error)
    const errorMsg = error.response?.data?.message || error.message || '更新邮箱失败'
    message.error(errorMsg)
  }
}

function cancelEditEmail() {
  isEditingEmail.value = false
  editEmailText.value = ''
}

// 获取当前用户唯一标识
const userId = computed(() => userStore.userInfo?.id || userStore.userInfo?.username || 'default')

// 密码修改

// 编辑帖子相关
const editingPost = ref(null)
const editContent = ref('')
const editMedia = ref([])
const editTags = ref([])
const showEditUploadMenu = ref(false)
const showEditEmojiPicker = ref(false)
const showEditTagSelect = ref(false)

// 表情列表
const emojis = ['😀','😃','😄','😁','😆','😅','🤣','😂','🙂','😊','😇','🥰','😍','🤩','😘','😗','😚','😙','🥲','😋','😛','😜','🤪','😝','🤑','🤗','🤭','🤫','🤔','🤐','🤨','😐','😑','😶','😏','😒','🙄','😬','🤥','😌','😔','😪','🤤','😴','😷','🤒','🤕','🤢','🤮','🥵','🥶','🥴','😵','🤯','🤠','🥳','🥸','😎','🤓','🧐','😕','😟','🙁','☹️','😮','😯','😲','😳','🥺','😦','😧','😨','😰','😥','😢','😭','😱','😖','😣','😞','😓','😩','😫','🥱','😤','😡','😠','🤬','😈','👿','💀','☠️','💩','🤡','👹','👺','👻','👽','👾','🤖','🎃','😺','😸','😹','😻','😼','😽','🙀','😿','😾']

// 默认标签
const defaultTags = ['非遗文化', '传统工艺', '文化传承', '艺术鉴赏', '手工制作', '文化探索', '民俗风情', '历史古迹']

// 密码修改
const pwdForm = ref({ oldPwd: '', newPwd: '', confirmPwd: '' })
const pwdLoading = ref(false)

const orders = ref([])
const likes = ref([])
const orderLoading = ref(false)
const orderStatusFilter = ref('')
const likesLoading = ref(false)

// 收藏
const favorites = ref([])
const favoritesLoading = ref(false)
const favCurrentPage = ref(1)
const favPageSize = 6

const paginatedFavorites = computed(() => {
  const start = (favCurrentPage.value - 1) * favPageSize
  return favorites.value.slice(start, start + favPageSize)
})

// 我的关注列表
const following = ref([])
const followingLoading = ref(false)

// 我的粉丝列表
const followers = ref([])
const followersLoading = ref(false)

// 订单状态选项
const orderStatusOptions = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待审核' },
  { value: 'CONFIRMED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'COMPLETED', label: '已结束' },
  { value: 'CANCELLED', label: '已取消' }
]

// 分页配置
const currentPage = ref(1)
const pageSize = 3
const pagedOrders = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return visibleOrders.value.slice(start, start + pageSize)
})

// 重置分页当订单列表变化时
watch(orders, () => {
  currentPage.value = 1
})

async function loadOrders() {
  orderLoading.value = true
  try {
    const res = await getMyBookings(orderStatusFilter.value)
    orders.value = res.data || []
  } catch (error) {
    console.error(' 获取订单失败:', error)
  } finally {
    orderLoading.value = false
  }
}

async function loadLikes() {
  likesLoading.value = true
  try {
    const res = await getMyLikes()
    const likeRecords = res.data || []

    const heritagePromises = likeRecords
      .filter(item => item.targetType === 'HERITAGE')
      .map(async item => {
        try {
          const result = await getHeritageDetail(item.id)
          const heritage = result.data
          if (heritage) {
            return {
              id: heritage.id,
              name: heritage.name,
              category: heritage.category,
              region: heritage.region,
              img: heritage.imageUrl || heritage.image_url || '',
              likedAt: item.createdAt,
              unavailable: false
            }
          }
        } catch (err) {
          console.error('获取非遗详情失败:', err)
        }
        return {
          id: item.id,
          name: '项目 #' + item.id,
          category: '未知分类',
          region: '未知地区',
          img: '',
          likedAt: item.createdAt,
          unavailable: true
        }
      })

    const results = await Promise.all(heritagePromises)
    likes.value = results
  } catch (error) {
    console.error('获取点赞列表失败:', error)
    likes.value = []
  } finally {
    likesLoading.value = false
  }
}

// 点赞分页
const likeCurrentPage = ref(1)
const likePageSize = 6

const paginatedLikes = computed(() => {
  const start = (likeCurrentPage.value - 1) * likePageSize
  return likes.value.slice(start, start + likePageSize)
})

async function loadFollowing() {
  followingLoading.value = true
  try {
    const res = await getFollowing()
    following.value = (res.data || []).map(user => ({
      id: user.id,
      nickname: user.nickname || user.username,
      avatar: user.avatar || `https://api.dicebear.com/9.x/adventurer/svg?seed=${user.username}`,
      role: user.role
    }))
  } catch (error) {
    console.error(' 获取关注列表失败:', error)
    following.value = []
  } finally {
    followingLoading.value = false
  }
}

async function loadFollowers() {
  followersLoading.value = true
  try {
    const res = await getFollowers()
    followers.value = (res.data || []).map(user => ({
      id: user.id,
      nickname: user.nickname || user.username,
      avatar: user.avatar || `https://api.dicebear.com/9.x/adventurer/svg?seed=${user.username}`,
      role: user.role
    }))
  } catch (error) {
    console.error(' 获取粉丝列表失败:', error)
    followers.value = []
  } finally {
    followersLoading.value = false
  }
}

// 关注分页
const followCurrentPage = ref(1)
const followPageSize = 5

const paginatedFollowing = computed(() => {
  const start = (followCurrentPage.value - 1) * followPageSize
  return following.value.slice(start, start + followPageSize)
})

async function handleUnfollow(userId) {
  try {
    await unfollowUser(userId)
    message.success('已取消关注')
    await loadFollowing()
  } catch (error) {
    console.error(' 取消关注失败:', error)
    message.error('取消关注失败')
  }
}

// 粉丝分页
const followerCurrentPage = ref(1)
const followerPageSize = 5

const paginatedFollowers = computed(() => {
  const start = (followerCurrentPage.value - 1) * followerPageSize
  return followers.value.slice(start, start + followerPageSize)
})

async function handleRemoveFollower(userId) {
  try {
    await unfollowUser(userId)
    message.success('已移除粉丝')
    await loadFollowers()
  } catch (error) {
    console.error(' 移除粉丝失败:', error)
    message.error('移除粉丝失败')
  }
}

// 查看其他用户主页
async function loadUserProfile(id) {
  try {
    const [userRes, countsRes, followersRes, followingRes] = await Promise.all([
      getUserById(id),
      getUserFollowCounts(id),
      getUserFollowers(id),
      getUserFollowing(id)
    ])
    if (userRes.data) {
      profileUser.value = userRes.data
    }
    if (countsRes.data) {
      profileFollowCounts.value = countsRes.data
    }
    if (followersRes.data) {
      profileFollowers.value = followersRes.data.map(u => ({
        id: u.id,
        nickname: u.nickname || u.username,
        avatar: u.avatar || `https://api.dicebear.com/9.x/adventurer/svg?seed=${u.username || u.id}`,
        role: u.role
      }))
    }
    if (followingRes.data) {
      profileFollowing.value = followingRes.data.map(u => ({
        id: u.id,
        nickname: u.nickname || u.username,
        avatar: u.avatar || `https://api.dicebear.com/9.x/adventurer/svg?seed=${u.username || u.id}`,
        role: u.role
      }))
    }
  } catch (err) {
    console.error('加载用户信息失败:', err)
    message.error('用户不存在或加载失败')
  }

  try {
    const res = await checkFollow(id)
    isFollowing.value = res.data?.followed || false
  } catch {}

  // 如果被查看用户是传承人，加载传承人相关数据
  if (profileUser.value?.role === 'INHERITOR') {
    inheritorDataLoading.value = true
    try {
      const [certRes, postsRes, expRes] = await Promise.all([
        getInheritorPublicApplication(id).catch(err => {
          console.error('获取传承人认证信息失败:', err?.msg || err?.message || err)
          return { data: null }
        }),
        getInheritorPosts(id).catch(err => {
          console.error('获取传承人非遗项目失败:', err?.msg || err?.message || err)
          return { data: [] }
        }),
        getUserExperiences(id, { page: 1, size: 50 }).catch(err => {
          console.error('获取传承人活动失败:', err?.msg || err?.message || err)
          return { data: { records: [] } }
        })
      ])
      console.log('传承人认证信息响应:', certRes)
      inheritorCertInfo.value = certRes.data || null
      inheritorHeritages.value = (postsRes.data || []).map(h => ({
        id: h.id,
        name: h.name,
        category: h.category,
        region: h.region,
        img: h.imageUrl || h.image_url || '',
        status: h.status
      }))
      inheritorExperiences.value = ((expRes.data?.records || expRes.data || [])).map(e => ({
        id: e.id,
        name: e.name,
        type: e.type,
        location: e.location,
        price: e.price,
        img: e.imageUrl || '',
        status: e.status
      }))
    } catch {}
    inheritorDataLoading.value = false
  }
}

async function handleFollowOther() {
  if (!profileUser.value) return
  followLoading.value = true
  try {
    await followUser(profileUser.value.id)
    isFollowing.value = true
    profileFollowCounts.value.followerCount++
    message.success('已关注')
  } catch (err) {
    const errMsg = err?.response?.data?.msg || err?.response?.data?.message || '关注失败'
    message.error(errMsg)
  } finally {
    followLoading.value = false
  }
}

async function handleUnfollowOther() {
  if (!profileUser.value) return
  followLoading.value = true
  try {
    await unfollowUser(profileUser.value.id)
    isFollowing.value = false
    profileFollowCounts.value.followerCount = Math.max(0, profileFollowCounts.value.followerCount - 1)
    message.success('已取消关注')
  } catch (err) {
    message.error('取消关注失败')
  } finally {
    followLoading.value = false
  }
}

async function loadFavorites() {
  favoritesLoading.value = true
  try {
    const res = await getMyFavorites()
    favorites.value = (res.data || []).map(h => ({
      id: h.id,
      name: h.name,
      category: h.category,
      region: h.region,
      img: h.imageUrl || h.image_url || ''
    }))
  } catch (error) {
    console.error('获取收藏列表失败:', error)
    favorites.value = []
  } finally {
    favoritesLoading.value = false
  }
}

async function handleRemoveFavorite(heritageId) {
  try {
    await removeFavorite(heritageId)
    message.success('已取消收藏')
    await loadFavorites()
  } catch (error) {
    console.error('取消收藏失败:', error)
    message.error('取消收藏失败')
  }
}

async function handleUnlike(heritageId) {
  try {
    await toggleLike('HERITAGE', heritageId)
    message.success('已取消点赞')
    await loadLikes()
  } catch (error) {
    console.error(' 取消点赞失败:', error)
    message.error('取消点赞失败')
  }
}

function handleStatusFilterChange(status) {
  orderStatusFilter.value = status
  currentPage.value = 1
  loadOrders()
}

// 订单状态映射
function getOrderStatusLabel(status) {
  return ORDER_STATUS_MAP[status] || status
}

function getOrderStatusClass(status) {
  const classMap = {
    'PENDING': 'pending',
    'CONFIRMED': 'confirmed',
    'REJECTED': 'rejected',
    'COMPLETED': 'completed',
    'CANCELLED': 'cancelled'
  }
  return classMap[status] || 'pending'
}

onMounted(async () => {
  if (isOwnProfile.value) {
    // 从后端刷新用户信息，确保显示的是最新的数据
    try {
      const res = await getUserInfo()
      if (res.data) {
        userStore.persistSession(userStore.token, res.data)
        // 更新简介
        if (res.data.bio) {
          userBio.value = res.data.bio
        }
      }
    } catch (error) {
      console.error(' 刷新用户信息失败:', error)
    }

    if (route.query.tab) activeTab.value = route.query.tab
    currentAvatar.value = getUserStorage('yiyun_avatar', '')
    const savedBio = getUserStorage('yiyun_bio', '')
    if (savedBio && !userBio.value) userBio.value = savedBio

    await Promise.all([
      loadCreations(),
      loadOrders(),
      loadLikes(),
      loadFavorites(),
      loadFollowing(),
      loadFollowers(),
      loadCertificationStatus()
    ])
  } else {
    // 查看其他用户
    await loadUserProfile(route.params.id)
  }
})

watch(() => route.path, (newPath) => {
  if (newPath === '/profile') {
    loadCreations()
    loadOrders()
    loadLikes()
    loadFavorites()
    loadFollowing()
    loadFollowers()
  }
})

// 监听路由参数变化，在 /user/:id 之间切换时重新加载
watch(() => route.path + '_' + (route.params.id || ''), (newVal) => {
  const id = route.params.id
  if (id && !(route.path === '/profile' || String(id) === String(userStore.userInfo?.id))) {
    profileUser.value = null
    profileFollowCounts.value = { followerCount: 0, followingCount: 0 }
    profileFollowers.value = []
    profileFollowing.value = []
    inheritorCertInfo.value = null
    inheritorHeritages.value = []
    inheritorExperiences.value = []
    profileActiveTab.value = 'cert'
    isFollowing.value = false
    loadUserProfile(id)
  }
})

const visibleOrders = computed(() => {
  return orders.value.filter(o => o.status !== '已取消' && o.status !== '已拒绝')
})

// 创作作品列表（从AIGC作品API获取）
const creations = ref([])
const creationCurrentPage = ref(1)
const creationPageSize = ref(6)
const creationPreviewUrl = ref('')
const creationPreviewName = ref('')

const paginatedCreations = computed(() => {
  const start = (creationCurrentPage.value - 1) * creationPageSize.value
  return creations.value.slice(start, start + creationPageSize.value)
})

function previewCreationImage(item) {
  creationPreviewUrl.value = item.imageUrl
  creationPreviewName.value = item.title || '未命名作品'
}

function closeCreationPreview() {
  creationPreviewUrl.value = ''
  creationPreviewName.value = ''
}

function downloadCreationImage() {
  if (!creationPreviewUrl.value) return
  const a = document.createElement('a')
  a.href = creationPreviewUrl.value
  a.download = creationPreviewName.value || 'image'
  a.target = '_blank'
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

async function loadCreations() {
  try {
    const res = await getMyArtworks({ page: 1, size: 9999 })
    // 将AIGC作品转换为统一的创作格式
    const list = res.data?.records || res.data || []
    creations.value = (Array.isArray(list) ? list : []).map(artwork => ({
      id: artwork.id,
      title: artwork.title || '未命名作品',
      type: '图像',
      imageUrl: artwork.fileUrl,
      content: artwork.description || '',
      createdAt: formatDateTime(artwork.createdAt),
      metadata: artwork
    }))
  } catch (error) {
    console.error('获取创作失败:', error)
    creations.value = []
  }
}

// 格式化日期时间
function formatDateTime(dateTimeStr) {
  if (!dateTimeStr) return ''
  const date = new Date(dateTimeStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

async function handleDeleteCreation(item) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除作品"${item.title}"吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteArtwork(item.id)
        message.success('删除成功')
        if (paginatedCreations.value.length === 1 && creationCurrentPage.value > 1) {
          creationCurrentPage.value--
        }
        await loadCreations()
      } catch (error) {
        message.error('删除失败')
      }
    }
  })
}

function getCreationTypeLabel(type) {
  return AIGC_TYPE_MAP[type] || type
}

const stats = computed(() => {
  return {
    totalOrders: visibleOrders.value.length,
    totalSpent: visibleOrders.value.reduce((s, o) => s + Number(o.total || 0), 0),
    totalPeople: visibleOrders.value.reduce((s, o) => s + (o.people || 1), 0),
    creations: creations.value.length,
  }
})


function goToBooking(order) {
  if (order.workshop) {
    router.push(`/booking?keyword=${encodeURIComponent(order.workshop)}`)
  } else {
    router.push('/booking')
  }
}

// 取消预约相关
const showCancelModal = ref(false)
const cancelOrderItem = ref(null)

function openCancelModal(order) {
  cancelOrderItem.value = order
  showCancelModal.value = true
}

function closeCancelModal() {
  showCancelModal.value = false
  cancelOrderItem.value = null
}

async function confirmCancelOrder() {
  if (cancelOrderItem.value) {
    try {
      await cancelBooking(cancelOrderItem.value.id)
      message.success('取消成功，费用将自动退还至原支付账户')
      await loadOrders()
    } catch (error) {
      message.error('取消失败')
    }
  }
  closeCancelModal()
}

const ratingValues = ref({})
const ratingSubmitting = ref({})

async function submitRating(orderId) {
  const rating = ratingValues.value[orderId]
  if (!rating || rating === 0) {
    message.warning('请先选择评分')
    return
  }
  ratingSubmitting.value[orderId] = true
  try {
    await rateBooking(orderId, { rating })
    message.success('评分成功！')
    await loadOrders()
  } catch (error) {
    const errMsg = error.response?.data?.message || error.message || '评分失败'
    message.error(errMsg)
  } finally {
    ratingSubmitting.value[orderId] = false
  }
}

// 传承人认证相关
const certStatus = ref('NONE')
const certLoading = ref(false)
const certEditMode = ref(false)
const certificationInfo = ref({
  realName: '',
  idCard: '',
  heritage: '',
  level: '',
  region: '',
  years: '',
  bio: '',
  phone: ''
})

const certForm = ref({
  realName: '',
  idCard: '',
  heritage: '',
  level: '',
  region: '',
  years: '',
  bio: '',
  phone: ''
})

// 地区选择
const selectedProvince = ref('')
const selectedCity = ref('')
const selectedDistrict = ref('')

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

const fullRegion = computed(() => {
  const parts = [selectedProvince.value, selectedCity.value, selectedDistrict.value].filter(Boolean)
  return parts.join(' ')
})

function parseRegion(regionStr) {
  if (!regionStr) return
  const parts = regionStr.split(' ')
  if (parts.length >= 1) selectedProvince.value = parts[0]
  if (parts.length >= 2) selectedCity.value = parts[1]
  if (parts.length >= 3) selectedDistrict.value = parts[2]
}

watch(selectedProvince, () => { selectedCity.value = ''; selectedDistrict.value = '' })
watch(selectedCity, () => { selectedDistrict.value = '' })

const certFiles = ref([])  // { uid, name, size, url, status, progress, isImage, previewUrl, errorMsg }

// 文件大小格式化
function formatFileSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function handleCertUpload(e) {
  const files = Array.from(e.target.files)
  if (!files.length) return

  if (certFiles.value.length + files.length > 5) {
    message.warning('最多只能上传5个文件')
    e.target.value = ''
    return
  }

  for (const file of files) {
    // 校验文件类型
    const validTypes = ['image/', 'application/pdf']
    const isValid = validTypes.some(t => file.type.startsWith(t))
    if (!isValid) {
      message.warning(`文件 "${file.name}" 格式不支持，已跳过`)
      continue
    }

    // 校验大小 (10MB)
    if (file.size > 10 * 1024 * 1024) {
      message.warning(`文件 "${file.name}" 超过10MB，已跳过`)
      continue
    }

    const uid = Date.now() + '_' + Math.random().toString(36).slice(2, 8)
    const isImage = file.type.startsWith('image/')
    const entry = {
      uid,
      name: file.name,
      size: file.size,
      url: null,
      status: 'uploading',
      progress: 0,
      isImage,
      previewUrl: isImage ? URL.createObjectURL(file) : null,
      errorMsg: '',
    }
    certFiles.value.push(entry)

    // 立即上传
    uploadFileWithProgress(file, (percent) => {
      entry.progress = percent
    })
      .then((res) => {
        entry.url = res.data?.url || res.url || ''
        entry.status = 'done'
        entry.progress = 100
      })
      .catch((err) => {
        entry.status = 'error'
        entry.errorMsg = err?.response?.data?.msg || err?.message || '上传失败'
        console.error('文件上传失败:', file.name, err)
      })
  }

  e.target.value = ''
}

function removeCertFile(index) {
  const entry = certFiles.value[index]
  if (entry.previewUrl) {
    URL.revokeObjectURL(entry.previewUrl)
  }
  certFiles.value.splice(index, 1)
}

const heritageOptions = [
  { value: '京剧', label: '京剧' },
  { value: '昆曲', label: '昆曲' },
  { value: '古琴艺术', label: '古琴艺术' },
  { value: '书法', label: '书法' },
  { value: '剪纸', label: '剪纸' },
  { value: '刺绣', label: '刺绣' },
  { value: '陶瓷', label: '陶瓷' },
  { value: '景泰蓝', label: '景泰蓝' },
  { value: '皮影戏', label: '皮影戏' },
  { value: '中医针灸', label: '中医针灸' },
  { value: '二十四节气', label: '二十四节气' }
]

async function loadCertificationStatus() {
  if (userStore.isInheritor) {
    certStatus.value = 'APPROVED'
    // 先尝试从 getMyApplication 获取完整的申请数据（包含 idCard、skill 等）
    try {
      const res = await getMyApplication()
      if (res.data) {
        const app = res.data
        certificationInfo.value = {
          realName: app.realName || '',
          idCard: app.idCard || '',
          heritage: app.skill || '',
          level: app.level || '',
          region: app.region || '',
          years: app.years || '',
          bio: app.bio || app.description || '',
          phone: app.phone || '',
          certificates: app.certificates || '',
          rejectReason: app.rejectReason || ''
        }
        return
      }
    } catch {}
    // API 获取失败时，回退到 userStore 中的信息
    if (userStore.userInfo) {
      certificationInfo.value = {
        realName: userStore.userInfo.realName || userStore.userInfo.nickname || '',
        idCard: userStore.userInfo.idCard || '',
        heritage: userStore.userInfo.skill || '',
        level: userStore.userInfo.level || '',
        region: userStore.userInfo.region || '',
        years: userStore.userInfo.years || '',
        bio: userStore.userInfo.bio || '',
        phone: userStore.userInfo.phone || '',
        certificates: userStore.userInfo.certificates || ''
      }
    }
    return
  }
  try {
    const res = await getMyApplication()
    if (res.data) {
      const app = res.data
      certStatus.value = app.status || 'NONE'
      certificationInfo.value = {
        realName: app.realName || '',
        idCard: app.idCard || '',
        heritage: app.skill || '',
        level: app.level || '',
        region: app.region || '',
        years: app.years || '',
        bio: app.bio || app.description || '',
        phone: app.phone || '',
        certificates: app.certificates || '',
        rejectReason: app.rejectReason || ''
      }
      if (app.status === 'PENDING' || app.status === 'REJECTED') {
        certForm.value = {
          realName: app.realName || '',
          idCard: app.idCard || '',
          heritage: app.skill || '',
          level: app.level || '',
          region: app.region || '',
          years: app.years || '',
          bio: app.bio || '',
          phone: app.phone || ''
        }
        parseRegion(app.region || '')
      }
    }
  } catch (error) {
    certStatus.value = 'NONE'
  }
}

function maskIdCard(idCard) {
  if (!idCard || idCard.length < 10) return idCard
  return idCard.substring(0, 6) + '********' + idCard.substring(idCard.length - 4)
}

function enterCertEditMode() {
  certForm.value = {
    realName: certificationInfo.value.realName || '',
    idCard: certificationInfo.value.idCard || '',
    heritage: certificationInfo.value.heritage || '',
    level: certificationInfo.value.level || '',
    region: certificationInfo.value.region || '',
    years: certificationInfo.value.years || '',
    bio: certificationInfo.value.bio || '',
    phone: certificationInfo.value.phone || ''
  }
  certFiles.value = []
  certEditMode.value = true
}

function cancelCertEdit() {
  certEditMode.value = false
  certFiles.value.forEach(f => {
    if (f.previewUrl) URL.revokeObjectURL(f.previewUrl)
  })
  certFiles.value = []
}

async function submitCertEdit() {
  if (!certForm.value.level) {
    message.error('请选择传承级别')
    return
  }

  const uploading = certFiles.value.filter(f => f.status === 'uploading')
  if (uploading.length > 0) {
    message.warning('还有文件正在上传中，请稍候...')
    return
  }

  const errors = certFiles.value.filter(f => f.status === 'error')
  if (errors.length > 0) {
    message.warning('有文件上传失败，请移除后重新上传')
    return
  }

  const uploadedUrls = certFiles.value
    .filter(f => f.status === 'done' && f.url)
    .map(f => f.url)

  certLoading.value = true
  try {
    await applyInheritor({
      realName: certForm.value.realName.trim(),
      idCard: certForm.value.idCard.trim(),
      skill: certForm.value.heritage,
      level: certForm.value.level,
      region: fullRegion.value,
      years: certForm.value.years,
      bio: certForm.value.bio,
      phone: certForm.value.phone,
      certificates: uploadedUrls.length > 0 ? JSON.stringify(uploadedUrls) : undefined
    })

    certificationInfo.value = {
      ...certificationInfo.value,
      realName: certForm.value.realName.trim(),
      idCard: certForm.value.idCard.trim(),
      heritage: certForm.value.heritage,
      level: certForm.value.level,
      region: fullRegion.value,
      years: certForm.value.years,
      bio: certForm.value.bio,
      phone: certForm.value.phone
    }

    message.success('认证信息更新成功')
    certEditMode.value = false
    certFiles.value.forEach(f => {
      if (f.previewUrl) URL.revokeObjectURL(f.previewUrl)
    })
    certFiles.value = []
  } catch (error) {
    console.error('更新认证信息失败:', error)
    const errMsg = error.response?.data?.msg || error.response?.data?.message || '更新失败，请稍后重试'
    message.error(errMsg)
  } finally {
    certLoading.value = false
  }
}

async function submitCertification() {
  if (!certForm.value.realName.trim()) {
    message.error('请输入真实姓名')
    return
  }
  if (!certForm.value.idCard.trim() || !/^\d{17}[\dXx]$/.test(certForm.value.idCard.trim())) {
    message.error('请输入正确的18位身份证号')
    return
  }
  if (!certForm.value.heritage) {
    message.error('请选择传承项目')
    return
  }
  if (!certForm.value.level) {
    message.error('请选择传承级别')
    return
  }

  // 检查是否有正在上传的文件
  const uploading = certFiles.value.filter(f => f.status === 'uploading')
  if (uploading.length > 0) {
    message.warning('还有文件正在上传中，请稍候...')
    return
  }

  // 检查是否有上传失败的文件
  const errors = certFiles.value.filter(f => f.status === 'error')
  if (errors.length > 0) {
    message.warning('有文件上传失败，请移除后重新上传')
    return
  }

  // 收集已上传的文件URL
  const uploadedUrls = certFiles.value
    .filter(f => f.status === 'done' && f.url)
    .map(f => f.url)

  if (uploadedUrls.length === 0) {
    message.warning('请至少上传一份证明材料')
    return
  }

  certLoading.value = true
  try {
    await applyInheritor({
      realName: certForm.value.realName.trim(),
      idCard: certForm.value.idCard.trim(),
      skill: certForm.value.heritage,
      level: certForm.value.level,
      region: fullRegion.value,
      years: certForm.value.years,
      bio: certForm.value.bio,
      phone: certForm.value.phone,
      certificates: JSON.stringify(uploadedUrls)
    })

    certStatus.value = 'PENDING'
    certificationInfo.value = { ...certForm.value }

    message.success('认证申请提交成功！管理员将在3个工作日内审核')

    // 清理预览URL
    certFiles.value.forEach(f => {
      if (f.previewUrl) URL.revokeObjectURL(f.previewUrl)
    })
    certFiles.value = []
  } catch (error) {
    console.error('提交认证失败:', error)
    const errMsg = error.response?.data?.msg || error.response?.data?.message || '提交失败，请稍后重试'
    message.error(errMsg)
  } finally {
    certLoading.value = false
  }
}

function selectAvatar(seed) {
  const url = `https://api.dicebear.com/9.x/adventurer/svg?seed=${seed}`
  currentAvatar.value = url
  setUserStorage('yiyun_avatar', url)
  
  // 同步到后端
  if (userStore.userInfo) {
    userStore.persistSession(userStore.token, { ...userStore.userInfo, avatar: url })
    updateUserInfo({ avatar: url })
      .then(res => {
        // 头像更新成功
      })
      .catch(err => {
        console.error('❌ 更新头像失败:', err)
      })
  }
  
  showAvatarPicker.value = false
  message.success('头像已更新')
}

async function handleAvatarUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  try {
    const res = await uploadFileWithProgress(file)
    const url = res.data?.url || res.url || ''
    if (!url) { message.error('头像上传失败'); return }

    currentAvatar.value = url
    setUserStorage('yiyun_avatar', url)

    if (userStore.userInfo) {
      userStore.persistSession(userStore.token, { ...userStore.userInfo, avatar: url })
      updateUserInfo({ avatar: url }).catch(err => {
        console.error('更新头像失败:', err)
      })
    }

    showAvatarPicker.value = false
    message.success('头像已更新')
  } catch (err) {
    console.error('头像上传失败:', err)
    message.error('头像上传失败，请稍后重试')
  }
}

async function changePwd() {
  if (!pwdForm.value.oldPwd) { message.error('请输入原密码'); return }
  if (pwdForm.value.newPwd.length < 6) { message.error('新密码至少6位'); return }
  if (pwdForm.value.newPwd !== pwdForm.value.confirmPwd) { message.error('两次密码不一致'); return }
  
  pwdLoading.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.value.oldPwd,
      newPassword: pwdForm.value.newPwd
    })
    pwdForm.value = { oldPwd: '', newPwd: '', confirmPwd: '' }
    message.success('密码修改成功')
  } catch (error) {
    console.error(' 修改密码失败:', error)
    const errorMsg = error.response?.data?.message || error.message || '修改密码失败'
    message.error(errorMsg)
  } finally {
    pwdLoading.value = false
  }
}

// 编辑简介
function startEditBio() {
  editBioText.value = userBio.value
  isEditingBio.value = true
}

async function saveBio() {
  if (editBioText.value.trim()) {
    try {
      // 同步到后端
      await updateUserInfo({ bio: editBioText.value.trim() })
      
      // 更新本地状态
      userBio.value = editBioText.value
      setUserStorage('yiyun_bio', userBio.value)
      
      if (userStore.userInfo) {
        userStore.persistSession(userStore.token, { ...userStore.userInfo, bio: editBioText.value })
      }
      
      message.success('简介已更新')
    } catch (error) {
      console.error(' 更新简介失败:', error)
      message.error('更新简介失败')
    }
  }
  isEditingBio.value = false
}

function cancelEditBio() {
  isEditingBio.value = false
}
</script>

<template>
  <div class="profile-wrapper">
    <!-- 顶部装饰背景 -->
    <div class="profile-bg">
      <div class="bg-pattern"></div>
      <div class="bg-overlay"></div>
    </div>

    <div class="container profile-page">
      <!-- 返回按钮（仅查看他人时显示） -->
      <button v-if="!isOwnProfile" class="back-btn" @click="router.back()" title="返回上一页">
        <span>←</span> 返回
      </button>

      <!-- 用户卡片 -->
      <section class="profile-hero glass-card">
        <button v-if="isOwnProfile" class="hero-edit-btn" @click="openSettings"><span>✏️</span> 编辑</button>
        <button v-else class="hero-follow-btn" :class="{ following: isFollowing }" @click="isFollowing ? handleUnfollowOther() : handleFollowOther()" :disabled="followLoading">
          {{ followLoading ? '...' : isFollowing ? '已关注' : '+ 关注' }}
        </button>
        <div class="ph-content">
          <div class="avatar-wrap">
            <img :src="userAvatar" class="ph-avatar" />
            <span v-if="(isOwnProfile ? userStore.isInheritor : profileUser?.role === 'INHERITOR')" class="ph-cert-badge" title="已认证非遗传承人">🏅</span>
          </div>
          <div class="ph-main">
            <div class="ph-info">
              <h1>{{ userName }}</h1>
              <p class="ph-role">{{ displayRole }}</p>
              <p class="ph-motto">{{ displayBio }}</p>
            </div>
            <div v-if="isOwnProfile" class="ph-stats">
              <div class="phs" @click="activeTab='orders'"><strong>{{ stats.totalOrders }}</strong><span>预约</span></div>
              <div class="phs" @click="activeTab='favorites'"><strong>{{ favorites.length }}</strong><span>收藏</span></div>
              <div class="phs" @click="activeTab='likes'"><strong>{{ likes.length }}</strong><span>点赞</span></div>
              <div class="phs" @click="activeTab='following'"><strong>{{ following.length }}</strong><span>关注</span></div>
              <div class="phs" @click="activeTab='followers'"><strong>{{ followers.length }}</strong><span>粉丝</span></div>
            </div>
            <div v-else class="ph-stats">
              <div class="phs"><strong>{{ profileFollowCounts.followingCount }}</strong><span>关注</span></div>
              <div class="phs"><strong>{{ profileFollowCounts.followerCount }}</strong><span>粉丝</span></div>
              <template v-if="isInheritorProfile">
                <div class="phs" @click="profileActiveTab='heritages'"><strong>{{ inheritorHeritages.length }}</strong><span>非遗项目</span></div>
                <div class="phs" @click="profileActiveTab='experiences'"><strong>{{ inheritorExperiences.length }}</strong><span>发布的活动</span></div>
              </template>
            </div>
          </div>
        </div>
      </section>

      <!-- 查看普通用户时展示的粉丝列表 -->
      <div v-if="!isOwnProfile && !isInheritorProfile" class="profile-social-section">
        <section class="social-card glass-card">
          <h3 class="social-title">🌟 粉丝 ({{ profileFollowCounts.followerCount }})</h3>
          <div v-if="profileFollowers.length === 0" class="social-empty">暂无粉丝</div>
          <div v-else class="user-list">
            <div v-for="u in paginatedProfileFollowers" :key="u.id" class="user-card glass-card card-hover" @click="goToUserProfile(u.id)">
              <img :src="u.avatar" class="user-avatar" />
              <div class="user-info">
                <h4>{{ u.nickname }}</h4>
                <span class="user-role">{{ u.role === 'ADMIN' ? '管理员' : u.role === 'INHERITOR' ? '非遗传承人' : '文化旅行者' }}</span>
              </div>
            </div>
          </div>

          <Pagination v-model="profileFollowerCurrentPage" :total="profileFollowers.length" :page-size="5" />
        </section>
      </div>

      <!-- 传承人主页：二级导航 + 内容 -->
      <template v-if="!isOwnProfile && isInheritorProfile">
        <div class="profile-tabs">
          <button :class="{active: profileActiveTab==='cert'}" @click="profileActiveTab='cert'">🏅 认证信息</button>
          <button :class="{active: profileActiveTab==='followers'}" @click="profileActiveTab='followers'">🌟 粉丝</button>
          <button :class="{active: profileActiveTab==='heritages'}" @click="profileActiveTab='heritages'">📚 非遗项目</button>
          <button :class="{active: profileActiveTab==='experiences'}" @click="profileActiveTab='experiences'">🎯 发布的活动</button>
        </div>

        <!-- 认证信息 -->
        <section v-if="profileActiveTab==='cert'" class="tab-content">
          <div v-if="inheritorDataLoading" class="empty-state glass-card"><p>加载中...</p></div>
          <div v-else-if="inheritorCertInfo" class="certification-container glass-card">
            <div class="cert-badge-large" style="margin-bottom:20px">
              <span class="badge-icon">✅</span>
              <span class="badge-title">已认证传承人</span>
              <span class="badge-divider">|</span>
              <span class="badge-meta">{{ inheritorCertInfo.level }} · {{ inheritorCertInfo.skill }}</span>
            </div>
            <div class="cert-details">
              <div class="detail-row"><label>姓名：</label><span>{{ inheritorCertInfo.realName }}</span></div>
              <div class="detail-row"><label>传承技能：</label><span>{{ inheritorCertInfo.skill }}</span></div>
              <div class="detail-row"><label>传承级别：</label><span>{{ inheritorCertInfo.level }}</span></div>
              <div class="detail-row"><label>所在地区：</label><span>{{ inheritorCertInfo.region || '未填写' }}</span></div>
              <div class="detail-row"><label>从艺年限：</label><span>{{ inheritorCertInfo.years || '未填写' }}</span></div>
              <div v-if="inheritorCertInfo.description || inheritorCertInfo.bio" class="detail-row">
                <label>个人简介：</label><span>{{ inheritorCertInfo.bio || inheritorCertInfo.description }}</span>
              </div>
            </div>
          </div>
          <div v-else class="empty-state glass-card"><div class="empty-icon">🏅</div><p>暂无认证信息</p></div>
        </section>

        <!-- 粉丝 -->
        <section v-if="profileActiveTab==='followers'" class="tab-content">
          <div v-if="profileFollowers.length === 0" class="empty-state glass-card">
            <div class="empty-icon">🌟</div><p>暂无粉丝</p>
          </div>
          <div v-else class="user-list">
            <div v-for="u in paginatedProfileFollowers" :key="u.id" class="user-card glass-card card-hover" @click="goToUserProfile(u.id)">
              <img :src="u.avatar" class="user-avatar" />
              <div class="user-info">
                <h4>{{ u.nickname }}</h4>
                <span class="user-role">{{ u.role === 'ADMIN' ? '管理员' : u.role === 'INHERITOR' ? '非遗传承人' : '文化旅行者' }}</span>
              </div>
            </div>
          </div>

          <Pagination v-model="profileFollowerCurrentPage" :total="profileFollowers.length" :page-size="5" />
        </section>

        <!-- 非遗项目 -->
        <section v-if="profileActiveTab==='heritages'" class="tab-content">
          <div v-if="inheritorDataLoading" class="empty-state glass-card"><p>加载中...</p></div>
          <div v-else-if="!inheritorHeritages.length" class="empty-state glass-card">
            <div class="empty-icon">📚</div><p>暂无非遗项目</p>
          </div>
          <div v-else class="fav-grid">
            <div v-for="h in paginatedInheritorHeritages" :key="h.id" class="fav-card glass-card card-hover" @click="$router.push(`/heritage/${h.id}`)">
              <img v-if="h.img" :src="h.img" :alt="h.name" />
              <div v-else class="fav-placeholder">🏛️</div>
              <div class="fav-info">
                <h4>{{ h.name }}</h4>
                <span>{{ h.category }} · {{ h.region }}</span>
              </div>
            </div>
          </div>

          <Pagination v-model="inheritorHeritageCurrentPage" :total="inheritorHeritages.length" :page-size="6" />
        </section>

        <!-- 发布的活动 -->
        <section v-if="profileActiveTab==='experiences'" class="tab-content">
          <div v-if="inheritorDataLoading" class="empty-state glass-card"><p>加载中...</p></div>
          <div v-else-if="!inheritorExperiences.length" class="empty-state glass-card">
            <div class="empty-icon">🎯</div><p>暂无发布的活动</p>
          </div>
          <div v-else class="fav-grid">
            <div v-for="e in paginatedInheritorExperiences" :key="e.id" class="fav-card glass-card card-hover" @click="$router.push('/booking')">
              <img v-if="e.img" :src="e.img" :alt="e.name" />
              <div v-else class="fav-placeholder">🎯</div>
              <div class="fav-info">
                <h4>{{ e.name }}</h4>
                <span>{{ e.type === '线下工坊' ? '线下工坊' : e.type === '线上活动' ? '线上活动' : e.type }} · {{ e.location }}</span>
                <span v-if="e.price != null">¥{{ e.price }}</span>
              </div>
            </div>
          </div>

          <Pagination v-model="inheritorExperienceCurrentPage" :total="inheritorExperiences.length" :page-size="6" />
        </section>
      </template>

      <!-- 设置弹窗 -->
      <div v-if="showSettingsModal" class="modal-mask" @click.self="showSettingsModal = false">
        <div class="settings-modal glass-card">
          <div class="sm-head">
            <h3>账号设置</h3>
            <button class="modal-close" @click="showSettingsModal = false">✕</button>
          </div>
          <div class="sm-body">
            <!-- 左栏：个人资料 -->
            <div class="sm-col">
              <h4>📷 个人资料</h4>
              <div class="sm-avatar-section">
                <img :src="editForm.avatar" class="sm-avatar-preview" />
                <div class="sm-avatar-actions">
                  <label class="sm-upload-btn">
                    📁 上传图片
                    <input type="file" accept="image/*" hidden @change="handleSettingsAvatarUpload" />
                  </label>
                  <button class="sm-preset-btn" @click="showAvatarGrid = !showAvatarGrid">🎨 预设头像</button>
                </div>
              </div>
              <div v-if="showAvatarGrid" class="sm-avatar-grid">
                <img v-for="s in avatarSeeds" :key="s"
                  :src="`https://api.dicebear.com/9.x/adventurer/svg?seed=${s}`"
                  class="sm-avatar-option" :class="{ active: editForm.avatar.includes(s) }"
                  @click="selectSettingsAvatar(s)" />
              </div>
              <div class="sm-field">
                <label>用户名</label>
                <input v-model="editForm.nickname" placeholder="请输入用户名" />
              </div>
              <div class="sm-field">
                <label>个人简介</label>
                <textarea v-model="editForm.bio" rows="3" placeholder="请简要介绍自己"></textarea>
              </div>
            </div>

            <!-- 右栏：账号安全 -->
            <div class="sm-col">
              <h4>🔒 账号安全</h4>
              <div class="sm-field">
                <label>手机号</label>
                <input v-model="editForm.phone" placeholder="请输入11位手机号" />
              </div>
              <div class="sm-field">
                <label>邮箱</label>
                <input v-model="editForm.email" placeholder="请输入邮箱地址" />
              </div>
              <div class="sm-divider"></div>
              <div class="sm-field">
                <label>原密码</label>
                <input type="password" v-model="editForm.oldPwd" placeholder="不修改密码则留空" />
              </div>
              <div class="sm-field">
                <label>新密码</label>
                <input type="password" v-model="editForm.newPwd" placeholder="至少6位新密码" />
              </div>
              <div class="sm-field">
                <label>确认密码</label>
                <input type="password" v-model="editForm.confirmPwd" placeholder="再次输入新密码" />
              </div>
            </div>
          </div>
          <div class="sm-footer">
            <button class="btn-ghost" @click="showSettingsModal = false">取消</button>
            <button class="btn-primary" @click="saveSettings" :disabled="settingsSaving">
              {{ settingsSaving ? '保存中...' : '保存设置' }}
            </button>
          </div>
        </div>
      </div>


      <!-- 标签页（仅自己可见） -->
      <div v-if="isOwnProfile" class="profile-tabs">
        <button :class="{active: activeTab==='orders'}" @click="activeTab='orders'">🎫 我的预约</button>
        <button :class="{active: activeTab==='favorites'}" @click="activeTab='favorites'">⭐ 我的收藏</button>
        <button :class="{active: activeTab==='likes'}" @click="activeTab='likes'">👍 我的点赞</button>
        <button :class="{active: activeTab==='following'}" @click="activeTab='following'">👥 我的关注</button>
        <button :class="{active: activeTab==='followers'}" @click="activeTab='followers'">🌟 我的粉丝</button>
        <button :class="{active: activeTab==='creations'}" @click="activeTab='creations'">🎨 我的创作</button>
        <button :class="{active: activeTab==='certification'}" @click="activeTab='certification'">🏅 传承人认证</button>
      </div>

      <!-- 我的创作 -->
      <section v-if="isOwnProfile && activeTab==='creations'" class="tab-content">
        <!-- 空状态 -->
        <div v-if="!creations.length" class="empty-state glass-card">
          <div class="empty-icon">🎨</div>
          <p>暂无创作作品</p>
          <button class="btn-primary" @click="$router.push('/ai-studio')">去AIGC工坊创作</button>
        </div>

        <!-- 创作画廊网格 -->
        <div v-else class="gallery-grid">
          <div v-for="item in paginatedCreations" :key="item.id" class="gallery-item glass-card card-hover">
            <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.title" @click="previewCreationImage(item)" />
            <div v-else class="gallery-placeholder">🎨</div>
            <div class="gallery-item-info">
              <div class="gallery-item-meta">
                <span class="type-badge">{{ getCreationTypeLabel(item.type) }}</span>
                <span v-if="item.metadata?.heritageName" class="heritage-badge">{{ item.metadata.heritageName }}</span>
              </div>
              <div class="gallery-item-desc">{{ item.title }}</div>
              <div class="gallery-item-time">{{ item.createdAt }}</div>
              <div class="gallery-item-actions">
                <button class="action-btn" @click="$router.push('/ai-studio')">✏️ 查看</button>
                <button class="action-btn" @click="handleDeleteCreation(item)">🗑️ 删除</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <Pagination v-model="creationCurrentPage" :total="creations.length" :page-size="6" />
      </section>

      <!-- 订单列表 -->
      <section v-if="isOwnProfile && activeTab==='orders'" class="tab-content">
        <!-- 状态筛选 -->
        <div class="order-filter">
          <div class="filter-label">状态筛选：</div>
          <div class="filter-buttons">
            <button 
              v-for="opt in orderStatusOptions" 
              :key="opt.value"
              :class="['filter-btn', { active: orderStatusFilter === opt.value }]"
              @click="handleStatusFilterChange(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>
        
        <div v-if="orderLoading" class="empty-state glass-card">
          <p>订单加载中...</p>
        </div>
        <div v-else-if="!visibleOrders.length" class="empty-state glass-card">
          <div class="empty-icon">🎫</div>
          <p>暂无预约订单</p>
          <button class="btn-primary" @click="$router.push('/booking')">去预约体验</button>
        </div>
        <div v-for="o in pagedOrders" :key="o.orderNo" class="order-card glass-card" @click="goToBooking(o)">
          <div class="oc-head">
            <span class="oc-no">{{ o.orderNo }}</span>
            <span class="oc-status" :class="getOrderStatusClass(o.status)">{{ getOrderStatusLabel(o.status) }}</span>
          </div>
          <div class="oc-body">
            <h3>{{ o.workshop }}</h3>
            <div class="oc-meta">
              <span>📍 {{ o.location }}</span>
              <span>📅 {{ o.date }} {{ o.time }}</span>
            </div>
            <div class="oc-info">
              <span>👤 联系人：{{ o.contactName }}</span>
              <span>📱 {{ o.contactPhone }}</span>
            </div>
            <div class="oc-info">
              <span>👥 预约人数：{{ o.people || 1 }} 人</span>
              <span>✅ 已使用：{{ o.usedPeople || 0 }} 人</span>
            </div>
          </div>
          <div v-if="o.status === 'COMPLETED'" class="oc-rating">
            <template v-if="o.rating != null">
              <span class="rate-label">已评分：</span>
              <StarRating :model-value="Number(o.rating)" :disabled="true" :size="16" />
            </template>
            <template v-else>
              <span class="rate-label">评分：</span>
              <StarRating v-model="ratingValues[o.id]" :size="18" />
              <button class="btn-primary rate-submit" :disabled="ratingSubmitting[o.id]" @click.stop="submitRating(o.id)">
                {{ ratingSubmitting[o.id] ? '提交中...' : '提交评分' }}
              </button>
            </template>
          </div>
          <div class="oc-foot">
            <span class="oc-price">¥{{ o.total }}</span>
            <span class="oc-time">{{ o.createdAt }}</span>
            <button v-if="o.status === 'PENDING' || o.status === 'CONFIRMED'" class="btn-ghost cancel-btn" @click.stop="openCancelModal(o)">取消预约</button>
          </div>
        </div>

        <Pagination v-if="visibleOrders.length > 3" v-model="currentPage" :total="visibleOrders.length" :page-size="3" />
      </section>

      <!-- 收藏 -->
      <section v-if="isOwnProfile && activeTab==='favorites'" class="tab-content">
        <div v-if="favoritesLoading" class="empty-state glass-card">
          <p>加载中...</p>
        </div>
        <div v-else-if="!favorites.length" class="empty-state glass-card">
          <div class="empty-icon">⭐</div>
          <p>暂无收藏</p>
          <button class="btn-primary" @click="$router.push('/explore')">去探索非遗</button>
        </div>
        <div v-else class="fav-grid">
          <div v-for="f in paginatedFavorites" :key="f.id" class="fav-card glass-card card-hover" @click="$router.push(`/heritage/${f.id}`)">
            <img v-if="f.img" :src="f.img" :alt="f.name" />
            <div v-else class="fav-placeholder">🏛️</div>
            <div class="fav-info">
              <h4>{{ f.name }}</h4>
              <span>{{ f.category }} · {{ f.region }}</span>
            </div>
            <button class="unlike-btn" @click.stop="handleRemoveFavorite(f.id)">取消收藏</button>
          </div>
        </div>

        <Pagination v-model="favCurrentPage" :total="favorites.length" :page-size="6" />
      </section>

      <!-- 点赞 -->
      <section v-if="isOwnProfile && activeTab==='likes'" class="tab-content">
        <div v-if="likesLoading" class="empty-state glass-card">
          <p>加载中...</p>
        </div>
        <div v-else-if="!likes.length" class="empty-state glass-card">
          <div class="empty-icon">👍</div>
          <p>暂无点赞</p>
        </div>
        <div v-else class="fav-grid">
          <div v-for="f in paginatedLikes" :key="f.id" class="fav-card glass-card card-hover" :class="{ unavailable: f.unavailable }" @click="$router.push(`/heritage/${f.id}`)">
            <img v-if="f.img" :src="f.img" :alt="f.name" />
            <div v-else class="fav-placeholder">🏛️</div>
            <div class="fav-info">
              <h4>{{ f.name }}</h4>
              <span>{{ f.category }} · {{ f.region }}</span>
              <span v-if="f.unavailable" class="unavailable-tag">已下架或审核中</span>
            </div>
            <button class="unlike-btn" @click.stop="handleUnlike(f.id)">取消点赞</button>
          </div>
        </div>

        <Pagination v-model="likeCurrentPage" :total="likes.length" :page-size="6" />
      </section>

      <!-- 我的关注 -->
      <section v-if="isOwnProfile && activeTab==='following'" class="tab-content">
        <div v-if="followingLoading" class="empty-state glass-card">
          <p>加载中...</p>
        </div>
        <div v-else-if="!following.length" class="empty-state glass-card">
          <div class="empty-icon">👥</div>
          <p>暂无关注的用户</p>
        </div>
        <div v-else class="user-list">
          <div v-for="u in paginatedFollowing" :key="u.id" class="user-card glass-card card-hover" @click="goToUserProfile(u.id)">
            <img :src="u.avatar" class="user-avatar" />
            <div class="user-info">
              <h4>{{ u.nickname }}</h4>
              <span class="user-role">{{ u.role === 'ADMIN' ? '管理员' : u.role === 'INHERITOR' ? '非遗传承人' : '文化旅行者' }}</span>
            </div>
            <button class="btn-ghost unfollow-btn" @click.stop="handleUnfollow(u.id)">取消关注</button>
          </div>
        </div>

        <Pagination v-model="followCurrentPage" :total="following.length" :page-size="5" />
      </section>

      <!-- 我的粉丝 -->
      <section v-if="isOwnProfile && activeTab==='followers'" class="tab-content">
        <div v-if="followersLoading" class="empty-state glass-card">
          <p>加载中...</p>
        </div>
        <div v-else-if="!followers.length" class="empty-state glass-card">
          <div class="empty-icon">🌟</div>
          <p>暂无粉丝</p>
        </div>
        <div v-else class="user-list">
          <div v-for="u in paginatedFollowers" :key="u.id" class="user-card glass-card card-hover" @click="goToUserProfile(u.id)">
            <img :src="u.avatar" class="user-avatar" />
            <div class="user-info">
              <h4>{{ u.nickname }}</h4>
              <span class="user-role">{{ u.role === 'ADMIN' ? '管理员' : u.role === 'INHERITOR' ? '非遗传承人' : '文化旅行者' }}</span>
            </div>
            <button class="btn-ghost remove-btn" @click.stop="handleRemoveFollower(u.id)">移除粉丝</button>
          </div>
        </div>

        <Pagination v-model="followerCurrentPage" :total="followers.length" :page-size="5" />
      </section>


      <!-- 传承人认证 -->
      <section v-if="isOwnProfile && activeTab==='certification'" class="tab-content">
        <div class="certification-container glass-card">
          <div class="cert-header">
            <h2>🏅 非遗传承人认证</h2>
            <p class="cert-desc">申请成为非遗传承人，展示您的专业身份和技艺传承</p>
          </div>

          <!-- 已认证状态 -->
          <div v-if="certStatus === 'APPROVED'" class="certified-status">
            <div class="cert-badge-large">
              <span class="badge-icon">✅</span>
              <span class="badge-title">已认证传承人</span>
              <span class="badge-divider">|</span>
              <span class="badge-meta">{{ certificationInfo.level }} · {{ certificationInfo.heritage }}</span>
              <span class="badge-divider" v-if="certificationInfo.certifiedAt">|</span>
              <span class="badge-meta" v-if="certificationInfo.certifiedAt">认证时间：{{ certificationInfo.certifiedAt }}</span>
            </div>

            <!-- 查看模式 -->
            <div v-if="!certEditMode" class="cert-details">
              <div class="detail-row">
                <label>姓名：</label>
                <span>{{ certificationInfo.realName }}</span>
              </div>
              <div class="detail-row">
                <label>身份证号：</label>
                <span>{{ maskIdCard(certificationInfo.idCard) }}</span>
              </div>
              <div class="detail-row">
                <label>传承技能：</label>
                <span>{{ certificationInfo.heritage }}</span>
              </div>
              <div class="detail-row">
                <label>传承级别：</label>
                <span>{{ certificationInfo.level }}</span>
              </div>
              <div class="detail-row">
                <label>联系方式：</label>
                <span>{{ certificationInfo.phone || '未填写' }}</span>
              </div>
              <div class="detail-row">
                <label>佐证材料：</label>
                <span>{{ certificationInfo.certificates ? '已上传' : '已提交' }}</span>
              </div>
            </div>

            <!-- 编辑模式 -->
            <div v-else class="cert-edit-form">
              <h4 style="margin-bottom:16px;">编辑认证信息</h4>

              <div class="form-group">
                <label>真实姓名</label>
                <input type="text" :value="certForm.realName" disabled />
              </div>

              <div class="form-group">
                <label>身份证号</label>
                <input type="text" :value="certForm.idCard" disabled />
              </div>

              <div class="form-group">
                <label>传承技能</label>
                <input type="text" :value="certForm.heritage" disabled />
              </div>

              <div class="form-group">
                <label>传承级别 <span class="required">*</span></label>
                <select v-model="certForm.level">
                  <option value="">请选择级别</option>
                  <option value="国家级">国家级</option>
                  <option value="省级">省级</option>
                  <option value="市级">市级</option>
                  <option value="县级">县级</option>
                </select>
              </div>

              <div class="form-group">
                <label>联系方式 <span class="required">*</span></label>
                <input type="text" v-model="certForm.phone" placeholder="请输入手机号" maxlength="11" />
              </div>

              <div class="form-group">
                <label>佐证材料 <span class="required">*</span></label>
                <p class="form-hint">上传资质证书、技艺作品等证明材料（图片或PDF），留空则保留原有材料</p>
                <div class="upload-area" :class="{ 'has-files': certFiles.length > 0 }">
                  <label class="upload-trigger">
                    <span class="upload-icon">📎</span>
                    <span>点击选择文件</span>
                    <input type="file" accept="image/*,.pdf" multiple @change="handleCertUpload" :disabled="certLoading" hidden />
                  </label>
                  <span class="upload-tip">支持图片、PDF格式，单文件 ≤ 10MB，最多5个文件</span>
                </div>
                <div v-if="certFiles.length > 0" class="cert-file-list">
                  <div v-for="(file, index) in certFiles" :key="file.uid" class="cert-file-item" :class="'status-' + file.status">
                    <div class="file-thumb">
                      <img v-if="file.isImage && file.previewUrl" :src="file.previewUrl" alt="预览" />
                      <span v-else-if="file.isImage && file.url" class="file-thumb-img" :style="{ backgroundImage: 'url(' + file.url + ')' }"></span>
                      <span v-else class="file-icon-pdf">📕</span>
                    </div>
                    <div class="file-info">
                      <span class="file-name">{{ file.name }}</span>
                      <span class="file-size">{{ formatFileSize(file.size) }}</span>
                      <div v-if="file.status === 'uploading'" class="upload-progress">
                        <div class="progress-bar"><div class="progress-fill" :style="{ width: file.progress + '%' }"></div></div>
                        <span class="progress-text">{{ file.progress }}%</span>
                      </div>
                      <span v-else-if="file.status === 'done'" class="file-status-done">✅ 已上传</span>
                      <span v-else-if="file.status === 'error'" class="file-status-error">❌ {{ file.errorMsg || '上传失败' }}</span>
                    </div>
                    <button class="file-remove" @click="removeCertFile(index)" :disabled="certLoading" title="移除文件">✕</button>
                  </div>
                </div>
              </div>

              <div class="form-actions" style="margin-top:24px;">
                <button class="btn-ghost" @click="cancelCertEdit">取消</button>
                <button class="btn-primary" :disabled="certLoading" @click="submitCertEdit" style="margin-left:12px;">
                  {{ certLoading ? '提交中...' : '保存修改' }}
                </button>
              </div>
            </div>

            <div class="cert-actions" v-if="!certEditMode">
              <button class="btn-primary" style="margin-bottom:16px" @click="enterCertEditMode">✏️ 编辑认证信息</button>
              <h4>传承人管理入口</h4>
              <div class="action-links">
                <button class="action-link-btn" @click="$router.push('/inheritor/heritages')">📚 内容管理</button>
                <button class="action-link-btn" @click="$router.push('/inheritor/experiences')">🎯 体验管理</button>
                <button class="action-link-btn" @click="$router.push('/inheritor/bookings')">📋 预约管理</button>
              </div>
            </div>
          </div>

          <!-- 待审核状态 -->
          <div v-else-if="certStatus === 'PENDING'" class="cert-pending">
            <div class="status-card pending">
              <span class="status-icon">⏳</span>
              <div class="status-info">
                <h3>审核中</h3>
                <p>您的认证申请已提交，正在等待管理员审核，请耐心等待</p>
                <p class="status-hint">管理员将在3个工作日内完成审核</p>
              </div>
            </div>
            <div class="cert-details" style="margin-top:20px">
              <div class="detail-row"><label>姓名：</label><span>{{ certificationInfo.realName }}</span></div>
              <div class="detail-row"><label>身份证号：</label><span>{{ maskIdCard(certificationInfo.idCard) }}</span></div>
              <div class="detail-row"><label>传承技能：</label><span>{{ certificationInfo.heritage }}</span></div>
              <div class="detail-row"><label>传承级别：</label><span>{{ certificationInfo.level }}</span></div>
              <div class="detail-row"><label>联系方式：</label><span>{{ certificationInfo.phone }}</span></div>
              <div class="detail-row"><label>所在地区：</label><span>{{ certificationInfo.region }}</span></div>
              <div class="detail-row"><label>从艺年限：</label><span>{{ certificationInfo.years }}</span></div>
              <div class="detail-row"><label>个人简介：</label><span>{{ certificationInfo.bio }}</span></div>
            </div>
          </div>

          <!-- 已驳回状态 -->
          <div v-else-if="certStatus === 'REJECTED'" class="cert-rejected">
            <div class="status-card rejected">
              <span class="status-icon">❌</span>
              <div class="status-info">
                <h3>审核未通过</h3>
                <p v-if="certificationInfo.rejectReason">驳回原因：{{ certificationInfo.rejectReason }}</p>
                <p v-else>您的认证申请未通过审核，请修改信息后重新提交</p>
              </div>
            </div>
            <button class="btn-primary" style="margin-top:16px" @click="certStatus = 'NONE'">修改信息重新提交</button>
          </div>

          <!-- 未认证表单 -->
          <div v-else class="cert-form">
            <div class="status-card none" style="margin-bottom:20px">
              <span class="status-icon">📝</span>
              <div class="status-info">
                <p>您还未提交传承人认证申请，填写以下信息后点击提交即可</p>
              </div>
            </div>

            <div class="form-group">
              <label>真实姓名 <span class="required">*</span></label>
              <input type="text" v-model="certForm.realName" placeholder="请输入真实姓名" />
            </div>

            <div class="form-group">
              <label>身份证号 <span class="required">*</span></label>
              <input type="text" v-model="certForm.idCard" placeholder="请输入18位身份证号" maxlength="18" />
              <p class="form-hint">身份证号仅用于身份核验，平台将严格保密</p>
            </div>

            <div class="form-group">
              <label>传承技能 <span class="required">*</span></label>
              <select v-model="certForm.heritage">
                <option value="">请选择传承项目</option>
                <option v-for="h in heritageOptions" :key="h.value" :value="h.value">{{ h.label }}</option>
              </select>
            </div>

            <div class="form-group">
              <label>传承级别 <span class="required">*</span></label>
              <select v-model="certForm.level">
                <option value="">请选择级别</option>
                <option value="国家级">国家级</option>
                <option value="省级">省级</option>
                <option value="市级">市级</option>
                <option value="县级">县级</option>
              </select>
            </div>

            <div class="form-group">
              <label>联系方式 <span class="required">*</span></label>
              <input type="text" v-model="certForm.phone" placeholder="请输入手机号" maxlength="11" />
            </div>

            <div class="form-group">
              <label>所在地区 <span class="required">*</span></label>
              <div class="region-selects">
                <select v-model="selectedProvince" class="region-select">
                  <option value="">请选择省</option>
                  <option v-for="p in provinces" :key="p.value" :value="p.value">{{ p.label }}</option>
                </select>
                <select v-model="selectedCity" class="region-select" :disabled="!selectedProvince">
                  <option value="">请选择市</option>
                  <option v-for="c in cities" :key="c.value" :value="c.value">{{ c.label }}</option>
                </select>
                <select v-model="selectedDistrict" class="region-select" :disabled="!selectedCity">
                  <option value="">请选择区</option>
                  <option v-for="d in districts" :key="d.value" :value="d.value">{{ d.label }}</option>
                </select>
              </div>
            </div>

            <div class="form-group">
              <label>从艺年限</label>
              <input type="text" v-model="certForm.years" placeholder="请输入从艺年限，如：15年" />
            </div>

            <div class="form-group">
              <label>个人简介</label>
              <textarea v-model="certForm.bio" rows="3" placeholder="请简要介绍您的传承经历和技艺特色"></textarea>
            </div>

            <div class="form-group">
              <label>佐证材料上传 <span class="required">*</span></label>
              <p class="form-hint">上传资质证书、技艺作品等证明材料（图片或PDF），有助于提高审核通过率</p>
              <div class="upload-area" :class="{ 'has-files': certFiles.length > 0 }">
                <label class="upload-trigger">
                  <span class="upload-icon">📎</span>
                  <span>点击选择文件</span>
                  <input type="file" accept="image/*,.pdf" multiple @change="handleCertUpload" :disabled="certLoading" hidden />
                </label>
                <span class="upload-tip">支持图片、PDF格式，单文件 ≤ 10MB，最多5个文件</span>
              </div>
              <!-- 文件列表（即时上传 + 预览） -->
              <div v-if="certFiles.length > 0" class="cert-file-list">
                <div v-for="(file, index) in certFiles" :key="file.uid" class="cert-file-item" :class="'status-' + file.status">
                  <!-- 缩略图 -->
                  <div class="file-thumb">
                    <img v-if="file.isImage && file.previewUrl" :src="file.previewUrl" alt="预览" />
                    <span v-else-if="file.isImage && file.url" class="file-thumb-img" :style="{ backgroundImage: 'url(' + file.url + ')' }"></span>
                    <span v-else class="file-icon-pdf">📕</span>
                  </div>
                  <!-- 文件信息 -->
                  <div class="file-info">
                    <span class="file-name">{{ file.name }}</span>
                    <span class="file-size">{{ formatFileSize(file.size) }}</span>
                    <!-- 上传进度条 -->
                    <div v-if="file.status === 'uploading'" class="upload-progress">
                      <div class="progress-bar">
                        <div class="progress-fill" :style="{ width: file.progress + '%' }"></div>
                      </div>
                      <span class="progress-text">{{ file.progress }}%</span>
                    </div>
                    <!-- 成功 -->
                    <span v-else-if="file.status === 'done'" class="file-status-done">✅ 已上传</span>
                    <!-- 失败 -->
                    <span v-else-if="file.status === 'error'" class="file-status-error">❌ {{ file.errorMsg || '上传失败' }}</span>
                  </div>
                  <!-- 移除按钮 -->
                  <button class="file-remove" @click="removeCertFile(index)" :disabled="certLoading" title="移除文件">✕</button>
                </div>
              </div>
            </div>

            <div class="form-actions">
              <button class="btn-primary" :disabled="certLoading" @click="submitCertification">
                {{ certLoading ? '提交中...' : '提交认证申请' }}
              </button>
              <p class="form-note">提交申请后，管理员将在3个工作日内审核，审核通过后您将获得传承人身份标识</p>
            </div>
          </div>
        </div>
      </section>

    </div>

    <!-- 创作图片放大预览 -->
    <div v-if="creationPreviewUrl" class="gallery-preview-overlay" @click.self="closeCreationPreview">
      <div class="gallery-preview-container">
        <img :src="creationPreviewUrl" :alt="creationPreviewName" />
        <div class="gallery-preview-actions">
          <span class="gallery-preview-name">{{ creationPreviewName }}</span>
          <button class="btn-download" @click="downloadCreationImage">下载图片</button>
          <button class="btn-close-preview" @click="closeCreationPreview">×</button>
        </div>
      </div>
    </div>

    <!-- 取消预约确认弹窗 -->
    <div v-if="showCancelModal" class="modal-overlay" @click.self="closeCancelModal">
      <div class="delete-modal">
        <div class="delete-icon">⚠️</div>
        <h3>确认取消预约</h3>
        <p>取消后将无法恢复，确认继续？</p>
        <div class="delete-actions">
          <button class="btn-ghost" @click="closeCancelModal">取消</button>
          <button class="btn-danger" @click="confirmCancelOrder">确认取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 全局背景 */
.profile-wrapper { position: relative; min-height: 100vh; }
.profile-bg {
  position: absolute; top: 0; left: 0; right: 0; height: 320px; z-index: 0;
  background: linear-gradient(135deg, #6b2018 0%, #a0422e 30%, #c9472c 50%, #d4a553 80%, #e8c87a 100%);
}
.bg-pattern {
  position: absolute; inset: 0;
  background-image: radial-gradient(circle at 20% 50%, rgba(255,255,255,0.06) 1px, transparent 1px),
                     radial-gradient(circle at 80% 20%, rgba(255,255,255,0.04) 1px, transparent 1px);
  background-size: 40px 40px, 60px 60px;
}
.bg-overlay {
  position: absolute; bottom: 0; left: 0; right: 0; height: 120px;
  background: linear-gradient(to top, var(--bg-warm, #fdf8f0), transparent);
}

.profile-page { position: relative; z-index: 1; padding-top: 100px; padding-bottom: 48px; padding-left: 48px; padding-right: 48px; display: flex; flex-direction: column; gap: 24px; max-width: 900px; margin: 0 auto; }

.back-btn {
  align-self: flex-start;
  display: flex; align-items: center; gap: 4px;
  padding: 8px 18px; border: 1px solid var(--bg-glass-border); border-radius: 22px;
  background: var(--bg-glass); color: var(--text-secondary); font-size: 13px;
  cursor: pointer; transition: all 0.2s;
}
.back-btn:hover { background: var(--bg-glass-hover, rgba(0,0,0,0.04)); color: var(--text-primary); }
.back-btn span { font-size: 15px; }

/* Hero */
.profile-hero { padding: 28px 32px; position: relative; }
.hero-edit-btn {
  position: absolute; top: 16px; right: 20px;
  display: flex; align-items: center; gap: 4px;
  padding: 6px 16px; border: 1px solid var(--color-primary); border-radius: 20px;
  background: rgba(201,71,44,0.04); color: var(--color-primary); font-size: 13px;
  cursor: pointer; transition: all 0.2s;
}
.hero-edit-btn:hover { background: var(--color-primary); color: #fff; }
.hero-edit-btn span { font-size: 14px; }
.hero-follow-btn {
  position: absolute; top: 16px; right: 20px;
  display: flex; align-items: center; gap: 4px;
  padding: 6px 20px; border: 1px solid var(--color-primary); border-radius: 20px;
  background: var(--color-primary); color: #fff; font-size: 13px;
  cursor: pointer; transition: all 0.2s; font-weight: 500;
}
.hero-follow-btn:hover { opacity: 0.9; transform: translateY(-1px); }
.hero-follow-btn.following { background: transparent; color: var(--color-primary); border-color: var(--color-primary); }
.hero-follow-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.ph-content { display: flex; align-items: flex-start; gap: 24px; }
.avatar-wrap { position: relative; flex-shrink: 0; }
.ph-avatar { width: 100px; height: 100px; border-radius: 50%; border: 4px solid #fff; box-shadow: 0 4px 20px rgba(0,0,0,0.12); background: #fff; object-fit: cover; }
.avatar-edit {
  position: absolute; bottom: 2px; right: 2px; width: 28px; height: 28px;
  background: var(--color-primary); border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-size: 13px; border: 2px solid #fff; opacity: 0; transition: opacity 0.2s;
}
.avatar-wrap:hover .avatar-edit { opacity: 1; }
.ph-cert-badge { position: absolute; bottom: -2px; left: -4px; font-size: 20px; line-height: 1; filter: drop-shadow(0 2px 3px rgba(0,0,0,0.2)); pointer-events: none; }
.ph-main { flex: 1; display: flex; flex-direction: column; gap: 16px; }
.ph-info { }
.ph-info h1 { font-family: var(--font-serif); font-size: 24px; margin: 0; color: var(--text-primary); font-weight: 600; }
.ph-role { font-size: 13px; color: var(--text-muted); margin-top: 4px; }
.ph-motto { font-size: 13px; color: var(--text-secondary); line-height: 1.6; margin: 8px 0 0; font-style: italic; }
.ph-right { display: flex; flex-direction: column; align-items: flex-end; gap: 16px; flex-shrink: 0; }
.ph-stats { display: flex; gap: 16px; flex-shrink: 0; }
.phs { 
  display: flex; flex-direction: column; align-items: center; gap: 2px; 
  padding: 8px 14px; background: rgba(201,71,44,0.04); border-radius: 10px; 
  cursor: pointer; transition: background 0.2s;
}
.phs:hover { background: rgba(200,80,42,0.08); }
.phs strong { font-size: 18px; color: var(--color-primary); font-weight: 600; }
.phs span { font-size: 11px; color: var(--text-muted); }

/* Avatar Modal */
.modal-mask { position: fixed; inset: 0; z-index: 1000; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; backdrop-filter: blur(4px); }
.avatar-modal { width: 480px; padding: 28px; }
.am-head { display: flex; justify-content: space-between; align-items: center; }
.am-head h3 { font-family: var(--font-serif); margin: 0; }
.modal-close { background: none; border: none; font-size: 18px; cursor: pointer; color: var(--text-muted); }
.am-tip { font-size: 13px; color: var(--text-muted); margin: 8px 0 16px; }
.avatar-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; }
.avatar-option { width: 100%; aspect-ratio: 1; border-radius: 50%; border: 3px solid transparent; cursor: pointer; background: #fff; transition: all 0.2s; }
.avatar-option:hover { border-color: var(--color-primary); transform: scale(1.1); }
.avatar-option.active { border-color: var(--color-primary); box-shadow: 0 0 0 3px rgba(201,71,44,0.2); }
.am-upload { margin-top: 16px; text-align: center; }
.upload-btn { cursor: pointer; }

/* Tabs */
.profile-tabs {
  display: flex;
  justify-content: space-between;
  gap: 0;
  background: rgba(255,255,255,0.9);
  border-radius: 20px;
  padding: 6px;
  box-shadow: 0 4px 20px rgba(120, 76, 49, 0.08);
  overflow-x: auto;
  overflow-y: hidden;
  border: 1px solid rgba(201, 71, 44, 0.1);
  scrollbar-width: thin;
  scrollbar-color: rgba(201, 71, 44, 0.3) transparent;
}
.profile-tabs::-webkit-scrollbar {
  height: 6px;
}
.profile-tabs::-webkit-scrollbar-track {
  background: rgba(0,0,0,0.05);
  border-radius: 3px;
}
.profile-tabs::-webkit-scrollbar-thumb {
  background: rgba(201, 71, 44, 0.3);
  border-radius: 3px;
}
.profile-tabs::-webkit-scrollbar-thumb:hover {
  background: rgba(201, 71, 44, 0.5);
}
.profile-tabs button {
  flex: 1;
  padding: 10px 8px;
  border: none;
  background: rgba(201,71,44,0.04);
  font-size: 13px;
  cursor: pointer;
  color: var(--text-secondary);
  border-radius: 14px;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 4px;
  white-space: nowrap;
  font-weight: 500;
}
.profile-tabs button:hover { 
  background: rgba(201,71,44,0.1); 
  color: var(--color-primary);
  transform: translateY(-2px);
}
.profile-tabs button.active { 
  color: #fff; 
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)); 
  font-weight: 600; 
  box-shadow: 0 6px 20px rgba(201, 71, 44, 0.35); 
  transform: translateY(-3px);
}
.profile-tabs button.active:hover { 
  transform: translateY(-3px); 
  box-shadow: 0 8px 24px rgba(201, 71, 44, 0.4);
}

.tab-content { display: flex; flex-direction: column; gap: 20px; }

/* Orders */
.order-filter { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-label { font-size: 14px; color: var(--text-secondary); font-weight: 500; }
.filter-buttons { display: flex; gap: 8px; flex-wrap: wrap; }
.filter-btn { padding: 6px 16px; border: 1px solid var(--bg-glass-border); border-radius: 20px; font-size: 13px; cursor: pointer; background: #fff; color: var(--text-secondary); transition: all 0.2s; }
.filter-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }
.filter-btn.active { background: var(--color-primary); border-color: var(--color-primary); color: #fff; }
.order-card { padding: 20px; transition: transform 0.2s; cursor: pointer; }
.order-card:hover { transform: translateY(-2px); }
.oc-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.oc-no { font-family: monospace; font-size: 13px; color: var(--text-muted); }
.oc-status { padding: 3px 12px; border-radius: 12px; font-size: 12px; font-weight: 600; }
.oc-status.pending { background: rgba(251,191,36,0.12); color: #b45309; }
.oc-status.confirmed { background: rgba(79,154,122,0.12); color: #4f9a7a; }
.oc-status.rejected { background: rgba(239,68,68,0.12); color: #dc2626; }
.oc-status.completed { background: rgba(107,114,128,0.12); color: #6b7280; }
.oc-status.cancelled { background: rgba(107,114,128,0.12); color: #6b7280; }
.oc-status.cancelled { background: rgba(201,71,44,0.1); color: #c9472c; }
.oc-body h3 { font-family: var(--font-serif); font-size: 18px; margin-bottom: 8px; }
.oc-meta { display: flex; flex-wrap: wrap; gap: 14px; font-size: 13px; color: var(--text-muted); }
.oc-info { display: flex; gap: 16px; margin-top: 8px; font-size: 13px; color: var(--text-secondary); }

.oc-rating { display: flex; align-items: center; gap: 8px; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--bg-glass-border); }
.rate-label { font-size: 13px; color: var(--text-secondary); white-space: nowrap; }
.rate-submit { font-size: 12px; padding: 4px 14px; }
.oc-foot { display: flex; align-items: center; gap: 16px; margin-top: 12px; border-top: 1px solid var(--bg-glass-border); padding-top: 12px; }
.oc-price { font-size: 20px; font-weight: 700; color: var(--color-primary); }
.oc-time { flex: 1; font-size: 12px; color: var(--text-muted); }
.cancel-btn { font-size: 12px; padding: 4px 14px; }

/* Favorites */
.fav-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.fav-card { overflow: hidden; cursor: pointer; }
.fav-card img { width: 100%; height: 160px; object-fit: cover; transition: transform 0.4s; }
.fav-placeholder {
  width: 100%; height: 160px; display: flex; align-items: center; justify-content: center;
  font-size: 48px; background: rgba(201,71,44,0.06); color: var(--text-muted);
}
.fav-card.unavailable { opacity: 0.7; }
.unavailable-tag {
  display: inline-block; font-size: 11px; color: #b45309; background: rgba(251,191,36,0.15);
  padding: 2px 8px; border-radius: 4px; margin-top: 4px;
}
.fav-card:hover img { transform: scale(1.06); }
.fav-info { padding: 14px; }
.fav-info h4 { margin: 0 0 4px; font-family: var(--font-serif); }
.fav-info span { font-size: 12px; color: var(--text-muted); }

/* Follow Cards */
.follow-card { display: flex; align-items: center; gap: 14px; padding: 16px; transition: background 0.2s; }
.follow-card:hover { background: rgba(200,80,42,0.04); }
.follow-avatar { width: 50px; height: 50px; border-radius: 50%; border: 2px solid var(--bg-glass-border); }
.follow-info { flex: 1; }
.follow-info h4 { margin: 0; font-size: 14px; font-weight: 600; color: var(--text-primary); }
.follow-info p { margin: 2px 0 0; font-size: 12px; color: var(--text-muted); }
.unfollow-btn { color: var(--text-muted); font-size: 12px; padding: 4px 14px; }

/* 创作画廊网格 */
.gallery-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }
.gallery-item { border-radius: var(--radius-lg); overflow: hidden; display: flex; flex-direction: column; background: #fff; }
.gallery-item img { width: 100%; height: 240px; object-fit: cover; cursor: pointer; }
.gallery-placeholder { width: 100%; height: 240px; display: flex; align-items: center; justify-content: center; font-size: 64px; background: rgba(201,71,44,0.04); color: var(--text-muted); }
.gallery-item-info { padding: 16px; }
.gallery-item-meta { display: flex; gap: 8px; margin-bottom: 8px; }
.type-badge, .heritage-badge { padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: bold; }
.type-badge { background: rgba(201,71,44,0.1); color: var(--color-primary); }
.heritage-badge { background: rgba(52,152,219,0.1); color: #3498db; }
.gallery-item-desc { font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 4px; }
.gallery-item-time { font-size: 12px; color: var(--text-muted); margin-bottom: 8px; }
.gallery-item-actions { display: flex; gap: 8px; justify-content: flex-end; }
.gallery-item-actions .action-btn { padding: 4px 12px; font-size: 12px; border: 1px solid var(--bg-glass-border); background: transparent; border-radius: 4px; cursor: pointer; transition: all 0.3s; color: var(--text-secondary); }
.gallery-item-actions .action-btn:hover { background: var(--color-primary); color: white; border-color: var(--color-primary); }

/* 图片预览 */
.gallery-preview-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.85); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.gallery-preview-container { position: relative; max-width: 90vw; max-height: 90vh; display: flex; flex-direction: column; align-items: center; }
.gallery-preview-container > img { max-width: 90vw; max-height: 80vh; object-fit: contain; border-radius: 12px; box-shadow: 0 8px 40px rgba(0,0,0,0.5); }
.gallery-preview-actions { display: flex; align-items: center; gap: 16px; margin-top: 20px; }
.gallery-preview-name { color: #fff; font-size: 16px; opacity: 0.85; }
.btn-download { padding: 10px 24px; background: var(--color-primary); color: #fff; border: none; border-radius: 8px; font-size: 15px; cursor: pointer; transition: all 0.3s; }
.btn-download:hover { background: #b33a22; transform: translateY(-1px); }
.btn-close-preview { padding: 10px 16px; background: rgba(255,255,255,0.15); color: #fff; border: 1px solid rgba(255,255,255,0.3); border-radius: 8px; font-size: 18px; cursor: pointer; transition: all 0.3s; }
.btn-close-preview:hover { background: rgba(255,255,255,0.25); }
.unfollow-btn:hover { color: var(--color-primary); }
.follow-btn { font-size: 12px; padding: 4px 14px; }

/* Quick Post */
.quick-post-form { padding: 16px; }
.qp-row { display: flex; gap: 12px; align-items: flex-start; }
.qp-avatar { width: 40px; height: 40px; border-radius: 50%; }
.qp-textarea { flex: 1; resize: none; border: 1px solid var(--bg-glass-border); border-radius: var(--radius-md); padding: 10px 12px; background: var(--bg-glass); font-size: 14px; outline: none; }
.qp-textarea:focus { border-color: var(--color-primary); }
.qp-footer { display: flex; justify-content: flex-end; margin-top: 10px; }
.qp-btn { font-size: 13px; padding: 6px 16px; }

/* Post Edit Form */
.post-form { padding: 18px; }
.post-form-row { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 12px; }
.post-avatar { width: 38px; height: 38px; border-radius: 50%; border: 2px solid var(--bg-glass-border); flex-shrink: 0; }
.post-textarea { flex: 1; resize: none; border: 1px solid var(--bg-glass-border); border-radius: var(--radius-md); padding: 10px 12px; background: var(--bg-glass); color: var(--text-primary); font-size: 14px; line-height: 1.7; }
.post-textarea:focus { outline: none; border-color: var(--color-primary); }
.post-form-footer { display: flex; justify-content: space-between; align-items: center; }
.post-tags-select { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.post-media-upload { margin-bottom: 12px; }
.upload-preview { display: flex; flex-wrap: wrap; gap: 8px; }
.media-item { position: relative; width: 100px; height: 100px; border-radius: var(--radius-md); overflow: hidden; border: 1px solid var(--bg-glass-border); }
.preview-img, .preview-video { width: 100%; height: 100%; object-fit: cover; }
.emoji-preview { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; font-size: 48px; background: var(--bg-glass); }
.remove-media { position: absolute; top: 4px; right: 4px; width: 24px; height: 24px; border-radius: 50%; background: rgba(0,0,0,0.6); color: #fff; border: none; cursor: pointer; font-size: 16px; display: flex; align-items: center; justify-content: center; }
.upload-btn { width: 100px; height: 100px; border-radius: var(--radius-md); border: 2px dashed var(--bg-glass-border); background: var(--bg-glass); display: flex; align-items: center; justify-content: center; cursor: pointer; transition: all var(--transition-fast); }
.upload-btn:hover { border-color: var(--color-primary); background: rgba(200,80,42,0.06); }
.upload-icon { font-size: 32px; color: var(--text-muted); }
.upload-menu { display: flex; gap: 16px; margin-top: 12px; padding: 12px; background: var(--bg-glass); border-radius: var(--radius-md); }
.upload-option { display: flex; align-items: center; gap: 8px; padding: 8px 16px; border-radius: var(--radius-md); cursor: pointer; transition: all var(--transition-fast); }
.upload-option:hover { background: rgba(200,80,42,0.1); }
.option-icon { font-size: 20px; }
.file-input { display: none; }
.emoji-picker { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; padding: 12px; background: var(--bg-glass); border-radius: var(--radius-md); max-height: 200px; overflow-y: auto; }
.emoji-item { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; font-size: 20px; border-radius: 8px; cursor: pointer; transition: all var(--transition-fast); }
.emoji-item:hover { background: rgba(200,80,42,0.1); }
.tag-selector { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; padding: 12px; background: var(--bg-glass); border-radius: var(--radius-md); }

/* Post Cards */
.post-card { padding: 20px; transition: all 0.3s; }
.post-card:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(201,71,44,0.1); }
.post-header { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.post-user-avatar { width: 40px; height: 40px; border-radius: 50%; border: 2px solid var(--bg-glass-border); }
.post-user-info { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.post-user-name { font-weight: 600; color: var(--text-primary); font-size: 15px; }
.badge-label { font-size: 11px; padding: 2px 8px; border-radius: 4px; background: rgba(200,80,42,0.1); color: var(--color-primary); }
.post-time { font-size: 12px; color: var(--text-muted); }
.post-content { color: var(--text-primary); line-height: 1.8; margin-bottom: 14px; font-size: 14px; }
.post-images { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
.post-img { width: 150px; height: 110px; border-radius: var(--radius-md); object-fit: cover; cursor: pointer; transition: all var(--transition-fast); }
.post-img:hover { transform: scale(1.04); }
.post-tags-row { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
.post-tag { padding: 4px 10px; background: rgba(201,71,44,0.06); border-radius: 12px; font-size: 12px; color: var(--color-primary); }

/* Post Edit */
.post-edit { margin-bottom: 14px; }
.edit-textarea { width: 100%; padding: 12px 14px; border: 2px solid var(--color-primary); border-radius: var(--radius-md); font-size: 14px; resize: none; outline: none; background: #fff; }
.edit-actions { display: flex; gap: 10px; margin-top: 10px; }
.edit-save, .edit-cancel { font-size: 12px; padding: 5px 14px; border-radius: 6px; }

.post-actions-bar { display: flex; gap: 16px; padding-top: 12px; border-top: 1px solid var(--bg-glass-border); }
.action-btn { background: none; border: none; padding: 8px 14px; border-radius: 20px; cursor: pointer; color: var(--text-secondary); font-size: 13px; transition: all 0.2s; }
.action-btn:hover { background: rgba(200,80,42,0.08); color: var(--color-primary); }
.delete-btn:hover { color: #e74c3c; background: rgba(231,76,60,0.1); }

.empty-state { text-align: center; padding: 60px 20px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-state p { color: var(--text-muted); margin-bottom: 16px; }

/* 用户列表 */
.user-list { display: flex; flex-direction: column; gap: 12px; }
.user-card { display: flex; align-items: center; gap: 16px; padding: 16px; transition: all 0.3s; cursor: pointer; }
.user-card:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(201,71,44,0.1); }
.user-avatar { width: 50px; height: 50px; border-radius: 50%; border: 2px solid var(--bg-glass-border); }
.user-info { flex: 1; }
.user-info h4 { margin: 0 0 4px; font-size: 15px; color: var(--text-primary); }
.user-role { font-size: 12px; color: var(--text-muted); }
.unfollow-btn, .remove-btn { font-size: 13px; padding: 6px 16px; }

/* 查看他人主页 — 粉丝/关注 */
.profile-social-section { display: flex; flex-direction: column; gap: 20px; }
.social-card { padding: 20px 24px; }
.social-title { font-size: 16px; font-weight: 600; margin: 0 0 14px; color: var(--text-primary); }
.social-empty { text-align: center; color: var(--text-muted); font-size: 14px; padding: 20px 0; }

/* 点赞卡片 */
.fav-card { position: relative; cursor: pointer; }
.unlike-btn { 
  position: absolute; 
  top: 8px; 
  right: 8px; 
  background: rgba(255,255,255,0.9); 
  border: none; 
  padding: 4px 12px; 
  border-radius: 12px; 
  font-size: 11px; 
  cursor: pointer; 
  opacity: 0; 
  transition: all 0.2s;
}
.fav-card:hover .unlike-btn { opacity: 1; }
.unlike-btn:hover { background: #ff4d4f; color: #fff; }

/* 传承人认证 */
.certification-container { padding: 32px; }
.cert-header { text-align: center; margin-bottom: 32px; }
.cert-header h2 { font-family: var(--font-serif); font-size: 24px; margin: 0 0 8px; color: var(--text-primary); }
.cert-desc { font-size: 14px; color: var(--text-muted); margin: 0; }

/* 已认证状态 */
.certified-status { }
.cert-badge-large { display: flex; align-items: center; gap: 12px; padding: 20px 24px; background: linear-gradient(135deg, rgba(255,215,0,0.1) 0%, rgba(255,183,0,0.1) 100%); border-radius: var(--radius-lg); margin-bottom: 24px; border: 2px solid rgba(255,215,0,0.3); flex-wrap: wrap; }
.badge-icon { font-size: 28px; }
.badge-title { font-size: 18px; font-weight: 700; color: var(--text-primary); white-space: nowrap; }
.badge-divider { color: rgba(255,215,0,0.4); font-size: 16px; }
.badge-meta { font-size: 13px; color: var(--text-secondary); }

.cert-details { display: flex; flex-direction: column; gap: 16px; }
.detail-row { display: flex; padding: 12px 0; border-bottom: 1px solid var(--bg-glass-border); font-size: 14px; }
.detail-row label { min-width: 100px; color: var(--text-muted); font-weight: 600; }
.detail-row span { flex: 1; color: var(--text-primary); }

/* 认证表单 */
.cert-form { max-width: 600px; margin: 0 auto; }
.form-group { margin-bottom: 20px; }
.form-group label { display: block; margin-bottom: 8px; font-size: 14px; color: var(--text-secondary); font-weight: 600; }
.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--bg-glass-border);
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
  background: #fff;
}
.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus { border-color: var(--color-primary); }
.form-group textarea { resize: vertical; }

.required { color: #dc2626; margin-left: 4px; font-weight: bold; }

.upload-area { border: 2px dashed var(--bg-glass-border); border-radius: 8px; padding: 20px; text-align: center; transition: border-color 0.2s; }
.upload-area:hover { border-color: var(--color-primary); }
.upload-area.has-files { padding: 14px 20px; }
.upload-trigger { display: inline-flex; align-items: center; gap: 8px; cursor: pointer; color: var(--color-primary); font-weight: 600; padding: 8px 20px; border: 1px solid var(--color-primary); border-radius: 20px; transition: all 0.2s; }
.upload-trigger:hover { background: rgba(201,71,44,0.08); }
.upload-trigger .upload-icon { font-size: 16px; }
.upload-trigger input[type="file"] { display: none; }
.upload-tip { display: block; font-size: 12px; color: var(--text-muted); margin-top: 10px; }

/* 证明材料文件列表 */
.cert-file-list { margin-top: 12px; display: flex; flex-direction: column; gap: 8px; }
.cert-file-item { display: flex; align-items: center; gap: 12px; padding: 10px 12px; background: rgba(201,71,44,0.03); border: 1px solid var(--bg-glass-border); border-radius: 8px; transition: border-color 0.2s; }
.cert-file-item.status-uploading { border-color: rgba(59,130,246,0.3); background: rgba(59,130,246,0.03); }
.cert-file-item.status-done { border-color: rgba(34,197,94,0.3); background: rgba(34,197,94,0.03); }
.cert-file-item.status-error { border-color: rgba(255,77,79,0.3); background: rgba(255,77,79,0.03); }

.file-thumb { width: 48px; height: 48px; border-radius: 6px; overflow: hidden; flex-shrink: 0; background: var(--bg-card); display: flex; align-items: center; justify-content: center; }
.file-thumb img { width: 100%; height: 100%; object-fit: cover; }
.file-thumb-img { display: block; width: 100%; height: 100%; background-size: cover; background-position: center; }
.file-icon-pdf { font-size: 28px; }

.file-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.file-name { font-size: 13px; font-weight: 500; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-size { font-size: 11px; color: var(--text-muted); }

.upload-progress { display: flex; align-items: center; gap: 8px; margin-top: 2px; }
.progress-bar { flex: 1; height: 4px; background: rgba(255,255,255,0.1); border-radius: 2px; overflow: hidden; }
.progress-fill { height: 100%; background: linear-gradient(90deg, #3b82f6, #60a5fa); border-radius: 2px; transition: width 0.3s ease; }
.progress-text { font-size: 11px; color: #3b82f6; white-space: nowrap; }

.file-status-done { font-size: 12px; color: #22c55e; }
.file-status-error { font-size: 12px; color: #ff4d4f; }

.file-remove { flex-shrink: 0; width: 24px; height: 24px; border: none; background: rgba(255,255,255,0.05); color: var(--text-muted); border-radius: 50%; cursor: pointer; font-size: 12px; display: flex; align-items: center; justify-content: center; transition: all 0.2s; }
.file-remove:hover { background: rgba(255,77,79,0.15); color: #ff4d4f; }

.form-actions { text-align: center; margin-top: 32px; }
.form-actions button { min-width: 200px; }
.form-note { font-size: 12px; color: var(--text-muted); margin-top: 12px; }

/* 传承人认证 */
.certification-section { margin-top: 16px; }
.certified-badge { display: inline-flex; align-items: center; gap: 8px; padding: 8px 16px; background: linear-gradient(135deg, #ffd700 0%, #ffb700 100%); border-radius: 20px; color: #8b4513; font-weight: 600; box-shadow: 0 2px 8px rgba(255,215,0,0.3); }
.badge-icon { font-size: 16px; }
.badge-text { font-size: 13px; }
.badge-level { font-size: 11px; opacity: 0.8; }
.certify-btn { font-size: 13px; padding: 8px 20px; }

/* 认证弹窗 */
.certify-modal { max-width: 480px; width: 90%; padding: 24px; }
.certify-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.certify-head h3 { margin: 0; font-size: 18px; }
.certify-form { display: flex; flex-direction: column; gap: 16px; }
.certify-group { display: flex; flex-direction: column; gap: 6px; }
.certify-group label { font-size: 13px; color: var(--text-secondary); font-weight: 600; }

/* 删除弹窗 */
.delete-modal { width: 360px; padding: 28px; text-align: center; }
.delete-icon { font-size: 48px; margin-bottom: 16px; }
.delete-modal h3 { margin: 0 0 12px; font-size: 18px; }
.delete-modal p { margin: 0 0 8px; color: var(--text-secondary); font-size: 14px; }
.delete-question { color: var(--text-muted); font-style: italic; font-size: 13px; margin-top: 16px !important; }
.delete-actions { display: flex; gap: 12px; justify-content: center; margin-top: 24px; }
.btn-danger { background: #ff4d4f; color: #fff; border: none; padding: 10px 24px; border-radius: var(--radius-md); cursor: pointer; font-size: 14px; transition: all var(--transition-fast); }
.btn-danger:hover { background: #ff7875; }
.certify-group input, .certify-group select { padding: 10px 14px; border: 1px solid var(--bg-glass-border); border-radius: 8px; font-size: 14px; outline: none; transition: border-color 0.2s; background: #fff; }
.certify-group input:focus, .certify-group select:focus { border-color: var(--color-primary); }
.certify-group textarea { padding: 10px 14px; border: 1px solid var(--bg-glass-border); border-radius: 8px; font-size: 14px; resize: none; outline: none; transition: border-color 0.2s; background: #fff; }
.certify-group textarea:focus { border-color: var(--color-primary); }
.required { color: #dc2626; margin-left: 4px; font-weight: bold; }
.certify-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
.certify-actions button { font-size: 13px; padding: 8px 20px; }

.status-card { display: flex; align-items: center; gap: 20px; padding: 20px 24px; border-radius: var(--radius-lg); }
.status-card.pending { background: linear-gradient(135deg, rgba(255,152,0,0.08), rgba(255,152,0,0.03)); border: 1px solid rgba(255,152,0,0.25); }
.status-card.rejected { background: linear-gradient(135deg, rgba(220,38,38,0.08), rgba(220,38,38,0.03)); border: 1px solid rgba(220,38,38,0.25); }
.status-card.none { background: linear-gradient(135deg, rgba(200,80,42,0.05), rgba(200,80,42,0.02)); border: 1px solid rgba(200,80,42,0.15); }
.status-icon { font-size: 36px; }
.status-info h3 { margin: 0 0 6px; font-size: 18px; color: var(--text-primary); }
.status-info p { margin: 4px 0; font-size: 14px; color: var(--text-secondary); }
.status-hint { font-size: 12px !important; color: var(--text-muted) !important; }

.cert-actions { margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--bg-glass-border); }
.cert-actions h4 { font-size: 15px; color: var(--text-primary); margin: 0 0 12px; }
.action-links { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.action-link-btn { padding: 12px 16px; border: 1px solid var(--bg-glass-border); border-radius: 8px; background: #fff; font-size: 14px; color: var(--text-primary); cursor: pointer; transition: all 0.2s; text-align: left; }
.action-link-btn:hover { border-color: var(--color-primary); background: rgba(200,80,42,0.05); transform: translateY(-1px); }

.region-selects { display: flex; gap: 10px; }
.region-select { flex: 1; padding: 10px 14px; border: 1px solid var(--bg-glass-border); border-radius: 8px; font-size: 14px; outline: none; transition: border-color 0.2s; background: #fff; }
.region-select:focus { border-color: var(--color-primary); }
.region-select:disabled { opacity: 0.5; cursor: not-allowed; }

.cert-edit-form { max-width: 600px; margin: 24px auto 0; padding: 20px; background: rgba(201,71,44,0.02); border-radius: var(--radius-lg); border: 1px solid var(--bg-glass-border); }

.form-hint { font-size: 12px; color: var(--text-muted); margin-top: 4px; }

/* 设置弹窗 */
.settings-modal { width: 720px; max-height: 85vh; overflow-y: auto; padding: 0; }
.sm-head { display: flex; justify-content: space-between; align-items: center; padding: 20px 28px 0; }
.sm-head h3 { font-family: var(--font-serif); font-size: 20px; margin: 0; }
.sm-body { display: grid; grid-template-columns: 1fr 1fr; gap: 0; padding: 20px 28px; }
.sm-col { padding: 0 12px; }
.sm-col:first-child { border-right: 1px solid var(--bg-glass-border); padding-left: 0; }
.sm-col:last-child { padding-right: 0; }
.sm-col h4 { font-size: 15px; color: var(--text-primary); margin: 0 0 16px; }
.sm-avatar-section { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.sm-avatar-preview { width: 64px; height: 64px; border-radius: 50%; border: 3px solid var(--color-primary); object-fit: cover; background: #fff; }
.sm-avatar-actions { display: flex; flex-direction: column; gap: 6px; }
.sm-upload-btn, .sm-preset-btn {
  padding: 4px 12px; border: 1px solid var(--bg-glass-border); border-radius: 6px;
  background: #fff; font-size: 12px; cursor: pointer; transition: all 0.2s;
}
.sm-upload-btn:hover, .sm-preset-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }
.sm-avatar-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 8px; margin-bottom: 16px; }
.sm-avatar-option { width: 100%; aspect-ratio: 1; border-radius: 50%; border: 2px solid transparent; cursor: pointer; background: #fff; transition: all 0.2s; }
.sm-avatar-option:hover { border-color: var(--color-primary); transform: scale(1.1); }
.sm-avatar-option.active { border-color: var(--color-primary); box-shadow: 0 0 0 2px rgba(201,71,44,0.2); }
.sm-field { margin-bottom: 14px; }
.sm-field label { display: block; font-size: 12px; color: var(--text-muted); font-weight: 600; margin-bottom: 4px; }
.sm-field input, .sm-field textarea {
  width: 100%; padding: 8px 12px; border: 1px solid var(--bg-glass-border); border-radius: 8px;
  font-size: 13px; outline: none; transition: border-color 0.2s; background: #fff;
}
.sm-field input:focus, .sm-field textarea:focus { border-color: var(--color-primary); }
.sm-field textarea { resize: vertical; }
.sm-divider { border-top: 1px solid var(--bg-glass-border); margin: 16px 0; }
.sm-footer { display: flex; justify-content: flex-end; gap: 12px; padding: 16px 28px 24px; }
.sm-footer button { padding: 8px 24px; font-size: 14px; }

@media (max-width: 900px) {
  .gallery-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 600px) {
  .gallery-grid { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .ph-content { flex-direction: column; align-items: center; text-align: center; }
  .ph-stats { flex-wrap: wrap; justify-content: center; }
  .fav-grid { grid-template-columns: 1fr; }
  .profile-tabs { overflow-x: auto; }
  .avatar-modal { width: 90%; }
  .avatar-grid { grid-template-columns: repeat(4, 1fr); }
  .settings-modal { width: 95%; }
  .sm-body { grid-template-columns: 1fr; }
  .sm-col:first-child { border-right: none; border-bottom: 1px solid var(--bg-glass-border); padding-bottom: 16px; margin-bottom: 16px; }
  .hero-edit-btn { top: 12px; right: 12px; }
}
</style>
