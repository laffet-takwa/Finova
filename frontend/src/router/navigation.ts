export interface NavItem {
  label: string
  to: string
  icon: string
  badge?: () => number
}

export interface NavSection {
  title: string
  items: NavItem[]
}

export const CUSTOMER_NAV: NavSection[] = [
  {
    title: 'Banking',
    items: [
      { label: 'Dashboard', to: '/dashboard', icon: 'LayoutDashboard' },
      { label: 'Accounts', to: '/accounts', icon: 'Wallet' },
      { label: 'Send money', to: '/transfer', icon: 'ArrowUpRight' },
      { label: 'Transactions', to: '/transactions', icon: 'Receipt' },
      { label: 'Notifications', to: '/notifications', icon: 'Bell' },
    ],
  },
  {
    title: 'Account',
    items: [
      { label: 'Security', to: '/security', icon: 'ShieldCheck' },
      { label: 'Settings', to: '/settings', icon: 'Settings' },
      { label: 'Help', to: '/security#support', icon: 'LifeBuoy' },
    ],
  },
]

export const ADMIN_NAV: NavSection[] = [
  {
    title: 'Operations',
    items: [
      { label: 'Overview', to: '/admin', icon: 'LayoutDashboard' },
      { label: 'Users', to: '/admin/users', icon: 'Users' },
      { label: 'Accounts', to: '/admin/accounts', icon: 'Wallet' },
      { label: 'Transactions', to: '/admin/transactions', icon: 'Receipt' },
      { label: 'Audit logs', to: '/admin/audit', icon: 'ScrollText' },
    ],
  },
  {
    title: 'Risk',
    items: [
      { label: 'Fraud & risk', to: '/admin/fraud', icon: 'ShieldAlert' },
      { label: 'Settings', to: '/admin/settings', icon: 'Settings' },
    ],
  },
]