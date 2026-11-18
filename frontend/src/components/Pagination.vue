<template>
  <div v-if="totalPages > 1" class="pagination">
    <button class="page-btn" :disabled="modelValue <= 1" @click="goTo(modelValue - 1)">‹ 上一页</button>
    <template v-for="(p, i) in visiblePages" :key="i">
      <button v-if="p !== '...'" class="page-btn" :class="{ active: p === modelValue }" @click="goTo(p)">{{ p }}</button>
      <span v-else class="page-ellipsis">...</span>
    </template>
    <button class="page-btn" :disabled="modelValue >= totalPages" @click="goTo(modelValue + 1)">下一页 ›</button>
    <span class="page-info">共 {{ total }} 项</span>
    <div class="page-jump">
      <span>前往</span>
      <input type="number" v-model="jumpInput" @keyup.enter="doJump" min="1" :max="totalPages" />
      <span>页</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  modelValue: { type: Number, required: true },
  total: { type: Number, required: true },
  pageSize: { type: Number, default: 10 },
})

const emit = defineEmits(['update:modelValue', 'pageChange'])

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

const visiblePages = computed(() => {
  const current = props.modelValue
  const totalP = totalPages.value
  if (totalP <= 6) return Array.from({ length: totalP }, (_, i) => i + 1)
  if (current <= 3) return [1, 2, 3, 4, '...', totalP]
  if (current >= totalP - 2) return [1, '...', totalP - 3, totalP - 2, totalP - 1, totalP]
  return [1, '...', current - 1, current, current + 1, '...', totalP]
})

const jumpInput = ref('')

function goTo(p) {
  if (p >= 1 && p <= totalPages.value) {
    emit('update:modelValue', p)
    emit('pageChange', p)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

function doJump() {
  const p = parseInt(jumpInput.value)
  if (p && p >= 1 && p <= totalPages.value) {
    goTo(p)
    jumpInput.value = ''
  }
}
</script>

<style scoped>
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
