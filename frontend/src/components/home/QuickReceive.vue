<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
</script>
<template>
  <RouterLink class="quick-receive" to="/receive" aria-label="快捷入库">
    <span class="quick-receive__icon"><Plus aria-hidden="true" /></span>
    <span class="quick-receive__copy">
      <strong>快捷入库</strong>
      <small>扫描条码或手动添加物品</small>
    </span>
    <span class="quick-receive__action">开始入库</span>
  </RouterLink>
</template>
