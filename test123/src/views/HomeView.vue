<script setup lang="ts">
import { ref } from 'vue'
import {
  IxButton,
  IxInput,
  IxSelect,
  IxSelectItem,
  IxTextarea,
  showMessage,
  showToast,
} from '@siemens/ix-vue'

const title = ref('')
const category = ref('')
const description = ref('')

function onSubmit() {
  if (!title.value.trim()) {
    showMessage.warning('提示', '请先填写报修标题', '知道了')
    return
  }
  showToast({
    title: '提交成功',
    message: `报修单「${title.value}」已提交`,
    type: 'success',
  })
}
</script>

<template>
  <div class="page">
    <h1 class="page-title">报修系统 · 前端底座</h1>
    <p class="page-desc">
      Vue 3 + TypeScript + Vite + Siemens iX 已集成。下面是组件能力自检，可验证表单、按钮、消息提示是否正常。
    </p>

    <div class="demo-grid">
      <section class="card">
        <h2>报修单提交（示例表单）</h2>
        <div class="field">
          <IxInput v-model="title" label="报修标题" placeholder="请输入报修标题" />
        </div>
        <div class="field">
          <IxSelect v-model="category" label="故障分类" placeholder="请选择分类">
            <IxSelectItem label="设备故障" value="device" />
            <IxSelectItem label="网络问题" value="network" />
            <IxSelectItem label="软件异常" value="software" />
            <IxSelectItem label="其他" value="other" />
          </IxSelect>
        </div>
        <div class="field">
          <IxTextarea v-model="description" label="问题描述" placeholder="请描述故障现象" />
        </div>
        <div class="actions">
          <IxButton variant="primary" @click="onSubmit">提交</IxButton>
          <IxButton
            variant="secondary"
            @click="showMessage.info('提示', '这是一条 info 消息', '好的')"
          >
            消息提示
          </IxButton>
        </div>
      </section>

      <section class="card">
        <h2>集成说明</h2>
        <ul class="notes">
          <li>组件库：@siemens/ix（Web Components）+ @siemens/ix-vue（Vue 封装，支持 v-model）</li>
          <li>状态管理：Pinia（src/stores）</li>
          <li>路由：vue-router（src/router）</li>
          <li>请求：axios（/api 已配置代理，见 vite.config.ts）</li>
          <li>主题：classic / light（main.ts 中设置，可切换 dark）</li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page-desc {
  color: var(--theme-color-soft-text, #666);
  margin: 0 0 1.25rem;
}

.demo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 1rem;
}

.card {
  border: 1px solid var(--theme-color-weak-bdr, #e0e0e0);
  border-radius: 8px;
  padding: 1.25rem;
  background: var(--theme-color-1, #fff);
}

.card h2 {
  margin: 0 0 1rem;
  font-size: 1rem;
}

.field {
  margin-bottom: 1rem;
}

.actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1rem;
}

.notes {
  padding-left: 1.25rem;
  line-height: 1.8;
}
</style>
