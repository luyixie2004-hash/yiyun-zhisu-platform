<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getHeritageDetail, getHeritageImages, getHeritageExperiences } from '../api/heritage'
import { getCommentsByHeritage, getCommentReplies, createComment, replyComment, likeComment, unlikeComment, deleteComment as apiDeleteComment } from '../api/comment'
import { toggleLike as apiToggleLike, checkLike as apiCheckLike, getLikeCount, addFavorite, removeFavorite, checkFavorite } from '../api/user'
import { uploadFile } from '../api/file'
import { createBooking } from '../api/booking'
import { message } from 'ant-design-vue'
import { useUserStore } from '../stores/user'
import { useUserNavigation } from '../composables/useUserNavigation'
import { CATEGORY_MAP, LEVEL_MAP } from '../utils/constants'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { goToUserProfile } = useUserNavigation()
const loading = ref(false)
const heritage = ref(null)
const images = ref([])
const experiences = ref([])
const comments = ref([])
const selectedImageIndex = ref(0)

// 自动轮播相关
let carouselTimer = null
const AUTO_PLAY_INTERVAL = 2000 // 2秒

// 点赞状态
const likeState = ref({ liked: false, count: 0 })

// 收藏状态
const isFav = ref(false)
const favLoading = ref(false)

// 评论相关
const newComment = ref('')
const replyingTo = ref(null)
const replyContent = ref('')

// 图片上传相关
const commentImages = ref([])
const showImageUploader = ref(false)

// 表情包相关
const emojis = ['😀','😃','😄','😁','😆','😅','🤣','😂','🙂','😊','😇','🥰','😍','🤩','😘','😗','😚','😙','🥲','😋','😛','😜','🤪','😝','🤑','🤗','🤭','🤫','🤔','🤐','🤨','😐','😑','😶','😏','😒','🙄','😬','🤥','😌','😔','😪','🤤','😴','😷','🤒','🤕','🤢','🤮','🥵','🥶','🥴','😵','🤯','🤠','🥳','🥸','😎','🤓','🧐','😕','😟','🙁','☹️','😮','😯','😲','😳','🥺','😦','😧','😨','😰','😥','😢','😭','😱','😖','😣','😞','😓','😩','😫','🥱','😤','😡','😠','🤬','😈','👿','💀','☠️','💩','🤡','👹','👺','👻','👽','👾','🤖','🎃','😺','😸','😹','😻','😼','😽','🙀','😿','😾']
const showEmojiPicker = ref(false)

// 默认占位图（内联 SVG，不依赖外部服务）
const PLACEHOLDER_SVG = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="800" height="450" viewBox="0 0 800 450"><rect fill="#f0ebe3" width="800" height="450"/><text fill="#c8502a" font-family="serif" font-size="36" text-anchor="middle" x="400" y="210">华脉万象</text><text fill="#999" font-family="sans-serif" font-size="16" text-anchor="middle" x="400" y="250">图片加载中...</text></svg>')

// 图片加载失败处理（防止无限循环）
function handleImageError(event) {
  if (event.target.src === PLACEHOLDER_SVG) return
  event.target.src = PLACEHOLDER_SVG
}

// 回复相关
const replyImages = ref([])
const showReplyEmojiPicker = ref(false)

// 预约弹窗相关
const bookingModalVisible = ref(false)
const selectedActivity = ref(null)
const bookingForm = ref({
  name: '',
  phone: '',
  people: 1,
  selectedTime: ''
})

// 可预约时间段（优先使用体验项目的schedule字段）
const availableTimeSlots = computed(() => {
  if (!selectedActivity.value) return []
  const schedule = selectedActivity.value.schedule
  if (schedule) {
    try {
      const parsed = typeof schedule === 'string' ? JSON.parse(schedule) : schedule
      if (Array.isArray(parsed) && parsed.length > 0) return parsed
    } catch { /* ignore */ }
    // schedule 非 JSON，尝试按逗号或分号拆分
    const parts = schedule.split(/[,;，；]/)
    if (parts.length > 0) return parts.map(t => ({ label: t.trim(), value: t.trim() }))
  }
  return [
    { label: '上午 09:00-12:00', value: '09:00-12:00' },
    { label: '下午 14:00-17:00', value: '14:00-17:00' },
    { label: '晚上 18:00-21:00', value: '18:00-21:00' }
  ]
})

const detailTags = computed(() => {
  if (!heritage.value) return []
  return [heritage.value.category, heritage.value.region, heritage.value.level, heritage.value.originDynasty].filter(Boolean)
})

const allImages = computed(() => {
  if (!heritage.value) return []
  // 直接使用数据库中的图片，不再重复添加封面图
  const dbImages = images.value || []
  if (dbImages.length === 0) {
    // 如果没有图片，使用封面图作为后备（已在 normalizeHeritage 中编码）
    return [{
      id: 0,
      heritageId: heritage.value.id,
      imageUrl: heritage.value.imageUrl,
      sortOrder: 0
    }]
  }
  // 对图片 URL 中的中文字符进行编码，确保浏览器能正确加载
  return dbImages.map(img => ({
    ...img,
    imageUrl: (img.imageUrl && /[^\x00-\x7F]/.test(img.imageUrl)) ? encodeURI(img.imageUrl) : img.imageUrl
  }))
})

const articleBlocks = computed(() => {
  if (!heritage.value) return []
  return [
    {
      title: '历史脉络',
      content: heritage.value.description || heritage.value.summary || '该项目承载着鲜明的地域记忆与代际传承价值。',
    },
    {
      title: '代表技艺',
      content: `${heritage.value.name}的核心看点在于传统技法、表演程式与地域审美的结合，适合从材料、工序、身段或礼俗语境理解。`,
    },
    {
      title: '当代体验',
      content: '推荐结合体验工坊、传承人故事、地方演出和主题路线一起深入理解，让“知道”变成“参与”。',
    },
  ]
})

async function loadDetail() {
  loading.value = true
  try {
    const [detailRes, imagesRes, experiencesRes] = await Promise.all([
      getHeritageDetail(route.params.id),
      getHeritageImages(route.params.id),
      getHeritageExperiences(route.params.id)
    ])

    heritage.value = normalizeHeritage(detailRes.data)
    images.value = imagesRes.data || []
    experiences.value = experiencesRes.data || []
    
    await loadComments()
    await loadLikeStatus()
    await loadFavStatus()
  } catch (error) {
    console.error('加载非遗详情失败', error)
    message.error('加载非遗详情失败，请稍后重试')
    heritage.value = null
  } finally {
    loading.value = false
  }
}

async function loadComments() {
  try {
    const res = await getCommentsByHeritage(route.params.id, { size: 50 })
    const topLevelComments = (res.data?.records || res.data || []).map(normalizeComment)
    
    // 获取每个评论的回复
    for (const comment of topLevelComments) {
      try {
        const repliesRes = await getCommentReplies(comment.id)
        comment.replies = (repliesRes.data || []).map(normalizeComment)
      } catch (error) {
        console.error('加载回复失败', error)
        comment.replies = []
      }
    }
    
    comments.value = topLevelComments
  } catch (error) {
    console.error('加载评论失败', error)
    comments.value = []
  }
}

async function loadLikeStatus() {
  if (!userStore.isLoggedIn) {
    try {
      const res = await getLikeCount('HERITAGE', route.params.id)
      likeState.value.count = res.data || 0
    } catch (error) {
      likeState.value.count = 0
    }
    return
  }
  
  try {
    const res = await apiCheckLike('HERITAGE', route.params.id)
    likeState.value = {
      liked: res.data?.liked || false,
      count: res.data?.count || 0
    }
  } catch (error) {
    console.error('获取点赞状态失败', error)
  }
}

function normalizeHeritage(item) {
  if (!item) return null

  return {
    id: item.id,
    name: item.name,
    category: CATEGORY_MAP[item.category] || item.category,
    region: item.region,
    level: LEVEL_MAP[item.level] || item.level,
    summary: item.summary || item.desc,
    description: item.description || item.desc,
    imageUrl: (item.imageUrl && /[^\x00-\x7F]/.test(item.imageUrl)) ? encodeURI(item.imageUrl) : item.imageUrl,
    videoUrl: item.videoUrl,
    inheritanceType: item.inheritanceType || 'INDIVIDUAL',
    inheritor: item.inheritorName || item.inheritor,
    userId: item.userId || null,
    originDynasty: item.originDynasty || '',
    views: item.views || 0,
  }
}

function normalizeComment(item) {
  const normalized = {
    ...item,
    createdAt: formatDate(item.createdAt),
    images: item.images ? JSON.parse(item.images) : [],
    replies: [],
    // 映射后端字段名到前端期望的字段名
    userAvatar: item.avatar || item.userAvatar || 'https://api.dicebear.com/9.x/adventurer/svg?seed=heritage',
    userNickname: item.nickname || item.userNickname || item.username || '用户'
  }
  return normalized
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 点赞功能
async function toggleLike() {
  if (!userStore.isLoggedIn) {
    message.warning('请先登录后再点赞')
    router.push('/login')
    return
  }
  
  try {
    const res = await apiToggleLike('HERITAGE', route.params.id)
    likeState.value = {
      liked: res.data?.liked || false,
      count: res.data?.count || 0
    }
    message.success(likeState.value.liked ? '点赞成功' : '已取消点赞')
  } catch (error) {
    message.error('操作失败，请稍后重试')
  }
}

// 收藏功能
async function loadFavStatus() {
  if (!userStore.isLoggedIn) return
  try {
    const res = await checkFavorite(route.params.id)
    isFav.value = res.data === true
  } catch (err) {
    console.error('获取收藏状态失败:', err)
  }
}

async function toggleFavorite() {
  if (!userStore.isLoggedIn) {
    message.warning('请先登录后再收藏')
    router.push('/login')
    return
  }
  if (favLoading.value) return
  favLoading.value = true
  try {
    if (isFav.value) {
      await removeFavorite(route.params.id)
      isFav.value = false
      message.success('已取消收藏')
    } else {
      await addFavorite(route.params.id)
      isFav.value = true
      message.success('收藏成功')
    }
  } catch (err) {
    const msg = err?.response?.data?.msg || err?.msg || '操作失败'
    message.error(msg)
  } finally {
    favLoading.value = false
  }
}

// 图片上传功能
async function handleImageUpload(event) {
  const file = event.target.files[0]
  if (!file) return
  
  if (!file.type.startsWith('image/')) {
    message.warning('只允许上传图片文件')
    return
  }
  
  try {
    const res = await uploadFile(file)
    commentImages.value.push(res.data.url)
    showImageUploader.value = false
    message.success('图片上传成功')
  } catch (error) {
    message.error('图片上传失败，请稍后重试')
  }
}

function removeCommentImage(index) {
  commentImages.value.splice(index, 1)
}

// 表情包功能
function toggleEmojiPicker() {
  showEmojiPicker.value = !showEmojiPicker.value
}

function addEmoji(emoji) {
  newComment.value += emoji
  showEmojiPicker.value = false
}

// 评论功能
async function submitComment() {
  if (!userStore.isLoggedIn) {
    message.warning('请先登录后再评论')
    router.push('/login')
    return
  }
  
  if (!newComment.value.trim() && commentImages.value.length === 0) {
    message.warning('请输入评论内容或上传图片')
    return
  }
  
  try {
    const commentData = {
      heritageId: route.params.id,
      content: newComment.value,
      images: commentImages.value.length > 0 ? JSON.stringify(commentImages.value) : undefined
    }
    await createComment(commentData)
    newComment.value = ''
    commentImages.value = []
    await loadComments()
    message.success('评论成功')
  } catch (error) {
    message.error('评论失败，请稍后重试')
  }
}

// 删除评论功能
async function deleteComment(commentId) {
  if (!userStore.isLoggedIn) {
    message.warning('请先登录')
    router.push('/login')
    return
  }
  
  try {
    await apiDeleteComment(commentId)
    await loadComments()
    message.success('删除成功')
  } catch (error) {
    message.error('删除失败，请稍后重试')
  }
}

// 检查是否是当前用户的评论
function isOwnComment(comment) {
  return userStore.isLoggedIn && comment.userId === userStore.userInfo.id
}

// 回复图片上传
async function handleReplyImageUpload(event) {
  const file = event.target.files[0]
  if (!file) return
  
  if (!file.type.startsWith('image/')) {
    message.warning('只允许上传图片文件')
    return
  }
  
  try {
    const res = await uploadFile(file)
    replyImages.value.push(res.data.url)
    message.success('图片上传成功')
  } catch (error) {
    message.error('图片上传失败，请稍后重试')
  }
}

function removeReplyImage(index) {
  replyImages.value.splice(index, 1)
}

// 回复表情包
function toggleReplyEmojiPicker() {
  showReplyEmojiPicker.value = !showReplyEmojiPicker.value
}

function addReplyEmoji(emoji) {
  replyContent.value += emoji
  showReplyEmojiPicker.value = false
}

async function submitReply(parentId) {
  if (!userStore.isLoggedIn) {
    message.warning('请先登录后再回复')
    router.push('/login')
    return
  }
  
  if (!replyContent.value.trim() && replyImages.value.length === 0) {
    message.warning('请输入回复内容或上传图片')
    return
  }
  
  try {
    const replyData = {
      content: replyContent.value,
      images: replyImages.value.length > 0 ? JSON.stringify(replyImages.value) : undefined
    }
    await replyComment(parentId, replyData)
    replyContent.value = ''
    replyImages.value = []
    replyingTo.value = null
    await loadComments()
    message.success('回复成功')
  } catch (error) {
    message.error('回复失败，请稍后重试')
  }
}

async function likeCommentItem(commentId) {
  if (!userStore.isLoggedIn) {
    message.warning('请先登录后再点赞')
    router.push('/login')
    return
  }
  
  try {
    // 找到对应的评论或回复
    let targetComment = null
    for (const comment of comments.value) {
      if (comment.id === commentId) {
        targetComment = comment
        break
      }
      if (comment.replies) {
        for (const reply of comment.replies) {
          if (reply.id === commentId) {
            targetComment = reply
            break
          }
        }
      }
    }
    
    if (targetComment) {
      if (targetComment.liked) {
        // 已点赞，取消点赞
        const res = await unlikeComment(commentId)
        targetComment.liked = false
        targetComment.likeCount = res.data?.likeCount || 0
        message.success('已取消点赞')
      } else {
        // 未点赞，进行点赞
        const res = await likeComment(commentId)
        targetComment.liked = true
        targetComment.likeCount = res.data?.likeCount || 0
        message.success('点赞成功')
      }
    } else {
      // 如果找不到评论，重新加载列表
      await loadComments()
    }
  } catch (error) {
    console.error('点赞操作失败:', error)
    message.error('操作失败，请稍后重试')
  }
}

function startReply(comment) {
  replyingTo.value = comment.id
}

function cancelReply() {
  replyingTo.value = null
  replyContent.value = ''
}

// 预约弹窗相关方法
function openBookingModal(activity) {
  if (!userStore.isLoggedIn) {
    message.warning('请先登录后再预约')
    router.push('/login')
    return
  }
  
  // 如果点击的是"预约体验"按钮（activity为null），检查是否有可预约的活动
  if (!activity) {
    if (experiences.value.length === 0) {
      message.warning('为空')
      return
    }
    // 如果有多个活动，选择第一个可用的
    activity = experiences.value.find(exp => 
      exp.status === 'APPROVED' && 
      (exp.bookedNum || 0) < exp.quota
    )
    if (!activity) {
      message.warning('为空')
      return
    }
  }
  
  // 检查活动是否可预约
  const currentBooked = activity.bookedNum || 0
  if (activity.status !== 'APPROVED' || currentBooked >= activity.quota) {
    message.warning('该体验项目已满员或暂未开放')
    return
  }
  
  selectedActivity.value = activity
  bookingForm.value = {
    name: '',
    phone: '',
    people: 1,
    selectedTime: ''
  }
  bookingModalVisible.value = true
}

function closeBookingModal() {
  bookingModalVisible.value = false
  selectedActivity.value = null
  bookingForm.value = {
    name: '',
    phone: '',
    people: 1,
    selectedTime: ''
  }
}

// 提交预约
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
  
  if (!bookingForm.value.selectedTime) {
    message.warning('请选择预约时间段')
    return
  }
  
  try {
    await createBooking({
      experienceId: selectedActivity.value.id,
      bookingDate: selectedActivity.value.startTime || new Date().toISOString().split('T')[0],
      bookingTime: bookingForm.value.selectedTime,
      people: bookingForm.value.people,
      mainPerson: {
        name: bookingForm.value.name,
        phone: bookingForm.value.phone
      },
      companions: []
    })
    
    message.success('预约成功！')
    closeBookingModal()
  } catch (error) {
    console.error('预约失败:', error)
    message.error('预约失败，请稍后重试')
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/explore')
  }
}

// 自动轮播功能
function startCarousel() {
  if (carouselTimer) return
  if (allImages.value.length <= 1) return
  
  carouselTimer = setInterval(() => {
    selectedImageIndex.value = (selectedImageIndex.value + 1) % allImages.value.length
  }, AUTO_PLAY_INTERVAL)
}

function stopCarousel() {
  if (carouselTimer) {
    clearInterval(carouselTimer)
    carouselTimer = null
  }
}

function resetCarousel() {
  stopCarousel()
  startCarousel()
}

// 用户手动切换时重置定时器
function handleManualSelect(index) {
  selectedImageIndex.value = index
  resetCarousel()
}

function prevImage() {
  const len = allImages.value.length
  selectedImageIndex.value = (selectedImageIndex.value - 1 + len) % len
  resetCarousel()
}

function nextImage() {
  selectedImageIndex.value = (selectedImageIndex.value + 1) % allImages.value.length
  resetCarousel()
}

onMounted(async () => {
  await loadDetail()
  startCarousel()
})

onUnmounted(() => {
  stopCarousel()
})
</script>

<template>
  <div class="detail-page page-container">
    <div class="container" v-if="heritage">
      <!-- 头部英雄区 -->
      <section class="detail-hero glass-card" style="position: relative;">
        <button class="btn-ghost back-btn" style="position: absolute; top: 24px; right: 24px; z-index: 10; padding: 6px 14px; font-size: 14px;" @click="goBack">← 返回</button>
        
        <!-- 图片轮播 -->
        <div
          class="hero-images"
          @mouseenter="stopCarousel"
          @mouseleave="startCarousel"
        >
          <div class="main-image-wrapper">
            <transition name="carousel-fade" mode="out-in">
              <img
                :key="selectedImageIndex"
                :src="allImages[selectedImageIndex]?.imageUrl"
                :alt="heritage.name"
                class="detail-cover"
                loading="eager"
                @error="handleImageError"
              />
            </transition>
            <div class="image-overlay"></div>

            <!-- 左右切换箭头 -->
            <button
              v-if="allImages.length > 1"
              class="carousel-arrow carousel-prev"
              @click="prevImage"
              title="上一张"
            >‹</button>
            <button
              v-if="allImages.length > 1"
              class="carousel-arrow carousel-next"
              @click="nextImage"
              title="下一张"
            >›</button>

            <!-- 图片计数器 -->
            <div v-if="allImages.length > 1" class="carousel-counter">
              {{ selectedImageIndex + 1 }} / {{ allImages.length }}
            </div>
          </div>

          <!-- 缩略图导航 -->
          <div v-if="allImages.length > 1" class="thumbnails">
            <div
              v-for="(img, index) in allImages"
              :key="img.id"
              class="thumbnail-item"
              :class="{ active: selectedImageIndex === index }"
              @click="handleManualSelect(index)"
            >
              <img
                :src="img.imageUrl"
                :alt="`${heritage.name} 图片${index + 1}`"
                loading="eager"
                @error="handleImageError"
              />
            </div>
          </div>
        </div>
        
        <div class="detail-panel">
          <div class="detail-tags">
            <span v-for="tag in detailTags" :key="tag" class="tag tag-red">{{ tag }}</span>
          </div>
          <h1 class="section-title">{{ heritage.name }} <span>非遗档案</span></h1>
          <p class="detail-summary">{{ heritage.summary }}</p>
          <div class="detail-meta">
            <span>📍 {{ heritage.region }}</span>
            <span v-if="heritage.originDynasty">🏯 {{ heritage.originDynasty }}</span>
            <span v-if="heritage.inheritanceType === 'GROUP'">👥 全体民众</span>
            <span v-else :class="{ 'clickable-user': heritage.userId }" @click="heritage.userId && goToUserProfile(heritage.userId)">👤 {{ heritage.inheritor || '待补充传承人信息' }}</span>
            <span>🔥 {{ heritage.views }} 浏览</span>
            <span>👍 {{ likeState.count }} 点赞</span>
          </div>
          <div class="detail-actions">
            <button class="action-btn like-btn" @click="toggleLike" :class="{ liked: likeState.liked }">
              <span class="like-icon">👍</span>
              <span>{{ likeState.count }}</span>
            </button>
            <button class="action-btn fav-btn" @click="toggleFavorite" :class="{ favorited: isFav }" :disabled="favLoading">
              <span class="fav-icon">{{ isFav ? '⭐' : '☆' }}</span>
              <span>{{ isFav ? '已收藏' : '收藏' }}</span>
            </button>
            <button class="btn-primary" @click="$router.push(`/ai-studio?topic=${encodeURIComponent(heritage.name)}`)">立即对话</button>
            <button class="btn-ghost" @click="$router.push(`/booking?keyword=${encodeURIComponent(heritage.name)}`)">预约体验</button>
          </div>
        </div>
      </section>

      <section class="content-grid">
        <div class="content-main">
          <!-- 项目介绍 -->
          <div class="content-card glass-card" v-for="block in articleBlocks" :key="block.title">
            <h3>{{ block.title }}</h3>
            <p>{{ block.content }}</p>
          </div>

          <!-- 关联体验项目 -->
          <div v-if="experiences.length > 0" class="content-card glass-card">
            <h3>相关体验项目</h3>
            <div class="related-list">
              <div 
                v-for="exp in experiences" 
                :key="exp.id" 
                class="related-item"
                @click="openBookingModal(exp)"
                style="cursor: pointer;"
              >
                <img :src="exp.imageUrl || heritage.imageUrl" :alt="exp.name" v-img-fallback />
                <div class="related-info">
                  <strong>{{ exp.name }}</strong>
                  <p class="related-meta">{{ exp.type }} · {{ exp.location }}</p>
                  <p class="related-price">¥{{ exp.price }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- 评论区 -->
          <div class="content-card glass-card comments-section">
            <h3>评论 ({{ comments.length }})</h3>
            
            <!-- 发表评论 -->
            <div v-if="userStore.isLoggedIn" class="comment-input">
              <textarea 
                v-model="newComment" 
                placeholder="分享你的看法..." 
                rows="3"
                @keydown.enter.ctrl="submitComment"
              ></textarea>
              
              <!-- 上传的图片预览 -->
              <div v-if="commentImages.length > 0" class="comment-images-preview">
                <div v-for="(img, index) in commentImages" :key="index" class="preview-item">
                  <img :src="img" alt="评论图片" />
                  <span class="remove-btn" @click="removeCommentImage(index)">×</span>
                </div>
              </div>
              
              <div class="comment-actions">
                <div class="comment-tools">
                  <label class="upload-btn">
                    📷 图片
                    <input type="file" accept="image/*" @change="handleImageUpload" style="display: none;" />
                  </label>
                  <button class="emoji-btn" @click="toggleEmojiPicker">😊 表情</button>
                </div>
                <button class="btn-primary" @click="submitComment">发表评论</button>
              </div>
              
              <!-- 表情包选择器 -->
              <div v-if="showEmojiPicker" class="emoji-picker">
                <div v-for="emoji in emojis" :key="emoji" class="emoji-item" @click="addEmoji(emoji)">{{ emoji }}</div>
              </div>
            </div>
            <div v-else class="login-prompt">
              <p>请先 <a href="/login">登录</a> 后再发表评论</p>
            </div>

            <!-- 评论列表 -->
            <div class="comments-list">
              <div v-for="comment in comments" :key="comment.id" class="comment-item">
                <div class="comment-header">
                  <img :src="comment.userAvatar || 'https://api.dicebear.com/9.x/adventurer/svg?seed=heritage'" class="comment-avatar" :class="{ 'clickable-user': comment.userId }" :alt="comment.userNickname" @click="comment.userId && goToUserProfile(comment.userId)" />
                  <div class="comment-meta">
                    <span class="comment-author" :class="{ 'clickable-user': comment.userId }" @click="comment.userId && goToUserProfile(comment.userId)">{{ comment.userNickname || '用户' }}</span>
                    <span class="comment-time">{{ comment.createdAt }}</span>
                  </div>
                </div>
                <p class="comment-content">{{ comment.content }}</p>
                
                <!-- 评论图片 -->
                <div v-if="comment.images && comment.images.length > 0" class="comment-images">
                  <img v-for="(img, index) in comment.images" :key="index" :src="img" alt="评论图片" class="comment-image" />
                </div>
                
                <div class="comment-actions">
                  <button class="comment-action" :class="{ liked: comment.liked }" @click="likeCommentItem(comment.id)">
                    👍 {{ comment.likeCount || 0 }}
                  </button>
                  <button class="comment-action" @click="startReply(comment)">
                    💬 回复
                  </button>
                  <button v-if="isOwnComment(comment)" class="comment-action delete-btn" @click="deleteComment(comment.id)">
                    🗑️ 删除
                  </button>
                </div>
                
                <!-- 回复列表 -->
                <div v-if="comment.replies && comment.replies.length > 0" class="replies-list">
                  <div v-for="reply in comment.replies" :key="reply.id" class="reply-item">
                    <div class="reply-header">
                      <img :src="reply.userAvatar || 'https://api.dicebear.com/9.x/adventurer/svg?seed=heritage'" class="reply-avatar" :class="{ 'clickable-user': reply.userId }" :alt="reply.userNickname" @click="reply.userId && goToUserProfile(reply.userId)" />
                      <span class="reply-author" :class="{ 'clickable-user': reply.userId }" @click="reply.userId && goToUserProfile(reply.userId)">{{ reply.userNickname || '用户' }}</span>
                      <span class="reply-time">{{ reply.createdAt }}</span>
                    </div>
                    <p class="reply-content">{{ reply.content }}</p>
                    <div v-if="reply.images && reply.images.length > 0" class="reply-images">
                      <img v-for="(img, index) in reply.images" :key="index" :src="img" alt="回复图片" class="reply-image" />
                    </div>
                    <div class="reply-actions">
                      <button class="reply-action" :class="{ liked: reply.liked }" @click="likeCommentItem(reply.id)">
                        👍 {{ reply.likeCount || 0 }}
                      </button>
                      <button v-if="isOwnComment(reply)" class="reply-action delete-btn" @click="deleteComment(reply.id)">
                        🗑️ 删除
                      </button>
                    </div>
                  </div>
                </div>
                
                <!-- 回复框 -->
                <div v-if="replyingTo === comment.id" class="reply-box">
                  <textarea 
                    v-model="replyContent" 
                    placeholder="输入回复内容..."
                    rows="2"
                  ></textarea>
                  
                  <!-- 回复图片预览 -->
                  <div v-if="replyImages.length > 0" class="reply-images-preview">
                    <div v-for="(img, index) in replyImages" :key="index" class="preview-item">
                      <img :src="img" alt="回复图片" />
                      <span class="remove-btn" @click="removeReplyImage(index)">×</span>
                    </div>
                  </div>
                  
                  <div class="reply-tools">
                    <label class="upload-btn">
                      📷 图片
                      <input type="file" accept="image/*" @change="handleReplyImageUpload" style="display: none;" />
                    </label>
                    <button class="emoji-btn" @click="toggleReplyEmojiPicker">😊 表情</button>
                  </div>
                  
                  <!-- 回复表情包选择器 -->
                  <div v-if="showReplyEmojiPicker" class="emoji-picker">
                    <div v-for="emoji in emojis" :key="emoji" class="emoji-item" @click="addReplyEmoji(emoji)">{{ emoji }}</div>
                  </div>
                  
                  <div class="reply-actions">
                    <button class="btn-ghost" @click="cancelReply">取消</button>
                    <button class="btn-primary" @click="submitReply(comment.id)">回复</button>
                  </div>
                </div>
              </div>

              <div v-if="comments.length === 0" class="empty-comments">
                <p>暂无评论，快来发表第一条评论吧！</p>
              </div>
            </div>
          </div>
        </div>

        <aside class="content-side glass-card">
          <h3>继续探索</h3>
          <div class="side-actions">
            <button class="side-btn" @click="$router.push(`/explore?category=${encodeURIComponent(heritage.category)}`)">同类项目</button>
            <button class="side-btn" @click="$router.push(`/ai-studio?topic=${encodeURIComponent(heritage.name)}`)">AI 问答</button>
            <button class="side-btn" @click="$router.push(`/booking?keyword=${encodeURIComponent(heritage.name)}`)">工坊预约</button>
            <button class="side-btn" @click="$router.push(`/graph?focus=${encodeURIComponent(heritage.name)}`)">知识图谱</button>
          </div>
          
          <h4>数据统计</h4>
          <div class="stats-card">
            <div class="stat-item">
              <span class="stat-value">{{ heritage.views }}</span>
              <span class="stat-label">浏览量</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ likeState.count }}</span>
              <span class="stat-label">点赞数</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ comments.length }}</span>
              <span class="stat-label">评论数</span>
            </div>
          </div>
        </aside>
      </section>
    </div>

    <div class="container empty-wrap" v-else-if="!loading">
      <div class="glass-card empty-state">
        <div style="font-size:54px">🏛️</div>
        <h2>未找到该非遗项目</h2>
        <button class="btn-primary" @click="goBack">返回上一页</button>
      </div>
    </div>

    <!-- 预约弹窗 -->
    <a-modal
      v-model:open="bookingModalVisible"
      :title="selectedActivity ? selectedActivity.name : '预约体验'"
      width="600px"
      @ok="submitBooking"
      @cancel="closeBookingModal"
      :footer="null"
    >
      <div v-if="selectedActivity" class="booking-modal-content">
        <!-- 活动信息 -->
        <div class="activity-info">
          <img
            :src="selectedActivity.imageUrl || heritage.imageUrl"
            :alt="selectedActivity.name"
            class="activity-image"
            v-img-fallback
          />
          <div class="activity-details">
            <h3>{{ selectedActivity.name }}</h3>
            <div class="activity-meta">
              <span>📍 {{ selectedActivity.location || '待定' }}</span>
              <span>⏱️ {{ selectedActivity.duration || '2小时' }}</span>
              <span>👤 {{ selectedActivity.instructor || '传承人' }}</span>
            </div>
            <p class="activity-price">¥{{ selectedActivity.price || 0 }}</p>
          </div>
        </div>

        <!-- 可预约时间段 -->
        <div class="time-slots-section">
          <h4>选择预约时间段</h4>
          <a-radio-group v-model:value="bookingForm.selectedTime" class="time-slots">
            <a-radio-button 
              v-for="slot in availableTimeSlots" 
              :key="slot.value" 
              :value="slot.value"
            >
              {{ slot.label }}
            </a-radio-button>
          </a-radio-group>
        </div>

        <!-- 预约表单 -->
        <div class="booking-form">
          <h4>预约信息</h4>
          <a-form layout="vertical">
            <a-form-item label="姓名" required>
              <a-input 
                v-model:value="bookingForm.name" 
                placeholder="请输入您的姓名"
                maxlength="20"
              />
            </a-form-item>
            <a-form-item label="手机号" required>
              <a-input 
                v-model:value="bookingForm.phone" 
                placeholder="请输入11位手机号"
                maxlength="11"
              />
            </a-form-item>
            <a-form-item label="预约人数" required>
              <a-input-number 
                v-model:value="bookingForm.people" 
                :min="1" 
                :max="10"
                style="width: 100%"
              />
            </a-form-item>
          </a-form>
        </div>

        <!-- 操作按钮 -->
        <div class="modal-actions">
          <button class="btn-ghost" @click="closeBookingModal">取消</button>
          <button class="btn-primary" @click="submitBooking">提交预约</button>
        </div>
      </div>

      <!-- 没有活动时的提示 -->
      <div v-else class="no-activity-tip">
        <p>暂无可预约的活动，请稍后再试</p>
        <button class="btn-primary" @click="closeBookingModal">关闭</button>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.detail-page { padding-bottom: 40px; }

/* 英雄区样式 */
.detail-hero { display: grid; grid-template-columns: 1.1fr 1fr; overflow: hidden; margin-top: 36px; position: relative; }
.hero-images { position: relative; }
.main-image-wrapper { position: relative; overflow: hidden; }
.detail-cover { width: 100%; height: 100%; min-height: 420px; object-fit: cover; display: block; }
.image-overlay { position: absolute; inset: 0; background: linear-gradient(to right, transparent 0%, rgba(0,0,0,0.3) 100%); pointer-events: none; }

/* 轮播切换动画 */
.carousel-fade-enter-active,
.carousel-fade-leave-active { transition: opacity 0.4s ease; }
.carousel-fade-enter-from,
.carousel-fade-leave-to { opacity: 0; }

/* 左右切换箭头 */
.carousel-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 5;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: none;
  background: rgba(255,255,255,0.85);
  color: var(--text-primary);
  font-size: 22px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0,0,0,0.12);
  opacity: 0;
}
.hero-images:hover .carousel-arrow { opacity: 1; }
.carousel-arrow:hover { background: #fff; color: var(--color-primary); transform: translateY(-50%) scale(1.08); }
.carousel-prev { left: 12px; }
.carousel-next { right: 12px; }

/* 图片计数器 */
.carousel-counter {
  position: absolute;
  bottom: 16px;
  right: 16px;
  z-index: 5;
  padding: 4px 12px;
  border-radius: 20px;
  background: rgba(0,0,0,0.55);
  color: #fff;
  font-size: 13px;
  letter-spacing: 1px;
}

.thumbnails { position: absolute; bottom: 16px; left: 16px; display: flex; gap: 8px; z-index: 5; }
.thumbnail-item { width: 60px; height: 60px; border-radius: 8px; overflow: hidden; border: 2px solid transparent; cursor: pointer; opacity: 0.7; transition: all 0.2s; flex-shrink: 0; background: #f0ebe3; }
.thumbnail-item:hover, .thumbnail-item.active { opacity: 1; border-color: var(--color-primary); box-shadow: 0 2px 8px rgba(0,0,0,0.2); }
.thumbnail-item img { width: 100%; height: 100%; object-fit: cover; display: block; }
.detail-panel { padding: 36px; display: flex; flex-direction: column; justify-content: center; gap: 16px; position: relative; z-index: 1; }
.detail-summary { color: var(--text-secondary); font-size: 15px; line-height: 1.9; }
.detail-meta { display: flex; flex-wrap: wrap; gap: 16px; color: var(--text-muted); font-size: 13px; }
.detail-actions { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
.action-btn { display: flex; align-items: center; gap: 6px; padding: 10px 16px; border-radius: var(--radius-md); border: 1px solid var(--bg-glass-border); background: var(--bg-glass); cursor: pointer; transition: all 0.2s; }
.action-btn:hover { background: rgba(200,80,42,0.1); }
.action-btn.liked { background: rgba(255,77,79,0.1); border-color: #ff4d4f; color: #ff4d4f; }

/* 点赞按钮样式 */
.like-btn .like-icon {
  font-size: 18px;
  filter: grayscale(100%);
  opacity: 0.6;
  transition: all 0.2s;
}
.like-btn:hover .like-icon {
  filter: grayscale(50%);
  opacity: 0.8;
}
.like-btn.liked .like-icon {
  filter: grayscale(0%);
  opacity: 1;
}
.like-btn.liked {
  background: rgba(255,77,79,0.1);
  border-color: #ff4d4f;
  color: #ff4d4f;
}

/* 收藏按钮样式 */
.fav-btn .fav-icon { font-size: 16px; transition: all 0.2s; }
.fav-btn.favorited { background: rgba(245,166,35,0.1); border-color: #f5a623; color: #f5a623; }
.fav-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.back-btn { background: rgba(255,255,255,0.9); backdrop-filter: blur(4px); }

/* 内容网格 */
.content-grid { margin-top: 28px; display: grid; grid-template-columns: 1fr 320px; gap: 24px; }
.content-main { display: flex; flex-direction: column; gap: 18px; }
.content-card { padding: 26px; }
.content-card h3 { margin-bottom: 16px; font-size: 20px; }
.content-card p { color: var(--text-secondary); line-height: 1.9; }

/* 相关列表 */
.related-list { display: flex; flex-direction: column; gap: 12px; }
.related-item { display: flex; gap: 12px; padding: 12px; background: var(--bg-glass); border-radius: var(--radius-md); cursor: pointer; transition: all 0.2s; }
.related-item:hover { background: var(--bg-hover); }
.related-item img { width: 100px; height: 80px; object-fit: cover; border-radius: var(--radius-sm); }
.related-info { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.related-info strong { font-size: 15px; color: var(--text-primary); }
.related-meta { font-size: 12px; color: var(--text-muted); }
.related-price { font-size: 14px; color: var(--color-primary); font-weight: 600; }
.related-date { font-size: 12px; color: var(--text-muted); }

/* 评论区 */
.comments-section { padding: 26px; }
.comment-input { margin-bottom: 24px; }
.comment-input textarea { width: 100%; padding: 12px; border: 1px solid var(--bg-glass-border); border-radius: var(--radius-md); resize: none; outline: none; transition: border-color 0.2s; }
.comment-input textarea:focus { border-color: var(--color-primary); }
.comment-actions { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; }
.hint { font-size: 12px; color: var(--text-muted); }
.login-prompt { padding: 16px; background: var(--bg-glass); border-radius: var(--radius-md); text-align: center; }
.login-prompt a { color: var(--color-primary); }

.comments-list { display: flex; flex-direction: column; gap: 20px; }
.comment-item { padding-bottom: 20px; border-bottom: 1px solid var(--bg-glass-border); }
.comment-header { display: flex; gap: 10px; align-items: center; }
.comment-avatar { 
  width: 36px; 
  height: 36px; 
  border-radius: 50%; 
  object-fit: cover;
  background: var(--bg-glass);
}
.comment-meta { display: flex; flex-direction: column; }
.comment-author { font-weight: 600; color: var(--text-primary); }
.comment-time { font-size: 12px; color: var(--text-muted); }
.comment-content { margin-top: 10px; color: var(--text-secondary); line-height: 1.7; }
.comment-actions { margin-top: 10px; display: flex; gap: 16px; }
.comment-action { font-size: 13px; color: var(--text-muted); cursor: pointer; background: none; border: none; padding: 4px 8px; border-radius: var(--radius-sm); transition: all 0.2s; }
.comment-action:hover { background: var(--bg-glass); color: var(--text-primary); }
.comment-action.liked { color: var(--color-primary); background: rgba(200,80,42,0.1); }

.reply-box { margin-top: 12px; padding-left: 46px; }
.reply-box textarea { width: 100%; padding: 10px; border: 1px solid var(--bg-glass-border); border-radius: var(--radius-sm); resize: none; outline: none; font-size: 13px; }
.reply-actions { display: flex; gap: 8px; margin-top: 8px; }

.empty-comments { text-align: center; padding: 32px; color: var(--text-muted); }

/* 评论图片预览 */
.comment-images-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.preview-item {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.preview-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.preview-item .remove-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  background: rgba(0,0,0,0.6);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 14px;
}

/* 评论工具 */
.comment-tools {
  display: flex;
  gap: 12px;
}
.upload-btn, .emoji-btn {
  padding: 6px 12px;
  border: 1px solid var(--bg-glass-border);
  border-radius: var(--radius-sm);
  background: var(--bg-glass);
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}
.upload-btn:hover, .emoji-btn:hover {
  background: var(--bg-hover);
  border-color: var(--color-primary);
}

/* 表情包选择器 */
.emoji-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
  padding: 12px;
  background: var(--bg-glass);
  border-radius: var(--radius-md);
  max-height: 200px;
  overflow-y: auto;
}
.emoji-item {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}
.emoji-item:hover {
  background: rgba(200,80,42,0.1);
  transform: scale(1.2);
}

/* 评论图片展示 */
.comment-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.comment-image {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: var(--radius-sm);
}

/* 删除按钮 */
.delete-btn:hover {
  color: #ff4d4f !important;
}

/* 回复图片预览 */
.reply-images-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

/* 回复工具 */
.reply-tools {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}

/* 回复列表 */
.replies-list {
  margin-top: 12px;
  padding-left: 46px;
  border-left: 2px solid var(--bg-glass-border);
}
.reply-item {
  padding: 12px 0;
  border-bottom: 1px dashed var(--bg-glass-border);
}
.reply-item:last-child {
  border-bottom: none;
}
.reply-header {
  display: flex;
  gap: 10px;
  align-items: center;
}
.reply-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--bg-glass);
}
.reply-author {
  font-weight: 500;
  font-size: 13px;
  color: var(--text-primary);
}
.reply-time {
  font-size: 11px;
  color: var(--text-muted);
}
.reply-content {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
}
.reply-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.reply-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: var(--radius-sm);
}
.reply-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}
.reply-action {
  font-size: 12px;
  color: var(--text-muted);
  cursor: pointer;
  background: none;
  border: none;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  transition: all 0.2s;
}
.reply-action:hover {
  background: var(--bg-glass);
  color: var(--text-primary);
}
.reply-action.liked {
  color: var(--color-primary);
  background: rgba(200,80,42,0.1);
}

/* 侧边栏 */
.content-side { padding: 24px; height: fit-content; }
.content-side h3 { margin-bottom: 14px; }
.content-side h4 { margin-top: 24px; margin-bottom: 12px; }
.side-actions { display: flex; flex-direction: column; gap: 10px; }
.side-btn { width: 100%; text-align: left; background: var(--bg-glass); border: 1px solid var(--bg-glass-border); color: var(--text-primary); border-radius: 14px; padding: 12px 14px; cursor: pointer; transition: all 0.2s; }
.side-btn:hover { background: var(--bg-hover); border-color: var(--color-primary); }

/* 统计卡片 */
.stats-card { display: flex; justify-content: space-around; padding: 16px; background: var(--bg-glass); border-radius: var(--radius-md); }
.stat-item { text-align: center; }
.stat-value { display: block; font-size: 24px; font-weight: 700; color: var(--color-primary); }
.stat-label { font-size: 12px; color: var(--text-muted); }

.empty-wrap { padding-top: 80px; }
.empty-state { padding: 48px; display: flex; flex-direction: column; align-items: center; gap: 16px; text-align: center; }

/* 预约弹窗样式 */
.booking-modal-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.activity-info {
  display: flex;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--bg-glass-border);
}

.activity-image {
  width: 120px;
  height: 90px;
  object-fit: cover;
  border-radius: var(--radius-md);
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

.booking-form {
  padding-top: 16px;
  border-top: 1px solid var(--bg-glass-border);
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
}

.no-activity-tip p {
  margin-bottom: 20px;
  color: var(--text-secondary);
  font-size: 15px;
}

@media (max-width: 960px) {
  .detail-hero, .content-grid { grid-template-columns: 1fr; }
  .detail-hero { grid-template-rows: auto; }
  .hero-images { order: 1; }
  .detail-panel { order: 2; }
}
</style>