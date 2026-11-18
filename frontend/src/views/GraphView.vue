<script setup>
import { onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getFullGraph } from '../api/graph'
import { GRAPH_NODE_TYPE_MAP } from '../utils/constants'
import { message } from 'ant-design-vue'

const route = useRoute()
const router = useRouter()
const chartContainer = ref(null)
const searchNode = ref(route.query.focus || '')
const selectedNode = ref(null)
const visibleTypes = ref(['非遗项目', '传承地区', '传承人', '技艺', '朝代'])
const graphState = ref({ nodes: [], links: [] })
const loading = ref(false)
let chart = null

const typeLabels = {
  '非遗项目': GRAPH_NODE_TYPE_MAP['非遗项目'],
  '传承地区': GRAPH_NODE_TYPE_MAP['传承地区'],
  '传承人': GRAPH_NODE_TYPE_MAP['传承人'],
  '技艺': GRAPH_NODE_TYPE_MAP['技艺'],
  '朝代': GRAPH_NODE_TYPE_MAP['朝代'],
}
const nodeColors = { '非遗项目': '#c9472c', '传承地区': '#d4a553', '传承人': '#4f9a7a', '技艺': '#6b7280', '朝代': '#8b5cf6' }

/** 解析节点的 properties JSON，提取 imageUrl / heritageId / userId */
function parseProps(node) {
  if (!node || !node.properties) return {}
  try {
    const raw = typeof node.properties === 'string' ? JSON.parse(node.properties) : node.properties
    return raw || {}
  } catch { return {} }
}

function buildOption(data) {
  const origin = window.location.origin

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(22,25,35,0.95)',
      borderColor: 'rgba(255,255,255,0.1)',
      textStyle: { color: '#F0EAD6', fontSize: 13 },
      formatter: (p) => {
        if (p.dataType === 'node') {
          const props = parseProps(p.data)
          const imgTag = props.imageUrl ? `<br/><img src="${props.imageUrl}" style="width:120px;height:80px;object-fit:cover;border-radius:6px;margin-top:6px" />` : ''
          return `<b>${p.data.name}</b><br/>${typeLabels[p.data.type] || ''}${imgTag}`
        }
        return p.data.label || '关联'
      },
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      force: {
        repulsion: 350,
        gravity: 0.06,
        edgeLength: [100, 260],
        layoutAnimation: true,
        friction: 0.6,
      },
      label: {
        show: true,
        position: 'right',
        color: '#ffffff',
        fontSize: 14,
        fontWeight: 'bold',
        textBorderColor: 'rgba(0,0,0,0.6)',
        textBorderWidth: 2,
        textShadowColor: 'rgba(0,0,0,0.8)',
        textShadowBlur: 4,
        textShadowOffsetX: 1,
        textShadowOffsetY: 1,
      },
      lineStyle: {
        color: '#d4a553',
        width: 3,
        curveness: 0.2,
        opacity: 0.8,
      },
      edgeLabel: {
        show: true,
        fontSize: 11,
        color: '#888',
        formatter: (p) => p.data.label || '',
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 4, color: '#d4a553', shadowColor: '#d4a553', shadowBlur: 10 },
        label: { show: true, color: '#ffd700', fontWeight: 'bold', fontSize: 16 },
      },
      data: data.nodes.map((n) => {
        return {
          ...n,
          symbolSize: Math.max(32, Math.sqrt(getNodeValue(n)) * 4.5),
          itemStyle: {
            color: nodeColors[n.type] || '#888',
            borderColor: 'rgba(255,255,255,0.5)',
            borderWidth: 2,
            shadowColor: 'rgba(0,0,0,0.5)',
            shadowBlur: 8,
          },
          label: { show: true },
        }
      }),
      links: data.links,
    }],
  }
}

function getNodeValue(node) {
  const baseValues = { '非遗项目': 50, '传承地区': 40, '传承人': 35, '技艺': 25, '朝代': 35 }
  return baseValues[node.type] || 20
}

function normalizeNode(node) {
  const props = parseProps(node)
  return {
    ...node,
    value: getNodeValue(node),
    desc: node.description || `${typeLabels[node.type] || '文化节点'}：${node.name}`,
    image: props.imageUrl || null,
    heritageId: props.heritageId || null,
    userId: props.userId || null,
    tags: [typeLabels[node.type] || '文化节点', node.level || node.category].filter(Boolean),
  }
}

const focusedNodeId = ref(null)

/** 聚焦到某个节点：只显示该节点和直接相连的邻居 */
function focusToNode(nodeId) {
  if (!chart || !nodeId) return
  focusedNodeId.value = nodeId

  const nodeIdsToShow = new Set()
  nodeIdsToShow.add(nodeId)
  for (const link of graphState.value.links) {
    if (link.source === nodeId) nodeIdsToShow.add(link.target)
    if (link.target === nodeId) nodeIdsToShow.add(link.source)
  }

  const filteredNodes = graphState.value.nodes.filter(n => nodeIdsToShow.has(n.id))
  const filteredLinks = graphState.value.links.filter(
    l => nodeIdsToShow.has(l.source) && nodeIdsToShow.has(l.target)
  )

  const option = buildOption({ nodes: filteredNodes, links: filteredLinks })
  const chartWidth = chart.getWidth()
  const chartHeight = chart.getHeight()
  option.series[0].data = option.series[0].data.map(n => {
    if (n.id === nodeId) {
      return {
        ...n,
        x: chartWidth / 2,
        y: chartHeight / 2,
        fixed: true,
        symbolSize: Math.max(60, n.symbolSize * 1.6),
        itemStyle: {
          ...n.itemStyle,
          borderColor: '#ffd700',
          borderWidth: 4,
          shadowColor: '#ffd700',
          shadowBlur: 28,
        },
        label: {
          ...n.label,
          fontSize: 16,
          color: '#ffd700',
        },
      }
    }
    return n
  })

  chart.setOption(option, true)
}

/** 点击节点：展示右侧详情面板 + 聚焦显示该节点及其邻居 */
function handleNodeClick(params) {
  if (params.dataType !== 'node') return
  const node = params.data
  selectedNode.value = normalizeNode(node)
  focusToNode(node.id)
}

function initChart() {
  if (!chartContainer.value) return
  chart = echarts.init(chartContainer.value, null, { renderer: 'canvas' })
  chart.setOption(buildOption(graphState.value))
  chart.on('click', handleNodeClick)
  if (searchNode.value) applySearchFilter()
}

function applyTypeFilter() {
  isSearchMode.value = false
  focusedNodeId.value = null
  const filteredNodes = graphState.value.nodes.filter((n) => visibleTypes.value.includes(n.type))
  const nodeIds = new Set(filteredNodes.map((n) => n.id))
  const filteredLinks = graphState.value.links.filter((l) => nodeIds.has(l.source) && nodeIds.has(l.target))
  chart?.setOption(buildOption({ nodes: filteredNodes, links: filteredLinks }), true)
}

const isSearchMode = ref(false)

function applySearchFilter() {
  if (!chart) return
  if (!searchNode.value || !searchNode.value.trim()) {
    isSearchMode.value = false
    applyTypeFilter()
    return
  }

  const keyword = searchNode.value.trim()
  const matchedNodes = graphState.value.nodes.filter(n => n.name.includes(keyword))
  if (matchedNodes.length === 0) {
    message.info('未找到匹配节点')
    return
  }

  isSearchMode.value = true
  const focusedNode = matchedNodes[0]
  const nodeIdsToShow = new Set()
  nodeIdsToShow.add(focusedNode.id)
  for (const link of graphState.value.links) {
    if (nodeIdsToShow.has(link.source)) nodeIdsToShow.add(link.target)
    if (nodeIdsToShow.has(link.target)) nodeIdsToShow.add(link.source)
  }

  const filteredNodes = graphState.value.nodes.filter(n => nodeIdsToShow.has(n.id))
  const filteredLinks = graphState.value.links.filter(
    l => nodeIdsToShow.has(l.source) && nodeIdsToShow.has(l.target)
  )

  const option = buildOption({ nodes: filteredNodes, links: filteredLinks })
  const chartWidth = chart.getWidth()
  const chartHeight = chart.getHeight()
  option.series[0].data = option.series[0].data.map(n => {
    if (n.id === focusedNode.id) {
      return {
        ...n,
        x: chartWidth / 2,
        y: chartHeight / 2,
        fixed: true,
        symbolSize: Math.max(60, n.symbolSize * 1.6),
        itemStyle: {
          ...n.itemStyle,
          borderColor: '#ffd700',
          borderWidth: 4,
          shadowColor: '#ffd700',
          shadowBlur: 28,
        },
        label: {
          ...n.label,
          fontSize: 16,
          color: '#ffd700',
        },
      }
    }
    return n
  })

  chart.setOption(option, true)
  const props = parseProps(focusedNode)
  selectedNode.value = normalizeNode({ ...focusedNode, properties: focusedNode.properties })
}

function resetView() {
  searchNode.value = ''
  selectedNode.value = null
  focusedNodeId.value = null
  visibleTypes.value = ['非遗项目', '传承地区', '传承人', '技艺', '朝代']
  isSearchMode.value = false
  chart?.setOption(buildOption(graphState.value), true)
}

/** 详情面板按钮：去往探索页筛选 */
function goExplore(node) {
  const q = encodeURIComponent(node.name || '')
  router.push(`/explore?q=${q}`)
}

/** 详情面板按钮：去往非遗详情页 */
function goHeritageDetail(node) {
  if (node.heritageId) router.push(`/heritage/${node.heritageId}`)
}

/** 详情面板按钮：去往传承人个人中心 */
function goUserProfile(node) {
  if (node.userId) router.push(`/user/${node.userId}`)
}

async function loadGraph() {
  loading.value = true
  try {
    const res = await getFullGraph()
    const data = res.data || res

    const nodes = (data.nodes || []).map(node => ({
      ...node,
      id: node.nodeId,
      type: node.type || node.type,
      value: 20,
    }))
    const links = (data.edges || []).map(edge => ({
      source: edge.sourceId,
      target: edge.targetId,
      label: edge.label || edge.relationType,
    }))

    graphState.value = { nodes, links }
  } catch (error) {
    console.error('❌ 加载图谱数据失败:', error)
    message.error('加载图谱数据失败，请稍后重试')
    graphState.value = { nodes: [], links: [] }
  } finally {
    loading.value = false
    await nextTick()
    initChart()
  }
}

watch(searchNode, () => applySearchFilter())

const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(() => {
  chart?.resize()
  if (isSearchMode.value) applySearchFilter()
}) : null
onMounted(async () => {
  await loadGraph()
  ro?.observe(chartContainer.value)
})
onUnmounted(() => {
  ro?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div class="graph-page page-container">
    <div class="graph-layout">
      <aside class="graph-sidebar glass-card">
        <h3 class="sidebar-title">知识图谱</h3>
        <div class="legend">
          <div v-for="(color, type) in nodeColors" :key="type" class="legend-item">
            <span class="legend-dot" :style="{background: color}"></span>
            <span>{{ typeLabels[type] }}</span>
          </div>
        </div>
        <div class="filter-group" style="margin-top:20px">
          <label>搜索节点</label>
          <a-input v-model:value="searchNode" placeholder="输入节点名称..." allow-clear />
        </div>
        <div class="filter-group" style="margin-top:16px">
          <label>显示类型</label>
          <div style="display:flex;flex-direction:column;gap:6px">
            <label v-for="(color, type) in nodeColors" :key="type" class="toggle-item">
              <input type="checkbox" v-model="visibleTypes" :value="type" @change="applyTypeFilter" style="accent-color:var(--color-primary)" />
              <span>{{ typeLabels[type] }}</span>
            </label>
          </div>
        </div>
        <button class="btn-ghost" style="width:100%;font-size:12px;margin-top:20px" @click="resetView">重置视图</button>
      </aside>

      <div class="graph-main">
        <div class="graph-toolbar">
          <template v-if="isSearchMode">
            <span class="graph-tip search-active">局部图谱 · 「{{ searchNode }}」</span>
            <button class="btn-back-global" @click="resetView">返回全局视图</button>
          </template>
          <template v-else-if="focusedNodeId">
            <span class="graph-tip search-active">聚焦节点 · 仅显示直接关联</span>
            <button class="btn-back-global" @click="resetView">返回全局视图</button>
          </template>
          <span v-else class="graph-tip">点击节点查看详情 · 仅显示相邻节点 · 右侧面板可跳转</span>
        </div>
        <div v-if="loading" class="chart-loading">
          <a-spin size="large" />
          <p>加载图谱数据中...</p>
        </div>
        <div v-else ref="chartContainer" class="chart-container"></div>
      </div>

      <aside class="detail-panel glass-card" :class="{ visible: selectedNode }">
        <template v-if="selectedNode">
          <!-- 非遗项目/传承人有图，朝代/技能/地区无图用占位 -->
          <img
            v-if="selectedNode.image"
            :src="selectedNode.image"
            class="detail-img"
            :alt="selectedNode.name"
          />
          <div v-else class="detail-img detail-img-placeholder">
            <span class="placeholder-icon">{{ selectedNode.type === '朝代' ? '📜' : selectedNode.type === '技艺' ? '🔧' : '📍' }}</span>
          </div>
          <div class="detail-body">
            <div class="detail-tags">
              <span v-for="tag in (selectedNode.tags || [])" :key="tag" class="tag tag-red">{{ tag }}</span>
            </div>
            <h3 class="detail-name">{{ selectedNode.name }}</h3>
            <p class="detail-desc">{{ selectedNode.desc }}</p>
            <div class="detail-actions">
              <!-- 非遗项目 → 详情页 -->
              <button v-if="selectedNode.type === '非遗项目' && selectedNode.heritageId" class="btn-primary" @click="goHeritageDetail(selectedNode)">
                查看非遗详情
              </button>
              <!-- 传承人 → 个人中心 -->
              <button v-else-if="selectedNode.type === '传承人' && selectedNode.userId" class="btn-primary" @click="goUserProfile(selectedNode)">
                查看个人中心
              </button>
              <!-- 朝代/技能/地区 → 探索筛选 -->
              <button v-else class="btn-primary" @click="goExplore(selectedNode)">
                筛选查看项目
              </button>
              <button class="btn-ghost" @click="router.push(`/guide?topic=${encodeURIComponent(selectedNode.name)}`)">AI问答</button>
            </div>
          </div>
        </template>
        <div v-else class="detail-placeholder">
          <div style="font-size:48px;margin-bottom:12px">🕸️</div>
          <p>点击图谱节点查看详细信息</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.graph-page { height: 100vh; display: flex; flex-direction: column; }
.graph-layout { flex: 1; display: grid; grid-template-columns: 220px 1fr 300px; gap: 0; height: calc(100vh - var(--header-height)); overflow: hidden; }
.graph-sidebar { margin: 16px 0 16px 16px; padding: 20px; overflow-y: auto; border-radius: var(--radius-lg); }
.sidebar-title { font-family: var(--font-serif); font-size: 16px; font-weight: 700; color: var(--text-primary); margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid var(--bg-glass-border); }
.legend { display: flex; flex-direction: column; gap: 10px; }
.legend-item, .toggle-item { display: flex; align-items: center; gap: 10px; font-size: 13px; color: var(--text-secondary); }
.legend-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.graph-main { position: relative; flex: 1; }
.graph-toolbar { position: absolute; top: 16px; left: 50%; transform: translateX(-50%); z-index: 10; }
.graph-tip { font-size: 12px; color: var(--text-muted); background: var(--bg-card); border: 1px solid var(--bg-glass-border); padding: 6px 16px; border-radius: var(--radius-full); }
.search-active { color: var(--color-primary); border-color: var(--color-primary); background: rgba(201,71,44,0.08); }
.btn-back-global { margin-left: 8px; padding: 4px 14px; font-size: 12px; color: #fff; background: var(--color-primary); border: none; border-radius: var(--radius-full); cursor: pointer; transition: opacity 0.2s; }
.btn-back-global:hover { opacity: 0.85; }
.chart-loading { width: 100%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; color: var(--text-muted); }
.chart-container { width: 100%; height: 100%; }
.detail-panel { margin: 16px 16px 16px 0; overflow-y: auto; border-radius: var(--radius-lg); transform: translateX(20px); opacity: 0; transition: all var(--transition-normal); pointer-events: none; }
.detail-panel.visible { transform: translateX(0); opacity: 1; pointer-events: all; }
.detail-img { width: 100%; height: 160px; object-fit: cover; border-radius: var(--radius-lg) var(--radius-lg) 0 0; }
.detail-img-placeholder { display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%); }
.placeholder-icon { font-size: 48px; }
.detail-body { padding: 18px; }
.detail-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.detail-name { font-family: var(--font-serif); font-size: 20px; font-weight: 700; color: var(--text-primary); margin-bottom: 10px; }
.detail-desc { font-size: 13px; color: var(--text-secondary); line-height: 1.7; }
.detail-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
.detail-placeholder { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; color: var(--text-muted); font-size: 14px; }
@media (max-width: 1100px) { .graph-layout { grid-template-columns: 200px 1fr; } .detail-panel { display: none; } }
@media (max-width: 768px) { .graph-layout { grid-template-columns: 1fr; } .graph-sidebar { display: none; } }
</style>
