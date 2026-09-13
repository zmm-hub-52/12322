<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

function navigate(path: string) {
  if (route.path !== path) {
    router.push(path)
  }
}
</script>

<template>
  <!-- ix-application：西门子 iX 应用外壳（头部 + 侧边菜单 + 内容区） -->
  <ix-application>
    <ix-application-header slot="application-header" name="报修系统" />

    <ix-menu
      slot="menu"
      application-name="报修系统"
      application-description="运维工单管理"
    >
      <ix-menu-item :active="route.path === '/home'" @click="navigate('/home')">
        首页
      </ix-menu-item>
      <ix-menu-item :active="route.path.startsWith('/orders')" @click="navigate('/orders')">
        报修单
      </ix-menu-item>
      <ix-menu-item :active="route.path === '/demo/native'" @click="navigate('/demo/native')">
        方案A · 原生表格
      </ix-menu-item>
      <ix-menu-item :active="route.path === '/demo/aggrid'" @click="navigate('/demo/aggrid')">
        方案B · AG Grid
      </ix-menu-item>
    </ix-menu>

    <!-- 默认插槽即主内容区 -->
    <router-view />
  </ix-application>
</template>

<style scoped>
/* 布局样式由 ix-application 提供，此处仅做必要的兜底 */
</style>
