import { pb } from './pb'

const LAST_SYNC_KEY = 'ref6_cal_last_sync'

export function getLastSync() {
  const v = localStorage.getItem(LAST_SYNC_KEY)
  return v ? new Date(v) : null
}

export function formatSyncTime(date) {
  if (!date) return null
  const now = new Date()
  const diffMin = Math.round((now - date) / 60000)
  if (diffMin < 1)  return 'just now'
  if (diffMin < 60) return `${diffMin}m ago`
  const diffH = Math.round(diffMin / 60)
  if (diffH < 24)   return `${diffH}h ago`
  return date.toLocaleDateString('en-AU', { day: 'numeric', month: 'short' })
}

export async function syncCalendar() {
  const base = pb.baseUrl.replace(/\/$/, '')
  const res = await fetch(`${base}/api/cal/sync`, { method: 'GET' })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.message || `Sync failed (HTTP ${res.status})`)
  }
  const data = await res.json()
  localStorage.setItem(LAST_SYNC_KEY, new Date().toISOString())
  return data // { ok, total, created, updated, skipped }
}
