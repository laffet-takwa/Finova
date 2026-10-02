/**
 * Cross-checks the frontend TypeScript types against the backend Java response
 * records. The two were written independently, so a field-name drift between
 * them is the most likely class of silent bug in this project: the page renders,
 * the value is simply `undefined`.
 *
 * Usage: node scripts/check-contracts.mjs
 * Exits non-zero when a field is missing on either side.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const ROOT = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (['target', 'node_modules', '.git', 'dist'].includes(entry)) continue
      walk(full, out)
    } else {
      out.push(full)
    }
  }
  return out
}

/**
 * Removes annotations from a record component list. They must go before the
 * list is split on commas, because `@Schema(example = "a, b")` contains commas
 * that would otherwise be read as field separators.
 */
function stripAnnotations(body) {
  let out = ''
  let index = 0
  while (index < body.length) {
    const at = body.indexOf('@', index)
    if (at === -1) {
      out += body.slice(index)
      break
    }
    out += body.slice(index, at)
    let cursor = at + 1
    while (cursor < body.length && /[\w.]/.test(body[cursor])) cursor += 1
    if (body[cursor] === '(') {
      let depth = 0
      while (cursor < body.length) {
        if (body[cursor] === '(') depth += 1
        else if (body[cursor] === ')') {
          depth -= 1
          if (depth === 0) {
            cursor += 1
            break
          }
        }
        cursor += 1
      }
    }
    index = cursor
  }
  return out
}

/** Record component names → field names, from a Java source file. */
function javaRecords(source) {
  const records = new Map()
  const head = /public\s+record\s+(\w+)\s*\(/g
  let match
  while ((match = head.exec(source)) !== null) {
    // Scan forward with a paren counter: `@Schema(example = "…")` annotations
    // inside the component list contain parentheses, so a `[^)]*` pattern
    // silently fails to match every annotated record.
    const open = head.lastIndex
    let depth = 1
    let index = open
    while (index < source.length && depth > 0) {
      const char = source[index]
      if (char === '(') depth += 1
      else if (char === ')') depth -= 1
      index += 1
    }
    const body = stripAnnotations(source.slice(open, index - 1))

    const fields = body
      .split(',')
      .map((part) => part.trim())
      .filter(Boolean)
      .map((part) => {
        // Drop default values, then take the identifier immediately before the
        // comma: `Type<Generic> name = default`.
        const tokens = part.split('=')[0].trim().split(/\s+/)
        return tokens[tokens.length - 1] ?? ''
      })
      .filter((name) => /^[a-z][A-Za-z0-9]*$/.test(name))

    records.set(match[1], new Set(fields))
    head.lastIndex = index
  }
  return records
}

/** Interface field names, from the frontend types file. */
function tsInterfaces(source) {
  const interfaces = new Map()
  const head = /export\s+interface\s+(\w+)[^{]*\{/g
  let match
  while ((match = head.exec(source)) !== null) {
    // Brace-count the body: an inline type such as
    // `dailySeries: Array<{ label: string }>` closes a brace long before the
    // interface does, and a `[^}]*` pattern would truncate the field list.
    const open = head.lastIndex
    let depth = 1
    let index = open
    while (index < source.length && depth > 0) {
      const char = source[index]
      if (char === '{') depth += 1
      else if (char === '}') depth -= 1
      index += 1
    }
    const body = source.slice(open, index - 1)

    const fields = body
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('//') && !line.startsWith('*') && !line.startsWith('/*'))
      .map((line) => line.split(/[?:]/)[0].trim())
      .filter((name) => /^[A-Za-z_][A-Za-z0-9_]*$/.test(name))

    interfaces.set(match[1], new Set(fields))
    head.lastIndex = index
  }
  return interfaces
}

// Java record name -> the TypeScript interface that consumes it.
const PAIRS = [
  ['AccountResponse', 'Account'],
  ['BalanceResponse', 'AccountBalance'],
  ['AccountLookupResponse', 'BeneficiaryLookup'],
  ['AccountStatsResponse', 'AccountStatsSummary'],
  ['TransactionResponse', 'Transaction'],
  ['TransactionTimelineResponse', 'TransactionTimeline'],
  ['TransactionSummaryResponse', 'TransactionSummary'],
  ['AdminTransactionStatsResponse', 'AdminTransactionStats'],
  ['FraudAlertResponse', 'FraudAlert'],
  ['FraudStatsResponse', 'FraudStatsSummary'],
  ['NotificationResponse', 'Notification'],
  ['NotificationStatsResponse', 'NotificationStats'],
  ['UnreadCountResponse', 'UnreadCount'],
  ['PreferenceResponse', 'NotificationPreference'],
]

const javaFiles = walk(join(ROOT, 'backend')).filter((file) => file.endsWith('.java'))

// Several services define a record with the same name (a Feign mirror on the
// consumer side, the canonical DTO on the producer side). Prefer the canonical
// one: a record inside a `client/` package is a copy of someone else's
// contract, not the definition.
function isMirror(file) {
  return /[\\/]client[\\/]/.test(file)
}

const java = new Map()
for (const file of javaFiles) {
  for (const [name, fields] of javaRecords(readFileSync(file, 'utf8'))) {
    const existing = java.get(name)
    if (!existing || (existing.mirror && !isMirror(file))) {
      java.set(name, { fields, file: relative(ROOT, file), mirror: isMirror(file) })
    }
  }
}

const tsSource = readFileSync(join(ROOT, 'frontend', 'src', 'types', 'index.ts'), 'utf8')
const ts = tsInterfaces(tsSource)

let problems = 0
let checked = 0

for (const [javaName, tsName] of PAIRS) {
  const javaSide = java.get(javaName)
  const tsSide = ts.get(tsName)

  if (!javaSide) {
    console.log(`\x1b[33m?\x1b[0m  ${javaName}: no backend record found (skipped)`)
    continue
  }
  if (!tsSide) {
    console.log(`\x1b[31m✗\x1b[0m  ${javaName}: frontend has no interface "${tsName}"`)
    problems += 1
    continue
  }

  const missingInTs = [...javaSide.fields].filter((field) => !tsSide.has(field))
  const missingInJava = [...tsSide].filter(
    (field) => !javaSide.fields.has(field) && !['toString', 'valueOf'].includes(field),
  )

  checked += 1
  if (missingInTs.length === 0 && missingInJava.length === 0) {
    console.log(`\x1b[32m✓\x1b[0m  ${javaName} ↔ ${tsName}  (${javaSide.fields.size} fields in sync)`)
  } else {
    problems += 1
    console.log(`\x1b[31m✗\x1b[0m  ${javaName} ↔ ${tsName}   [${javaSide.file}]`)
    if (missingInTs.length) console.log(`     backend has, frontend type lacks: ${missingInTs.join(', ')}`)
    if (missingInJava.length) console.log(`     frontend type has, backend lacks: ${missingInJava.join(', ')}`)
  }
}

console.log(`\n${checked} contracts checked, ${problems} with drift.`)
process.exit(problems > 0 ? 1 : 0)