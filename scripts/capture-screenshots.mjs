/**
 * Captures every Finova screen to docs/screenshots/.
 *
 * Runs against the Vite dev server in mock mode, so no backend is needed.
 * Customer routes are captured signed in as a customer, admin routes as an
 * administrator — the app's router guards redirect otherwise, which would
 * silently produce a screenshot of the login page.
 *
 *   node scripts/capture-screenshots.mjs
 *
 * Flags:
 *   --url=http://localhost:5173   dev server to capture from
 *   --out=docs/screenshots        output directory
 *   --only=dashboard,transactions capture a subset
 *   --mobile                      capture the mobile viewport instead of desktop
 */
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'
import puppeteer from 'puppeteer'

const args = Object.fromEntries(
  process.argv.slice(2).map((raw) => {
    const [key, value] = raw.replace(/^--/, '').split('=')
    return [key, value ?? true]
  }),
)

const BASE = args.url ?? 'http://localhost:5173'
const OUT = args.out ?? 'docs/screenshots'
const MOBILE = Boolean(args.mobile)
const ONLY = typeof args.only === 'string' ? new Set(args.only.split(',')) : null

const VIEWPORT = MOBILE
  ? { width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true }
  : { width: 1440, height: 900, deviceScaleFactor: 2 }

const SUFFIX = MOBILE ? '.mobile' : ''

const CUSTOMER = { email: 'takwa@finova.dev', password: 'Finova#2026' }
const ADMIN = { email: 'admin@finova.dev', password: 'Finova#2026' }

/**
 * Each screen names the role it needs, the route, and how to reach it.
 * `prepare` runs after sign-in so dynamic routes (an account id, a transfer
 * reference) can be discovered from the running app rather than hardcoded.
 */
const SCREENS = [
  { name: 'login', role: 'none', route: '/login' },
  { name: 'register', role: 'none', route: '/register' },

  { name: 'dashboard', role: 'customer', route: '/dashboard', settle: 2600 },
  { name: 'accounts', role: 'customer', route: '/accounts', settle: 2200 },
  {
    name: 'accounts',
    role: 'customer',
    route: '/accounts',
    settle: 1800,
    prepare: async (page) => {
      await clickByText(page, 'Open new account')
      await sleep(800)
    },
    modalSuffix: '-new-account',
  },
  {
    name: 'account-detail',
    role: 'customer',
    route: null,
    settle: 2400,
    resolve: async (page) => {
      // Account cards navigate on click, not via an anchor, so there is no
      // href to read out of the DOM.
      await page.goto(`${BASE}/accounts`, { waitUntil: 'networkidle2' })
      await sleep(1800)
      const before = page.url()
      await clickByText(page, 'View details')
      await sleep(1400)
      return page.url() === before ? '/accounts' : page.url().replace(BASE, '')
    },
  },
  { name: 'transfer', role: 'customer', route: '/transfer', settle: 1800 },
  {
    name: 'transfer-review',
    role: 'customer',
    route: null,
    settle: 2200,
    resolve: async (page) => {
      // Drive the real flow so the review screen has a genuine draft.
      await page.goto(`${BASE}/transfer`, { waitUntil: 'networkidle2' })
      await sleep(1400)
      await fillTransfer(page, 'TN58 2000 0789 1234 5678 90', '500')
      await clickByText(page, 'Continue')
      await sleep(1600)
      return page.url().replace(BASE, '')
    },
  },
  {
    name: 'transactions',
    role: 'customer',
    route: '/transactions',
    settle: 2200,
    prepare: async (page) => {
      await clickByText(page, 'Filters')
      await sleep(500)
    },
    modalSuffix: '-filters',
  },
  {
    name: 'transaction-detail',
    role: 'customer',
    route: null,
    settle: 2400,
    resolve: async (page) => {
      await page.goto(`${BASE}/transactions`, { waitUntil: 'networkidle2' })
      await sleep(2000)
      const before = page.url()
      // First table row / first card.
      await page.evaluate(() => {
        const row = document.querySelector('tbody tr') ?? document.querySelector('[class*="divide-y"] button')
        row?.click()
      })
      await sleep(1600)
      return page.url() === before ? '/transactions' : page.url().replace(BASE, '')
    },
  },
  { name: 'notifications', role: 'customer', route: '/notifications', settle: 2000 },
  { name: 'security', role: 'customer', route: '/security', settle: 1800 },
  { name: 'settings', role: 'customer', route: '/settings', settle: 1600 },
  { name: 'settings-notifications', role: 'customer', route: '/settings', settle: 1400, tab: 'Notifications' },
  { name: 'not-found', role: 'customer', route: '/this-route-does-not-exist', settle: 900 },

  { name: 'admin-dashboard', role: 'admin', route: '/admin', settle: 2800 },
  { name: 'admin-users', role: 'admin', route: '/admin/users', settle: 1800 },
  { name: 'admin-accounts', role: 'admin', route: '/admin/accounts', settle: 1800 },
  { name: 'admin-transactions', role: 'admin', route: '/admin/transactions', settle: 2200 },
  { name: 'admin-fraud', role: 'admin', route: '/admin/fraud', settle: 2400 },
  {
    name: 'admin-fraud-detail',
    role: 'admin',
    route: null,
    settle: 2200,
    resolve: async (page) => {
      await page.goto(`${BASE}/admin/fraud`, { waitUntil: 'networkidle2' })
      await sleep(2000)
      const before = page.url()
      // The "Review" action on the highest-risk alert card.
      await clickByText(page, 'Review')
      await sleep(1600)
      return page.url() === before ? '/admin/fraud' : page.url().replace(BASE, '')
    },
  },
  { name: 'admin-audit', role: 'admin', route: '/admin/audit', settle: 2000 },
  { name: 'admin-settings', role: 'admin', route: '/admin/settings', settle: 1600 },
]

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function clickByText(page, text) {
  const handle = await page.evaluateHandle((needle) => {
    const nodes = [...document.querySelectorAll('button, a, [role="tab"]')]
    return nodes.find((node) => (node.textContent ?? '').trim().toLowerCase().includes(needle.toLowerCase())) ?? null
  }, text)
  const element = handle.asElement()
  if (!element) return false
  await element.click()
  return true
}

async function openModalIfPresent(page, triggerText) {
  await clickByText(page, triggerText)
  await sleep(700)
}

async function fillTransfer(page, accountNumber, amount) {
  await page.evaluate(
    (number, value) => {
      const setNative = (input, next) => {
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
        setter.call(input, next)
        input.dispatchEvent(new Event('input', { bubbles: true }))
      }
      const byLabel = (label) =>
        [...document.querySelectorAll('label')].find((node) =>
          (node.textContent ?? '').toLowerCase().includes(label),
        )?.getAttribute('for')
      const target =
        document.getElementById(byLabel('account number')) ??
        [...document.querySelectorAll('input')].find((input) =>
          (input.placeholder ?? '').toLowerCase().includes('account'),
        )
      if (target) setNative(target, number)
      const amountInput = [...document.querySelectorAll('input')].find(
        (input) => input.inputMode === 'decimal' || (input.placeholder ?? '').toLowerCase().includes('amount'),
      )
      if (amountInput) setNative(amountInput, value)
    },
    accountNumber,
    amount,
  )
  await sleep(500)
}

async function firstHref(page, prefix, fallbackPrefixes = []) {
  return page.evaluate(
    (primary, fallbacks) => {
      const link = [...document.querySelectorAll('a[href]')].map((a) => a.getAttribute('href'))
      const hit = link.find((href) => href && href.startsWith(primary) && href !== primary)
      if (hit) return hit
      for (const prefix of fallbacks) {
        const other = link.find((href) => href && href.startsWith(prefix) && href !== prefix)
        if (other) return other
      }
      return null
    },
    prefix,
    fallbackPrefixes,
  )
}

async function signIn(page, role) {
  if (role === 'none') return
  // Must happen on the SAME page as the screenshots: `browser.newPage()`
  // creates a page in the browser's default context, which has its own
  // localStorage, so the session would not be visible to the captured page.
  const { email, password } = role === 'admin' ? ADMIN : CUSTOMER

  const attempts = 3
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle2' })
    // The dev server compiles routes on first request, so the first load of a
    // session can be slow enough that the app has not mounted yet.
    await page.waitForSelector('input[type="password"]', { timeout: 20000 })
    await sleep(400)

    await page.evaluate(
      (mail, pass) => {
        const setNative = (input, next) => {
          const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
          setter.call(input, next)
          input.dispatchEvent(new Event('input', { bubbles: true }))
        }
        const inputs = [...document.querySelectorAll('input[type="email"], input[type="password"]')]
        if (inputs[0]) setNative(inputs[0], mail)
        if (inputs[1]) setNative(inputs[1], pass)
      },
      email,
      password,
    )
    await sleep(300)
    await clickByText(page, 'Sign In')
    await sleep(2500)

    const landed = page.url().replace(BASE, '')
    if (!landed.includes('/login')) {
      console.log(`  · signed in as ${role} -> ${landed}`)
      return
    }

    // Surface whatever the page is actually saying instead of failing blind.
    const diagnostic = await page.evaluate(() => ({
      alert: document.querySelector('[role="alert"]')?.textContent?.trim().slice(0, 160) ?? null,
      emailValue: document.querySelector('input[type="email"]')?.value ?? null,
      hasButton: [...document.querySelectorAll('button')].some((b) =>
        (b.textContent ?? '').includes('Sign In'),
      ),
    }))
    console.log(`  · sign-in attempt ${attempt}/${attempts} failed (${landed}) ${JSON.stringify(diagnostic)}`)
    await sleep(1500)
  }

  throw new Error(`sign-in failed for ${role} after ${attempts} attempts`)
}

mkdirSync(OUT, { recursive: true })

const browser = await puppeteer.launch({
  headless: 'shell',
  executablePath: process.env.CHROME_PATH || undefined,
  args: ['--no-sandbox', '--disable-dev-shm-usage', '--force-color-profile=srgb', '--font-render-hinting=none'],
})

const consoleErrors = []
let captured = 0
let failed = 0

for (const role of ['none', 'customer', 'admin']) {
  const screens = SCREENS.filter((screen) => screen.role === role).filter(
    (screen) => !ONLY || ONLY.has(screen.name),
  )
  if (screens.length === 0) continue

  const context = await browser.createBrowserContext()
  const page = await context.newPage()
  await page.setViewport(VIEWPORT)

  page.on('console', (message) => {
    if (message.type() === 'error') {
      const text = message.text()
      if (!text.includes('favicon')) consoleErrors.push(`[${role}] ${text.slice(0, 200)}`)
    }
  })
  page.on('pageerror', (error) => consoleErrors.push(`[${role}] pageerror: ${String(error).slice(0, 200)}`))

  await signIn(page, role)

  for (const screen of screens) {
    try {
      if (screen.resolve) {
        const route = await screen.resolve(page)
        await sleep(screen.settle ?? 1500)
        const file = join(OUT, `${screen.name}${SUFFIX}.png`)
        await page.screenshot({ path: file, fullPage: true })
        console.log(`  ✓ ${screen.name}${SUFFIX}  (${route})`)
      } else {
        await page.goto(`${BASE}${screen.route}`, { waitUntil: 'networkidle2' })
        await sleep(screen.settle ?? 1500)
        if (screen.tab) {
          await clickByText(page, screen.tab)
          await sleep(800)
        }
        if (screen.prepare) await screen.prepare(page)
        const file = join(OUT, `${screen.name}${screen.modalSuffix ?? ''}${SUFFIX}.png`)
        await page.screenshot({ path: file, fullPage: true })
        console.log(`  ✓ ${screen.name}${SUFFIX}  (${screen.route ?? ''})`)
      }
      captured += 1
    } catch (error) {
      console.log(`  ✗ ${screen.name}${SUFFIX}: ${String(error).split('\n')[0]}`)
      failed += 1
    }
  }

  await context.close()
}

await browser.close()

console.log(`\n${captured} screenshots captured, ${failed} failed -> ${OUT}${MOBILE ? ' (mobile)' : ''}`)
if (consoleErrors.length) {
  console.log(`\nConsole errors (${consoleErrors.length}):`)
  for (const error of [...new Set(consoleErrors)].slice(0, 15)) console.log(`  ! ${error}`)
} else {
  console.log('No console errors.')
}