import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { configureAuthHooks } from '@/api/apiClient'
import { useAuthStore } from '@/stores/authStore'
import './styles/main.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

const auth = useAuthStore(pinia)

configureAuthHooks(
  () => auth.refreshSession(),
  () => {
    if (router.currentRoute.value.meta.public) return
    void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  },
)

app.mount('#app')
