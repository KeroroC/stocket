<script setup lang="ts">
import type { CurrentAccount } from '../../auth/AuthState'
import { visibleNavGroups } from './navConfig'

const props = defineProps<{ account: CurrentAccount }>()
const groups = visibleNavGroups(props.account.role)
</script>

<template>
  <aside class="desktop-sidebar">
    <div class="desktop-sidebar__brand">Stocket</div>
    <nav aria-label="桌面主导航">
      <section v-for="group in groups" :key="group.id" class="desktop-sidebar__group">
        <h2 class="desktop-sidebar__group-label">{{ group.label }}</h2>
        <RouterLink
          v-for="item in group.items"
          :key="item.to"
          :to="item.to"
          class="desktop-sidebar__link"
        >
          <component :is="item.icon" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </section>
    </nav>
  </aside>
</template>
