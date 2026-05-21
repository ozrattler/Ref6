import PocketBase from 'pocketbase'

const DEFAULT_URL = 'https://refappb.duckdns.org'

export const pb = new PocketBase(
  localStorage.getItem('pb_url') || DEFAULT_URL
)
