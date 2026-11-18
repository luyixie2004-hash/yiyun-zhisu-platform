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
      <h2 class="auth-title">管理员登录</h2>
      <p class="auth-sub">登录后台管理系统，处理非遗平台业务</p>

      <a-form :model="form" @finish="handleLogin" layout="vertical">
        <a-form-item name="account" :rules="[{required:true,message:'请输入管理员账号'}, {min:2,max:50,message:'账号长度2-50位'}]">
          <a-input v-model:value="form.account" placeholder="管理员账号 / 邮箱 / 手机号" size="large" class="auth-input" />
        </a-form-item>
        <a-form-item name="password" :rules="[{required:true,message:'请输入管理员密码'}, {min:6,max:30,message:'密码长度6-30位'}]">
          <a-input-password v-model:value="form.password" placeholder="管理员密码" size="large" class="auth-input" />
        </a-form-item>
        
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" block :loading="loading" class="auth-btn">进入后台</a-button>
        </a-form-item>
      </a-form>
      
      <div class="auth-footer">
        返回普通登录？
        <router-link to="/login" class="user-link">切换用户登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({ account: '', password: '' })

async function handleLogin() {
  loading.value = true
  try {
    // 调用后端登录接口进行验证
    await userStore.loginAction(form)
    
    // 检查用户角色是否为管理员
    const userInfo = userStore.userInfo
    if (userInfo && userInfo.role !== 'ADMIN') {
      message.error('该账号不是管理员，请使用普通登录')
      userStore.logout()
      return
    }
    
    message.success('管理员登录成功！', { duration: 1000 })
    router.push('/admin/heritage')
  } catch (error) {
    const errMsg = error?.msg || error?.message || '登录失败'
    message.error(errMsg)
  } finally { 
    loading.value = false 
  }
}
</script>

<style scoped>
.auth-page { 
  min-height: 100vh; 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  position: relative; 
  overflow: hidden; 
  background: var(--bg-base); 
}
.auth-bg { 
  position: absolute; 
  inset: 0; 
  background: radial-gradient(ellipse 80% 60% at 30% 40%, rgba(200,80,42,0.15), transparent 60%),
              radial-gradient(ellipse 60% 40% at 80% 70%, rgba(43,95,142,0.12), transparent 50%); 
}
.auth-particle { 
  position: absolute; 
  border-radius: 50%; 
}
.p1 { 
  width: 8px; 
  height: 8px; 
  background: var(--color-accent); 
  top: 20%; 
  left: 20%; 
}
.p2 { 
  width: 6px; 
  height: 6px; 
  background: var(--color-jade); 
  bottom: 30%; 
  right: 25%; 
}
.auth-card { 
  position: relative; 
  z-index: 1; 
  width: 100%; 
  max-width: 400px; 
  padding: 40px; 
}
.auth-logo { 
  display: flex; 
  align-items: center; 
  gap: 10px; 
  justify-content: center; 
  margin-bottom: 28px; 
}
.logo-icon { 
  width: 40px; 
  height: 40px; 
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)); 
  border-radius: 10px; 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  font-family: var(--font-serif); 
  font-size: 18px; 
  font-weight: 900; 
  color: #fff; 
}
.logo-text { 
  font-family: var(--font-serif); 
  font-size: 20px; 
  font-weight: 700; 
  color: var(--text-primary); 
}
.auth-title { 
  font-family: var(--font-serif); 
  font-size: 24px; 
  font-weight: 700; 
  color: var(--text-primary); 
  text-align: center; 
  margin-bottom: 6px; 
}
.auth-sub { 
  font-size: 13px; 
  color: var(--text-muted); 
  text-align: center; 
  margin-bottom: 20px; 
}
.auth-input :deep(.ant-input), 
.auth-input :deep(.ant-input-password) { 
  background: var(--bg-glass) !important; 
  border-color: var(--bg-glass-border) !important; 
  color: var(--text-primary) !important; 
  border-radius: var(--radius-md) !important; 
}
.auth-btn { 
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark)) !important; 
  border: none !important; 
  border-radius: var(--radius-full) !important; 
  height: 44px !important; 
  font-size: 15px !important; 
}
.auth-footer { 
  text-align: center; 
  font-size: 13px; 
  color: var(--text-muted); 
  margin-top: 16px; 
}
.user-link { 
  color: var(--color-primary-light); 
  text-decoration: none; 
  margin-left: 4px;
}
</style>
