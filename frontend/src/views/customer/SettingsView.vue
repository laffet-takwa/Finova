<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Info, MonitorSmartphone, Save, ShieldCheck, User } from 'lucide-vue-next'
import PageHeader from '@/components/ui/PageHeader.vue'
import TabList from '@/components/ui/TabList.vue'
import { tabPanelId, type TabItem } from '@/components/ui/tabs'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseSelect from '@/components/ui/BaseSelect.vue'
import BaseToggle from '@/components/ui/BaseToggle.vue'
import DetailRow from '@/components/ui/DetailRow.vue'
import StatusCheckRow from '@/components/ui/StatusCheckRow.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import { useAuthStore } from '@/stores/authStore'
import { useNotificationStore } from '@/stores/notificationStore'
import { useToastStore } from '@/stores/toastStore'
import { userApi } from '@/api'
import { AppError } from '@/utils/errors'
import { formatDateTime, isValidPhone } from '@/utils/format'
import { useDisplayPreferences } from '@/composables/useDisplayPreferences'
import type { NotificationPreference, SecurityStatus } from '@/types'

const router = useRouter()
const authStore = useAuthStore()
const notificationStore = useNotificationStore()
const toast = useToastStore()
const {
  displayCurrency,
  timeFormat,
  numberFormat,
  currencies,
  formatDisplayAmount,
  formatDisplayDateTime,
} = useDisplayPreferences()

type SectionId = 'profile' | 'security' | 'notifications' | 'preferences'

const SECTIONS: TabItem[] = [
  { id: 'profile', label: 'Profile' },
  { id: 'security', label: 'Security' },
  { id: 'notifications', label: 'Notifications' },
  { id: 'preferences', label: 'Preferences' },
]

const SECTION_IDS: SectionId[] = ['profile', 'security', 'notifications', 'preferences']

function onSectionChange(id: string): void {
  if (SECTION_IDS.includes(id as SectionId)) activeSection.value = id as SectionId
}

const activeSection = ref<SectionId>('profile')

// -- Profile ----------------------------------------------------------------
const firstName = ref('')
const lastName = ref('')
const phone = ref('')
const firstNameError = ref<string | null>(null)
const lastNameError = ref<string | null>(null)
const phoneError = ref<string | null>(null)
const profileError = ref<string | null>(null)
const savingProfile = ref(false)
const profileLoaded = ref(false)

const email = computed(() => authStore.user?.email ?? '—')
const memberSince = computed(() => (authStore.user?.createdAt ? formatDateTime(authStore.user.createdAt) : '—'))

// -- Security summary -------------------------------------------------------
const security = ref<SecurityStatus | null>(null)
const securityLoading = ref(true)
const securityError = ref<string | null>(null)

// -- Notification preferences -----------------------------------------------
const preferences = ref<NotificationPreference>({
  emailEnabled: true,
  pushEnabled: true,
  inAppEnabled: true,
  transferAlerts: true,
  securityAlerts: true,
  marketingEmails: false,
})
const savedPreferences = ref<NotificationPreference>({ ...preferences.value })
const preferencesLoading = ref(true)
const preferencesError = ref<string | null>(null)
const savingPreferences = ref(false)

const preferencesDirty = computed(() => PREFERENCE_KEYS.some((key) => preferences.value[key] !== savedPreferences.value[key]))

/** The six booleans the customer can actually change (`updatedAt` is server-owned). */
type PreferenceKey =
  | 'emailEnabled'
  | 'pushEnabled'
  | 'inAppEnabled'
  | 'transferAlerts'
  | 'securityAlerts'
  | 'marketingEmails'

const PREFERENCE_KEYS: PreferenceKey[] = [
  'emailEnabled',
  'pushEnabled',
  'inAppEnabled',
  'transferAlerts',
  'securityAlerts',
  'marketingEmails',
]

const PREFERENCE_ROWS: Array<{ key: PreferenceKey; label: string; description: string }> = [
  { key: 'emailEnabled', label: 'Email notifications', description: 'Send notification emails to your registered address.' },
  { key: 'pushEnabled', label: 'Push notifications', description: 'Deliver alerts to this browser when Finova is open.' },
  { key: 'inAppEnabled', label: 'In-app notifications', description: 'Show alerts in the Finova notification centre and topbar.' },
  { key: 'transferAlerts', label: 'Transfer alerts', description: 'Receipts, failures and holds for every transfer you make or receive.' },
  { key: 'securityAlerts', label: 'Security alerts', description: 'New-device sign-ins, blocked accounts and password changes.' },
  { key: 'marketingEmails', label: 'Product and marketing emails', description: 'Occasional news about Finova features. Never financial data.' },
]

function preferencePayload(source: NotificationPreference): NotificationPreference {
  const payload: NotificationPreference = {
    emailEnabled: source.emailEnabled,
    pushEnabled: source.pushEnabled,
    inAppEnabled: source.inAppEnabled,
    transferAlerts: source.transferAlerts,
    securityAlerts: source.securityAlerts,
    marketingEmails: source.marketingEmails,
  }
  return payload
}

// -- Display preferences ----------------------------------------------------
const currencyOptions = computed(() =>
  currencies.map((code) => ({
    value: code,
    label:
      code === 'TND' ? 'TND — Tunisian dinar' : code === 'EUR' ? 'EUR — Euro' : 'USD — US dollar',
  })),
)

const NUMBER_FORMAT_OPTIONS = [
  { value: 'dot', label: '1,234.500 — dot as decimal separator' },
  { value: 'comma', label: '1.234,500 — comma as decimal separator' },
]

const previewAmount = computed(() => formatDisplayAmount(12_450.75, displayCurrency.value))
const previewTime = computed(() => formatDisplayDateTime(new Date().toISOString()))

const currencyPreference = computed({
  get: () => displayCurrency.value,
  set: (value: string) => {
    displayCurrency.value = currencies.includes(value as (typeof currencies)[number])
      ? (value as (typeof currencies)[number])
      : 'TND'
  },
})

const numberFormatPreference = computed({
  get: () => numberFormat.value,
  set: (value: string) => {
    numberFormat.value = value === 'comma' ? 'comma' : 'dot'
  },
})

const timeFormatPreference = computed({
  get: () => timeFormat.value,
  set: (value: string) => {
    timeFormat.value = value === '12h' ? '12h' : '24h'
  },
})

function hydrateProfile(): void {
  firstName.value = authStore.user?.firstName ?? ''
  lastName.value = authStore.user?.lastName ?? ''
  phone.value = authStore.user?.phone ?? ''
  profileLoaded.value = true
}

function validateProfile(): boolean {
  firstNameError.value = firstName.value.trim().length === 0 ? 'Enter your first name.' : null
  lastNameError.value = lastName.value.trim().length === 0 ? 'Enter your last name.' : null
  phoneError.value = isValidPhone(phone.value.trim())
    ? null
    : 'Enter a valid phone number, for example +216 55 214 780.'
  return !firstNameError.value && !lastNameError.value && !phoneError.value
}

async function onSaveProfile(): Promise<void> {
  profileError.value = null
  if (savingProfile.value || !validateProfile()) return
  savingProfile.value = true
  try {
    const updated = await userApi.updateProfile({
      firstName: firstName.value.trim(),
      lastName: lastName.value.trim(),
      phone: phone.value.trim(),
    })
    authStore.updateLocalUser(updated)
    hydrateProfile()
    toast.success('Profile updated', 'Your details were saved to your Finova profile.')
  } catch (cause) {
    if (cause instanceof AppError) {
      firstNameError.value = cause.fieldError('firstName') ?? firstNameError.value
      lastNameError.value = cause.fieldError('lastName') ?? lastNameError.value
      phoneError.value = cause.fieldError('phone') ?? phoneError.value
      profileError.value = cause.fieldError('firstName') || cause.fieldError('lastName') || cause.fieldError('phone')
        ? null
        : cause.message
    } else {
      profileError.value = 'We could not save your profile. Please try again.'
    }
  } finally {
    savingProfile.value = false
  }
}

async function loadSecurity(): Promise<void> {
  securityLoading.value = true
  securityError.value = null
  try {
    security.value = await userApi.securityStatus()
  } catch (cause) {
    security.value = null
    securityError.value = cause instanceof Error ? cause.message : 'We could not load your security status.'
  } finally {
    securityLoading.value = false
  }
}

async function loadPreferences(): Promise<void> {
  preferencesLoading.value = true
  preferencesError.value = null
  const loaded = await notificationStore.fetchPreferences()
  if (loaded) {
    preferences.value = { ...loaded }
    savedPreferences.value = { ...loaded }
  } else {
    savedPreferences.value = { ...preferences.value }
    preferencesError.value =
      'We could not load your notification preferences from Finova. The switches below show the defaults for this browser.'
  }
  preferencesLoading.value = false
}

async function onSavePreferences(): Promise<void> {
  if (savingPreferences.value || !preferencesDirty.value) return
  savingPreferences.value = true
  try {
    const saved = await notificationStore.updatePreferences(preferencePayload(preferences.value))
    savedPreferences.value = { ...saved }
    preferences.value = { ...saved }
    toast.success('Notification preferences saved', 'Finova will use these settings from now on.')
  } catch (cause) {
    toast.fromError(cause, 'We could not save your notification preferences')
  } finally {
    savingPreferences.value = false
  }
}

function onResetPreferences(): void {
  preferences.value = { ...savedPreferences.value }
}

watch(activeSection, (section) => {
  if (section === 'security' && security.value === null && !securityLoading.value) void loadSecurity()
})

onMounted(async () => {
  hydrateProfile()
  await Promise.all([loadSecurity(), loadPreferences()])
})
</script>

<template>
  <div class="space-y-5 pb-4">
    <PageHeader
      eyebrow="Account"
      title="Settings"
      description="Your profile, your security, what Finova notifies you about, and how figures are displayed."
    />

    <div class="grid gap-5 lg:grid-cols-[15rem_minmax(0,1fr)] lg:items-start">
      <div class="min-w-0 lg:sticky lg:top-20">
        <TabList
          :tabs="SECTIONS"
          :model-value="activeSection"
          :ariaLabel="'Settings sections'"
          layout="vertical"
          @update:model-value="onSectionChange"
        />
      </div>

      <div class="min-w-0">
        <!-- Profile -->
        <section
          v-show="activeSection === 'profile'"
          class="fin-card p-4 sm:p-5"
          role="tabpanel"
          :id="tabPanelId('Settings sections', 'profile')"
          aria-label="Profile settings"
        >
          <h2 class="flex items-center gap-2 text-headline text-ink">
            <User :size="17" class="text-primary" aria-hidden="true" />
            Profile
          </h2>
          <p class="mt-1 text-[0.875rem] leading-relaxed text-ink-muted">
            These details appear on your statements and are shared with Finova support when you ask for help.
          </p>

          <div v-if="profileError" class="mt-3 rounded-md bg-danger-light px-3 py-2.5 text-[0.875rem] text-danger-dark" role="alert">
            {{ profileError }}
          </div>

          <form class="mt-4 space-y-4" novalidate @submit.prevent="onSaveProfile">
            <div class="grid gap-4 sm:grid-cols-2">
              <BaseInput
                v-model="firstName"
                label="First name"
                autocomplete="given-name"
                required
                :error="firstNameError ?? undefined"
              />
              <BaseInput
                v-model="lastName"
                label="Last name"
                autocomplete="family-name"
                required
                :error="lastNameError ?? undefined"
              />
            </div>

            <BaseInput
              v-model="phone"
              label="Phone"
              type="tel"
              inputmode="tel"
              autocomplete="tel"
              required
              :error="phoneError ?? undefined"
              hint="Used for security alerts by SMS."
            />

            <BaseInput
              :model-value="email"
              label="Email"
              type="email"
              readonly
              disabled
              hint="Email changes need verification, so contact Finova support to move your account to a new address."
            />

            <dl class="divide-y divide-border border-t border-border">
              <DetailRow label="Customer since" :value="memberSince" />
              <DetailRow label="Role" :value="authStore.isAdmin ? 'Finova administrator' : 'Customer'" />
            </dl>

            <div class="flex justify-end border-t border-border pt-4">
              <BaseButton type="submit" :loading="savingProfile" :disabled="savingProfile">
                <template #icon>
                  <Save :size="15" aria-hidden="true" />
                </template>
                Save profile
              </BaseButton>
            </div>
          </form>
        </section>

        <!-- Security -->
        <section
          v-show="activeSection === 'security'"
          class="space-y-5"
          role="tabpanel"
          :id="tabPanelId('Settings sections', 'security')"
          aria-label="Security settings"
        >
          <div class="fin-card p-4 sm:p-5">
            <h2 class="flex items-center gap-2 text-headline text-ink">
              <ShieldCheck :size="17" class="text-primary" aria-hidden="true" />
              Security
            </h2>

            <div v-if="securityLoading" class="mt-3" aria-busy="true">
              <Skeleton variant="block" :rows="2" />
              <span class="sr-only">Loading your security status</span>
            </div>

            <ErrorState
              v-else-if="securityError"
              class="mt-3"
              compact
              title="We could not load your security status"
              :description="securityError"
              retry-label="Try again"
              @retry="loadSecurity"
            />

            <ul v-else-if="security" class="mt-2 divide-y divide-border">
              <StatusCheckRow
                label="Password protected"
                value="In place"
                state="ok"
                :description="security.lastPasswordChange
                  ? `Last changed on ${formatDateTime(security.lastPasswordChange)}.`
                  : 'Finova does not report when your password was last changed.'"
              />
              <StatusCheckRow
                label="Two-factor authentication"
                value="Not enabled"
                state="attention"
                description="Not available in this release — Finova reports it as disabled for your account."
              />
              <StatusCheckRow
                label="Recent activity monitored"
                value="Monitored"
                state="ok"
                :description="`${security.recentLogins.length} recent sign-in attempt${security.recentLogins.length === 1 ? '' : 's'} on record.`"
              />
            </ul>

            <p class="mt-3 text-[0.875rem] leading-relaxed text-ink-muted">
              Changing your password, reviewing recent sign-ins and the support contact details all live in the
              security centre.
            </p>
            <div class="mt-3">
              <BaseButton variant="secondary" size="sm" @click="router.push({ name: 'security' })">
                Open the security centre
              </BaseButton>
            </div>
          </div>
        </section>

        <!-- Notifications -->
        <section
          v-show="activeSection === 'notifications'"
          class="fin-card p-4 sm:p-5"
          role="tabpanel"
          :id="tabPanelId('Settings sections', 'notifications')"
          aria-label="Notification settings"
        >
          <h2 class="flex items-center gap-2 text-headline text-ink">
            <Bell :size="17" class="text-primary" aria-hidden="true" />
            Notifications
          </h2>
          <p class="mt-1 text-[0.875rem] leading-relaxed text-ink-muted">
            These switches are stored on your Finova account and apply to every device you sign in on.
          </p>

          <div v-if="preferencesLoading" class="mt-4" aria-busy="true">
            <Skeleton variant="block" :rows="4" />
            <span class="sr-only">Loading your notification preferences</span>
          </div>

          <ErrorState
            v-else-if="preferencesError"
            class="mt-4"
            compact
            title="We could not load your preferences"
            :description="preferencesError"
            retry-label="Try again"
            @retry="loadPreferences"
          />

          <div v-else class="mt-2 divide-y divide-border">
            <BaseToggle
              v-for="row in PREFERENCE_ROWS"
              :key="row.key"
              :model-value="preferences[row.key]"
              :label="row.label"
              :description="row.description"
              @update:model-value="preferences[row.key] = $event"
            />
          </div>

          <div class="flex flex-wrap items-center justify-end gap-2 border-t border-border pt-4">
            <BaseButton variant="ghost" :disabled="!preferencesDirty || savingPreferences" @click="onResetPreferences">
              Discard changes
            </BaseButton>
            <BaseButton
              :loading="savingPreferences"
              :disabled="!preferencesDirty || savingPreferences"
              @click="onSavePreferences"
            >
              <template #icon>
                <Save :size="15" aria-hidden="true" />
              </template>
              Save preferences
            </BaseButton>
          </div>
        </section>

        <!-- Preferences -->
        <section
          v-show="activeSection === 'preferences'"
          class="fin-card p-4 sm:p-5"
          role="tabpanel"
          :id="tabPanelId('Settings sections', 'preferences')"
          aria-label="Display preferences"
        >
          <h2 class="flex items-center gap-2 text-headline text-ink">
            <MonitorSmartphone :size="17" class="text-primary" aria-hidden="true" />
            Preferences
          </h2>

          <div class="mt-4 grid gap-4 sm:grid-cols-2">
            <BaseSelect
              v-model="currencyPreference"
              label="Display currency"
              :options="currencyOptions"
              hint="Used for summaries and previews that are not tied to one account."
            />

            <div>
              <p class="fin-label">Time format</p>
              <div
                class="flex rounded-md border border-border bg-surface p-1"
                role="radiogroup"
                aria-label="Time format"
              >
                <button
                  v-for="option in [
                    { value: '24h', label: '24-hour (14:32)' },
                    { value: '12h', label: '12-hour (2:32 PM)' },
                  ]"
                  :key="option.value"
                  type="button"
                  role="radio"
                  :aria-checked="timeFormatPreference === option.value"
                  class="flex-1 rounded px-3 py-2 text-[0.875rem] font-semibold transition-colors"
                  :class="
                    timeFormatPreference === option.value
                      ? 'bg-primary text-white'
                      : 'text-ink-muted hover:bg-surface-sunken hover:text-ink'
                  "
                  @click="timeFormatPreference = option.value === '12h' ? '12h' : '24h'"
                >
                  {{ option.label }}
                </button>
              </div>
              <p class="fin-hint">Applies to the times shown in your transaction details and security activity.</p>
            </div>
          </div>

          <div class="mt-4">
            <BaseSelect
              v-model="numberFormatPreference"
              label="Number format"
              :options="NUMBER_FORMAT_OPTIONS"
              hint="How amounts and thousands separators are written in this browser."
            />
          </div>

          <div class="mt-5 rounded-md border border-border bg-surface-sunken p-4">
            <p class="text-label text-ink">Preview</p>
            <dl class="mt-2 divide-y divide-border">
              <DetailRow label="Amount" :value="previewAmount" mono />
              <DetailRow label="Date and time" :value="previewTime" mono />
            </dl>
          </div>

          <p class="mt-4 flex items-start gap-2 rounded-md bg-primary-soft px-3 py-2.5 text-caption text-ink-muted">
            <Info :size="14" class="mt-0.5 shrink-0 text-primary" aria-hidden="true" />
            <span>
              These display preferences are stored in <strong class="font-semibold text-ink">this browser only</strong>
              (local storage), not on your Finova account — signing in on another device resets them. Notification
              preferences above and your profile <em>are</em> saved on the server.
            </span>
          </p>
        </section>
      </div>
    </div>
  </div>
</template>
