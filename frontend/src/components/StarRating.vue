<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  modelValue: { type: Number, default: 0 },
  disabled: { type: Boolean, default: false },
  size: { type: Number, default: 20 },
})

const emit = defineEmits(['update:modelValue'])

const hoverIndex = ref(0)

const stars = computed(() => {
  const result = []
  for (let i = 1; i <= 5; i++) {
    const displayValue = hoverIndex.value || props.modelValue
    const filled = displayValue >= i
    const half = !filled && displayValue >= i - 0.5
    result.push({ index: i, filled, half })
  }
  return result
})

function onMouseMove(e, idx) {
  if (props.disabled) return
  const rect = e.target.getBoundingClientRect()
  const x = e.clientX - rect.left
  hoverIndex.value = x < rect.width / 2 ? idx - 0.5 : idx
}

function onMouseLeave() {
  if (!props.disabled) hoverIndex.value = 0
}

function onClick(e, idx) {
  if (props.disabled) return
  const rect = e.target.getBoundingClientRect()
  const x = e.clientX - rect.left
  const value = x < rect.width / 2 ? idx - 0.5 : idx
  emit('update:modelValue', value)
}
</script>

<template>
  <span class="star-rating" :class="{ readonly: disabled }">
    <span
      v-for="star in stars"
      :key="star.index"
      class="star"
      :style="{ fontSize: size + 'px', cursor: disabled ? 'default' : 'pointer' }"
      @mousemove="(e) => onMouseMove(e, star.index)"
      @mouseleave="onMouseLeave"
      @click="(e) => onClick(e, star.index)"
    >
      <span v-if="star.filled" class="star-filled">&starf;</span>
      <span v-else-if="star.half" class="star-half-wrap">
        <span class="star-half-bg">&starf;</span>
        <span class="star-half-fg">&starf;</span>
      </span>
      <span v-else class="star-empty">&starf;</span>
    </span>
    <span v-if="modelValue > 0" class="rating-text">{{ modelValue.toFixed(1) }}</span>
  </span>
</template>

<style scoped>
.star-rating {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  user-select: none;
}

.star {
  display: inline-block;
  transition: transform 0.15s;
  line-height: 1;
}

.star:hover {
  transform: scale(1.18);
}

.star-filled {
  color: #f5a623;
}

.star-empty {
  color: #d9d9d9;
}

.star-half-wrap {
  position: relative;
  display: inline-block;
}

.star-half-bg {
  color: #d9d9d9;
}

.star-half-fg {
  color: #f5a623;
  position: absolute;
  left: 0;
  top: 0;
  width: 50%;
  overflow: hidden;
}

.star-rating.readonly .star {
  cursor: default;
}
.star-rating.readonly .star:hover {
  transform: none;
}

.rating-text {
  margin-left: 6px;
  font-size: 13px;
  color: #f5a623;
  font-weight: 600;
}
</style>
