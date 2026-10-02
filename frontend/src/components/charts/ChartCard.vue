<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart as ChartJS,
  DoughnutController,
  Filler,
  Legend,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
  type ChartConfiguration,
} from 'chart.js'
import Skeleton from '@/components/ui/Skeleton.vue'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  // Renderers: without these controllers `new Chart(...)` throws
  // `"bar" is not a registered controller` at mount time.
  LineController,
  BarController,
  DoughnutController,
  Filler,
  Tooltip,
  Legend,
)

const props = withDefaults(
  defineProps<{
    type: 'line' | 'bar' | 'doughnut'
    title?: string
    subtitle?: string
    labels: string[]
    datasets: Array<{
      label: string
      data: Array<number | null>
      color?: string
      fill?: boolean
      dashed?: boolean
    }>
    height?: number
    currency?: string
    moneyFormat?: boolean
    loading?: boolean
    emptyMessage?: string
  }>(),
  {
    title: undefined,
    subtitle: undefined,
    height: 260,
    currency: '',
    moneyFormat: true,
    loading: false,
    emptyMessage: 'No data for this period yet.',
  },
)

const canvas = ref<HTMLCanvasElement | null>(null)
const chart = shallowRef<ChartJS | null>(null)
const hasData = () => props.datasets.some((set) => set.data.some((value) => (value ?? 0) > 0))

const PALETTE = ['#173B5F', '#3B82F6', '#16A34A', '#F59E0B', '#DC2626', '#667085']

function buildConfig(): ChartConfiguration {
  const isDoughnut = props.type === 'doughnut'
  const isCurrency = props.moneyFormat && props.currency

  return {
    type: props.type,
    data: {
      labels: props.labels,
      datasets: props.datasets.map((set, index) => ({
        label: set.label,
        data: set.data,
        borderColor: set.color ?? PALETTE[index % PALETTE.length],
        backgroundColor: isDoughnut
          ? (set.color ?? PALETTE[index % PALETTE.length])
          : props.type === 'bar'
            ? (set.color ?? PALETTE[index % PALETTE.length])
            : `color-mix(in srgb, ${set.color ?? PALETTE[index % PALETTE.length]} 12%, transparent)`,
        borderWidth: isDoughnut ? 0 : 2,
        borderDash: set.dashed ? [5, 4] : undefined,
        fill: isDoughnut ? false : (set.fill ?? props.type === 'line'),
        tension: 0.35,
        pointRadius: 0,
        pointHoverRadius: 4,
        pointHoverBorderWidth: 2,
        pointHoverBackgroundColor: set.color ?? PALETTE[index % PALETTE.length],
        borderRadius: props.type === 'bar' ? 4 : undefined,
        maxBarThickness: 34,
      })),
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 420, easing: 'easeOutQuart' },
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: {
          display: props.datasets.length > 1,
          position: 'bottom',
          align: 'start',
          labels: {
            usePointStyle: true,
            pointStyle: 'circle',
            boxWidth: 7,
            boxHeight: 7,
            padding: 16,
            color: '#667085',
            font: { size: 12, family: 'Inter, sans-serif' },
          },
        },
        tooltip: {
          backgroundColor: '#0D2740',
          titleColor: '#FFFFFF',
          bodyColor: '#E4E7EC',
          borderColor: 'rgba(255,255,255,0.12)',
          borderWidth: 1,
          padding: 11,
          cornerRadius: 6,
          displayColors: props.datasets.length > 1,
          titleFont: { size: 12, weight: 600, family: 'Inter, sans-serif' },
          bodyFont: { size: 12, family: 'Inter, sans-serif' },
          callbacks: {
            label: (item) => {
              const value = Number(item.parsed.y ?? item.parsed)
              const text = isCurrency
                ? `${value.toLocaleString('en-US', { minimumFractionDigits: 3, maximumFractionDigits: 3 })} ${props.currency}`
                : value.toLocaleString('en-US')
              return `${item.dataset.label ?? ''}: ${text}`.trim()
            },
          },
        },
      },
      scales: isDoughnut
        ? undefined
        : {
            x: {
              grid: { display: false },
              border: { display: false },
              ticks: {
                color: '#98A2B3',
                font: { size: 11, family: 'Inter, sans-serif' },
                maxRotation: 0,
                autoSkipPadding: 16,
              },
            },
            y: {
              beginAtZero: props.type !== 'line',
              grid: { color: '#EEF1F5' },
              border: { display: false },
              ticks: {
                color: '#98A2B3',
                font: { size: 11, family: 'Inter, sans-serif' },
                callback: (value) => {
                  const numeric = Number(value)
                  if (!isCurrency) return numeric.toLocaleString('en-US')
                  if (numeric >= 1_000_000) return `${(numeric / 1_000_000).toFixed(1)}M`
                  if (numeric >= 1_000) return `${(numeric / 1_000).toFixed(1)}k`
                  return numeric.toLocaleString('en-US')
                },
              },
            },
          },
    },
  } as ChartConfiguration
}

function render(): void {
  if (!canvas.value) return
  chart.value?.destroy()
  chart.value = new ChartJS(canvas.value, buildConfig())
}

watch(() => [props.labels, props.datasets], () => render(), { deep: true })

onMounted(render)

onBeforeUnmount(() => {
  chart.value?.destroy()
  chart.value = null
})
</script>

<template>
  <section class="fin-card flex flex-col p-4 sm:p-5">
    <header v-if="title || $slots.actions" class="mb-4 flex items-start justify-between gap-4">
      <div class="min-w-0">
        <h3 v-if="title" class="text-headline text-ink">{{ title }}</h3>
        <p v-if="subtitle" class="mt-0.5 text-caption text-ink-muted">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="shrink-0">
        <slot name="actions" />
      </div>
    </header>

    <div class="relative flex-1" :style="{ minHeight: `${height}px` }">
      <div v-if="loading" class="absolute inset-0 flex flex-col justify-end gap-2 pb-6" aria-hidden="true">
        <div class="fin-skeleton h-full min-h-[180px] w-full" />
      </div>

      <div
        v-else-if="!hasData()"
        class="absolute inset-0 flex items-center justify-center rounded-md bg-surface-sunken"
      >
        <p class="text-[0.875rem] text-ink-subtle">{{ emptyMessage }}</p>
      </div>

      <canvas
        v-show="!loading && hasData()"
        ref="canvas"
        :aria-label="title ?? 'Chart'"
        role="img"
      />
    </div>
  </section>
</template>