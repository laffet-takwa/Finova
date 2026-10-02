<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { Lock, ShieldCheck, Sparkles } from 'lucide-vue-next'

interface Feature {
  icon: typeof Lock
  title: string
  detail: string
}

const features: Feature[] = [
  { icon: Lock, title: 'Bank-grade security', detail: 'BCrypt credentials, JWT sessions, audited access.' },
  { icon: ShieldCheck, title: 'Protected transfers', detail: 'Every transfer is scored before the money moves.' },
  { icon: Sparkles, title: 'Instant insight', detail: 'Live balances, instant notifications, clear history.' },
]
</script>

<template>
  <div class="min-h-screen bg-background">
    <div class="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <!-- Brand panel -->
      <aside
        class="relative hidden overflow-hidden bg-primary-dark lg:flex lg:flex-col lg:justify-between lg:p-12"
      >
        <div
          class="pointer-events-none absolute -left-24 top-1/3 h-96 w-96 rounded-full bg-primary/50 blur-3xl"
          aria-hidden="true"
        />
        <div
          class="pointer-events-none absolute -right-32 -top-24 h-80 w-80 rounded-full bg-accent/20 blur-3xl"
          aria-hidden="true"
        />

        <RouterLink to="/login" class="relative flex items-center gap-2.5" aria-label="Finova home">
          <span class="flex h-9 w-9 items-center justify-center rounded-md bg-white/10" aria-hidden="true">
            <svg viewBox="0 0 32 32" class="h-5 w-5" fill="none">
              <path d="M10 9.5v13M22 9.5v13M10 16h12" stroke="#FFFFFF" stroke-width="2.6" stroke-linecap="round" />
              <circle cx="22" cy="11.6" r="2.1" fill="#3B82F6" />
            </svg>
          </span>
          <span class="font-display text-lg font-extrabold tracking-tight text-white">FINOVA</span>
        </RouterLink>

        <div class="relative max-w-md">
          <h1 class="font-display text-[2.75rem] font-extrabold leading-[1.1] tracking-tight text-white">
            Banking made simple.<br />
            Transactions made smarter.
          </h1>
          <p class="mt-5 text-[1.0625rem] leading-relaxed text-white/70">
            Secure digital banking for your everyday life — instant transfers, transparent fees and
            real-time protection against fraud.
          </p>

          <ul class="mt-10 space-y-5">
            <li v-for="feature in features" :key="feature.title" class="flex gap-3">
              <span
                class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-white/10"
                aria-hidden="true"
              >
                <component :is="feature.icon" :size="17" class="text-accent" />
              </span>
              <span>
                <span class="block text-[0.9375rem] font-semibold text-white">{{ feature.title }}</span>
                <span class="mt-0.5 block text-[0.8125rem] leading-relaxed text-white/55">
                  {{ feature.detail }}
                </span>
              </span>
            </li>
          </ul>
        </div>

        <p class="relative text-caption text-white/40">
          Simulated banking platform for demonstration purposes. No real funds are moved.
        </p>
      </aside>

      <!-- Form panel -->
      <main class="flex flex-col bg-surface">
        <div class="flex items-center justify-between px-5 py-5 lg:hidden">
          <RouterLink to="/login" class="flex items-center gap-2" aria-label="Finova home">
            <span class="flex h-8 w-8 items-center justify-center rounded-md bg-primary" aria-hidden="true">
              <svg viewBox="0 0 32 32" class="h-5 w-5" fill="none">
                <path d="M10 9.5v13M22 9.5v13M10 16h12" stroke="#FFFFFF" stroke-width="2.6" stroke-linecap="round" />
                <circle cx="22" cy="11.6" r="2.1" fill="#3B82F6" />
              </svg>
            </span>
            <span class="font-display text-[1.0625rem] font-extrabold tracking-tight text-ink">FINOVA</span>
          </RouterLink>
          <RouterLink to="/register" class="text-[0.875rem] font-semibold text-primary hover:underline">
            Create account
          </RouterLink>
        </div>

        <div class="flex flex-1 items-center justify-center px-5 py-8 sm:px-10 lg:py-12">
          <div class="w-full max-w-[26rem]">
            <slot />
          </div>
        </div>
      </main>
    </div>
  </div>
</template>