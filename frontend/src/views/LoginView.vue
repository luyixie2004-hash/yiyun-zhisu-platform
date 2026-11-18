<template>
  <div class="auth-page">
    <div class="auth-bg">
      <div class="auth-particle p1 animate-float"></div>
      <div class="auth-particle p2 animate-float-2"></div>
    </div>
    <div class="auth-card glass-card">
      <div class="auth-logo">
        <span class="logo-icon">华</span>
        <span class="logo-text">遗韵智溯</span>
      </div>
      <h2 class="auth-title">欢迎回来</h2>
      <p class="auth-sub">登录您的账户，探寻千年非遗文化</p>

      <a-form :model="form" @finish="handleLogin" layout="vertical">
        <a-form-item name="account" :rules="[{required:true,message:'请输入用户名/邮箱/手机号'}, {min:2,max:50,message:'账号长度2-50位'}]">
          <a-input v-model:value="form.account" placeholder="用户名 / 邮箱 / 手机号" size="large" class="auth-input" />
        </a-form-item>
        <a-form-item name="password" :rules="[{required:true,message:'请输入密码'}, {min:6,max:30,message:'密码长度6-30位'}]">
          <a-input-password v-model:value="form.password" placeholder="密码" size="large" class="auth-input" />
        </a-form-item>
        
        
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" block :loading="loading" class="auth-btn">登录</a-button>
        </a-form-item>
      </a-form>
      <div class="auth-footer">
        还没有账户？<router-link to="/register">立即注册</router-link>
      </div>
      <div class="admin-entry">
        <router-link to="/admin-login" class="admin-entry-btn">管理员入口</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({ account: '', password: '' })

const redirectTo = computed(() => String(route.query.redirect || ''))

async function handleLogin() {
  loading.value = true
  try {
    await userStore.loginAction(form)
    message.success('登录成功！', { duration: 2000 })
    router.push(redirectTo.value || '/home')
  } catch (error) {
    const errMsg = error?.msg || error?.message || '登录失败'
    if (errMsg.includes('封禁')) {
      message.error('账户已被封禁，请联系管理员')
    } else if (errMsg.includes('状态异常')) {
      message.error('账户状态异常，请联系管理员')
    } else {
      message.error(errMsg)
    }
  } finally { loading.value = false }
}
</script>

<style scoped>
.auth-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; position: relative; overflow: hidden; background: var(--bg-base); }
.auth-bg { position: absolute; inset: 0; background: radial-gradient(ellipse 80% 60% at 30% 40%, rgba(200,80,42,0.15), transparent 60%),radial-gradient(ellipse 60% 40% at 80% 70%, rgba(43,95,142,0.12), transparent 50%); }
.auth-particle { position: absolute; border-radius: 50%; }
.p1 { width: 8px; height: 8px; background: var(--color-accent); top: 20%; left: 20%; }
.p2 { width: 6px; height: 6px; background: var(--color-jade); bottom: 30%; right: 25%; }
.auth-card { position: relative; z-index: 1; width: 100%; max-width: 400px; padding: 40px; }
.auth-logo { display: flex; align-items: center; gap: 10px; justify-content: center; margin-bottom: 28px; }
.logo-icon { width: 40px; height: 40px; background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)); border-radius: 10px; display: flex; align-items: center; justify-content: center; font-family: var(--font-serif); font-size: 18px; font-weight: 900; color: #fff; }
.logo-text { font-family: var(--font-serif); font-size: 20px; font-weight: 700; color: var(--text-primary); }
.auth-title { font-family: var(--font-serif); font-size: 24px; font-weight: 700; color: var(--text-primary); text-align: center; margin-bottom: 6px; }
.auth-sub { font-size: 13px; color: var(--text-muted); text-align: center; margin-bottom: 20px; }
.auth-input :deep(.ant-input), .auth-input :deep(.ant-input-password) { background: var(--bg-glass) !important; border-color: var(--bg-glass-border) !important; color: var(--text-primary) !important; border-radius: var(--radius-md) !important; }
.auth-btn { background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)) !important; border: none !important; border-radius: var(--radius-full) !important; height: 44px !important; font-size: 15px !important; }
.auth-footer { text-align: center; font-size: 13px; color: var(--text-muted); margin-top: 16px; }
.auth-footer a { color: var(--color-primary-light); text-decoration: none; }
.admin-link { color: var(--text-muted) !important; text-decoration: underline !important; opacity: 0.8; cursor: pointer; }
.admin-entry { text-align: center; margin-top: 12px; }
.admin-entry-btn { font-size: 13px; color: var(--text-muted); text-decoration: none; cursor: pointer; opacity: 0.8; }

</style>
