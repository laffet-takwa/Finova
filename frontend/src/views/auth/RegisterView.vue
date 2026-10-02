<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { Eye, EyeOff, TriangleAlert } from 'lucide-vue-next'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import PasswordStrengthMeter from '@/components/ui/PasswordStrengthMeter.vue'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { AppError } from '@/utils/errors'
import { isValidEmail, isValidPhone } from '@/utils/format'

interface FieldErrors {
  firstName: string
  lastName: string
  email: string
  phone: string
  password: string
  confirmPassword: string
}

const MAX_PASSWORD_LENGTH = 72

const auth = useAuthStore()
const toast = useToastStore()
const router = useRouter()

const firstName = ref('')
const lastName = ref('')
const email = ref('')
const phone = ref('')
const password = ref('')
const confirmPassword = ref('')
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const submitting = ref(false)
const formError = ref('')
const fieldErrors = ref<FieldErrors>({
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
})

function clearFieldError(field: keyof FieldErrors): void {
  if (!fieldErrors.value[field]) return
  fieldErrors.value = { ...fieldErrors.value, [field]: '' }
}

watch(firstName, () => clearFieldError('firstName'))
watch(lastName, () => clearFieldError('lastName'))
watch(email, () => clearFieldError('email'))
watch(phone, () => clearFieldError('phone'))
watch(password, () => {
  clearFieldError('password')
  if (confirmPassword.value) clearFieldError('confirmPassword')
})
watch(confirmPassword, () => clearFieldError('confirmPassword'))

function validatePassword(): string {
  const value = password.value
  if (!value) return 'Choose a password for your account.'
  if (value.length < 10) return 'Use at least 10 characters.'
  if (value.length > MAX_PASSWORD_LENGTH) return `Use at most ${MAX_PASSWORD_LENGTH} characters.`
  if (!/[A-Z]/.test(value)) return 'Add at least one uppercase letter.'
  if (!/[a-z]/.test(value)) return 'Add at least one lowercase letter.'
  if (!/\d/.test(value)) return 'Add at least one number.'
  if (!/[^A-Za-z0-9]/.test(value)) return 'Add at least one special character.'
  return ''
}

function validate(): boolean {
  const emailValue = email.value.trim()
  const phoneValue = phone.value.trim()
  const next: FieldErrors = {
    firstName: firstName.value.trim() ? '' : 'Enter your first name.',
    lastName: lastName.value.trim() ? '' : 'Enter your last name.',
    email: emailValue
      ? isValidEmail(emailValue)
        ? ''
        : 'Enter a valid email address, for example name@finova.dev.'
      : 'Enter your email address.',
    phone: phoneValue
      ? isValidPhone(phoneValue)
        ? ''
        : 'Enter a valid phone number, for example +216 20 123 456.'
      : 'Enter your phone number.',
    password: validatePassword(),
    confirmPassword: !confirmPassword.value
      ? 'Repeat your password.'
      : confirmPassword.value === password.value
        ? ''
        : 'The two passwords do not match.',
  }
  fieldErrors.value = next
  return Object.values(next).every((message) => !message)
}

async function submit(): Promise<void> {
  if (submitting.value || !validate()) return
  submitting.value = true
  formError.value = ''
  try {
    await auth.register({
      firstName: firstName.value.trim(),
      lastName: lastName.value.trim(),
      email: email.value.trim(),
      phone: phone.value.trim(),
      password: password.value,
      confirmPassword: confirmPassword.value,
    })
    toast.success('Welcome to Finova', 'Your account is ready — your dashboard is loading.')
    await router.push({ name: 'dashboard' })
  } catch (cause) {
    if (cause instanceof AppError) {
      formError.value = cause.message
      const details: Partial<FieldErrors> = {
        email: cause.fieldError('email'),
        phone: cause.fieldError('phone'),
        password: cause.fieldError('password'),
        confirmPassword: cause.fieldError('confirmPassword'),
      }
      const resolved = Object.fromEntries(
        Object.entries(details).filter(([, message]) => Boolean(message)),
      ) as Partial<FieldErrors>
      if (Object.keys(resolved).length > 0) {
        fieldErrors.value = { ...fieldErrors.value, ...resolved }
      }
    } else {
      formError.value = 'We could not create your account just now. Please try again.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div>
    <div class="mb-6">
      <h1 class="text-title text-ink">Create your Finova account</h1>
      <p class="mt-1.5 text-[0.9375rem] text-ink-muted">
        Open an account in minutes and start sending money straight away.
      </p>
    </div>

    <form class="space-y-4" novalidate @submit.prevent="submit">
      <div class="grid gap-4 sm:grid-cols-2">
        <BaseInput
          v-model="firstName"
          label="First name"
          autocomplete="given-name"
          placeholder="Takwa"
          required
          :error="fieldErrors.firstName"
        />
        <BaseInput
          v-model="lastName"
          label="Last name"
          autocomplete="family-name"
          placeholder="Ben Salah"
          required
          :error="fieldErrors.lastName"
        />
      </div>

      <BaseInput
        v-model="email"
        label="Email"
        type="email"
        inputmode="email"
        autocomplete="email"
        placeholder="name@finova.dev"
        required
        :error="fieldErrors.email"
      />

      <BaseInput
        v-model="phone"
        label="Phone"
        type="tel"
        inputmode="tel"
        autocomplete="tel"
        placeholder="+216 20 123 456"
        required
        :error="fieldErrors.phone"
      />

      <BaseInput
        v-model="password"
        label="Password"
        :type="showPassword ? 'text' : 'password'"
        autocomplete="new-password"
        placeholder="At least 10 characters"
        required
        :maxlength="MAX_PASSWORD_LENGTH"
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

      <PasswordStrengthMeter v-if="password" :password="password" label="Password strength" />

      <BaseInput
        v-model="confirmPassword"
        label="Confirm password"
        :type="showConfirmPassword ? 'text' : 'password'"
        autocomplete="new-password"
        placeholder="Repeat your password"
        required
        :error="fieldErrors.confirmPassword"
      >
        <template #suffix>
          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded text-ink-subtle transition-colors hover:text-ink fin-focus-ring"
            :aria-label="showConfirmPassword ? 'Hide password' : 'Show password'"
            :aria-pressed="showConfirmPassword"
            @click="showConfirmPassword = !showConfirmPassword"
          >
            <EyeOff v-if="showConfirmPassword" :size="17" aria-hidden="true" />
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

      <BaseButton type="submit" block :loading="submitting" :disabled="submitting">
        Create your Finova account
      </BaseButton>
    </form>

    <p class="mt-4 text-center text-caption leading-relaxed text-ink-subtle">
      By creating an account you agree to the Finova terms of use and privacy policy.
    </p>

    <p class="mt-5 text-center text-[0.875rem] text-ink-muted">
      Already have an account?
      <RouterLink :to="{ name: 'login' }" class="fin-link">Sign in</RouterLink>
    </p>
  </div>
</template>