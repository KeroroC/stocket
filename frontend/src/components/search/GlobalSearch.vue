<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'
import { useGlobalSearch } from '../../dashboard/useGlobalSearch'
const search = useGlobalSearch()
</script>
<template>
  <section class="global-search">
    <label><Search aria-hidden="true" /><span class="sr-only">全局搜索</span><input v-model="search.query.value" type="search" aria-label="全局搜索" placeholder="名称、条码或位置" /></label>
    <p v-if="search.loading.value">搜索中…</p>
    <p v-if="search.error.value" role="alert">{{ search.error.value }}</p>
    <ul v-if="search.results.value.length" class="global-search__results">
      <li v-for="item in search.results.value" :key="item.id">
        <strong>{{ item.name }}</strong>
        <span>总量 {{ item.totalAvailable }}</span>
        <span>位置 {{ item.locations.join('、') || '未设置' }}</span>
        <span>最近批次 {{ item.recentBatch ?? '无' }}</span>
        <span>最早过期 {{ item.earliestExpiration ?? '无' }}</span>
      </li>
    </ul>
  </section>
</template>
