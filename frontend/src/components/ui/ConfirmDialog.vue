<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { AlertTriangle, Loader2 } from 'lucide-vue-next'
import BaseModal from '@/components/ui/BaseModal.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    message: string
    detail?: string
    confirmLabel?: string
    cancelLabel?: string
    tone?: 'danger' | 'primary' | 'warning'
    requireTyping?: boolean
    typingPhrase?: string
    loading?: boolean
  }>(),
  {
    detail: undefined,
    confirmLabel: 'Confirm',
    cancelLabel: 'Cancel',
    tone: 'danger',
    requireTyping: false,
    typingPhrase: '',
    loading: false,
  },
)

const emit = defineEmits<{ confirm: []; cancel: [] }>()

const typed = ref('')
const canConfirm = computed(
  () => !props.requireTyping || typed.value.trim().toUpperCase() === props.typingPhrase.trim().toUpperCase(),
)

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) typed.value = ''
  },
)

const toneClasses = computed(() => {
  if (props.tone === 'danger') return 'bg-danger-light text-danger'
  if (props.tone === 'warning') return 'bg-warning-light text-warning-dark'
  return 'bg-primary-soft text-primary'
})
</script>

<template>
  <BaseModal :open="open" :title="title" size="sm" :dismissable="!loading" @close="emit('cancel')">
    <div class="flex gap-4">
      <div
        class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full"
        :class="toneClasses"
        aria-hidden="true"
      >
        <AlertTriangle :size="20" />
      </div>

      <div class="min-w-0 flex-1">
        <p class="text-[0.9375rem] leading-relaxed text-ink">{{ message }}</p>
        <p v-if="detail" class="mt-2 text-[0.875rem] leading-relaxed text-ink-muted">{{ detail }}</p>

        <div v-if="requireTyping" class="mt-4">
          <label :for="`confirm-typing-${title.replace(/\s/g, '-')}`" class="fin-label">
            Type <span class="font-mono font-semibold text-ink">{{ typingPhrase }}</span> to confirm
          </label>
          <input
            :id="`confirm-typing-${title.replace(/\s/g, '-')}`"
            v-model="typed"
            type="text"
            class="fin-input"
            autocomplete="off"
            spellcheck="false"
            data-autofocus
          />
        </div>
      </div>
    </div>

    <template #footer>
      <BaseButton variant="secondary" :disabled="loading" @click="emit('cancel')">
        {{ cancelLabel }}
      </BaseButton>
      <BaseButton
        :variant="tone === 'warning' ? 'primary' : 'danger'"
        :loading="loading"
        :disabled="!canConfirm"
        data-autofocus
        @click="emit('confirm')"
      >
        <template v-if="loading" #icon>
          <Loader2 :size="15" class="animate-spin" aria-hidden="true" />
        </template>
        {{ confirmLabel }}
      </BaseButton>
    </template>
  </BaseModal>
</template>