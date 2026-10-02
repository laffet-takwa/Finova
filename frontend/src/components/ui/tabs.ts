/**
 * Shared types and helpers for `TabList.vue`.
 *
 * The panel ids are derived from the tablist's accessible name rather than a
 * component uid, so the view that renders the `role="tabpanel"` element can
 * produce the exact same id and `aria-controls` never points at nothing.
 */
export interface TabItem {
  id: string
  label: string
  /** Optional trailing count (unread notifications, section totals…). */
  count?: number | null
}

export function tabPanelId(ariaLabel: string, tabId: string): string {
  const base = ariaLabel
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
  return `tabpanel-${base}-${tabId}`
}
