<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'
import { useGlobalSearch } from '../../dashboard/useGlobalSearch'

withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
const search = useGlobalSearch()
</script>
<template>
  <section class="global-search" :class="{ 'global-search--compact': compact }" aria-label="全局搜索">
    <el-input v-model="search.query.value" class="global-search__field" type="search" aria-label="全局搜索" placeholder="搜索名称、条码或位置" clearable><template #prefix><el-icon><Search /></el-icon></template></el-input>
    <p v-if="search.loading.value" class="global-search__status">搜索中…</p>
    <p v-if="search.error.value" class="global-search__status global-search__status--error" role="alert">
      {{ search.error.value }}
    </p>
    <el-card v-if="search.results.value.length" class="global-search__results" shadow="always">
      <div v-for="item in search.results.value" :key="item.id" class="global-search__result">
        <strong>{{ item.name }}</strong>
        <span>总量 {{ item.totalAvailable }}</span>
        <span>位置 {{ item.locations.join('、') || '未设置' }}</span>
        <span>最近批次 {{ item.recentBatch ?? '无' }}</span>
        <span>最早过期 {{ item.earliestExpiration ?? '无' }}</span>
      </div>
    </el-card>
  </section>
</template>
