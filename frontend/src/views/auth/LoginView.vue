<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ChevronDown, Eye, EyeOff, Info, TriangleAlert } from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { AppError } from '@/utils/errors'
import { isValidEmail } from '@/utils/format'

interface DemoAccount {
  role: string
  email: string
  detail: string
}

const DEMO_PASSWORD = 'Finova#2026'

const demoAccounts: DemoAccount[] = [
  {
    role: 'Customer',
    email: 'takwa@finova.dev',
    detail: 'Balances, transfers and transaction history.',
  },
  {
    role: 'Administrator',
    email: 'admin@finova.dev',
    detail: 'Fraud review, user management and the audit log.',
  },
]

const auth = useAuthStore()
const toast = useToastStore()
const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const formError = ref('')
const resetNoticeVisible = ref(false)
const fieldErrors = ref({ email: '', password: '' })

watch(email, () => {
  fieldErrors.value.email = ''
  formError.value = ''
})

watch(password, () => {
  fieldErrors.value.password = ''
  formError.value = ''
})

function validate(): boolean {
  const emailValue = email.value.trim()
  const next = {
    email: emailValue
      ? isValidEmail(emailValue)
        ? ''
        : 'Enter a valid email address, for example name@finova.dev.'
      : 'Enter the email address linked to your Finova account.',
    password: password.value ? '' : 'Enter your password.',
  }
  fieldErrors.value = next
  return !next.email && !next.password
}

async function submit(): Promise<void> {
  if (submitting.value || !validate()) return
  submitting.value = true
  try {
    await auth.login({ email: email.value.trim(), password: password.value })
    const redirect = route.query.redirect
    const target = typeof redirect === 'string' && redirect.length > 0 ? redirect : { name: 'dashboard' }
    await router.push(target)
  } catch (cause) {
    formError.value =
      cause instanceof AppError
        ? cause.message
        : 'We could not sign you in just now. Please try again.'
  } finally {
    submitting.value = false
  }
}

function useDemoCredentials(demoEmail: string): void {
  email.value = demoEmail
  password.value = DEMO_PASSWORD
  toast.info('Credentials filled in', 'Select “Sign In” to open the demo.')
}
</script>

<template>
  <div>
    <div class="mb-7">
      <h1 class="text-title text-ink">Welcome back</h1>
      <p class="mt-1.5 text-[0.9375rem] text-ink-muted">Sign in to your Finova account.</p>
    </div>

    <form class="space-y-4" novalidate @submit.prevent="submit">
      <BaseInput
        v-model="email"
        label="Email"
        type="email"
        inputmode="email"
        autocomplete="username"
        placeholder="name@finova.dev"
        required
        :error="fieldErrors.email"
      />

      <BaseInput
        v-model="password"
        label="Password"
        :type="showPassword ? 'text' : 'password'"
        autocomplete="current-password"
        placeholder="Your password"
        required
        :error="fieldErrors.password"
      >
        <template #suffix>
          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded text-ink-subtle transition-colors hover:text-ink fin-focus-ring"
            :aria-label="showPassword ? 'Hide password' : 'Show password'"
            :aria-pressed="showPassword"
            @click="showPassword = !showPassword"
          >
            <EyeOff v-if="showPassword" :size="17" aria-hidden="true" />
            <Eye v-else :size="17" aria-hidden="true" />
          </button>
        </template>
      </BaseInput>

      <div
        v-if="formError"
        role="alert"
        class="flex items-start gap-2.5 rounded-md border border-danger/30 bg-danger-light px-3.5 py-3"
      >
        <TriangleAlert :size="17" class="mt-0.5 shrink-0 text-danger" aria-hidden="true" />
        <p class="text-[0.875rem] font-medium leading-relaxed text-danger-dark">{{ formError }}</p>
      </div>

      <BaseButton type="submit" block :loading="submitting" :disabled="submitting">Sign In</BaseButton>

      <div class="flex justify-end">
        <button
          type="button"
          class="fin-link text-[0.875rem]"
          :aria-expanded="resetNoticeVisible"
          aria-controls="reset-notice"
          @click="resetNoticeVisible = true"
        >
          Forgot password?
        </button>
      </div>

      <div
        v-if="resetNoticeVisible"
        id="reset-notice"
        role="status"
        class="flex items-start gap-2.5 rounded-md border border-primary/20 bg-primary-soft px-3.5 py-3"
      >
        <Info :size="16" class="mt-0.5 shrink-0 text-primary" aria-hidden="true" />
        <p class="text-[0.8125rem] leading-relaxed text-ink-muted">
          Self-service password reset is not available in this demo. Finova support can restore your
          access — write to support@finova.dev with your registered email address and we will guide
          you through the next steps.
        </p>
      </div>
    </form>

    <details class="group mt-6 rounded-md border border-border bg-surface-sunken">
      <summary
        class="flex cursor-pointer list-none items-center justify-between gap-2 px-3.5 py-2.5 text-[0.8125rem] font-semibold text-ink-muted [&::-webkit-details-marker]:hidden"
      >
        <span>Demo credentials</span>
        <ChevronDown
          :size="15"
          class="shrink-0 transition-transform group-open:rotate-180"
          aria-hidden="true"
        />
      </summary>

      <div class="border-t border-border px-3.5 py-3">
        <ul class="space-y-2.5">
          <li
            v-for="account in demoAccounts"
            :key="account.email"
            class="flex items-center justify-between gap-3"
          >
            <div class="min-w-0">
              <p class="text-[0.8125rem] font-semibold text-ink">{{ account.role }}</p>
              <p class="font-mono text-caption text-ink-muted">{{ account.email }}</p>
              <p class="text-caption text-ink-subtle">{{ account.detail }}</p>
            </div>
            <BaseButton size="sm" variant="secondary" @click="useDemoCredentials(account.email)">
              Use
            </BaseButton>
          </li>
        </ul>
        <p class="mt-3 border-t border-border pt-2.5 text-caption text-ink-subtle">
          Password for both accounts: <span class="font-mono text-ink-muted">{{ DEMO_PASSWORD }}</span>
        </p>
      </div>
    </details>

    <p class="mt-6 text-center text-[0.875rem] text-ink-muted">
      Don't have an account?
      <RouterLink :to="{ name: 'register' }" class="fin-link">Create account</RouterLink>
    </p>
  </div>
</template>