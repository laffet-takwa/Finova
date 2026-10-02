<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Eye, EyeOff, Info, KeyRound, LifeBuoy, ShieldCheck, ShieldX } from 'lucide-vue-next'
import PageHeader from '@/components/ui/PageHeader.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import DetailRow from '@/components/ui/DetailRow.vue'
import StatusCheckRow from '@/components/ui/StatusCheckRow.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import PasswordStrengthMeter from '@/components/ui/PasswordStrengthMeter.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { userApi } from '@/api'
import { AppError } from '@/utils/errors'
import { evaluatePassword, formatDateTime, formatRelative } from '@/utils/format'
import { useDisplayPreferences } from '@/composables/useDisplayPreferences'
import type { SecurityStatus } from '@/types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toast = useToastStore()
const { formatDisplayDateTime } = useDisplayPreferences()

const loading = ref(true)
const error = ref<string | null>(null)
const offline = ref(false)
const security = ref<SecurityStatus | null>(null)

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const showCurrent = ref(false)
const showNew = ref(false)
const showConfirm = ref(false)
const passwordError = ref<string | null>(null)
const currentPasswordError = ref<string | null>(null)
const newPasswordError = ref<string | null>(null)
const confirmPasswordError = ref<string | null>(null)
const savingPassword = ref(false)

const strength = computed(() => evaluatePassword(newPassword.value))

const passwordChanged = computed(() => {
  if (security.value?.lastPasswordChange) return formatDateTime(security.value.lastPasswordChange)
  return 'Not recorded by Finova'
})

const LOGIN_ICONS = { SUCCESS: ShieldCheck, FAILURE: ShieldX }

async function load(force = false): Promise<void> {
  loading.value = true
  error.value = null
  try {
    security.value = await userApi.securityStatus()
  } catch (cause) {
    security.value = null
    error.value = cause instanceof Error ? cause.message : 'We could not load your security status.'
    offline.value = cause instanceof AppError ? cause.isNetworkError : false
  } finally {
    loading.value = false
  }
}

function validatePasswords(): boolean {
  currentPasswordError.value = null
  newPasswordError.value = null
  confirmPasswordError.value = null

  if (currentPassword.value.length === 0) {
    currentPasswordError.value = 'Enter your current password.'
  }
  if (strength.value.score < 3) {
    newPasswordError.value = 'Your new password must satisfy every requirement below.'
  } else if (newPassword.value === currentPassword.value) {
    newPasswordError.value = 'Choose a password you have not used before.'
  }
  if (confirmPassword.value.length === 0) {
    confirmPasswordError.value = 'Confirm your new password.'
  } else if (confirmPassword.value !== newPassword.value) {
    confirmPasswordError.value = 'The two passwords do not match.'
  }
  return (
    currentPasswordError.value === null &&
    newPasswordError.value === null &&
    confirmPasswordError.value === null
  )
}

async function onChangePassword(): Promise<void> {
  passwordError.value = null
  if (savingPassword.value) return
  if (!validatePasswords()) return

  savingPassword.value = true
  try {
    await userApi.changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    })
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    toast.success('Password changed', 'Use your new password the next time you sign in.')
    await load(true)
  } catch (cause) {
    if (cause instanceof AppError) {
      passwordError.value = cause.fieldError('currentPassword') ?? cause.fieldError('newPassword') ?? cause.message
    } else {
      passwordError.value = 'We could not change your password. Please try again.'
    }
  } finally {
    savingPassword.value = false
  }
}

onMounted(async () => {
  await load()
  // The sidebar "Help" link points at /security#support; the router scrolls to the
  // top for hash links, so bring the support card into view ourselves.
  if (route.hash === '#support') {
    await nextTick()
    document.getElementById('support')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
})
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader
      eyebrow="Account"
      title="Security centre"
      :description="`Signed in as ${authStore.user?.email ?? 'your Finova account'}. Review what protects your money and change your password.`"
    />

    <!-- Loading -->
    <div v-if="loading" class="space-y-5" aria-busy="true">
      <div class="fin-card space-y-4 p-5">
        <Skeleton variant="block" :rows="3" />
      </div>
      <div class="fin-card space-y-4 p-5">
        <Skeleton variant="block" :rows="2" />
      </div>
      <span class="sr-only">Loading your security status</span>
    </div>

    <!-- Error -->
    <ErrorState
      v-else-if="error"
      class="fin-card"
      title="We could not load your security status"
      :description="error"
      :offline="offline"
      retry-label="Try again"
      @retry="load(true)"
    />

    <template v-else-if="security">
      <div class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
        <div class="space-y-5">
          <!-- Security status -->
          <section class="fin-card p-4 sm:p-5">
            <h2 class="text-headline text-ink">Security status</h2>
            <ul class="mt-2 divide-y divide-border">
              <StatusCheckRow
                label="Password protected"
                value="In place"
                state="ok"
                :description="`Your password was last changed on ${passwordChanged}. Finova stores it as a one-way hash and never displays it.`"
              />

              <!-- TODO(2FA): when enrolment ships, replace this row with the real
                   enrolment action driven by `security.twoFactorEnabled === true`.
                   Until then the API reports twoFactorEnabled: false, so the row
                   must not claim protection Finova cannot provide. -->
              <StatusCheckRow
                label="Two-factor authentication"
                value="Not enabled"
                state="attention"
                description="Two-factor enrolment is not available in this release. Finova reports two-factor authentication as disabled for your account, so we do not claim it is protecting you."
              />

              <StatusCheckRow
                label="Recent activity monitored"
                value="Monitored"
                state="ok"
                :description="`Finova records every sign-in attempt and scores outgoing transfers. ${security.recentLogins.length} sign-in attempt${security.recentLogins.length === 1 ? '' : 's'} are listed below.`"
              />
            </ul>
          </section>

          <!-- Recent login activity -->
          <section class="fin-card p-4 sm:p-5">
            <h2 class="text-headline text-ink">Recent login activity</h2>
            <p class="mt-0.5 text-caption text-ink-muted">
              Sign-in attempts on your Finova account, newest first.
            </p>

            <ul class="mt-3 divide-y divide-border">
              <li
                v-for="(entry, index) in security.recentLogins"
                :key="`${entry.occurredAt}-${index}`"
                class="flex flex-col gap-2 rounded-md p-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4"
                :class="entry.result === 'FAILURE' ? 'bg-danger-light' : ''"
              >
                <div class="min-w-0">
                  <p class="flex flex-wrap items-center gap-2">
                    <span
                      class="flex items-center gap-1.5 text-caption font-bold uppercase tracking-wider"
                      :class="entry.result === 'SUCCESS' ? 'text-success-dark' : 'text-danger-dark'"
                    >
                      <component
                        :is="LOGIN_ICONS[entry.result]"
                        :size="14"
                        aria-hidden="true"
                      />
                      {{ entry.result === 'SUCCESS' ? 'Successful sign-in' : 'Failed sign-in' }}
                    </span>
                    <StatusBadge
                      :status="entry.result === 'SUCCESS' ? 'COMPLETED' : 'FAILED'"
                      size="sm"
                    />
                  </p>
                  <p class="mt-1 text-[0.9375rem] font-semibold text-ink">{{ entry.device }}</p>
                  <p v-if="entry.userAgent" class="mt-1 break-all font-mono text-caption text-ink-subtle">
                    {{ entry.userAgent }}
                  </p>
                  <p class="mt-1 text-caption text-ink-subtle">
                    <span class="font-mono">{{ entry.ipAddress ?? 'IP not provided' }}</span>
                    <span aria-hidden="true"> · </span>
                    <span>{{ entry.location ?? 'Location not provided by Finova' }}</span>
                  </p>
                  <p class="mt-0.5 text-caption text-ink-subtle">
                    Location source: {{ security.locationSource.replace(/_/g, ' ').toLowerCase() }}
                  </p>
                </div>

                <p class="shrink-0 text-caption text-ink-subtle sm:text-right">
                  <span class="block tabular-nums">{{ formatDisplayDateTime(entry.occurredAt) }}</span>
                  <span class="block">{{ formatRelative(entry.occurredAt) }}</span>
                </p>
              </li>
            </ul>

            <p
              v-if="security.recentLogins.some((entry) => entry.result === 'FAILURE')"
              class="mt-3 flex items-start gap-2 rounded-md bg-warning-light px-3 py-2.5 text-caption text-warning-dark"
              role="alert"
            >
              <Info :size="14" class="mt-0.5 shrink-0" aria-hidden="true" />
              <span>
                A failed sign-in is recorded above. If it was not you, change your password now and review every active
                session.
              </span>
            </p>
          </section>

          <!-- Change password -->
          <section class="fin-card p-4 sm:p-5">
            <div class="flex items-center gap-2">
              <KeyRound :size="17" class="text-primary" aria-hidden="true" />
              <h2 class="text-headline text-ink">Change password</h2>
            </div>
            <p class="mt-1 text-[0.875rem] leading-relaxed text-ink-muted">
              Choose a password you do not use anywhere else. You will stay signed in on this device.
            </p>

            <form class="mt-4 space-y-4" novalidate @submit.prevent="onChangePassword">
              <div
                v-if="passwordError"
                class="rounded-md bg-danger-light px-3 py-2.5 text-[0.875rem] text-danger-dark"
                role="alert"
              >
                {{ passwordError }}
              </div>

              <BaseInput
                v-model="currentPassword"
                label="Current password"
                :type="showCurrent ? 'text' : 'password'"
                autocomplete="current-password"
                required
                :error="currentPasswordError ?? undefined"
              >
                <template #suffix>
                  <button
                    type="button"
                    class="flex h-8 w-8 items-center justify-center rounded-md text-ink-subtle transition-colors hover:bg-surface-sunken hover:text-ink"
                    :aria-label="showCurrent ? 'Hide current password' : 'Show current password'"
                    @click="showCurrent = !showCurrent"
                  >
                    <EyeOff v-if="showCurrent" :size="16" aria-hidden="true" />
                    <Eye v-else :size="16" aria-hidden="true" />
                  </button>
                </template>
              </BaseInput>

              <div>
                <BaseInput
                  v-model="newPassword"
                  label="New password"
                  :type="showNew ? 'text' : 'password'"
                  autocomplete="new-password"
                  required
                  :error="newPasswordError ?? undefined"
                >
                  <template #suffix>
                    <button
                      type="button"
                      class="flex h-8 w-8 items-center justify-center rounded-md text-ink-subtle transition-colors hover:bg-surface-sunken hover:text-ink"
                      :aria-label="showNew ? 'Hide new password' : 'Show new password'"
                      @click="showNew = !showNew"
                    >
                      <EyeOff v-if="showNew" :size="16" aria-hidden="true" />
                      <Eye v-else :size="16" aria-hidden="true" />
                    </button>
                  </template>
                </BaseInput>
                <PasswordStrengthMeter :password="newPassword" label="New password strength" />
              </div>

              <BaseInput
                v-model="confirmPassword"
                label="Confirm new password"
                :type="showConfirm ? 'text' : 'password'"
                autocomplete="new-password"
                required
                :error="confirmPasswordError ?? undefined"
              >
                <template #suffix>
                  <button
                    type="button"
                    class="flex h-8 w-8 items-center justify-center rounded-md text-ink-subtle transition-colors hover:bg-surface-sunken hover:text-ink"
                    :aria-label="showConfirm ? 'Hide confirmation' : 'Show confirmation'"
                    @click="showConfirm = !showConfirm"
                  >
                    <EyeOff v-if="showConfirm" :size="16" aria-hidden="true" />
                    <Eye v-else :size="16" aria-hidden="true" />
                  </button>
                </template>
              </BaseInput>

              <div class="flex justify-end border-t border-border pt-4">
                <BaseButton type="submit" :loading="savingPassword" :disabled="savingPassword">
                  Change password
                </BaseButton>
              </div>
            </form>
          </section>
        </div>

        <!-- Support -->
        <aside class="space-y-4 lg:sticky lg:top-20">
          <section id="support" class="fin-card scroll-mt-24 p-4 sm:p-5">
            <h2 class="flex items-center gap-2 text-headline text-ink">
              <LifeBuoy :size="17" class="text-primary" aria-hidden="true" />
              Support
            </h2>
            <p class="mt-1.5 text-[0.875rem] leading-relaxed text-ink-muted">
              This Finova build is a demonstration environment. Accounts, balances and transactions are simulated and
              <strong class="font-semibold text-ink">no real money is held, moved or protected by a real bank</strong>.
            </p>

            <dl class="mt-3 divide-y divide-border border-t border-border">
              <DetailRow label="Security questions" value="Use the Security centre above" />
              <DetailRow label="Held transfers" value="Quote the transfer reference" />
              <DetailRow label="Bug reports" value="Include the trace reference shown on the transfer" />
            </dl>

            <div class="mt-3 space-y-2">
              <BaseButton variant="secondary" size="sm" block @click="router.push({ name: 'transactions' })">
                Review your transactions
              </BaseButton>
              <BaseButton variant="ghost" size="sm" block to="/settings">Open settings</BaseButton>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>
