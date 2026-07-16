<script setup lang="ts">
withDefaults(defineProps<{ title: string; description?: string; eyebrow?: string; headingLevel?: 1 | 2 }>(), { headingLevel: 1 })
</script>

<template>
  <header class="st-page-header">
    <div class="st-page-header__copy">
      <p v-if="eyebrow" class="st-page-header__eyebrow">{{ eyebrow }}</p>
      <component :is="`h${headingLevel}`">{{ title }}</component>
      <p v-if="description" class="st-page-header__description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="st-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.st-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--st-space-6);
  margin-bottom: var(--st-space-6);
}

.st-page-header__copy {
  min-width: 0;
  max-width: 48rem;
}

h1,
h2,
p {
  margin: 0;
  letter-spacing: 0;
}

h1 {
  font-size: clamp(1.65rem, 4vw, 1.9rem);
  line-height: 1.18;
  letter-spacing: -0.02em;
}

h2 {
  font-size: 1.35rem;
  line-height: 1.25;
}

.st-page-header__description {
  margin-top: var(--st-space-2);
  color: var(--st-color-text-muted);
  line-height: 1.6;
}

.st-page-header__eyebrow {
  margin-bottom: var(--st-space-1);
  color: var(--st-color-primary);
  font-size: 0.8125rem;
  font-weight: 700;
}

.st-page-header__actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--st-space-2);
}

@media (max-width: 640px) {
  .st-page-header {
    flex-direction: column;
    gap: var(--st-space-4);
  }

  .st-page-header__actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
