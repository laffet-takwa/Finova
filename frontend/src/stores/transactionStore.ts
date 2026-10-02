import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { newIdempotencyKey, transactionApi } from '@/api'
import { useAccountStore } from '@/stores/accountStore'
import type {
  BeneficiaryLookup,
  CreateTransactionRequest,
  Currency,
  PageResponse,
  Transaction,
  TransactionFilters,
  TransactionSummary,
} from '@/types'

/**
 * The in-flight transfer, shared by the three transfer screens. It is a plain
 * reactive object — no API calls, no persistence beyond the store — and it also
 * carries the **idempotency key minted when the user leaves step 2**, so every
 * retry of the final submit is recognised by the backend as the same transfer
 * instead of a second debit.
 */
export interface TransferDraft {
  senderAccountId: string
  receiverAccountNumber: string
  amount: number
  currency: Currency
  description: string
  idempotencyKey: string
  /** Resolved beneficiary, when the user ran "Verify recipient". */
  recipient: BeneficiaryLookup | null
  createdAt: string
}

export const useTransactionStore = defineStore('transactions', () => {
  const transactions = ref<Transaction[]>([])
  const page = ref<PageResponse<Transaction> | null>(null)
  const summary = ref<TransactionSummary | null>(null)
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<string | null>(null)
  const filters = ref<TransactionFilters>({ page: 0, size: 20, sort: 'createdAt,desc' })
  const submitting = ref(false)
  const lastCreated = ref<Transaction | null>(null)
  const transferDraft = ref<TransferDraft | null>(null)
  /** True when the last `createTransfer` was answered from the idempotency ledger. */
  const lastIdempotentReplay = ref(false)

  const totalElements = computed(() => page.value?.totalElements ?? 0)
  const totalPages = computed(() => page.value?.totalPages ?? 0)
  const currentPage = computed(() => page.value?.page ?? 0)
  const recent = computed(() => transactions.value.slice(0, 8))
  const pending = computed(() => transactions.value.filter((t) => t.status === 'PENDING'))

  function applyPage(next: PageResponse<Transaction> | null): void {
    page.value = next
    transactions.value = next?.content ?? []
    loaded.value = true
  }

  async function fetchList(nextFilters: Partial<TransactionFilters> = {}, force = false): Promise<void> {
    if (loaded.value && !force && Object.keys(nextFilters).length === 0) return
    loading.value = true
    error.value = null
    const merged = { ...filters.value, ...nextFilters }
    filters.value = merged
    try {
      applyPage(await transactionApi.list(merged))
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Unable to load transactions.'
      applyPage(null)
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function fetchSummary(force = false): Promise<TransactionSummary | null> {
    if (summary.value && !force) return summary.value
    try {
      summary.value = await transactionApi.summary()
      return summary.value
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Unable to load your activity summary.'
      return null
    }
  }

  async function fetchOne(id: string, force = false): Promise<Transaction> {
    if (!force) {
      const cached = transactions.value.find((item) => item.id === id)
      if (cached) return cached
    }
    const transaction = await transactionApi.get(id)
    const index = transactions.value.findIndex((item) => item.id === id)
    if (index === -1) transactions.value = [transaction, ...transactions.value]
    else transactions.value[index] = transaction
    return transaction
  }

  /**
   * Submits a transfer. The idempotency key is generated once per attempt and
   * reused across retries of the same logical transfer so a flaky network can
   * never produce a duplicate debit.
   */
  async function createTransfer(
    payload: CreateTransactionRequest,
    idempotencyKey?: string,
  ): Promise<Transaction> {
    submitting.value = true
    try {
      const result = await transactionApi.create(payload, { idempotencyKey: idempotencyKey ?? newIdempotencyKey() })
      const created = result.transaction
      lastIdempotentReplay.value = result.idempotentReplay
      lastCreated.value = created

      // A replay is the same transfer the ledger already recorded: it must not be
      // listed or settled twice, it only confirms the original submission.
      if (!result.idempotentReplay) {
        const index = transactions.value.findIndex((item) => item.id === created.id)
        if (index === -1) transactions.value = [created, ...transactions.value]
        else transactions.value[index] = created
        summary.value = null

        const accountStore = useAccountStore()
        if (created.settledSenderBalance != null) {
          accountStore.applySettledBalance(created.senderAccountId, created.settledSenderBalance)
        } else {
          void accountStore.refreshBalance(created.senderAccountId).catch(() => undefined)
        }
      }
      return created
    } finally {
      submitting.value = false
    }
  }

  function clearLast(): void {
    lastCreated.value = null
  }

  function setTransferDraft(draft: TransferDraft): void {
    transferDraft.value = draft
  }

  function clearTransferDraft(): void {
    transferDraft.value = null
  }

  function reset(): void {
    transactions.value = []
    page.value = null
    summary.value = null
    loaded.value = false
    error.value = null
    lastCreated.value = null
    transferDraft.value = null
    lastIdempotentReplay.value = false
    filters.value = { page: 0, size: 20, sort: 'createdAt,desc' }
  }

  return {
    transactions,
    page,
    summary,
    loading,
    loaded,
    error,
    filters,
    submitting,
    lastCreated,
    transferDraft,
    lastIdempotentReplay,
    totalElements,
    totalPages,
    currentPage,
    recent,
    pending,
    applyPage,
    fetchList,
    fetchSummary,
    fetchOne,
    createTransfer,
    clearLast,
    setTransferDraft,
    clearTransferDraft,
    reset,
  }
})
