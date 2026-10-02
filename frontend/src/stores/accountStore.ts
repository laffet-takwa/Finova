import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { accountApi } from '@/api'
import type { Account, AccountFilters, AccountStatsSummary, Currency } from '@/types'

export const useAccountStore = defineStore('accounts', () => {
  const accounts = ref<Account[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<string | null>(null)
  const creating = ref(false)
  const stats = ref<AccountStatsSummary | null>(null)

  const byId = computed(() => {
    const map: Record<string, Account> = {}
    for (const account of accounts.value) map[account.id] = account
    return map
  })

  const activeAccounts = computed(() =>
    accounts.value.filter((account) => account.status === 'ACTIVE'),
  )

  const totalBalance = computed(() =>
    accounts.value
      .filter((account) => account.status === 'ACTIVE')
      .reduce((sum, account) => sum + (account.balance ?? 0), 0),
  )

  const totalsByCurrency = computed(() => {
    const totals: Record<string, number> = {}
    for (const account of accounts.value) {
      if (account.status !== 'ACTIVE') continue
      totals[account.currency] = (totals[account.currency] ?? 0) + (account.balance ?? 0)
    }
    return totals
  })

  const primaryCurrency = computed<Currency>(() => {
    const first = accounts.value[0]
    return first?.currency ?? 'TND'
  })

  const defaultSenderAccount = computed<Account | null>(
    () => activeAccounts.value.find((account) => account.accountType === 'CHECKING') ?? activeAccounts.value[0] ?? null,
  )

  function setAccounts(next: Account[]): void {
    accounts.value = next
    loaded.value = true
  }

  /** Applies a post-settlement balance so the UI reflects a transfer immediately. */
  function applySettledBalance(accountId: string, balance: number): void {
    const index = accounts.value.findIndex((account) => account.id === accountId)
    if (index === -1) return
    const current = accounts.value[index]
    accounts.value[index] = { ...current, balance, availableBalance: balance }
  }

  async function fetchAll(force = false): Promise<Account[]> {
    if (loaded.value && !force) return accounts.value
    loading.value = true
    error.value = null
    try {
      const data = await accountApi.list()
      setAccounts(data)
      return data
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Unable to load your accounts.'
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function fetchOne(id: string, force = false): Promise<Account> {
    const cached = byId.value[id]
    if (cached && !force) return cached
    const account = await accountApi.get(id)
    const index = accounts.value.findIndex((item) => item.id === id)
    if (index === -1) accounts.value = [...accounts.value, account]
    else accounts.value[index] = account
    loaded.value = true
    return account
  }

  async function refreshBalance(id: string): Promise<number> {
    const balance = await accountApi.balance(id)
    applySettledBalance(id, balance.balance)
    return balance.balance
  }

  async function create(payload: import('@/types').CreateAccountRequest): Promise<Account> {
    creating.value = true
    try {
      const account = await accountApi.create(payload)
      accounts.value = [account, ...accounts.value]
      loaded.value = true
      return account
    } finally {
      creating.value = false
    }
  }

  async function setStatus(
    id: string,
    status: 'ACTIVE' | 'BLOCKED' | 'CLOSED',
    reason?: string,
  ): Promise<Account> {
    const updated = await accountApi.updateStatus(id, { status, reason })
    const index = accounts.value.findIndex((item) => item.id === id)
    if (index !== -1) accounts.value[index] = updated
    return updated
  }

  async function fetchStats(): Promise<AccountStatsSummary> {
    stats.value = await accountApi.adminStats()
    return stats.value
  }

  function reset(): void {
    accounts.value = []
    loaded.value = false
    error.value = null
    stats.value = null
  }

  return {
    accounts,
    loading,
    loaded,
    error,
    creating,
    stats,
    byId,
    activeAccounts,
    totalBalance,
    totalsByCurrency,
    primaryCurrency,
    defaultSenderAccount,
    setAccounts,
    applySettledBalance,
    fetchAll,
    fetchOne,
    refreshBalance,
    create,
    setStatus,
    fetchStats,
    reset,
  }
})

export type AccountFiltersExport = AccountFilters
