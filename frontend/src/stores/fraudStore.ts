import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fraudApi, type FraudFilters } from '@/api/fraudApi'
import type { FraudAlert, FraudStatsSummary, PageResponse } from '@/types'

export const useFraudStore = defineStore('fraud', () => {
  const alerts = ref<FraudAlert[]>([])
  const page = ref<PageResponse<FraudAlert> | null>(null)
  const stats = ref<FraudStatsSummary | null>(null)
  const selected = ref<FraudAlert | null>(null)
  const loading = ref(false)
  const loaded = ref(false)
  const acting = ref(false)
  const error = ref<string | null>(null)
  const filters = ref<FraudFilters>({ page: 0, size: 20 })

  const totalElements = computed(() => page.value?.totalElements ?? 0)
  const totalPages = computed(() => page.value?.totalPages ?? 0)
  const openAlerts = computed(() => alerts.value.filter((alert) => alert.status === 'OPEN'))
  const highRisk = computed(() => alerts.value.filter((alert) => alert.riskLevel === 'HIGH'))

  function upsert(alert: FraudAlert): void {
    const index = alerts.value.findIndex((item) => item.id === alert.id)
    if (index === -1) alerts.value = [alert, ...alerts.value]
    else alerts.value[index] = alert
    if (selected.value?.id === alert.id) selected.value = alert
  }

  async function fetchAlerts(next: Partial<FraudFilters> = {}, force = false): Promise<void> {
    if (loaded.value && !force && Object.keys(next).length === 0) return
    loading.value = true
    error.value = null
    filters.value = { ...filters.value, ...next }
    try {
      const result = await fraudApi.list(filters.value)
      page.value = result
      alerts.value = result.content
      loaded.value = true
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Unable to load fraud alerts.'
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function fetchStats(force = false): Promise<FraudStatsSummary | null> {
    if (stats.value && !force) return stats.value
    try {
      stats.value = await fraudApi.stats()
      return stats.value
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Unable to load fraud statistics.'
      return null
    }
  }

  async function fetchOne(id: string): Promise<FraudAlert> {
    const alert = await fraudApi.get(id)
    upsert(alert)
    selected.value = alert
    return alert
  }

  async function selectAlert(alert: FraudAlert | null): Promise<void> {
    selected.value = alert
    if (alert) {
      try {
        await fetchOne(alert.id)
      } catch {
        /* the list copy is already usable */
      }
    }
  }

  async function runAction(
    id: string,
    action: 'review' | 'safe' | 'confirm' | 'block',
    note?: string,
  ): Promise<FraudAlert> {
    acting.value = true
    try {
      const result =
        action === 'review'
          ? await fraudApi.startReview(id, { note })
          : action === 'safe'
            ? await fraudApi.markSafe(id, { note })
            : action === 'confirm'
              ? await fraudApi.confirmFraud(id, { note })
              : await fraudApi.blockAccount(id, { note })
      upsert(result)
      stats.value = null
      return result
    } finally {
      acting.value = false
    }
  }

  function reset(): void {
    alerts.value = []
    page.value = null
    stats.value = null
    selected.value = null
    loaded.value = false
    error.value = null
  }

  return {
    alerts,
    page,
    stats,
    selected,
    loading,
    loaded,
    acting,
    error,
    filters,
    totalElements,
    totalPages,
    openAlerts,
    highRisk,
    fetchAlerts,
    fetchStats,
    fetchOne,
    selectAlert,
    runAction,
    reset,
  }
})
