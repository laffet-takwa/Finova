import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { tokenStorage } from '@/api/apiClient'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    public?: boolean
    requiresAdmin?: boolean
    section?: string
    hideInNav?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: { name: 'dashboard' },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: 'Sign in', public: true, layout: 'auth' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { title: 'Create account', public: true, layout: 'auth' },
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/customer/DashboardView.vue'),
    meta: { title: 'Dashboard', section: 'Dashboard' },
  },
  {
    path: '/accounts',
    name: 'accounts',
    component: () => import('@/views/customer/AccountsView.vue'),
    meta: { title: 'My accounts', section: 'Accounts' },
  },
  {
    path: '/accounts/:id',
    name: 'account-detail',
    component: () => import('@/views/customer/AccountDetailView.vue'),
    meta: { title: 'Account details', hideInNav: true },
  },
  {
    path: '/transfer',
    name: 'transfer',
    component: () => import('@/views/transfer/TransferView.vue'),
    meta: { title: 'Send money', section: 'Send money' },
  },
  {
    path: '/transfer/review',
    name: 'transfer-review',
    component: () => import('@/views/transfer/TransferReviewView.vue'),
    meta: { title: 'Review transfer', hideInNav: true },
  },
  {
    path: '/transfer/success/:id',
    name: 'transfer-success',
    component: () => import('@/views/transfer/TransferSuccessView.vue'),
    meta: { title: 'Transfer completed', hideInNav: true },
  },
  {
    path: '/transactions',
    name: 'transactions',
    component: () => import('@/views/customer/TransactionsView.vue'),
    meta: { title: 'Transactions', section: 'Transactions' },
  },
  {
    path: '/transactions/:id',
    name: 'transaction-detail',
    component: () => import('@/views/customer/TransactionDetailView.vue'),
    meta: { title: 'Transaction details', hideInNav: true },
  },
  {
    path: '/notifications',
    name: 'notifications',
    component: () => import('@/views/customer/NotificationsView.vue'),
    meta: { title: 'Notifications', section: 'Notifications' },
  },
  {
    path: '/security',
    name: 'security',
    component: () => import('@/views/customer/SecurityView.vue'),
    meta: { title: 'Security', section: 'Security' },
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('@/views/customer/SettingsView.vue'),
    meta: { title: 'Settings', section: 'Settings' },
  },
  {
    path: '/admin',
    name: 'admin-dashboard',
    component: () => import('@/views/admin/AdminDashboardView.vue'),
    meta: { title: 'Overview', section: 'Overview', requiresAdmin: true },
  },
  {
    path: '/admin/users',
    name: 'admin-users',
    component: () => import('@/views/admin/AdminUsersView.vue'),
    meta: { title: 'Users', section: 'Users', requiresAdmin: true },
  },
  {
    path: '/admin/accounts',
    name: 'admin-accounts',
    component: () => import('@/views/admin/AdminAccountsView.vue'),
    meta: { title: 'Accounts', section: 'Accounts', requiresAdmin: true },
  },
  {
    path: '/admin/transactions',
    name: 'admin-transactions',
    component: () => import('@/views/admin/AdminTransactionsView.vue'),
    meta: { title: 'Transactions', section: 'Transactions', requiresAdmin: true },
  },
  {
    path: '/admin/fraud',
    name: 'admin-fraud',
    component: () => import('@/views/admin/FraudDashboardView.vue'),
    meta: { title: 'Fraud & risk', section: 'Fraud & risk', requiresAdmin: true },
  },
  {
    path: '/admin/fraud/:id',
    name: 'admin-fraud-detail',
    component: () => import('@/views/admin/FraudDetailView.vue'),
    meta: { title: 'Alert details', requiresAdmin: true, hideInNav: true },
  },
  {
    path: '/admin/audit',
    name: 'admin-audit',
    component: () => import('@/views/admin/AdminAuditView.vue'),
    meta: { title: 'Audit logs', section: 'Audit logs', requiresAdmin: true },
  },
  {
    path: '/admin/settings',
    name: 'admin-settings',
    component: () => import('@/views/admin/AdminSettingsView.vue'),
    meta: { title: 'System settings', section: 'Settings', requiresAdmin: true },
  },
  {
    path: '/forbidden',
    name: 'forbidden',
    component: () => import('@/views/ForbiddenView.vue'),
    meta: { title: 'Access denied' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: 'Page not found', public: true },
  },
]

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition ?? { top: 0, behavior: 'smooth' }
  },
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  auth.initialize()
  const toast = useToastStore()

  if (to.meta.public) {
    if ((to.name === 'login' || to.name === 'register') && auth.isAuthenticated) {
      return { name: 'dashboard' }
    }
    return true
  }

  if (!auth.isAuthenticated && !tokenStorage.getAccess()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && auth.role !== 'ADMIN') {
    toast.warning('Administrator access required', 'This area is restricted to Finova staff.')
    return { name: 'dashboard' }
  }

  return true
})

router.afterEach((to) => {
  const base = 'Finova'
  document.title = to.meta.title ? `${to.meta.title} · ${base}` : base
})

export default router
