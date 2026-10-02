<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { CloudOff } from 'lucide-vue-next'
import Sidebar from '@/components/layout/Sidebar.vue'
import Topbar from '@/components/layout/Topbar.vue'
import MobileNavigation from '@/components/layout/MobileNavigation.vue'
import { ADMIN_NAV, CUSTOMER_NAV } from '@/router/navigation'

const route = useRoute()

const collapsed = ref(localStorage.getItem('finova.sidebar.collapsed') === 'true')
const mobileNavOpen = ref(false)
const offline = ref(!navigator.onLine)

function toggleCollapsed(): void {
  collapsed.value = !collapsed.value
  localStorage.setItem('finova.sidebar.collapsed', String(collapsed.value))
}

const title = computed(() => {
  const all = [...CUSTOMER_NAV.flatMap((section) => section.items), ...ADMIN_NAV.flatMap((section) => section.items)]
  return all.find((item) => route.path === item.to)?.label ?? 'Finova'
})

watch(
  () => route.fullPath,
  () => {
    mobileNavOpen.value = false
  },
)

watch(
  () => route.path,
  () => {
    const active = document.activeElement
    if (active instanceof HTMLElement && (active.hasAttribute('aria-expanded') || active.tagName === 'BUTTON')) {
      active.blur()
    }
  },
)

function handleOnline(): void {
  offline.value = false
}

function handleOffline(): void {
  offline.value = true
}

window.addEventListener('online', handleOnline)
window.addEventListener('offline', handleOffline)
</script>

<template>
  <div class="flex min-h-screen bg-background">
    <!-- Desktop sidebar -->
    <div class="sticky top-0 hidden h-screen shrink-0 lg:block">
      <Sidebar :collapsed="collapsed" @toggle="toggleCollapsed" />
    </div>

    <!-- Mobile drawer -->
    <Transition name="drawer">
      <div v-if="mobileNavOpen" class="fixed inset-0 z-40 lg:hidden">
        <div
          class="absolute inset-0 bg-primary-dark/45 backdrop-blur-[2px]"
          aria-hidden="true"
          @click="mobileNavOpen = false"
        />
        <div class="absolute inset-y-0 left-0 h-full animate-slide-up" role="dialog" aria-modal="true" aria-label="Navigation">
          <Sidebar
            :collapsed="false"
            @toggle="mobileNavOpen = false"
            @navigate="mobileNavOpen = false"
          />
        </div>
      </div>
    </Transition>

    <div class="flex min-w-0 flex-1 flex-col">
      <Topbar :title="title" @toggle-sidebar="mobileNavOpen = true" />

      <Transition name="banner">
        <div
          v-if="offline"
          class="flex items-center justify-center gap-2 bg-warning-light px-4 py-2 text-[0.8125rem] font-medium text-warning-dark"
          role="status"
        >
          <CloudOff :size="15" aria-hidden="true" />
          You are offline. Some actions will fail until the connection is restored.
        </div>
      </Transition>

      <main class="flex-1 px-4 pb-24 pt-5 sm:px-6 sm:pb-8 lg:pb-8">
        <div class="mx-auto w-full max-w-[86rem]">
          <RouterView />
        </div>
      </main>
    </div>

    <MobileNavigation />
  </div>
</template>

<style scoped>
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 160ms ease-out;
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}

.banner-enter-active,
.banner-leave-active {
  transition: all 180ms ease-out;
}

.banner-enter-from,
.banner-leave-to {
  opacity: 0;
  transform: translateY(-100%);
}

@media (prefers-reduced-motion: reduce) {
  .drawer-enter-active,
  .drawer-leave-active,
  .banner-enter-active,
  .banner-leave-active {
    transition: none;
  }
}
</style>