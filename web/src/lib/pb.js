import PocketBase from 'pocketbase'

const DEFAULT_URL = 'https://refappb.duckdns.org'

// Evict stale stored URLs that are plain HTTP (e.g. old local-IP addresses).
// Those would be blocked as mixed content when the app is served over HTTPS.
const stored = localStorage.getItem('pb_url')
const resolvedUrl = (stored && stored.startsWith('https://')) ? stored : DEFAULT_URL
if (stored !== resolvedUrl) localStorage.setItem('pb_url', resolvedUrl)

export const pb = new PocketBase(resolvedUrl)
