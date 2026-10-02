<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { Compass } from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'

const route = useRoute()
const router = useRouter()

function goToDashboard(): void {
  void router.push({ name: 'dashboard' })
}
</script>

<template>
  <section class="mx-auto flex max-w-xl flex-col items-center py-10 text-center sm:py-16">
    <span
      class="flex h-16 w-16 items-center justify-center rounded-full bg-primary-soft"
      aria-hidden="true"
    >
      <Compass :size="30" class="text-primary" />
    </span>

    <p class="mt-6 text-caption font-semibold uppercase tracking-[0.14em] text-primary">
      Error 404
    </p>
    <h1 class="mt-1.5 text-title text-ink">Page not found</h1>

    <p class="mt-3 max-w-md text-[0.9375rem] leading-relaxed text-ink-muted">
      We could not find anything at this address. The link may be out of date, or the page may have
      moved since you last used it.
    </p>

    <p class="mt-4 max-w-full break-all rounded-md border border-border bg-surface px-3.5 py-2 font-mono text-[0.8125rem] text-ink-muted">
      {{ route.fullPath }}
    </p>

    <BaseButton class="mt-7" @click="goToDashboard">Back to dashboard</BaseButton>
  </section>
</template>