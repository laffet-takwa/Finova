<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ShieldAlert } from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'

const router = useRouter()

function goToDashboard(): void {
  void router.push({ name: 'dashboard' })
}
</script>

<template>
  <section class="mx-auto flex max-w-xl flex-col items-center py-10 text-center sm:py-16">
    <span
      class="flex h-16 w-16 items-center justify-center rounded-full bg-danger-light"
      aria-hidden="true"
    >
      <ShieldAlert :size="30" class="text-danger" />
    </span>

    <p class="mt-6 text-caption font-semibold uppercase tracking-[0.14em] text-danger">Error 403</p>
    <h1 class="mt-1.5 text-title text-ink">Access denied</h1>

    <p class="mt-3 max-w-md text-[0.9375rem] leading-relaxed text-ink-muted">
      This area is reserved for Finova administrators. Your current session is not permitted to open
      staff pages such as fraud review, user management or the audit log. If you should have access,
      sign in with an administrator account or contact Finova support.
    </p>

    <BaseButton class="mt-7" @click="goToDashboard">Back to dashboard</BaseButton>
  </section>
</template>