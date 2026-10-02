# FINOVA Frontend — View Contract

Read this completely before writing a `.vue` file. The design system, data layer and
UI component library are **already written, typecheck clean, and are yours to use**.
Do not modify them.

## 1. Golden path — 12 rules

1. **Never invent a UI primitive.** Use the library in `src/components/ui/`. If you truly
   need something new, add it once in `src/components/ui/` and use it everywhere.
2. **Never duplicate.** If two views need the same thing (a money cell, a status pill,
   a filter row), it belongs in `src/components/` — not copy-pasted markup.
3. **Tailwind classes only.** No inline `style="..."` for layout or colour (a `style` for
   a chart or a one-off transform is fine). No new CSS files. No CSS-in-JS.
4. **Only the Finova palette.** `primary`, `primary-dark`, `primary-light`, `primary-soft`,
   `accent`, `success`, `warning`, `danger`, `surface`, `background`, `ink`, `ink-muted`,
   `ink-subtle`, `border`, `border-strong` — plus `success-light`, `warning-light`,
   `danger-light`, `accent-light`. **Never** a raw hex, never Tailwind's `blue-500` /
   `gray-100` / `red-600` etc. If you need a new tone, add it to `tailwind.config.js` first.
5. **Never `style`-attribute the money.** Always `formatAmount` / `formatMoney` /
   `formatSignedMoney` from `@/utils/format`. Never `toFixed()` in a template.
6. **Status is never colour-only.** Always `<StatusBadge>` or `<RiskBadge>` (they ship a
   dot/icon **and** a text label). Never a bare coloured span.
7. **Every async block needs all four states**: loading (skeleton), empty, error (with a
   working retry), success. Use `Skeleton`, `EmptyState`, `ErrorState`.
8. **Accessibility is not optional.** `<label for>` on every input, `aria-label` on
   icon-only buttons, `role="alert"` on errors, `aria-live="polite"` on count changes,
   visible `:focus-visible` (the design system handles it), logical heading order, and a
   keyboard path through every interaction.
9. **Responsive is a redesign, not a shrink.** Plan the mobile layout explicitly. Tables
   become cards (see `TransactionTable`, which already does this). Test at 375px.
10. **`<script setup lang="ts">`, Composition API, `ref`/`computed`/`watch`.** No Options API,
    no `any`, no `@ts-ignore`, no `defineProps` without types. Strict TS is on.
11. **Data flows through the Pinia stores**, never a direct `api` import in a view. Stores
    own `loading`/`error`; views read them.
12. **Real content only.** No lorem ipsum, no "Item 1", no "Test", no fake company names.
    Real Tunisian/Maghreb names, real merchants, plausible amounts.

## 2. Stack

Vue 3.5 `<script setup>` · TypeScript (strict) · Vite 5 · Vue Router 4 · Pinia 2 ·
Tailwind 3 · lucide-vue-next · Chart.js 4.

Aliases: `@/` → `src/`.

```bash
cd C:\Users\takwa\Desktop\Finova\frontend
npm.cmd run typecheck     # vue-tsc --noEmit  — MUST PASS
npm.cmd run dev           # dev server on :5173, proxies /api to the gateway
```

`node_modules` is already installed. Do **not** run `npm install` and do not add dependencies.

## 3. Data layer — already written, use it

### Stores (`@/stores/*`)
`authStore` — `user, isAuthenticated, isAdmin, fullName, initials, login, register,
logout, refreshSession, fetchMe, updateLocalUser`. Also `loading`.
`accountStore` — `accounts, activeAccounts, totalBalance, totalsByCurrency,
primaryCurrency, defaultSenderAccount, byId, loading, error, fetchAll, fetchOne,
refreshBalance, create, setStatus, applySettledBalance, reset`.
`transactionStore` — `transactions, page, summary, loading, error, filters, submitting,
totalElements, totalPages, fetchList, fetchSummary, fetchOne, createTransfer, reset`.
`notificationStore` — `notifications, unread, unreadCount, byCategory, loading,
fetchUnreadCount, fetchUnread, fetchList, markRead, markAllRead, fetchPreferences,
updatePreferences`.
`fraudStore` — `alerts, page, stats, selected, loading, acting, totalElements, fetchAlerts,
fetchStats, fetchOne, selectAlert, runAction('review'|'safe'|'confirm'|'block', note)`.
`adminStore` — `userStats, accountStats, transactionStats, fraudAlerts, recentAudit,
recentActivity, users, accounts, transactions, loading, acting, error, settings, isReady,
fetchOverview, fetchUsers, fetchAccounts, fetchTransactions, setUserStatus,
setAccountStatus`.
`toastStore` — `success, info, warning, error, fromError(cause, fallbackTitle), dismiss`.

### API modules (`@/api`) — types in `@/types`
`authApi, accountApi, userApi, transactionApi, fraudApi, notificationApi, adminApi, newIdempotencyKey`.

### Errors
`import { AppError, humanMessageFor } from '@/utils/errors'`. Every rejected request is an
`AppError` with `.message` (already human-readable), `.code`, `.status`, `.correlationId`,
`.isNetworkError`, and `.fieldError('fieldName')` for per-field validation errors from the
backend. In views: `toast.fromError(error, 'Unable to …')` or
`catch (e) { if (e instanceof AppError) formError.value = e.fieldError('email') ?? e.message }`.

### Formatting (`@/utils/format`) — use these, never roll your own
`formatAmount, formatMoney, formatSignedMoney, formatCompact, formatPercent, formatDate,
formatDateTime, formatTime, formatRelative, maskAccountNumber, initials, fullName,
greetingFor, STATUS_LABELS, ACCOUNT_STATUS_LABELS, RISK_LABELS, toIsoDate, startOfDayIso,
endOfDayIso, isValidEmail, isValidPhone, evaluatePassword, CURRENCIES`.

`evaluatePassword(password)` → `{ score: 0|1|2|3, label: 'Weak'|'Medium'|'Strong', requirements: [{label, met}] }`.

### Mock mode
`VITE_USE_MOCKS=true` (set in `.env`) routes every API module through `src/mock/*` with
realistic fictional data and realistic latency, so every skeleton/empty state is genuinely
exercised. **Do not import from `@/mock` anywhere.** Switching to the real backend is only an
env change. Demo credentials for mock mode: `takwa@finova.dev` / `Finova#2026` (customer) and
`admin@finova.dev` / `Finova#2026` (administrator).

## 4. UI library — already written, use it

`src/components/ui/`
| Component | Props | Notes |
|---|---|---|
| `BaseButton` | `variant: primary\|accent\|secondary\|ghost\|danger`, `size: sm\|md\|lg`, `loading`, `disabled`, `block`, `to`, `href`, `ariaLabel` | slots: default, `#icon`, `#trailing` |
| `BaseInput` | `modelValue`, `label`, `type`, `placeholder`, `hint`, `error`, `required`, `autocomplete`, `inputmode`, `maxlength` | slots: `#prefix`, `#prefixInner`, `#suffix` (for a show/hide eye) |
| `BaseSelect` | `modelValue`, `label`, `options: {value,label}[]`, `placeholder`, `hint`, `error`, `clearable` | |
| `BaseToggle` | `modelValue`, `label`, `description` | switch, `role="switch"` |
| `BaseModal` | `open`, `title`, `description`, `size: sm\|md\|lg\|xl`, `dismissable` | slot + `#footer`; focus-trapped, Esc closes |
| `ConfirmDialog` | `open`, `title`, `message`, `detail`, `confirmLabel`, `cancelLabel`, `tone: danger\|primary\|warning`, `requireTyping`, `typingPhrase`, `loading` | emits `confirm`/`cancel` |
| `StatusBadge` | `status` (TransactionStatus \| AccountStatus \| FraudStatus \| string), `size` | |
| `RiskBadge` | `level`, `score`, `size` | |
| `Skeleton` | `variant: text\|block`, `rows`, `width` | |
| `EmptyState` | `title`, `description`, `icon`, `actionLabel`, `compact` | emits `action` |
| `ErrorState` | `title`, `description`, `retryLabel`, `offline`, `compact`, `correlationId` | emits `retry` |
| `ToastViewport` | — | already mounted in `App.vue`; just call the store |
| `SearchInput` | `modelValue`, `placeholder`, `label`, `debounceMs`, `clearable` | emits `update:modelValue`, `search` |
| `DateRangePicker` | `from`, `to`, `label`, `presets` | `v-model:from` / `v-model:to` |
| `FilterBar` | `activeCount`, `expandable`, `defaultOpen` | slots: `#search`, `#primary`, `#advanced`; emits `reset`; has a built-in Reset button |
| `Pagination` | `page`, `totalPages`, `totalElements`, `pageSize`, `label` | emits `change`, `sizeChange` |
| `PageHeader` | `eyebrow`, `title`, `description` | slot `#actions` |

`src/components/account/` — `BalanceCard` (`total, currency, monthlyChangePercent,
accountCount, loading, hidden`; emits `send`, `viewAccounts`, `toggleVisibility`),
`AccountCard` (`account`; emits `view`, `transfer`).

`src/components/transaction/` — `TransactionRow` (`transaction, perspective:
'sender'|'receiver'|'neutral', showStatus, compact`; emits `click`),
`TransactionTable` (`transactions, loading, page, totalPages, totalElements, pageSize,
showRisk, emptyTitle, emptyDescription`; emits `select`, `pageChange`, `sizeChange`,
`emptyAction`; **already renders cards on mobile**).

`src/components/notification/NotificationItem` (`notification, unread, onDark`; emits
`click`, `markRead`).

`src/components/charts/ChartCard` (`type: 'line'|'bar'|'doughnut'`, `title`, `subtitle`,
`labels: string[]`, `datasets: {label, data, color?, fill?, dashed?}[]`, `height`,
`currency`, `moneyFormat`, `loading`; slot `#actions`). Registering Chart.js is handled
inside — just import and use it.

`src/components/layout/` — `Sidebar`, `Topbar`, `MobileNavigation` (all done, used by `AppShell`).

`src/layouts/` — `AppShell.vue` (sidebar + topbar + mobile nav + offline banner; provides
a centred `max-w-[86rem]` content column), `AuthLayout.vue` (split brand/form layout for
`/login` and `/register`).

## 5. Routes already defined in `src/router/index.ts`

```
/login  /register                                                   layout: auth
/dashboard  /accounts  /accounts/:id  /transfer  /transfer/review
/transactions  /transactions/:id  /transfer/success/:id
/notifications  /security  /settings
/admin  /admin/users  /admin/accounts  /admin/transactions
/admin/fraud  /admin/fraud/:id  /admin/audit  /admin/settings
/forbidden  /:pathMatch(.*)*   (404)
```
Route names: `login, register, dashboard, accounts, account-detail, transfer,
transfer-review, transfer-success, transactions, transaction-detail, notifications,
security, settings, admin-dashboard, admin-users, admin-accounts, admin-transactions,
admin-fraud, admin-fraud-detail, admin-audit, admin-settings, forbidden, not-found`.
Guards already handle auth + ADMIN. Navigate with `router.push({ name: '…', params: {...} })`.
Route `meta.title` drives the topbar heading and the document title.

## 6. Data loading pattern — use `onMounted` + `watch`

```ts
const accountStore = useAccountStore()
const loading = ref(true)
const error = ref<string | null>(null)

async function load(force = false): Promise<void> {
  loading.value = true
  error.value = null
  try {
    await accountStore.fetchAll(force)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'We could not load your accounts.'
  } finally {
    loading.value = false
  }
}

onMounted(() => void load())
```
Re-run `load(true)` from the `ErrorState` retry button.

## 7. Money and typography rules

- Total balance / any headline money figure: `text-money` or larger, `fin-amount`
  (already tabular). Example: `12,450.750 TND`.
- Split the amount and the currency code visually (smaller, muted currency) where it reads
  better, but the number itself must stay at full tabular precision (3 decimals).
- Page titles: `PageHeader` with `eyebrow` (small uppercase, `text-primary`).
- Body copy: `text-[0.9375rem] text-ink-muted`. Metadata: `text-caption text-ink-subtle`.
- Section headings: `text-headline`. Card numbers: `text-money`/`text-[1.75rem]`.

## 8. Definition of done for every view

- [ ] Loading skeleton, empty state, error state with working retry, success state
- [ ] Responsive from 375px to 1920px with a deliberate mobile layout
- [ ] Keyboard reachable; visible focus; labelled controls; `role`/`aria-*` where needed
- [ ] No `any`, no `@ts-ignore`, no console noise; `npm.cmd run typecheck` passes
- [ ] Only Finova palette colours, only the shared components, real copy
- [ ] No horizontal overflow at any width; no dead buttons