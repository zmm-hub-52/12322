<script setup lang="ts">
import { useRouter } from 'vue-router'
import { IxButton } from '@siemens/ix-vue'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面不存在</p>
    <IxButton variant="primary" @click="router.push('/home')">返回首页</IxButton>
  </div>
</template>

<style scoped>
.not-found {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
}

.not-found h1 {
  font-size: 4rem;
  margin: 0;
}
</style>
