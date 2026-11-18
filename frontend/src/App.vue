<script setup>
import { computed, onMounted } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useUserStore } from './stores/user'
import AppHeader from './components/layout/AppHeader.vue'
import AppFooter from './components/layout/AppFooter.vue'

const userStore = useUserStore()
const route = useRoute()

const isBlankLayout = computed(() => route.meta.layout === 'blank')

onMounted(() => {
  userStore.hydrateUser()
})
</script>

<template>
  <template v-if="isBlankLayout">
    <router-view />
  </template>
  <div v-else class="app-wrapper">
    <AppHeader />
    <main class="main-content">
      <router-view />
    </main>
    <AppFooter />
  </div>
</template>

<style>
.app-wrapper { min-height: 100vh; display: flex; flex-direction: column; }
.main-content { flex: 1; }
</style>
