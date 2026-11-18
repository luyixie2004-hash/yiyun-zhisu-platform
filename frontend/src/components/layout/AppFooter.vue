<template>
  <footer class="app-footer">
    <div class="footer-inner">
      <div class="footer-brand">
        <div class="footer-logo">
          <span class="logo-icon">华</span>
          <span class="logo-text">遗韵智溯</span>
        </div>
        <p class="footer-tagline">以 AI 为引，以文化为根<br/>让非遗在数字时代焕发新生</p>
      </div>
      <div class="footer-column">
        <h4>核心功能</h4>
        <router-link to="/explore">非遗探索</router-link>
        <router-link to="/ai-studio">AIGC 文创工坊</router-link>
        <router-link to="/booking">体验工坊预约</router-link>
        <router-link to="/dashboard/explore">数据大屏</router-link>
      </div>
      <div class="footer-column">
        <h4>用户中心</h4>
        <router-link to="/profile">个人中心</router-link>
        <router-link to="/profile?tab=orders">我的预约订单</router-link>
        <router-link to="/profile?tab=favorites">我的收藏</router-link>
        <router-link to="/profile?tab=likes">我的点赞</router-link>
      </div>
    </div>
    <div class="footer-bottom">
      <span>© 2026 遗韵智溯 · 融合大模型与AIGC技术的非遗全景探索引擎</span>
      <span>Powered by DeepSeek AI · Spring Boot · Vue 3 · ECharts</span>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  background:
    linear-gradient(180deg, rgba(255, 253, 249, 0.96) 0%, rgba(248, 238, 222, 0.98) 62%, #f4e5cc 100%);
  border-top: 1px solid rgba(201, 71, 44, 0.14);
  padding: 64px 0 0;
  margin-top: 88px;
}

.footer-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 24px 48px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 48px;
  align-items: flex-start;
}

.footer-brand {
  /* first column */
}

.footer-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.logo-icon {
  width: 40px; height: 40px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-dark));
  border-radius: 12px; display: flex; align-items: center; justify-content: center;
  font-family: var(--font-serif); font-size: 18px; font-weight: 900;
  color: var(--text-on-primary); box-shadow: var(--shadow-glow-red);
}

.logo-text {
  font-family: var(--font-serif); font-size: 20px; font-weight: 700;
  color: var(--text-primary); letter-spacing: 0.06em;
}

.footer-tagline {
  color: var(--text-secondary); font-size: 14px; line-height: 1.9;
  margin: 0;
}

.footer-column {
  display: flex; flex-direction: column; gap: 12px;
}

.footer-column h4 {
  color: var(--text-primary); font-size: 14px; font-weight: 700;
  margin-bottom: 4px; letter-spacing: 0.08em;
}

.footer-column a {
  color: var(--text-secondary); font-size: 14px; text-decoration: none;
  transition: color 0.2s, transform 0.2s;
}

.footer-column a:hover {
  color: var(--color-primary); transform: translateX(3px);
}

.footer-bottom {
  border-top: 1px solid rgba(201, 71, 44, 0.08);
  padding: 18px 24px 26px;
  max-width: 1280px; margin: 0 auto;
  display: flex; justify-content: space-between; gap: 16px;
  color: var(--text-muted); font-size: 12px; flex-wrap: wrap;
}

@media (max-width: 768px) {
  .app-footer { padding-top: 48px; margin-top: 64px; }
  .footer-inner { grid-template-columns: 1fr; gap: 32px; padding-bottom: 36px; }
  .footer-bottom { flex-direction: column; align-items: flex-start; }
}
</style>
