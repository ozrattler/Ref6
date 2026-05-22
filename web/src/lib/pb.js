import PocketBase from 'pocketbase'

const LAN_URL = 'http://192.168.1.106:8090'
const EXT_URL = 'https://refappb.duckdns.org'

const hostname = window.location.hostname
const isLan = /^192\.168\./.test(hostname) || hostname === 'localhost' || hostname === '127.0.0.1'

// Auto-detected default based on network location
export const DEFAULT_URL = isLan ? LAN_URL : EXT_URL

// Use stored URL only when it's a genuine manual override (not one of the two standard defaults)
const stored = localStorage.getItem('pb_url')
const isManualOverride = stored && stored !== LAN_URL && stored !== EXT_URL
const resolvedUrl = isManualOverride ? stored : DEFAULT_URL

export const pb = new PocketBase(resolvedUrl)
