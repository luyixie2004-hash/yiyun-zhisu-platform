<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getInheritors } from '../api/inheritor'
import { getHeritageList } from '../api/heritage'
import { message } from 'ant-design-vue'
import { useUserNavigation } from '../composables/useUserNavigation'

const router = useRouter()
const { goToUserProfile } = useUserNavigation()
const activeCategory = ref('全部')
const loading = ref(false)
const inheritors = ref([])
const heritages = ref([])

const allInheritors = computed(() => {
  return inheritors.value.map((inheritor) => {
    const relatedHeritages = heritages.value.filter(h => h.inheritorName === (inheritor.nickname || inheritor.username))
    return {
      ...inheritor,
      name: inheritor.nickname || inheritor.username,
      skill: inheritor.skill || relatedHeritages[0]?.name || '非遗技艺',
      category: relatedHeritages[0]?.category || '传统技艺',
      region: inheritor.region || relatedHeritages[0]?.region || '中国',
      level: inheritor.level || relatedHeritages[0]?.level || '国家级',
      avatar: inheritor.avatar || `https://api.dicebear.com/9.x/adventurer/svg?seed=${encodeURIComponent(inheritor.nickname || inheritor.username)}`,
      heritageId: relatedHeritages[0]?.id,
      desc: inheritor.description || `${inheritor.nickname || inheritor.username}是非遗传承人，长期致力于非遗领域的保护与传承，在相关地区享有盛誉。`,
      relatedHeritages: relatedHeritages,
    }
  })
})

const categories = computed(() => {
  const cats = new Set(allInheritors.value.map((i) => i.category))
  return ['全部', ...cats]
})

const filteredInheritors = computed(() => {
  if (activeCategory.value === '全部') return allInheritors.value
  return allInheritors.value.filter((i) => i.category === activeCategory.value)
})

async function loadData() {
  loading.value = true
  try {
    const [inheritorRes, heritageRes] = await Promise.all([
      getInheritors(),
      getHeritageList({ size: 100 })
    ])
    inheritors.value = inheritorRes.data || []
    heritages.value = (heritageRes.data?.records || heritageRes.data || []).map(h => ({
      ...h,
      inheritorName: h.inheritor
    }))
  } catch (error) {
    console.error('加载传承人数据失败', error)
    message.error('加载传承人数据失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})

const selectedInheritor = ref(null)

function openDetail(inheritor) {
  selectedInheritor.value = inheritor
}

function closeDetail() {
  selectedInheritor.value = null
}

function goHeritage(id) {
  router.push(`/heritage/${id}`)
}

function goAI(name) {
  router.push(`/guide?topic=${encodeURIComponent(name + ' 传承故事')}`)
}
</script>

<template>
  <div class="page-container">
    <div class="container inheritors-page">
      <!-- 页面头部 -->
      <section class="page-hero glass-card">
        <div class="hero-deco"></div>
        <div class="hero-content">
          <div class="hero-badge">🏅 非遗传承</div>
          <h1 class="section-title">传承人 <span>专题</span></h1>
          <p class="hero-desc">传承人是非遗文化的活态载体，他们用双手守护技艺、以匠心延续文脉。走近每一位大师，感受薪火相传的力量。</p>
        </div>
        <div class="hero-stats">
          <div class="hstat"><span class="hstat-num">{{ allInheritors.length }}</span><span class="hstat-label">传承人</span></div>
          <div class="hstat"><span class="hstat-num">{{ categories.length - 1 }}</span><span class="hstat-label">技艺类别</span></div>
          <div class="hstat"><span class="hstat-num">{{ heritages.length }}</span><span class="hstat-label">非遗项目</span></div>
        </div>
      </section>

      <!-- 分类筛选 -->
      <div class="filter-bar">
        <span v-for="c in categories" :key="c" class="filter-tag" :class="{active: activeCategory===c}" @click="activeCategory=c">{{ c }}</span>
      </div>

      <!-- 传承人列表 -->
      <div v-if="loading" class="empty-state glass-card" style="padding: 48px; text-align: center; color: var(--text-muted);">加载中...</div>
      <div v-else class="inheritor-grid">
        <div v-for="person in filteredInheritors" :key="person.name" class="inheritor-card glass-card card-hover" @click="openDetail(person)">
          <div class="icard-header">
            <img :src="person.avatar" :alt="person.name" class="icard-avatar" />
            <div class="icard-level">{{ person.level }}</div>
          </div>
          <div class="icard-body">
            <h3>{{ person.name }}</h3>
            <div class="icard-skill"><span class="tag tag-red">{{ person.category }}</span> {{ person.skill }}</div>
            <div class="icard-region">📍 {{ person.region }}</div>
            <p class="icard-desc">{{ person.desc }}</p>
            <div class="icard-actions">
              <button class="btn-primary" style="padding:5px 14px;font-size:12px" @click.stop="goAI(person.name)">AI 解读</button>
              <button class="btn-ghost" style="padding:5px 14px;font-size:12px" @click.stop="goHeritage(person.heritageId)">查看项目</button>
              <button class="btn-ghost" style="padding:5px 14px;font-size:12px" @click.stop="goToUserProfile(person.id)">查看主页</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 传承人详情弹窗 -->
      <teleport to="body">
        <div v-if="selectedInheritor" class="modal-overlay" @click.self="closeDetail">
          <div class="modal-content glass-card">
            <button class="modal-close" @click="closeDetail">✕</button>
            <div class="modal-header">
              <img :src="selectedInheritor.avatar" :alt="selectedInheritor.name" class="modal-avatar clickable-user" @click="goToUserProfile(selectedInheritor.id)" />
              <div>
                <h2>{{ selectedInheritor.name }}</h2>
                <div class="modal-tags">
                  <span class="tag tag-red">{{ selectedInheritor.category }}</span>
                  <span class="tag tag-gold">{{ selectedInheritor.level }}</span>
                  <span class="tag tag-blue">{{ selectedInheritor.region }}</span>
                </div>
              </div>
            </div>
            <p class="modal-desc">{{ selectedInheritor.desc }}</p>
            <h4>关联非遗项目</h4>
            <div class="modal-heritages">
              <div v-for="h in selectedInheritor.relatedHeritages" :key="h.id" class="mh-item" @click="goHeritage(h.id); closeDetail()">
                <img :src="h.imageUrl" :alt="h.name" class="mh-img" v-img-fallback />
                <div>
                  <strong>{{ h.name }}</strong>
                  <p>{{ h.region }} · {{ h.category }}</p>
                </div>
              </div>
            </div>
            <div class="modal-actions">
              <button class="btn-primary" @click="goAI(selectedInheritor.name); closeDetail()">让 AI 讲述传承故事</button>
              <button class="btn-ghost" @click="$router.push(`/explore?q=${encodeURIComponent(selectedInheritor.name)}`); closeDetail()">搜索相关内容</button>
              <button class="btn-ghost" @click="goToUserProfile(selectedInheritor.id); closeDetail()">查看主页</button>
            </div>
          </div>
        </div>
      </teleport>
    </div>
  </div>
</template>

<style scoped>
.inheritors-page { padding-top: 36px; padding-bottom: 40px; display: flex; flex-direction: column; gap: 24px; }
.page-hero { padding: 36px; display: flex; align-items: center; gap: 28px; position: relative; overflow: hidden; }
.hero-deco { position: absolute; top: -40px; right: -40px; width: 200px; height: 200px; border-radius: 50%; background: radial-gradient(circle, rgba(200,80,42,0.08), transparent 70%); pointer-events: none; }
.hero-content { flex: 1; }
.hero-badge { display: inline-block; padding: 4px 14px; border-radius: var(--radius-full); background: rgba(200,80,42,0.08); color: var(--color-primary); font-size: 13px; font-weight: 600; margin-bottom: 12px; }
.hero-desc { color: var(--text-secondary); line-height: 1.9; margin-top: 12px; max-width: 600px; }
.hero-stats { display: flex; gap: 20px; }
.hstat { text-align: center; padding: 14px 20px; border-radius: var(--radius-lg); background: var(--bg-glass); border: 1px solid var(--bg-glass-border); }
.hstat-num { display: block; font-size: 28px; font-weight: 700; color: var(--color-primary-dark); }
.hstat-label { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
.filter-bar { display: flex; flex-wrap: wrap; gap: 6px; }
.filter-tag { padding: 6px 14px; border-radius: var(--radius-full); font-size: 13px; color: var(--text-secondary); background: var(--bg-glass); border: 1px solid var(--bg-glass-border); cursor: pointer; transition: all var(--transition-fast); }
.filter-tag.active, .filter-tag:hover { background: rgba(200,80,42,0.15); border-color: var(--color-primary); color: var(--color-primary-light); }
.inheritor-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px; }
.inheritor-card { overflow: hidden; cursor: pointer; }
.icard-header { position: relative; display: flex; justify-content: center; padding: 24px 0 12px; background: linear-gradient(135deg, rgba(200,80,42,0.06), rgba(212,165,83,0.06)); }
.icard-avatar { width: 80px; height: 80px; border-radius: 50%; border: 3px solid var(--color-primary); background: #fff; }
.icard-level { position: absolute; top: 10px; right: 10px; padding: 3px 10px; border-radius: var(--radius-full); background: rgba(212,165,83,0.9); color: #fff; font-size: 11px; font-weight: 700; }
.icard-body { padding: 16px; text-align: center; display: flex; flex-direction: column; gap: 6px; }
.icard-body h3 { font-family: var(--font-serif); font-size: 16px; }
.icard-skill { font-size: 13px; color: var(--text-secondary); display: flex; align-items: center; justify-content: center; gap: 6px; }
.icard-region { font-size: 12px; color: var(--text-muted); }
.icard-desc { font-size: 12px; color: var(--text-secondary); line-height: 1.7; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.icard-actions { display: flex; gap: 8px; justify-content: center; margin-top: 8px; }
/* Modal */
.modal-overlay { position: fixed; inset: 0; z-index: 2000; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,0.4); backdrop-filter: blur(4px); animation: fadeIn 0.2s; }
@keyframes fadeIn { from { opacity: 0 } to { opacity: 1 } }
.modal-content { width: 90%; max-width: 560px; max-height: 80vh; overflow-y: auto; padding: 28px; border-radius: var(--radius-lg); position: relative; animation: slideUp 0.3s ease; }
@keyframes slideUp { from { opacity: 0; transform: translateY(24px) } to { opacity: 1; transform: translateY(0) } }
.modal-close { position: absolute; top: 14px; right: 16px; background: none; border: none; font-size: 20px; cursor: pointer; color: var(--text-muted); }
.modal-header { display: flex; gap: 16px; align-items: center; margin-bottom: 16px; }
.modal-avatar { width: 72px; height: 72px; border-radius: 50%; border: 3px solid var(--color-primary); }
.modal-header h2 { font-family: var(--font-serif); font-size: 22px; }
.modal-tags { display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap; }
.modal-desc { color: var(--text-secondary); line-height: 1.9; margin-bottom: 18px; }
.modal-content h4 { margin-bottom: 10px; font-size: 15px; }
.modal-heritages { display: flex; flex-direction: column; gap: 8px; margin-bottom: 18px; }
.mh-item { display: flex; gap: 12px; padding: 10px; border-radius: var(--radius-md); background: var(--bg-glass); cursor: pointer; transition: background var(--transition-fast); }
.mh-item:hover { background: rgba(200,80,42,0.06); }
.mh-img { width: 64px; height: 48px; border-radius: var(--radius-sm); object-fit: cover; }
.mh-item strong { font-size: 14px; }
.mh-item p { font-size: 12px; color: var(--text-muted); margin-top: 2px; }
.modal-actions { display: flex; gap: 10px; }
@media (max-width: 1100px) { .inheritor-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 768px) { .inheritor-grid { grid-template-columns: repeat(2, 1fr); } .page-hero { flex-direction: column; text-align: center; } .hero-stats { justify-content: center; } }
@media (max-width: 480px) { .inheritor-grid { grid-template-columns: 1fr; } }
</style>
