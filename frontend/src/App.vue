<script setup lang="ts">
import { onMounted, onUnmounted, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'
import AuthLayout from '@/layouts/AuthLayout.vue'
import ToastViewport from '@/components/ui/ToastViewport.vue'
import { useAuthStore } from '@/stores/authStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { useAdminStore } from '@/stores/adminStore'

const route = useRoute()
const auth = useAuthStore()
const notifications = useNotificationStore()
const admin = useAdminStore()

const isAuthLayout = () => route.meta.layout === 'auth'

function syncSession(): void {
  if (auth.isAuthenticated && !route.meta.public) {
    notifications.startPolling()
    if (auth.isAdmin) admin.startPolling()
  } else {
    notifications.stopPolling()
    admin.stopPolling()
  }
}

onMounted(() => {
  auth.initialize()
  if (auth.isAuthenticated) {
    void auth.fetchMe()
    syncSession()
  }
})

watch(() => route.fullPath, syncSession)
watch(() => auth.isAuthenticated, syncSession)

onUnmounted(() => {
  notifications.stopPolling()
  admin.stopPolling()
})
</script>

<template>
  <AuthLayout v-if="isAuthLayout()">
    <RouterView v-slot="{ Component }">
      <Transition name="page" mode="out-in">
        <component :is="Component" />
      </Transition>
    </RouterView>
  </AuthLayout>

  <AppShell v-else>
    <RouterView v-slot="{ Component }">
      <Transition name="page" mode="out-in">
        <component :is="Component" />
      </Transition>
    </RouterView>
  </AppShell>

  <ToastViewport />
</template>

<style>
.page-enter-active,
.page-leave-active {
  transition: opacity 140ms ease-out, transform 140ms ease-out;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-2px);
}

@media (prefers-reduced-motion: reduce) {
  .page-enter-active,
  .page-leave-active {
    transition: none;
  }
}
</style>
