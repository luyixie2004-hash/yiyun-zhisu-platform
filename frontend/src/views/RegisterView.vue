<template>
  <div class="auth-page">
    <div class="auth-bg">
      <div class="auth-particle p1 animate-float"></div>
      <div class="auth-particle p2 animate-float-2"></div>
    </div>
    <div class="auth-card glass-card register-card">
      <div class="auth-logo">
        <span class="logo-icon">华</span>
        <span class="logo-text">华脉万象</span>
      </div>
      <h2 class="auth-title">加入我们</h2>
      <p class="auth-sub">注册账户，开启您的非遗文化探索之旅</p>
      
      <a-form :model="form" @finish="handleRegister" layout="vertical">
        <a-form-item name="username" :rules="[
          {required:true,message:'请输入用户名'},
          {min:2,max:20,message:'用户名长度2-20位'},
          {validator: validateUsername}
        ]">
          <a-input v-model:value="form.username" placeholder="用户名" size="large" class="auth-input" />
        </a-form-item>
        
        <a-form-item name="password" :rules="[
          {required:true,message:'请输入密码'},
          {min:6,max:30,message:'密码长度6-30位'}
        ]">
          <a-input-password v-model:value="form.password" placeholder="密码（6-30位）" size="large" class="auth-input" />
        </a-form-item>
        
        <a-form-item name="confirm" :rules="[{validator: validateConfirm}]">
          <a-input-password v-model:value="form.confirm" placeholder="确认密码" size="large" class="auth-input" />
        </a-form-item>
        
        <a-form-item name="nickname" :rules="[
          {required:true,message:'请输入昵称'},
          {min:2,max:20,message:'昵称长度2-20位'}
        ]">
          <a-input v-model:value="form.nickname" placeholder="昵称" size="large" class="auth-input" />
        </a-form-item>
        
        <a-form-item name="phone" :rules="[
          {required:true,message:'请输入手机号'},
          {pattern:/^1[3-9]\d{9}$/,message:'手机号格式不正确'},
          {validator: validatePhone}
        ]">
          <a-input v-model:value="form.phone" placeholder="手机号" size="large" class="auth-input" maxlength="11" type="tel" />
        </a-form-item>
        
        <a-form-item name="email" :rules="[
          {type:'email',message:'邮箱格式不正确'},
          {validator: validateEmail}
        ]">
          <a-input v-model:value="form.email" placeholder="邮箱（可选）" size="large" class="auth-input" />
        </a-form-item>
        
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" block :loading="loading" class="auth-btn">注册</a-button>
        </a-form-item>
      </a-form>
      
      <div class="auth-footer">
        已有账户？<router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '../stores/user'
import { checkUsername, checkPhone, checkEmail } from '../api/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirm: '',
  nickname: '',
  phone: '',
  email: '',
  avatar: ''
})

const defaultAvatar = 'https://api.dicebear.com/9.x/avataaars/svg?seed='

async function validateUsername(_, value) {
  if (!value) return Promise.resolve()
  if (!/^[a-zA-Z0-9_]{2,20}$/.test(value)) {
    return Promise.reject('用户名只能包含字母、数字和下划线')
  }
  try {
    const res = await checkUsername(value)
    if (res.data?.exists) {
      return Promise.reject('用户名已存在')
    }
  } catch {
    return Promise.reject('验证失败，请稍后重试')
  }
  return Promise.resolve()
}

async function validatePhone(_, value) {
  if (!value) return Promise.resolve()
  if (!/^1[3-9]\d{9}$/.test(value)) {
    return Promise.reject('手机号格式不正确')
  }
  try {
    const res = await checkPhone(value)
    if (res.data?.exists) {
      return Promise.reject('手机号已被注册')
    }
  } catch {
    return Promise.reject('验证失败，请稍后重试')
  }
  return Promise.resolve()
}

async function validateEmail(_, value) {
  if (!value) return Promise.resolve()
  try {
    const res = await checkEmail(value)
    if (res.data?.exists) {
      return Promise.reject('邮箱已被注册')
    }
  } catch {
    return Promise.reject('验证失败，请稍后重试')
  }
  return Promise.resolve()
}

async function validateConfirm(_, value) {
  if (!value) return Promise.reject('请确认密码')
  if (value !== form.password) return Promise.reject('两次密码不一致')
  return Promise.resolve()
}

async function handleRegister() {
  loading.value = true
  try {
    form.avatar = defaultAvatar + form.username
    
    const registerData = {
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      phone: form.phone,
      email: form.email || undefined,
      avatar: form.avatar
    }
    
    await userStore.registerAction(registerData)
    message.success('注册成功！请登录')
    router.push('/login')
  } catch (error) {
    message.error(error?.msg || error?.message || '注册失败，请稍后重试')
  } finally { loading.value = false }
}
</script>

<style scoped>
.auth-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; position: relative; overflow: hidden; background: var(--bg-base); }
.auth-bg { position: absolute; inset: 0; background: radial-gradient(ellipse 80% 60% at 70% 40%, rgba(43,95,142,0.15), transparent 60%),radial-gradient(ellipse 60% 40% at 20% 70%, rgba(200,80,42,0.12), transparent 50%); }
.auth-particle { position: absolute; border-radius: 50%; }
.p1 { width: 8px; height: 8px; background: var(--color-secondary-light); top: 25%; right: 20%; }
.p2 { width: 6px; height: 6px; background: var(--color-accent); bottom: 35%; left: 22%; }
.auth-card { position: relative; z-index: 1; width: 100%; max-width: 460px; padding: 40px; }
.auth-logo { display: flex; align-items: center; gap: 10px; justify-content: center; margin-bottom: 24px; }
.logo-icon { width: 40px; height: 40px; background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)); border-radius: 10px; display: flex; align-items: center; justify-content: center; font-family: var(--font-serif); font-size: 18px; font-weight: 900; color: #fff; }
.logo-text { font-family: var(--font-serif); font-size: 20px; font-weight: 700; color: var(--text-primary); }
.auth-title { font-family: var(--font-serif); font-size: 24px; font-weight: 700; color: var(--text-primary); text-align: center; margin-bottom: 6px; }
.auth-sub { font-size: 13px; color: var(--text-muted); text-align: center; margin-bottom: 24px; }
.auth-input :deep(.ant-input), .auth-input :deep(.ant-input-affix-wrapper), .auth-input :deep(.ant-select-selector) { 
  background: var(--bg-glass) !important; 
  border-color: var(--bg-glass-border) !important; 
  color: var(--text-primary) !important; 
  border-radius: var(--radius-md) !important; 
}
.auth-btn { background: linear-gradient(135deg, var(--color-secondary), #1E4A72) !important; border: none !important; border-radius: var(--radius-full) !important; height: 44px !important; font-size: 15px !important; }
.auth-footer { text-align: center; font-size: 13px; color: var(--text-muted); margin-top: 16px; }
.auth-footer a { color: var(--color-primary-light); text-decoration: none; }
</style>
