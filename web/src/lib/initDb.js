// Initialise PocketBase collections for Ref6.
// All operations use the admin API directly (no PocketBase SDK) so they work
// regardless of collection-level access rules.

const PUBLIC_RULES = {
  listRule: '', viewRule: '', createRule: '', updateRule: '', deleteRule: '',
}

const f = (name, type, required = false, options = {}) => ({ name, type, required, options })

const MATCHES_SCHEMA = [
  f('date',            'date',   true),
  f('competition',     'text'),
  f('home_team',       'text',   true),
  f('away_team',       'text',   true),
  f('final_score',     'text'),
  f('age_group',       'text'),
  f('half_length',     'number'),
  f('status',          'text'),
  f('referee',         'text'),
  f('ar1',             'text'),
  f('ar2',             'text'),
  f('fourth_official', 'text'),
  f('home_colour',     'text'),
  f('away_colour',     'text'),
  f('venue',           'text'),
  f('kickoff_date',    'text'),
  f('kickoff_time',    'text'),
]

const MATCH_SETUPS_SCHEMA = [
  f('competition',         'text'),
  f('home_team',           'text', true),
  f('away_team',           'text', true),
  f('age_group',           'text'),
  f('half_length',         'number'),
  f('two_yellows_rule',    'text'),
  f('dissent_sin_bin',     'bool'),
  f('record_goal_scorers', 'bool'),
  f('extra_time',          'bool'),
  f('penalties',           'bool'),
  f('status',              'text'),
  f('referee',             'text'),
  f('ar1',                 'text'),
  f('ar2',                 'text'),
  f('fourth_official',     'text'),
  f('home_colour',         'text'),
  f('away_colour',         'text'),
  f('venue',               'text'),
  f('kickoff_date',        'text'),
  f('kickoff_time',        'text'),
  f('ical_uid',            'text'),
]

const TEMPLATES_SCHEMA = [
  f('name',                'text', true),
  f('competition',         'text'),
  f('age_group',           'text'),
  f('half_length',         'number'),
  f('two_yellows_rule',    'text'),
  f('dissent_sin_bin',     'bool'),
  f('record_goal_scorers', 'bool'),
  f('extra_time',          'bool'),
  f('penalties',           'bool'),
  f('referee',             'text'),
  f('ar1',                 'text'),
  f('ar2',                 'text'),
  f('fourth_official',     'text'),
]

function incidentsSchema(matchesId) {
  return [
    { name: 'match_id', type: 'relation', required: true,
      options: { collectionId: matchesId, cascadeDelete: true, maxSelect: 1 } },
    f('half',                'number'),
    f('minute',              'number'),
    f('type',                'text'),
    f('team',                'text'),
    f('player_number',       'text'),
    f('player_name',         'text'),
    f('offence_description', 'text'),
    f('goal_type',           'text'),
  ]
}

// ── Internal helpers ─────────────────────────────────────────────────────────

async function fetchWithTimeout(url, opts = {}, ms = 6000) {
  const ctrl = new AbortController()
  const id = setTimeout(() => ctrl.abort(), ms)
  try {
    return await fetch(url, { ...opts, signal: ctrl.signal })
  } finally {
    clearTimeout(id)
  }
}

async function adminAuth(pbUrl, email, password) {
  const body = JSON.stringify({ identity: email, password })
  const headers = { 'Content-Type': 'application/json' }
  const endpoints = [
    '/api/superusers/auth-with-password',  // PocketBase v0.23+
    '/api/admins/auth-with-password',      // PocketBase v0.22 and earlier
  ]
  for (const path of endpoints) {
    try {
      const res = await fetchWithTimeout(pbUrl + path, { method: 'POST', headers, body })
      if (res.ok) {
        const data = await res.json()
        if (data.token) return data.token
      }
    } catch { /* try next endpoint */ }
  }
  throw new Error('Admin authentication failed — check email and password.')
}

async function getCollection(pbUrl, token, name) {
  const res = await fetchWithTimeout(`${pbUrl}/api/collections/${name}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (res.status === 404) return null
  if (!res.ok) throw new Error(`Cannot fetch collection "${name}" (HTTP ${res.status})`)
  return res.json()
}

// Creates a new collection or patches an existing one to add missing fields
// and set public access rules. Returns { action:'created'|'updated', name }.
async function ensureCollection(pbUrl, token, name, schema) {
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  }
  const existing = await getCollection(pbUrl, token, name)

  if (!existing) {
    const res = await fetchWithTimeout(`${pbUrl}/api/collections`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ name, type: 'base', schema, ...PUBLIC_RULES }),
    })
    const data = await res.json()
    if (!res.ok) throw new Error(data.message || `Failed to create "${name}"`)
    return { action: 'created', id: data.id }
  }

  // Collection exists — find missing fields and ensure public rules
  const existingNames = new Set(
    (existing.schema ?? existing.fields ?? []).map(fld => fld.name)
  )
  const missing = schema.filter(fld => !existingNames.has(fld.name))
  const currentSchema = existing.schema ?? existing.fields ?? []

  const res = await fetchWithTimeout(`${pbUrl}/api/collections/${existing.id}`, {
    method: 'PATCH',
    headers,
    body: JSON.stringify({
      schema: [...currentSchema, ...missing],
      ...PUBLIC_RULES,
    }),
  })
  const data = await res.json()
  if (!res.ok) throw new Error(data.message || `Failed to update "${name}"`)
  return { action: 'updated', id: existing.id, added: missing.length }
}

// ── Public API ────────────────────────────────────────────────────────────────

// Initialise / repair all Ref6 PocketBase collections.
// Returns an array of human-readable log strings.
export async function initDb(pbUrl, email, password) {
  const url = pbUrl.replace(/\/$/, '')
  const log = []

  const token = await adminAuth(url, email, password)
  log.push('✓ Authenticated as admin')

  // matches
  const mRes = await ensureCollection(url, token, 'matches', MATCHES_SCHEMA)
  log.push(mRes.action === 'created'
    ? '✓ Created matches collection'
    : `✓ Updated matches (${mRes.added} field${mRes.added !== 1 ? 's' : ''} added)`)

  // incidents (relation depends on matches id)
  const iRes = await ensureCollection(url, token, 'incidents', incidentsSchema(mRes.id))
  log.push(iRes.action === 'created'
    ? '✓ Created incidents collection'
    : `✓ Updated incidents (${iRes.added} field${iRes.added !== 1 ? 's' : ''} added)`)

  // match_setups
  const sRes = await ensureCollection(url, token, 'match_setups', MATCH_SETUPS_SCHEMA)
  log.push(sRes.action === 'created'
    ? '✓ Created match_setups collection'
    : `✓ Updated match_setups (${sRes.added} field${sRes.added !== 1 ? 's' : ''} added)`)

  // templates
  const tRes = await ensureCollection(url, token, 'templates', TEMPLATES_SCHEMA)
  log.push(tRes.action === 'created'
    ? '✓ Created templates collection'
    : `✓ Updated templates (${tRes.added} field${tRes.added !== 1 ? 's' : ''} added)`)

  log.push('✓ All collections have public read/write/delete access')
  return log
}

// Quick health check — returns 'ok' | 'missing' | 'permissions' | 'connection'
export async function checkDb(pbUrl) {
  const url = (pbUrl || '').replace(/\/$/, '')
  if (!url) return 'connection'
  try {
    const res = await fetchWithTimeout(
      `${url}/api/collections/match_setups/records?perPage=1`, {}, 4000
    )
    if (res.status === 404) return 'missing'
    if (res.status === 401 || res.status === 403) return 'permissions'
    if (res.ok) return 'ok'
    return 'error'
  } catch {
    return 'connection'
  }
}
