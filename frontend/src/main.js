import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'
import './assets/styles/global.css'
import './assets/styles/animations.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(Antd)

// 工具函数：对包含非 ASCII 字符（如中文）的 URL 进行编码，避免浏览器请求失败
function encodeImageUrl(url) {
  if (!url) return url
  // 只对包含非 ASCII 字符的 URL 进行编码，避免对已编码的 URL 二次编码
  if (/[^\x00-\x7F]/.test(url)) {
    return encodeURI(url)
  }
  return url
}

// 全局指令：图片加载失败时自动替换为默认占位图；同时自动编码含中文的 URL
app.directive('img-fallback', {
  mounted(el, binding) {
    // 编码含中文等非 ASCII 字符的图片 URL
    if (el.src) {
      el.src = encodeImageUrl(el.src)
    }
    // 同时处理通过 el.getAttribute('src') 设置的属性
    const srcAttr = el.getAttribute('src')
    if (srcAttr && /[^\x00-\x7F]/.test(srcAttr)) {
      el.setAttribute('src', encodeURI(srcAttr))
    }

    const fallback = binding.value || '/uploads/heritage/default.svg'
    const handler = () => {
      if (!el.src.endsWith('/default.svg')) {
        el.src = fallback
      }
    }
    el.addEventListener('error', handler)
    el._imgFallbackCleanup = () => el.removeEventListener('error', handler)
  },
  unmounted(el) {
    if (el._imgFallbackCleanup) {
      el._imgFallbackCleanup()
    }
  }
})

app.mount('#app')
