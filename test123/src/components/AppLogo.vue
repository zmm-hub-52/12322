<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 40 })
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 48 48"
    role="img"
    aria-label="ABC"
    xmlns="http://www.w3.org/2000/svg"
  >
    <rect width="48" height="48" rx="10" fill="var(--theme-color-primary, #006e93)" />
    <text
      x="24"
      y="31"
      text-anchor="middle"
      font-family="'Siemens Sans', 'Segoe UI', 'Microsoft YaHei', sans-serif"
      font-size="17"
      font-weight="700"
      letter-spacing="0.5"
      fill="var(--theme-color-primary--contrast, #ffffff)"
    >
      ABC
    </text>
  </svg>
</template>
