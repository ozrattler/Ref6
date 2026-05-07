import PocketBase from 'pocketbase'

const DEFAULT_URL = 'http://192.168.1.106:8090'

export const pb = new PocketBase(
  localStorage.getItem('pb_url') || DEFAULT_URL
)
