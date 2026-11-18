<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'


const router = useRouter()
const contentLeaving = ref(false)

// ── 时序叙事 ──
const timeOfDay = ref('noon') // dawn | noon | dusk | night
function detectTimeOfDay() {
  const h = new Date().getHours()
  if (h >= 5 && h < 10) return 'dawn'
  if (h >= 10 && h < 16) return 'noon'
  if (h >= 16 && h < 20) return 'dusk'
  return 'night'
}
// ── 标题字符交互 ──
const hoveredChar = ref(-1)
const titleChars = ['华', '脉', '万', '象']

// ── 标题笔画动画 ──
const titleRevealPhase = ref('waiting') // waiting | revealing | done

// ── AI 打字机副标题 ──
const typewriterText = ref('')
const typewriterDone = ref(false)
const typewriterCursor = ref(true)
const fullSubtitle = '融合大模型与AIGC技术的非遗全景探索引擎'
let typewriterTimer = null
let cursorBlinkTimer = null

function startTypewriter() {
  let i = 0
  typewriterText.value = ''
  typewriterDone.value = false
  typewriterCursor.value = true
  typewriterTimer = setInterval(() => {
    if (i < fullSubtitle.length) {
      typewriterText.value += fullSubtitle[i]
      i++
    } else {
      clearInterval(typewriterTimer)
      typewriterTimer = null
      typewriterDone.value = true
      cursorBlinkTimer = setInterval(() => {
        typewriterCursor.value = !typewriterCursor.value
      }, 530)
    }
  }, 55 + Math.random() * 35) // 随机间隔，模拟 AI 生成的不确定性
}

function stopTypewriter() {
  if (typewriterTimer) { clearInterval(typewriterTimer); typewriterTimer = null }
  if (cursorBlinkTimer) { clearInterval(cursorBlinkTimer); cursorBlinkTimer = null }
}

// ── 非遗色板轮转 ──
const heritagePalette = ref('ink') // porcelain | dunhuang | sancai | ink
const palettes = ['porcelain', 'dunhuang', 'sancai', 'ink']
const paletteLabels = { porcelain: '青花', dunhuang: '敦煌', sancai: '三彩', ink: '水墨' }

function cycleTimeOfDay() {
  const phases = ['dawn', 'noon', 'dusk', 'night']
  const idx = phases.indexOf(timeOfDay.value)
  timeOfDay.value = phases[(idx + 1) % 4]
  // 同时轮转色板
  const pIdx = palettes.indexOf(heritagePalette.value)
  heritagePalette.value = palettes[(pIdx + 1) % 4]
}

// ── 数字印章 ──
const seals = ref([])
let sealIdCounter = 0

function spawnSeal(x, y) {
  const id = ++sealIdCounter
  seals.value.push({ id, x, y })
  // 盖章瞬间爆发金色粒子
  for (let i = 0; i < 12; i++) {
    const a = (i / 12) * Math.PI * 2
    const spd = 1.5 + Math.random() * 3
    particles.push({
      x, y,
      vx: Math.cos(a) * spd,
      vy: Math.sin(a) * spd - 0.8,
      life: 0.7, decay: 0.02,
      size: 2 + Math.random() * 3.5,
      color: 'rgba(255,210,100,?)',
      type: 'gold', shape: 'spark',
      seed: Math.random() * Math.PI * 2,
    })
  }
  setTimeout(() => {
    seals.value = seals.value.filter(s => s.id !== id)
  }, 2600)
}

// ── 光标墨迹拖尾 ──
let inkTrail = []
const INK_TRAIL_MAX = 50
let lastTrailTime = 0


// ── 水墨入场状态 ──
const inkCanvasRef = ref(null)
const inkPhase = ref('idle') // idle | playing | fading | done
const contentVisible = ref(false)

// ── 粒子与涟漪交互画布 ──
const fxCanvasRef = ref(null)
let fxCtx = null
let fxCanvas = null
let fxRafId = null
let particles = []
let ripples = []
let mouseX = -100
let mouseY = -100
let mouseOnPage = false

const FX_PARTICLE_MAX = 100

function spawnParticle(x, y, type = 'gold', shape = null) {
  const angle = Math.random() * Math.PI * 2
  const speed = 0.8 + Math.random() * 3.5
  // 鎏金色系：暖金到亮金，模拟 AI 光泽
  const goldColors = ['rgba(255,225,140,?)', 'rgba(240,185,60,?)', 'rgba(255,248,205,?)', 'rgba(220,160,50,?)']
  // 墨花飞溅：浓墨到焦墨，模拟宣纸墨点
  const inkColors = ['rgba(35,18,8,?)', 'rgba(50,28,14,?)', 'rgba(25,12,5,?)', 'rgba(60,35,18,?)']
  const colors = type === 'gold' ? goldColors : inkColors
  // shape: 'spark' (gold diamond), 'splatter' (ink blob), 'motif' (tiny swirl), null = auto
  const resolvedShape = shape || (type === 'gold' ? 'spark' : 'splatter')
  return {
    x, y,
    vx: Math.cos(angle) * speed,
    vy: Math.sin(angle) * speed - 0.5,
    life: 0.7 + Math.random() * 0.3,
    decay: 0.006 + Math.random() * 0.02,
    size: resolvedShape === 'motif' ? 3 + Math.random() * 5 : 1.5 + Math.random() * 4.5,
    color: colors[Math.floor(Math.random() * colors.length)],
    type,
    shape: resolvedShape,
    // 用于 splatter 的随机偏移种子
    seed: Math.random() * Math.PI * 2,
  }
}

function spawnMotif(x, y) {
  // 非遗纹样碎片：云纹、回纹两种
  const motifType = Math.random() > 0.5 ? 'cloud' : 'fret'
  const angle = Math.random() * Math.PI * 2
  const speed = 0.3 + Math.random() * 1.2
  return {
    x, y,
    vx: Math.cos(angle) * speed,
    vy: Math.sin(angle) * speed - 1.2,
    life: 0.8 + Math.random() * 0.2,
    decay: 0.004 + Math.random() * 0.008,
    size: 2.5 + Math.random() * 4,
    color: 'rgba(255,215,120,?)',
    type: 'motif',
    shape: 'motif',
    motifType,
    seed: Math.random() * Math.PI * 2,
  }
}

function spawnRipple(x, y) {
  return {
    x, y,
    radius: 0,
    maxRadius: 50 + Math.random() * 80,
    life: 1,
    decay: 0.012 + Math.random() * 0.025,
  }
}

function setupFxCanvas() {
  fxCanvas = fxCanvasRef.value
  if (!fxCanvas) return
  fxCtx = fxCanvas.getContext('2d')
  const dpr = window.devicePixelRatio || 1
  const w = window.innerWidth
  const h = window.innerHeight
  fxCanvas.width = w * dpr
  fxCanvas.height = h * dpr
  fxCanvas.style.width = w + 'px'
  fxCanvas.style.height = h + 'px'
  fxCtx.setTransform(dpr, 0, 0, dpr, 0, 0)
}

function fxAnimate(timestamp) {
  fxRafId = requestAnimationFrame(fxAnimate)
  if (!fxCtx || !fxCanvas) return

  const w = window.innerWidth
  const h = window.innerHeight
  fxCtx.clearRect(0, 0, w, h)

  // ── 光标墨迹拖尾 ──
  if (mouseOnPage) {
    const now = performance.now()
    if (now - lastTrailTime > 16) { // ~60fps 采样
      inkTrail.push({ x: mouseX, y: mouseY, life: 1, size: 2.5 + Math.random() * 4 })
      lastTrailTime = now
    }
    // 额外金粒子
    for (let i = 0; i < 4; i++) {
      particles.push(spawnParticle(
        mouseX + (Math.random() - 0.5) * 16,
        mouseY + (Math.random() - 0.5) * 16,
        Math.random() > 0.35 ? 'gold' : 'ink'
      ))
    }
  }

  // 限制墨迹点数量
  while (inkTrail.length > INK_TRAIL_MAX) inkTrail.shift()

  // 绘制墨迹拖尾（水墨渗透效果）
  if (inkTrail.length >= 2) {
    fxCtx.save()
    fxCtx.lineCap = 'round'
    fxCtx.lineJoin = 'round'
    for (let i = 1; i < inkTrail.length; i++) {
      const p0 = inkTrail[i - 1]
      const p1 = inkTrail[i]
      const alpha = p1.life * 0.35
      if (alpha <= 0.01) continue

      // 主墨迹：宽、淡，模拟宣纸渗透
      fxCtx.globalAlpha = alpha * 0.6
      fxCtx.strokeStyle = `rgba(60,30,15,1)`
      fxCtx.lineWidth = p1.size * 5 + 6
      fxCtx.beginPath()
      fxCtx.moveTo(p0.x, p0.y)
      fxCtx.lineTo(p1.x, p1.y)
      fxCtx.stroke()

      // 中层：稍窄，略深
      fxCtx.globalAlpha = alpha * 0.8
      fxCtx.strokeStyle = `rgba(40,20,10,1)`
      fxCtx.lineWidth = p1.size * 2.5 + 2
      fxCtx.beginPath()
      fxCtx.moveTo(p0.x, p0.y)
      fxCtx.lineTo(p1.x, p1.y)
      fxCtx.stroke()

      // 核心线：细、深
      fxCtx.globalAlpha = alpha
      fxCtx.strokeStyle = `rgba(20,10,5,1)`
      fxCtx.lineWidth = p1.size
      fxCtx.beginPath()
      fxCtx.moveTo(p0.x, p0.y)
      fxCtx.lineTo(p1.x, p1.y)
      fxCtx.stroke()

      // 金色微光边缘
      fxCtx.globalAlpha = alpha * 0.3
      fxCtx.strokeStyle = `rgba(216,162,75,1)`
      fxCtx.lineWidth = p1.size * 3.5 + 4
      fxCtx.beginPath()
      fxCtx.moveTo(p0.x, p0.y)
      fxCtx.lineTo(p1.x, p1.y)
      fxCtx.stroke()
    }

    // 墨迹生命周期衰减
    for (let i = inkTrail.length - 1; i >= 0; i--) {
      inkTrail[i].life -= 0.018
      if (inkTrail[i].life <= 0) inkTrail.splice(i, 1)
    }
    fxCtx.restore()
  }

  // 限制粒子总数
  while (particles.length > FX_PARTICLE_MAX) particles.shift()

  // 绘制粒子（墨花飞溅 + 鎏金火花 + 纹样碎片）
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i]
    p.x += p.vx
    p.y += p.vy
    p.vy += 0.025
    p.life -= p.decay
    if (p.life <= 0) { particles.splice(i, 1); continue }

    fxCtx.save()
    fxCtx.globalAlpha = p.life
    const c = p.color.replace('?', String(Math.min(1, p.life * 1.2)))
    fxCtx.fillStyle = c
    fxCtx.shadowColor = c
    fxCtx.shadowBlur = p.size * 5

    if (p.shape === 'spark') {
      // 鎏金火花：四角星形
      const sz = p.size * (0.4 + p.life * 0.6)
      fxCtx.beginPath()
      fxCtx.moveTo(p.x, p.y - sz)
      fxCtx.lineTo(p.x + sz * 0.3, p.y - sz * 0.25)
      fxCtx.lineTo(p.x + sz, p.y)
      fxCtx.lineTo(p.x + sz * 0.3, p.y + sz * 0.25)
      fxCtx.lineTo(p.x, p.y + sz)
      fxCtx.lineTo(p.x - sz * 0.3, p.y + sz * 0.25)
      fxCtx.lineTo(p.x - sz, p.y)
      fxCtx.lineTo(p.x - sz * 0.3, p.y - sz * 0.25)
      fxCtx.closePath()
      fxCtx.fill()
      // 中心亮核
      fxCtx.globalAlpha = p.life * 1.3
      fxCtx.shadowBlur = p.size * 8
      fxCtx.beginPath()
      fxCtx.arc(p.x, p.y, sz * 0.25, 0, Math.PI * 2)
      fxCtx.fill()
    } else if (p.shape === 'motif') {
      // 纹样碎片：云纹弧线或回纹折角
      fxCtx.lineWidth = p.size * 0.45
      fxCtx.strokeStyle = c
      fxCtx.shadowColor = 'rgba(255,200,80,0.6)'
      fxCtx.shadowBlur = p.size * 3
      fxCtx.beginPath()
      const ms = p.size * (0.5 + p.life * 0.5)
      if (p.motifType === 'cloud') {
        // 云纹：多层重叠弧线
        for (let a = 0; a < 3; a++) {
          const r = ms * (0.5 + a * 0.25)
          fxCtx.arc(p.x, p.y, r, p.seed + a * 0.6, p.seed + Math.PI * 0.7 + a * 0.4)
        }
      } else {
        // 回纹：小折线
        fxCtx.moveTo(p.x - ms, p.y - ms * 0.6)
        fxCtx.lineTo(p.x - ms * 0.3, p.y - ms * 0.6)
        fxCtx.lineTo(p.x - ms * 0.3, p.y + ms * 0.3)
        fxCtx.lineTo(p.x + ms * 0.3, p.y + ms * 0.3)
        fxCtx.lineTo(p.x + ms * 0.3, p.y - ms * 0.3)
        fxCtx.lineTo(p.x + ms, p.y - ms * 0.3)
      }
      fxCtx.stroke()
      fxCtx.lineWidth = 1
    } else {
      // 墨花飞溅：不规则墨团（3-4个偏移圆叠加）
      const sz = p.size * (0.5 + p.life * 0.5)
      for (let b = 0; b < 4; b++) {
        const bx = p.x + Math.cos(p.seed + b * 1.8) * sz * 0.4
        const by = p.y + Math.sin(p.seed + b * 1.8) * sz * 0.4
        const br = sz * (0.3 + (b === 0 ? 0.35 : Math.random() * 0.2))
        fxCtx.beginPath()
        fxCtx.arc(bx, by, br, 0, Math.PI * 2)
        fxCtx.fill()
      }
      // 飞溅小卫星
      fxCtx.globalAlpha = p.life * 0.5
      for (let s = 0; s < 2; s++) {
        const sx = p.x + Math.cos(p.seed + s * 2.5) * sz * 0.9
        const sy = p.y + Math.sin(p.seed + s * 2.5) * sz * 0.9
        fxCtx.beginPath()
        fxCtx.arc(sx, sy, sz * 0.12, 0, Math.PI * 2)
        fxCtx.fill()
      }
    }
    fxCtx.restore()
  }

  // 绘制涟漪
  for (let i = ripples.length - 1; i >= 0; i--) {
    const r = ripples[i]
    r.radius += (r.maxRadius - r.radius) * 0.07 + 0.3
    r.life -= r.decay
    if (r.life <= 0) { ripples.splice(i, 1); continue }

    fxCtx.save()
    fxCtx.globalAlpha = r.life * 0.25
    fxCtx.strokeStyle = `rgba(216,162,75,${r.life * 0.45})`
    fxCtx.lineWidth = 2 * r.life
    fxCtx.beginPath()
    fxCtx.arc(r.x, r.y, r.radius, 0, Math.PI * 2)
    fxCtx.stroke()

    // 内光环
    fxCtx.globalAlpha = r.life * 0.1
    fxCtx.strokeStyle = `rgba(240,215,140,${r.life * 0.25})`
    fxCtx.lineWidth = 4 * r.life
    fxCtx.beginPath()
    fxCtx.arc(r.x, r.y, r.radius * 0.55, 0, Math.PI * 2)
    fxCtx.stroke()
    fxCtx.restore()
  }

}

// ── 全局点击涟漪 + 粒子爆发 + 数字印章 ──
function onPageClick(e) {
  // 跳过交互节点自身的点击（节点有自己的特效）
  if (e.target.closest('.enter-btn') || e.target.closest('.time-dial')) return

  ripples.push(spawnRipple(e.clientX, e.clientY))
  // 墨花飞溅 (ink splatter)
  for (let i = 0; i < 8; i++) {
    particles.push(spawnParticle(e.clientX, e.clientY, 'ink'))
  }
  // 鎏金火花 (golden sparks)
  for (let i = 0; i < 6; i++) {
    particles.push(spawnParticle(e.clientX, e.clientY, 'gold'))
  }
  // 非遗纹样碎片 (heritage motif fragments)
  for (let i = 0; i < 2; i++) {
    particles.push(spawnMotif(e.clientX, e.clientY))
  }
  // 数字印章
  spawnSeal(e.clientX, e.clientY)
}

// ── 光标追踪 ──
function onMouseMove(e) {
  mouseX = e.clientX
  mouseY = e.clientY
  if (!mouseOnPage) mouseOnPage = true
  // 磁吸纹样 CSS 变量
  const mx = (e.clientX / window.innerWidth - 0.5) * 40
  const my = (e.clientY / window.innerHeight - 0.5) * 40
  document.documentElement.style.setProperty('--mx', `${mx}px`)
  document.documentElement.style.setProperty('--my', `${my}px`)
}

function onMouseLeave() {
  mouseOnPage = false
}


// ── 特效画布缩放 ──
function onFxResize() {
  if (!fxCanvas || !fxCtx) return
  const dpr = window.devicePixelRatio || 1
  const w = window.innerWidth
  const h = window.innerHeight
  fxCanvas.width = w * dpr
  fxCanvas.height = h * dpr
  fxCanvas.style.width = w + 'px'
  fxCanvas.style.height = h + 'px'
  fxCtx.setTransform(dpr, 0, 0, dpr, 0, 0)
}

let inkCtx = null
let inkCanvas = null
let inkRafId = null
let inkStartTime = 0
let inkDrops = []

const INK_TOTAL = 5000   // 墨迹扩散总时长 (ms)
const INK_FADE = 700      // 画布淡出时长 (ms)
const CONTENT_TRIGGER = 0.72 // 帷幕擦除到 72% 时开始显现 Hero 内容

// ── 创建墨滴 ──
function createInkDrops(w, h) {
  const cx = w / 2
  const cy = h / 2
  const diag = Math.sqrt(w * w + h * h)
  return [
    { x: cx,             y: cy * 0.85,        maxR: diag * 0.55, delay: 0 },
    { x: cx * 0.45,      y: cy * 0.6,         maxR: diag * 0.38, delay: 280 },
    { x: cx * 1.55,      y: cy * 0.55,        maxR: diag * 0.36, delay: 400 },
    { x: cx * 0.55,      y: cy * 1.35,        maxR: diag * 0.4,  delay: 520 },
    { x: cx * 1.5,       y: cy * 1.25,        maxR: diag * 0.35, delay: 620 },
    { x: cx * 0.3,       y: cy * 0.25,        maxR: diag * 0.3,  delay: 700 },
    { x: cx * 1.7,       y: cy * 0.28,        maxR: diag * 0.28, delay: 780 },
  ]
}

// ── 绘制单颗墨滴（遮罩擦除模式：洗去帷幕，透出内容） ──
function drawInkDrop(drop, elapsed) {
  const activeTime = elapsed - drop.delay
  if (activeTime <= 0) return

  const progress = Math.min(activeTime / (INK_TOTAL - drop.delay), 1)
  // 缓动：墨迹前期扩散快、后期慢，模拟真实渗透
  const eased = 1 - Math.pow(1 - progress, 3.5)
  drop.radius = drop.maxR * eased
  if (drop.radius < 3) return

  const ctx = inkCtx
  const { x, y, radius } = drop

  // 径向渐变：中心完全擦除（透出内容），边缘渐弱
  const grad = ctx.createRadialGradient(x, y, radius * 0.08, x, y, radius)
  grad.addColorStop(0, 'rgba(255,255,255,1)')
  grad.addColorStop(0.35, 'rgba(255,255,255,0.92)')
  grad.addColorStop(0.6, 'rgba(255,255,255,0.55)')
  grad.addColorStop(0.82, 'rgba(255,255,255,0.18)')
  grad.addColorStop(1, 'rgba(255,255,255,0)')

  ctx.save()
  ctx.globalCompositeOperation = 'destination-out'

  ctx.beginPath()
  const segments = 90
  for (let i = 0; i <= segments; i++) {
    const angle = (i / segments) * Math.PI * 2
    // 有机边缘扰动（多频率叠加，模拟宣纸纤维吸墨）
    const noise =
      0.87 +
      0.13 * (
        Math.sin(angle * 8 + drop.x * 0.02) * Math.cos(angle * 5 + drop.y * 0.02) +
        Math.sin(angle * 14 + drop.x * 0.03) * Math.cos(angle * 11 + drop.y * 0.03) * 0.6 +
        Math.sin(angle * 3 + drop.y * 0.01) * 0.4
      )
    const r = radius * Math.max(0.82, Math.min(1.12, noise))
    const px = x + Math.cos(angle) * r
    const py = y + Math.sin(angle) * r
    if (i === 0) ctx.moveTo(px, py)
    else ctx.lineTo(px, py)
  }
  ctx.closePath()
  ctx.fillStyle = grad
  ctx.fill()

  ctx.restore()
}

// ── 绘制暗色帷幕（遮盖内容，等待墨滴洗开） ──
function drawDarkCurtain(w, h) {
  inkCtx.fillStyle = '#0b0907'
  inkCtx.fillRect(0, 0, w, h)
}

// ── 初始化水墨 Canvas ──
function setupInkCanvas() {
  inkCanvas = inkCanvasRef.value
  if (!inkCanvas) return
  inkCtx = inkCanvas.getContext('2d')
  const dpr = window.devicePixelRatio || 1
  const w = window.innerWidth
  const h = window.innerHeight
  inkCanvas.width = w * dpr
  inkCanvas.height = h * dpr
  inkCanvas.style.width = w + 'px'
  inkCanvas.style.height = h + 'px'
  inkCtx.setTransform(dpr, 0, 0, dpr, 0, 0)
  drawDarkCurtain(w, h)
}

// ── 水墨动画循环 ──
function inkAnimate(timestamp) {
  if (!inkCtx || !inkCanvas) return
  if (!inkStartTime) inkStartTime = timestamp

  const elapsed = timestamp - inkStartTime

  // 绘制所有墨滴（destination-out 模式逐步擦除帷幕，透出内容）
  for (const drop of inkDrops) {
    drawInkDrop(drop, elapsed)
  }

  // 在合适时机触发内容显现 + AI 打字机
  if (!contentVisible.value && elapsed > INK_TOTAL * CONTENT_TRIGGER) {
    contentVisible.value = true
    startTypewriter()
  }

  if (elapsed < INK_TOTAL) {
    inkRafId = requestAnimationFrame(inkAnimate)
  } else {
    // 墨迹扩散完毕，笔画同步完成 → 淡出画布
    titleRevealPhase.value = 'done'
    inkPhase.value = 'fading'
    setTimeout(() => {
      inkPhase.value = 'done'
    }, INK_FADE)
  }
}

// ── 启动水墨入场 ──
function startInkEntrance() {
  setupInkCanvas()
  const w = window.innerWidth
  const h = window.innerHeight
  inkDrops = createInkDrops(w, h)
  inkPhase.value = 'playing'
  // 笔画动画与墨滴同时开始，持续相同时长
  titleRevealPhase.value = 'revealing'
  inkRafId = requestAnimationFrame(inkAnimate)
}

// ── 处理窗口缩放 ──
function onInkResize() {
  if (!inkCanvas || !inkCtx || inkPhase.value === 'done') return
  const dpr = window.devicePixelRatio || 1
  const w = window.innerWidth
  const h = window.innerHeight
  inkCanvas.width = w * dpr
  inkCanvas.height = h * dpr
  inkCanvas.style.width = w + 'px'
  inkCanvas.style.height = h + 'px'
  inkCtx.setTransform(dpr, 0, 0, dpr, 0, 0)
  drawDarkCurtain(w, h)
  inkDrops = createInkDrops(w, h)
  inkStartTime = performance.now()
}

// ── 点击进入平台 ──
function handleEnter() {
  if (contentLeaving.value) return
  localStorage.setItem('huamai_visited', '1')
  contentLeaving.value = true
  const token = localStorage.getItem('yiyun_token')
  setTimeout(() => {
    router.push(token ? '/home' : '/login')
  }, 800)
}

// ── 生命周期 ──
onMounted(() => {
  timeOfDay.value = detectTimeOfDay()
  startInkEntrance()
  setupFxCanvas()
  fxRafId = requestAnimationFrame(fxAnimate)
  window.addEventListener('resize', onInkResize)
  window.addEventListener('resize', onFxResize)
  window.addEventListener('mousemove', onMouseMove, { passive: true })
  window.addEventListener('mouseleave', onMouseLeave)
  window.addEventListener('click', onPageClick)
})

onUnmounted(() => {
  if (inkRafId) cancelAnimationFrame(inkRafId)
  if (fxRafId) cancelAnimationFrame(fxRafId)
  stopTypewriter()
  window.removeEventListener('resize', onInkResize)
  window.removeEventListener('resize', onFxResize)
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseleave', onMouseLeave)
  window.removeEventListener('click', onPageClick)
  inkCtx = null
  inkCanvas = null
  fxCtx = null
  fxCanvas = null
})
</script>

<template>
  <div class="landing-root" :class="['tod-' + timeOfDay, 'pal-' + heritagePalette]">

    <!-- SVG 滤镜定义 -->
    <svg class="hidden-defs" xmlns="http://www.w3.org/2000/svg" style="position:absolute;width:0;height:0">
      <defs>
        <filter id="dialGlow">
          <feGaussianBlur stdDeviation="2.5" result="blur"/>
          <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
        </filter>
      </defs>
    </svg>

    <!-- 视频背景 -->
    <video
      class="landing-video"
      src="/videos/bg-video.mp4"
      autoplay
      muted
      loop
      playsinline
      preload="auto"
    ></video>

    <!-- 非遗碎片漂浮 -->
    <div class="heritage-fragments">
      <!-- 青花瓷瓶 -->
      <svg class="frag frag-vase" viewBox="0 0 80 120"><path d="M40,8 C28,8 18,16 18,28 L12,60 C10,68 12,76 18,82 C22,86 24,92 24,98 L24,112 L56,112 L56,98 C56,92 58,86 62,82 C68,76 70,68 68,60 L62,28 C62,16 52,8 40,8 Z" fill="rgba(100,160,210,0.06)" stroke="rgba(100,160,210,0.55)" stroke-width="1.6"/><path d="M28,36 C32,30 48,30 52,36" fill="none" stroke="rgba(100,160,210,0.4)" stroke-width="1.2"/></svg>
      <!-- 京剧脸谱 -->
      <svg class="frag frag-mask" viewBox="0 0 100 110"><ellipse cx="50" cy="55" rx="40" ry="44" fill="rgba(220,110,70,0.04)" stroke="rgba(220,110,70,0.5)" stroke-width="1.6"/><circle cx="35" cy="45" r="7" fill="none" stroke="rgba(220,110,70,0.4)" stroke-width="1.2"/><circle cx="65" cy="45" r="7" fill="none" stroke="rgba(220,110,70,0.4)" stroke-width="1.2"/><path d="M42,65 Q50,72 58,65" fill="none" stroke="rgba(220,110,70,0.35)" stroke-width="1.2"/><path d="M18,20 Q30,10 50,16 Q70,10 82,20" fill="none" stroke="rgba(220,110,70,0.3)" stroke-width="1.2"/></svg>
      <!-- 古琴 -->
      <svg class="frag frag-qin" viewBox="0 0 160 40"><rect x="8" y="10" width="144" height="20" rx="10" fill="rgba(210,170,90,0.04)" stroke="rgba(210,170,90,0.5)" stroke-width="1.6"/><line x1="30" y1="10" x2="30" y2="30" stroke="rgba(210,170,90,0.35)" stroke-width="1"/><line x1="50" y1="10" x2="50" y2="30" stroke="rgba(210,170,90,0.35)" stroke-width="1"/><line x1="70" y1="10" x2="70" y2="30" stroke="rgba(210,170,90,0.35)" stroke-width="1"/><line x1="90" y1="10" x2="90" y2="30" stroke="rgba(210,170,90,0.35)" stroke-width="1"/><line x1="110" y1="10" x2="110" y2="30" stroke="rgba(210,170,90,0.35)" stroke-width="1"/><line x1="8" y1="20" x2="152" y2="20" stroke="rgba(210,170,90,0.45)" stroke-width="0.8"/></svg>
      <!-- 剪纸 -->
      <svg class="frag frag-cut" viewBox="0 0 100 100"><circle cx="50" cy="50" r="42" fill="rgba(230,90,70,0.04)" stroke="rgba(230,90,70,0.45)" stroke-width="1.6"/><path d="M50,8 L54,28 L74,16 L60,36 L82,42 L64,52 L82,62 L60,68 L74,88 L54,76 L50,96 L46,76 L26,88 L40,68 L18,62 L36,52 L18,42 L40,36 L26,16 L46,28 Z" fill="none" stroke="rgba(230,90,70,0.35)" stroke-width="1"/></svg>
      <!-- 团扇 -->
      <svg class="frag frag-fan" viewBox="0 0 90 110"><path d="M45,10 C20,10 6,30 6,55 C6,75 18,90 36,94 L45,110 L54,94 C72,90 84,75 84,55 C84,30 70,10 45,10 Z" fill="rgba(170,210,170,0.04)" stroke="rgba(170,210,170,0.45)" stroke-width="1.6"/><path d="M30,40 Q45,30 60,40" fill="none" stroke="rgba(170,210,170,0.3)" stroke-width="1"/></svg>
      <!-- 茶壶 -->
      <svg class="frag frag-pot" viewBox="0 0 100 80"><ellipse cx="50" cy="48" rx="32" ry="22" fill="rgba(190,130,80,0.04)" stroke="rgba(190,130,80,0.5)" stroke-width="1.6"/><path d="M50,26 L50,18 Q50,10 60,12 L66,16" fill="none" stroke="rgba(190,130,80,0.4)" stroke-width="1.2"/><path d="M82,48 Q94,48 94,40" fill="none" stroke="rgba(190,130,80,0.4)" stroke-width="1.2"/><ellipse cx="50" cy="44" rx="22" ry="12" fill="none" stroke="rgba(190,130,80,0.35)" stroke-width="1"/></svg>
    </div>

    <!-- 渐变过渡桥（退出用） -->
    <div class="fade-bridge" :class="{ active: contentLeaving }"></div>

    <!-- 水墨入场画布 -->
    <canvas
      v-if="inkPhase !== 'done'"
      ref="inkCanvasRef"
      class="ink-canvas"
      :class="{ fading: inkPhase === 'fading' }"
    ></canvas>

    <!-- 粒子/涟漪交互画布 -->
    <canvas
      ref="fxCanvasRef"
      class="fx-canvas"
    ></canvas>

    <!-- 数字印章 -->
    <div class="seal-layer">
      <div
        v-for="seal in seals"
        :key="seal.id"
        class="digital-seal"
        :style="{ left: seal.x + 'px', top: seal.y + 'px' }"
      >
        <svg class="seal-svg" viewBox="0 0 80 80">
          <rect x="4" y="4" width="72" height="72" rx="4" fill="none" stroke="#c04030" stroke-width="3"/>
          <rect x="9" y="9" width="62" height="62" rx="2" fill="none" stroke="#c04030" stroke-width="0.8"/>
          <text x="40" y="34" text-anchor="middle" font-size="14" font-weight="900" fill="#c04030" font-family="'Noto Serif SC', 'SimSun', serif">华脉万象</text>
          <text x="40" y="52" text-anchor="middle" font-size="7" fill="#c04030" font-family="'Noto Serif SC', serif">非遗传承</text>
          <text x="40" y="66" text-anchor="middle" font-size="6" fill="#c04030" opacity="0.7" font-family="serif">HUAMAI</text>
        </svg>
      </div>
    </div>

    <!-- 内容层 -->
    <div class="landing-content" :class="{ leaving: contentLeaving }">

        <!-- Hero 首屏 -->
        <section class="hero-section">

          <!-- 时辰拨盘 -->
          <div class="time-dial" :class="{ visible: contentVisible }" @click.stop="cycleTimeOfDay" title="切换时辰">
            <svg class="dial-svg" viewBox="0 0 60 60">
              <circle cx="30" cy="30" r="26" fill="none" stroke="rgba(216,162,75,0.2)" stroke-width="1"/>
              <circle cx="30" cy="30" r="22" fill="none" stroke="rgba(216,162,75,0.1)" stroke-width="0.5" stroke-dasharray="3 5"/>
              <g v-if="timeOfDay === 'dawn'">
                <circle cx="30" cy="18" r="7" fill="rgba(255,180,100,0.5)" filter="url(#dialGlow)"/>
                <line x1="30" y1="8" x2="30" y2="4" stroke="rgba(255,180,100,0.4)" stroke-width="1"/>
                <line x1="30" y1="8" x2="26" y2="5" stroke="rgba(255,180,100,0.3)" stroke-width="0.8"/>
                <line x1="30" y1="8" x2="34" y2="5" stroke="rgba(255,180,100,0.3)" stroke-width="0.8"/>
              </g>
              <g v-else-if="timeOfDay === 'noon'">
                <circle cx="30" cy="30" r="10" fill="rgba(255,220,150,0.6)" filter="url(#dialGlow)"/>
                <line x1="30" y1="16" x2="30" y2="8" stroke="rgba(255,220,150,0.5)" stroke-width="1.2"/>
                <line x1="26" y1="30" x2="16" y2="30" stroke="rgba(255,220,150,0.4)" stroke-width="1"/>
                <line x1="34" y1="30" x2="44" y2="30" stroke="rgba(255,220,150,0.4)" stroke-width="1"/>
              </g>
              <g v-else-if="timeOfDay === 'dusk'">
                <circle cx="30" cy="40" r="8" fill="rgba(255,140,80,0.5)" filter="url(#dialGlow)"/>
                <path d="M30,52 L30,56" stroke="rgba(255,140,80,0.4)" stroke-width="1"/>
                <path d="M22,48 L19,51" stroke="rgba(255,140,80,0.3)" stroke-width="0.8"/>
                <path d="M38,48 L41,51" stroke="rgba(255,140,80,0.3)" stroke-width="0.8"/>
              </g>
              <g v-else>
                <circle cx="30" cy="30" r="4" fill="rgba(200,200,220,0.4)"/>
                <circle cx="22" cy="18" r="1.5" fill="rgba(200,200,220,0.2)"/>
                <circle cx="40" cy="22" r="1" fill="rgba(200,200,220,0.15)"/>
                <circle cx="20" cy="36" r="1.2" fill="rgba(200,200,220,0.15)"/>
                <circle cx="44" cy="40" r="0.8" fill="rgba(200,200,220,0.1)"/>
                <circle cx="32" cy="14" r="1" fill="rgba(200,200,220,0.18)"/>
              </g>
            </svg>
            <span class="dial-label">{{ {dawn:'晨',noon:'午',dusk:'暮',night:'夜'}[timeOfDay] }} · {{ paletteLabels[heritagePalette] }}</span>
          </div>

          <div class="hero-content" :class="{ visible: contentVisible }">
            <div class="hero-logo">
              <span class="logo-icon">华</span>
            </div>
            <!-- 书法笔画动画标题 -->
            <h1 class="hero-title" :class="titleRevealPhase">
              <span class="title-brush-wrap">
                <svg class="title-svg" viewBox="0 0 640 140" xmlns="http://www.w3.org/2000/svg">
                  <defs>
                    <linearGradient id="goldGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                      <stop offset="0%" stop-color="#b8860b"/>
                      <stop offset="22%" stop-color="#d8a24b"/>
                      <stop offset="42%" stop-color="#f0d78c"/>
                      <stop offset="50%" stop-color="#fffef0"/>
                      <stop offset="58%" stop-color="#f0d78c"/>
                      <stop offset="78%" stop-color="#d8a24b"/>
                      <stop offset="100%" stop-color="#b8860b"/>
                      <animateTransform attributeName="gradientTransform" type="translate" values="-1 0; 1 0; -1 0" dur="4s" repeatCount="indefinite"/>
                    </linearGradient>
                  </defs>
                  <text x="320" y="98" text-anchor="middle" font-family="'Noto Serif SC', 'SimSun', 'STSong', serif" font-size="96" font-weight="900" letter-spacing="0.08em" fill="url(#goldGrad)">华脉万象</text>
                  <text x="320" y="98" text-anchor="middle" font-family="'Noto Serif SC', 'SimSun', 'STSong', serif" font-size="96" font-weight="900" letter-spacing="0.08em" fill="none" stroke="url(#goldGrad)" stroke-width="0.5" opacity="0.6">华脉万象</text>
                </svg>
              </span>
            </h1>
            <p class="hero-subtitle" :class="{ typing: !typewriterDone }">
              <span class="subtitle-text">{{ typewriterText }}</span><span class="typewriter-cursor" :class="{ blink: typewriterDone, hidden: typewriterDone && !typewriterCursor }">|</span>
            </p>
            <p class="hero-stat" :class="{ visible: contentVisible }">
              <span class="stat-num">3,847</span>
              <span>非遗项目</span>
              <span class="stat-divider">·</span>
              <span class="stat-num">12,000<span class="stat-unit">+</span></span>
              <span>传承人</span>
              <span class="stat-divider">·</span>
              <span class="stat-num">10</span>
              <span>大门类</span>
            </p>
          </div>

          <!-- 底部入口 -->
          <div class="enter-wrap">
            <p class="enter-copy">开启你的非遗探索之旅</p>
            <button class="enter-btn" @click="handleEnter">
              <span>进入平台</span>
              <span class="enter-arrow">→</span>
            </button>
          </div>
        </section>

    </div>
  </div>
</template>

<style scoped>
/* ═══════════════════════════════════════════
   入户页 — 华脉万象  Landing
   ═══════════════════════════════════════════ */

/* ── 根容器 ── */
.landing-root {
  position: fixed;
  inset: 0;
  background: #0b0907;
  overflow: hidden;
}

/* ── 时序叙事：四个时辰的色板（通过色板轮转切换，影响 gold/accent 色值） ── */
.landing-root.tod-dawn {
}

.landing-root.tod-noon {
}

.landing-root.tod-dusk {
}

.landing-root.tod-night {
}

/* ── 非遗色板：四套配色方案 ── */
/* 青花瓷 */
.landing-root.pal-porcelain {
  --pal-gold-1: #8ab4d8;
  --pal-gold-2: #b8cfe0;
  --pal-accent: rgba(120, 160, 200, 0.45);
  --pal-glow: rgba(140, 180, 210, 0.18);
}

/* 敦煌 */
.landing-root.pal-dunhuang {
  --pal-gold-1: #d4956b;
  --pal-gold-2: #e8c9a0;
  --pal-accent: rgba(200, 130, 80, 0.45);
  --pal-glow: rgba(210, 140, 90, 0.18);
}

/* 唐三彩 */
.landing-root.pal-sancai {
  --pal-gold-1: #c8a850;
  --pal-gold-2: #e0d090;
  --pal-accent: rgba(180, 150, 70, 0.45);
  --pal-glow: rgba(200, 160, 80, 0.18);
}

/* 水墨（默认） */
.landing-root.pal-ink {
  --pal-gold-1: #d8a24b;
  --pal-gold-2: #f0d78c;
  --pal-accent: rgba(216, 162, 75, 0.4);
  --pal-glow: rgba(216, 162, 75, 0.15);
}

/* ── 视频背景 ── */
.landing-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: 0;
  pointer-events: none;
  filter: brightness(0.5);
}

/* ── 非遗碎片漂浮 ── */
.heritage-fragments {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  overflow: hidden;
}

.frag {
  position: absolute;
  opacity: 1;
  filter: drop-shadow(0 0 6px rgba(216, 162, 75, 0.35));
  transition: transform 1.2s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

/* 鼠标靠近时碎片微微避让（通过父级磁吸实现） */
.heritage-fragments {
  transform: translate(
    calc(var(--mx, 0px) * -0.04),
    calc(var(--my, 0px) * -0.04)
  );
  transition: transform 0.9s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.frag-vase {
  top: 12%;
  left: 15%;
  width: 70px;
  height: 100px;
  animation: fragFloat1 26s ease-in-out infinite;
}

.frag-mask {
  top: 18%;
  right: 10%;
  width: 80px;
  height: 90px;
  animation: fragFloat2 30s ease-in-out infinite;
}

.frag-qin {
  bottom: 22%;
  left: 20%;
  width: 140px;
  height: 36px;
  animation: fragFloat3 34s ease-in-out infinite;
}

.frag-cut {
  bottom: 15%;
  right: 18%;
  width: 90px;
  height: 90px;
  animation: fragFloat4 28s ease-in-out infinite;
}

.frag-fan {
  top: 40%;
  left: 5%;
  width: 60px;
  height: 75px;
  animation: fragFloat5 32s ease-in-out infinite;
}

.frag-pot {
  top: 50%;
  right: 6%;
  width: 80px;
  height: 64px;
  animation: fragFloat6 36s ease-in-out infinite;
}

@keyframes fragFloat1 {
  0%   { transform: translate(0, 0) rotate(0deg); }
  33%  { transform: translate(25px, -30px) rotate(5deg); }
  66%  { transform: translate(-15px, 10px) rotate(-3deg); }
  100% { transform: translate(0, 0) rotate(0deg); }
}

@keyframes fragFloat2 {
  0%   { transform: translate(0, 0) rotate(0deg); }
  33%  { transform: translate(-30px, -15px) rotate(-8deg); }
  66%  { transform: translate(20px, 20px) rotate(4deg); }
  100% { transform: translate(0, 0) rotate(0deg); }
}

@keyframes fragFloat3 {
  0%   { transform: translate(0, 0) rotate(0deg); }
  33%  { transform: translate(35px, -10px) rotate(2deg); }
  66%  { transform: translate(-20px, -20px) rotate(-2deg); }
  100% { transform: translate(0, 0) rotate(0deg); }
}

@keyframes fragFloat4 {
  0%   { transform: translate(0, 0) rotate(0deg); }
  33%  { transform: translate(-20px, -25px) rotate(6deg); }
  66%  { transform: translate(15px, 15px) rotate(-5deg); }
  100% { transform: translate(0, 0) rotate(0deg); }
}

@keyframes fragFloat5 {
  0%   { transform: translate(0, 0) rotate(0deg); }
  33%  { transform: translate(30px, 15px) rotate(-4deg); }
  66%  { transform: translate(-25px, -10px) rotate(3deg); }
  100% { transform: translate(0, 0) rotate(0deg); }
}

@keyframes fragFloat6 {
  0%   { transform: translate(0, 0) rotate(0deg); }
  33%  { transform: translate(-25px, -20px) rotate(7deg); }
  66%  { transform: translate(20px, 10px) rotate(-6deg); }
  100% { transform: translate(0, 0) rotate(0deg); }
}

/* ── 渐变过渡桥（退出用） ── */
.fade-bridge {
  position: absolute;
  inset: 0;
  z-index: 20;
  background: linear-gradient(160deg, #1a0f08 0%, #0f0704 35%, #0b0907 100%);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.55s ease-in;
}

.fade-bridge.active {
  opacity: 1;
}

/* ── 水墨入场画布 ── */
.ink-canvas {
  position: absolute;
  inset: 0;
  z-index: 30;
  pointer-events: none;
  transition: opacity 0.7s ease-out;
}

.ink-canvas.fading {
  opacity: 0;
}

/* ── 粒子/涟漪交互画布 ── */
.fx-canvas {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
}

/* ── 数字印章 ── */
.seal-layer {
  position: absolute;
  inset: 0;
  z-index: 6;
  pointer-events: none;
  overflow: hidden;
}

.digital-seal {
  position: absolute;
  transform: translate(-50%, -50%) scale(0);
  animation: sealStamp 2.6s cubic-bezier(0.22, 0.61, 0.36, 1) forwards;
  pointer-events: none;
}

.seal-svg {
  width: 88px;
  height: 88px;
  filter: drop-shadow(0 0 8px rgba(200, 40, 30, 0.55));
  opacity: 0.88;
}

@keyframes sealStamp {
  0%   { transform: translate(-50%, -50%) scale(0) rotate(-15deg); opacity: 0; }
  12%  { transform: translate(-50%, -50%) scale(1.35) rotate(3deg); opacity: 0.95; }
  20%  { transform: translate(-50%, -50%) scale(0.85) rotate(-1deg); opacity: 0.92; }
  30%  { transform: translate(-50%, -50%) scale(1.08) rotate(0.5deg); opacity: 0.88; }
  40%  { transform: translate(-50%, -50%) scale(1) rotate(0deg); opacity: 0.85; }
  65%  { transform: translate(-50%, -50%) scale(1) rotate(0deg); opacity: 0.7; }
  100% { transform: translate(-50%, -50%) scale(0.95) rotate(0deg); opacity: 0; }
}

/* ── SVG 笔画动画标题 ── */
.hero-title {
  margin: 0;
  line-height: 1.15;
  width: 100%;
  max-width: 680px;
}

.hero-title.waiting {
  opacity: 0;
  transform: translateY(16px);
  transition: opacity 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) 0.12s,
              transform 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) 0.12s;
}

.hero-title.revealing,
.hero-title.done {
  opacity: 1;
  transform: translateY(0);
}

.title-brush-wrap {
  display: block;
  -webkit-mask-image: linear-gradient(to right, #000 0%, #000 50%, transparent 50%, transparent 100%);
  -webkit-mask-size: 200% 100%;
  mask-image: linear-gradient(to right, #000 0%, #000 50%, transparent 50%, transparent 100%);
  mask-size: 200% 100%;
}

.hero-title.waiting .title-brush-wrap {
  -webkit-mask-position: 100% 0;
  mask-position: 100% 0;
}

.hero-title.revealing .title-brush-wrap {
  animation: brushReveal 5s cubic-bezier(0.22, 0.61, 0.36, 1) forwards;
}

.hero-title.done .title-brush-wrap {
  -webkit-mask-position: 0% 0;
  mask-position: 0% 0;
}

@keyframes brushReveal {
  0%   { -webkit-mask-position: 100% 0; mask-position: 100% 0; }
  100% { -webkit-mask-position: 0% 0;   mask-position: 0% 0; }
}

.title-svg {
  width: 100%;
  height: auto;
  display: block;
  filter: drop-shadow(0 0 4px rgba(0, 0, 0, 0.55)) drop-shadow(0 0 10px rgba(216, 162, 75, 0.5));
}

/* ── 内容层 ── */
.landing-content {
  position: absolute;
  inset: 0;
  z-index: 5;
  transition: opacity 0.4s ease-out;
}

.landing-content.leaving {
  opacity: 0;
}

/* ════════════════════════════════════
   Hero 首屏
   ════════════════════════════════════ */
.hero-section {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
}

.hero-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  text-align: center;
}

/* ── Hero 子元素 stagger 入场（由 contentVisible 触发） ── */
.hero-logo {
  opacity: 0;
  transform: translateY(16px);
  transition: opacity 0.7s cubic-bezier(0.22, 0.61, 0.36, 1),
              transform 0.7s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.hero-content.visible .hero-logo {
  opacity: 1;
  transform: translateY(0);
}

.hero-subtitle {
  margin: 0;
  font-size: 15px;
  line-height: 1.6;
  color: #fff;
  max-width: 460px;
  letter-spacing: 0.05em;
  text-shadow: 0 0 10px rgba(0, 0, 0, 0.7), 0 2px 4px rgba(0, 0, 0, 0.6);
  opacity: 0;
  transform: translateY(12px);
  transition: opacity 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) 0.24s,
              transform 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) 0.24s;
}

.hero-content.visible .hero-subtitle {
  opacity: 1;
  transform: translateY(0);
}

.hero-subtitle.typing .subtitle-text {
  /* 打字过程中保持可见 */
}

/* ── 平台数据文案 ── */
.hero-stat {
  margin: 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.92);
  letter-spacing: 0.06em;
  display: flex;
  align-items: center;
  gap: 8px;
  text-shadow: 0 0 8px rgba(0, 0, 0, 0.7), 0 1px 3px rgba(0, 0, 0, 0.5);
  opacity: 0;
  transform: translateY(10px);
  transition: opacity 0.65s cubic-bezier(0.22, 0.61, 0.36, 1) 0.48s,
              transform 0.65s cubic-bezier(0.22, 0.61, 0.36, 1) 0.48s;
}

.hero-content.visible .hero-stat.visible {
  opacity: 1;
  transform: translateY(0);
}

.stat-num {
  font-family: 'Noto Serif SC', 'SimSun', serif;
  font-weight: 700;
  color: rgba(255, 225, 160, 0.95);
  text-shadow: 0 0 6px rgba(200, 150, 60, 0.3);
}

.stat-unit {
  font-size: 11px;
  font-weight: 400;
  color: rgba(255, 225, 160, 0.72);
  margin-left: 1px;
}

.stat-divider {
  color: rgba(216, 162, 75, 0.4);
}

.typewriter-cursor {
  display: inline;
  color: rgba(255, 225, 160, 0.95);
  font-weight: 300;
  margin-left: 1px;
  text-shadow: 0 0 6px rgba(200, 150, 60, 0.4);
}

.typewriter-cursor.blink {
  animation: cursorBlink 1.06s step-end infinite;
}

.typewriter-cursor.hidden {
  opacity: 0;
}

@keyframes cursorBlink {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0; }
}

.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  background: linear-gradient(135deg, #c9472c, #a73720);
  border-radius: 14px;
  font-family: var(--font-serif, 'Noto Serif SC', serif);
  font-size: 24px;
  font-weight: 900;
  color: #fffaf5;
  box-shadow: 0 4px 24px rgba(200, 71, 44, 0.35);
}

/* ── 时辰拨盘 ── */
.time-dial {
  position: absolute;
  top: 28px;
  right: 28px;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  opacity: 0;
  transform: translateY(-8px);
  transition: opacity 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) 0.6s,
              transform 0.7s cubic-bezier(0.22, 0.61, 0.36, 1) 0.6s;
}

.time-dial.visible {
  opacity: 1;
  transform: translateY(0);
}

.dial-svg {
  width: 44px;
  height: 44px;
  transition: transform 0.4s ease;
}

.time-dial:hover .dial-svg {
  transform: rotate(30deg) scale(1.1);
}

.time-dial:active .dial-svg {
  transform: rotate(90deg) scale(0.95);
}

.dial-label {
  font-size: 11px;
  color: rgba(255, 225, 160, 0.78);
  letter-spacing: 0.08em;
  font-family: var(--font-serif, 'Noto Serif SC', serif);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
  transition: color 0.4s ease;
}

.time-dial:hover .dial-label {
  color: rgba(255, 235, 175, 0.95);
}

/* ── 底部入口 ── */
.enter-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.enter-copy {
  margin: 0;
  font-size: 14px;
  color: #fff;
  letter-spacing: 0.06em;
  text-shadow: 0 0 10px rgba(0, 0, 0, 0.7), 0 2px 4px rgba(0, 0, 0, 0.6);
}

.enter-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 15px 44px;
  border-radius: 999px;
  border: 1px solid rgba(216, 162, 75, 0.3);
  background: linear-gradient(135deg, rgba(200, 71, 44, 0.88), rgba(167, 55, 32, 0.92));
  color: #fffaf5;
  cursor: pointer;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.06em;
  font-family: var(--font-serif, 'Noto Serif SC', serif);
  box-shadow: 0 4px 28px rgba(200, 71, 44, 0.35);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  animation: btnBreathe 2.8s ease-in-out 5s infinite;
}

@keyframes btnBreathe {
  0%, 100% { box-shadow: 0 4px 28px rgba(200, 71, 44, 0.35); }
  50%      { box-shadow: 0 4px 40px rgba(200, 71, 44, 0.6), 0 0 60px rgba(216, 162, 75, 0.15); }
}

.enter-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 38px rgba(200, 71, 44, 0.55);
}

.enter-btn:active {
  transform: translateY(0);
}

.enter-arrow {
  font-size: 18px;
  transition: transform 0.3s;
}

.enter-btn:hover .enter-arrow {
  transform: translateX(3px);
}

/* ════════════════════════════════════
   无障碍：减少动画
   ════════════════════════════════════ */
@media (prefers-reduced-motion: reduce) {
  .frag { animation: none !important; }
  .hero-title.revealing .title-brush-wrap { animation: none !important; }
  .hero-title.revealing .title-brush-wrap,
  .hero-title.done .title-brush-wrap { -webkit-mask-position: 0% 0; mask-position: 0% 0; }
  .fx-canvas { display: none !important; }
  .typewriter-cursor { animation: none !important; }
  .digital-seal { animation: none !important; opacity: 0; }
  .enter-btn { animation: none !important; }
  .heritage-fragments { transition: none !important; }
}

/* ════════════════════════════════════
   响应式
   ════════════════════════════════════ */
@media (max-width: 640px) {
  .title-svg {
    max-width: 100%;
  }

  .hero-subtitle {
    font-size: 13px;
    max-width: 300px;
  }

  .hero-stat {
    font-size: 12px;
    gap: 5px;
  }

  .enter-btn {
    padding: 13px 36px;
    font-size: 15px;
  }

  .logo-icon {
    width: 42px;
    height: 42px;
    font-size: 20px;
    border-radius: 12px;
  }

  .hero-title {
    max-width: 100%;
  }

  .hero-content {
    padding: 28px 24px;
  }

  /* 移动端时辰拨盘 */
  .time-dial {
    top: 16px;
    right: 16px;
  }

  .dial-svg {
    width: 34px;
    height: 34px;
  }

  .dial-label { font-size: 9px; }

}
</style>
