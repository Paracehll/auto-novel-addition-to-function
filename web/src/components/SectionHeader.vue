<script lang="ts" setup>
defineProps<{ title: string }>();
</script>

<template>
  <n-flex
    align="baseline"
    justify="space-between"
    style="width: 100%; row-gap: 2px"
  >
    <n-flex align="baseline" :size="8">
      <n-h2 prefix="bar" style="flex-shrink: 0; margin-bottom: 0">{{ title }}</n-h2>
      <slot name="title-extra" />
    </n-flex>
    <n-flex style="flex-shrink: 0">
      <slot />
    </n-flex>
  </n-flex>
</template>
