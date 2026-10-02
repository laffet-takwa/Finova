import { computed, ref, watch } from 'vue'
import type { Currency } from '@/types'
import { CURRENCIES } from '@/utils/format'

/**
 * Client-side display preferences.
 *
 * These are deliberately **browser-local** (`localStorage`), not server state:
 * the backend has no preferences endpoint for display formatting, so the UI must
 * say so rather than pretend they sync. The values are read once per module load
 * and mirrored to storage on every change so any view can format money and time
 * the same way.
 */
export type TimeFormat = '24h' | '12h'
export type NumberFormat = 'dot' | 'comma'

const KEYS = {
  currency: 'finova.preferences.displayCurrency',
  timeFormat: 'finova.preferences.timeFormat',
  numberFormat: 'finova.preferences.numberFormat',
} as const

function readCurrency(): Currency {
  const raw = localStorage.getItem(KEYS.currency)
  return CURRENCIES.includes(raw as Currency) ? (raw as Currency) : 'TND'
}

function readTimeFormat(): TimeFormat {
  return localStorage.getItem(KEYS.timeFormat) === '12h' ? '12h' : '24h'
}

function readNumberFormat(): NumberFormat {
  return localStorage.getItem(KEYS.numberFormat) === 'comma' ? 'comma' : 'dot'
}

const displayCurrency = ref<Currency>(readCurrency())
const timeFormat = ref<TimeFormat>(readTimeFormat())
const numberFormat = ref<NumberFormat>(readNumberFormat())

watch(displayCurrency, (value) => localStorage.setItem(KEYS.currency, value))
watch(timeFormat, (value) => localStorage.setItem(KEYS.timeFormat, value))
watch(numberFormat, (value) => localStorage.setItem(KEYS.numberFormat, value))

/** `1,234.500 TND` or `1.234,500 TND`, always three decimals, always tabular. */
export function formatDisplayAmount(value: number | null | undefined, currency: Currency = displayCurrency.value): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  const locale = numberFormat.value === 'comma' ? 'de-DE' : 'en-US'
  return `${value.toLocaleString(locale, {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  })} ${currency}`
}

/** ISO timestamp rendered in the user's chosen clock convention. */
export function formatDisplayDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const day = date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
  if (timeFormat.value === '24h') {
    return `${day} ${date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false })}`
  }
  return `${day} ${date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true })}`
}

export function useDisplayPreferences() {
  return {
    displayCurrency,
    timeFormat,
    numberFormat,
    currencies: CURRENCIES,
    formatDisplayAmount,
    formatDisplayDateTime,
  }
}

/** Exported for the settings summary so the panel can explain where the value lives. */
export const PREFERENCE_STORAGE_PREFIX = 'finova.preferences.'
