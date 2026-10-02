<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Info, Lock, RotateCcw, Save, ServerCog, Terminal } from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseToggle from '@/components/ui/BaseToggle.vue'
import DetailRow from '@/components/ui/DetailRow.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { config } from '@/api/apiClient'
import type { AdminSettings } from '@/types'

/**
 * What actually decides each number. The screen below is a display of the
 * deployment's configuration, not a way to change it — an operator editing
 * `JWT_ACCESS_TTL` here would change nothing, which is exactly why the real
 * source of each value is spelled out rather than implied.
 */
const PLATFORM_CONFIG: Array<{ key: string; source: string; enforcedBy: string }> = [
  {
    key: 'Session timeout',
    source: 'JWT_ACCESS_TTL',
    enforcedBy: 'api-gateway · finova.jwt.access-token-ttl-seconds',
  },
  {
    key: 'Access token lifetime',
    source: 'JWT_ACCESS_TTL',
    enforcedBy: 'api-gateway — the gateway signs and validates every token',
  },
  {
    key: 'Refresh token lifetime',
    source: 'JWT_REFRESH_TTL',
    enforcedBy: 'user-service · finova.jwt.refresh-token-ttl-seconds',
  },
  {
    key: 'Allowed browser origins',
    source: 'ALLOWED_ORIGINS',
    enforcedBy: 'api-gateway CORS filter',
  },
  {
    key: 'Admin route perimeter',
    source: 'GATEWAY_ADMIN_PATHS',
    enforcedBy: 'api-gateway — every path containing an admin segment also requires ROLE_ADMIN',
  },
  {
    key: 'Event backbone',
    source: 'KAFKA_BOOTSTRAP_SERVERS',
    enforcedBy: 'transaction-service, fraud-service, notification-service',
  },
  {
    key: 'Fraud large-amount threshold',
    source: 'finova.fraud.large-amount.tnd',
    enforcedBy: 'fraud-service rule engine — a deployment value, not a stored one',
  },
  {
    key: 'Fraud corroboration bonus',
    source: 'finova.fraud.corroboration-bonus',
    enforcedBy: 'fraud-service scoring',
  },
  {
    key: 'Transfer amount ceiling',
    source: 'finova.transactions.max-amount',
    enforcedBy: 'transaction-service validation',
  },
  {
    key: 'Audit retention',
    source: 'AUDIT_RETENTION_DAYS',
    enforcedBy: 'user-service — entries older than this are pruned',
  },
]

const SERVICE_ROUTES: Array<{ service: string; route: string }> = [
  { service: 'user-service', route: '/api/auth/** · /api/users/**' },
  { service: 'account-service', route: '/api/accounts/**' },
  { service: 'transaction-service', route: '/api/transactions/**' },
  { service: 'fraud-service', route: '/api/fraud/**' },
  { service: 'notification-service', route: '/api/notifications/**' },
]

const adminStore = useAdminStore()
const toast = useToastStore()

const form = reactive<AdminSettings>({ ...adminStore.settings })
const saved = ref<AdminSettings>({ ...adminStore.settings })
const saving = ref(false)

const environmentTone = computed(() => {
  const environment = form.environment.toLowerCase()
  if (environment.includes('prod')) return 'danger'
  if (environment.includes('stag')) return 'warning'
  return 'success'
})

/** The environment name is always spelled out; the colour only reinforces it. */
const environmentChipClass = computed(() => {
  if (environmentTone.value === 'danger') return 'border-danger/25 bg-danger-light text-danger-dark'
  if (environmentTone.value === 'warning') return 'border-warning/25 bg-warning-light text-warning-dark'
  return 'border-success/20 bg-success-light text-success-dark'
})

const isDirty = computed(() =>
  (Object.keys(form) as Array<keyof AdminSettings>).some((key) => form[key] !== saved.value[key]),
)

function numberField(value: string): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function onSave(): void {
  if (!isDirty.value || saving.value) return
  saving.value = true
  try {
    const payload: AdminSettings = {
      platformName: form.platformName.trim() || 'Finova',
      environment: form.environment,
      maintenanceMode: form.maintenanceMode,
      transactionApprovalThreshold: Math.max(0, numberField(String(form.transactionApprovalThreshold))),
      maxTransfersPerHour: Math.max(0, Math.round(numberField(String(form.maxTransfersPerHour)))),
      sessionTimeoutMinutes: Math.max(1, Math.round(numberField(String(form.sessionTimeoutMinutes)))),
      fraudScoringEnabled: form.fraudScoringEnabled,
      notificationsEnabled: form.notificationsEnabled,
    }
    // Finova has no settings endpoint: this writes the operator's own console state only.
    adminStore.settings = payload
    saved.value = { ...payload }
    toast.success(
      'Settings applied to this session',
      'The values above now describe how this console behaves for you. They do not change the deployed platform.',
    )
  } finally {
    saving.value = false
  }
}

function onDiscard(): void {
  Object.assign(form, saved.value)
}

onMounted(() => {
  Object.assign(form, adminStore.settings)
  saved.value = { ...adminStore.settings }
})
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader
      eyebrow="Finova Administration"
      title="System settings"
      description="How this console is configured, and where each of those values actually comes from."
    >
      <template #actions>
        <span
          class="fin-chip border"
          :class="environmentChipClass"
          role="status"
          aria-live="polite"
        >
          {{ form.environment || 'unknown environment' }}
        </span>
      </template>
    </PageHeader>

    <p class="flex items-start gap-2 rounded-md bg-warning-light px-3 py-2.5 text-caption text-warning-dark">
      <Info :size="14" class="mt-0.5 shrink-0" aria-hidden="true" />
      <span>
        <strong class="font-semibold">Changes here apply to your operator session only.</strong>
        Finova does not expose an endpoint that persists platform settings, so saving here updates this
        browser's console state and nothing else. The authoritative thresholds are deployment
        configuration, listed in the read-only panel below.
      </span>
    </p>

    <div class="grid gap-4 lg:grid-cols-5 lg:gap-5">
      <!-- Editable -->
      <section class="fin-card min-w-0 p-4 sm:p-5 lg:col-span-3" aria-labelledby="admin-settings-system">
        <h2 id="admin-settings-system" class="text-headline text-ink">System</h2>
        <p class="mt-0.5 text-caption text-ink-muted">
          Displayed values for the platform this console is pointed at.
        </p>

        <form class="mt-4 space-y-4" novalidate @submit.prevent="onSave">
          <div class="grid gap-4 sm:grid-cols-2">
            <BaseInput
              v-model="form.platformName"
              label="Platform name"
              required
              hint="Shown in the browser title and the sign-in screen."
            />
            <BaseInput
              v-model="form.environment"
              label="Environment"
              required
              hint="development, staging or production."
            />
          </div>

          <div class="grid gap-4 sm:grid-cols-3">
            <BaseInput
              v-model="form.transactionApprovalThreshold"
              label="Transaction approval threshold"
              inputmode="numeric"
              hint="TND, above which a transfer is held for approval."
            />
            <BaseInput
              v-model="form.maxTransfersPerHour"
              label="Max transfers per hour"
              inputmode="numeric"
              hint="Velocity ceiling per customer."
            />
            <BaseInput
              v-model="form.sessionTimeoutMinutes"
              label="Session timeout (minutes)"
              inputmode="numeric"
              hint="Mirrors JWT_ACCESS_TTL, which the gateway enforces."
            />
          </div>

          <div class="divide-y divide-border border-t border-border">
            <BaseToggle
              :model-value="form.maintenanceMode"
              label="Maintenance mode"
              description="Display-only here. The real maintenance switch is a gateway configuration value, so turning this on does not stop customer traffic."
              @update:model-value="form.maintenanceMode = $event"
            />
            <BaseToggle
              :model-value="form.fraudScoringEnabled"
              label="Fraud scoring"
              description="Display-only here. The fraud service scores every transaction it consumes regardless of this switch."
              @update:model-value="form.fraudScoringEnabled = $event"
            />
            <BaseToggle
              :model-value="form.notificationsEnabled"
              label="Notifications"
              description="Display-only here. Delivery channels are set per user in notification preferences."
              @update:model-value="form.notificationsEnabled = $event"
            />
          </div>

          <div class="flex flex-wrap items-center justify-end gap-2 border-t border-border pt-4">
            <p v-if="isDirty" class="mr-auto text-caption font-semibold text-warning-dark" role="status">
              Unsaved changes
            </p>
            <BaseButton variant="ghost" :disabled="!isDirty || saving" @click="onDiscard">
              <template #icon>
                <RotateCcw :size="15" aria-hidden="true" />
              </template>
              Discard
            </BaseButton>
            <BaseButton type="submit" :disabled="!isDirty" :loading="saving">
              <template #icon>
                <Save :size="15" aria-hidden="true" />
              </template>
              Save settings
            </BaseButton>
          </div>
        </form>
      </section>

      <!-- Read-only configuration -->
      <section class="fin-card min-w-0 p-4 sm:p-5 lg:col-span-2" aria-labelledby="admin-config-heading">
        <h2 id="admin-config-heading" class="flex items-center gap-2 text-headline text-ink">
          <Lock :size="16" class="text-ink-subtle" aria-hidden="true" />
          Read-only platform configuration
        </h2>
        <p class="mt-1 text-[0.875rem] leading-relaxed text-ink-muted">
          These values come from the deployment, not from this screen. The gateway enforces access
          token lifetime, allowed origins and the admin route perimeter; the services below own
          everything else. Nothing on this panel can be changed from the console.
        </p>

        <div class="mt-4">
          <p class="fin-label flex items-center gap-1.5">
            <Terminal :size="13" aria-hidden="true" />
            Enforced from environment variables
          </p>
          <ul class="mt-2 space-y-2.5">
            <li
              v-for="row in PLATFORM_CONFIG"
              :key="`${row.key}-${row.source}`"
              class="border-b border-border/60 pb-2.5 last:border-0"
            >
              <p class="text-[0.875rem] font-medium text-ink">{{ row.key }}</p>
              <p class="mt-0.5 break-all font-mono text-caption text-ink">{{ row.source }}</p>
              <p class="mt-0.5 text-caption text-ink-subtle">{{ row.enforcedBy }}</p>
            </li>
          </ul>
        </div>

        <div class="mt-5 border-t border-border pt-4">
          <p class="fin-label flex items-center gap-1.5">
            <ServerCog :size="13" aria-hidden="true" />
            Service routes behind the gateway
          </p>
          <dl class="mt-2 divide-y divide-border">
            <DetailRow
              v-for="route in SERVICE_ROUTES"
              :key="route.service"
              :label="route.service"
              :value="route.route"
              mono
            />
          </dl>
        </div>

        <div class="mt-5 border-t border-border pt-4">
          <p class="fin-label">This console</p>
          <dl class="divide-y divide-border">
            <DetailRow label="API base URL" :value="config.baseURL" mono />
            <DetailRow
              label="Data source"
              :value="config.useMocks ? 'Local mock data (VITE_USE_MOCKS=true)' : 'Live Finova gateway'"
            />
            <DetailRow label="Dev proxy target" :value="config.devProxyTarget" mono />
          </dl>
          <p v-if="config.useMocks" class="mt-2 text-caption text-warning-dark">
            Mock mode is on, so figures on this console come from fixtures in the browser rather than
            the platform.
          </p>
        </div>
      </section>
    </div>
  </div>
</template>