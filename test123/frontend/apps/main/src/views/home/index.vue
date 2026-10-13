<template>
  <div class="home">
    <h2>欢迎使用微服务生态底座</h2>
    <p>从左侧菜单打开子应用（审批中心），体验无界微前端的多 Tab 管理：</p>
    <ul>
      <li>切换 Tab：保活复用，10 分钟内不重新加载</li>
      <li>10 分钟未激活：实例自动销毁，再次点击重新加载</li>
      <li>刷新页面：Tab 列表从 sessionStorage 恢复</li>
      <li>主题切换：主应用经 bus 广播，子应用实时同步</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
/** 首页 / 欢迎页（主应用壳自带的普通页面） */
</script>

<style scoped>
.home {
  padding-top: 24px;
  color: var(--brand-text);
}
.home h2 {
  color: var(--brand-primary);
}
.home ul {
  color: var(--brand-text-secondary);
  line-height: 1.9;
}
</style>
