import * as XLSX from 'xlsx'

const PREMIER_LEAGUE_CODES = new Set(['PLM', 'PLR', 'PLW', 'SPL', 'SPLR'])
const STATE_CUP_RE = /state\s*cup|(?<!\w)src(?!\w)/i

// Map a League column value → { ageGroup, halfLength }.
// Returns { ageGroup: null } for SRC (State Cup — caller infers from competition text).
function mapLeague(raw) {
  if (!raw) return { ageGroup: '', halfLength: 45 }
  const u = raw.toUpperCase().trim()

  if (u === 'SRC') return { ageGroup: null, halfLength: 45 }

  // Premier League variants and Women's Premier League → Open / Senior
  if (PREMIER_LEAGUE_CODES.has(u))
    return { ageGroup: 'Open / Senior', halfLength: 45 }

  // Over-35/45 (O35A, O35B, O45, …), Senior, AW (Women Open)
  if (/^O\d+/.test(u) || u === 'SENIOR' || u.startsWith('AW'))
    return { ageGroup: 'Open / Senior', halfLength: 45 }

  // Strip trailing W (Women's variant — same age group and half length)
  const base = u.endsWith('W') ? u.slice(0, -1) : u

  // U<n> or W<n> (e.g. U16, W16, U14B, U16W stripped to U16)
  const um = base.match(/^[UW](\d+)/)
  if (um) {
    const age = parseInt(um[1], 10)
    if (age >= 21) return { ageGroup: 'Open / Senior', halfLength: 45 }
    if (age >= 18) return { ageGroup: 'U18',           halfLength: 45 }
    if (age >= 15) return { ageGroup: 'U16/U15',       halfLength: 35 }
    if (age >= 13) return { ageGroup: 'U14/U13',       halfLength: 35 }
    return               { ageGroup: 'U12',            halfLength: 25 }
  }

  // Bare number >= 21 (e.g. "21A")
  const nm = base.match(/^(\d+)/)
  if (nm && parseInt(nm[1], 10) >= 21) return { ageGroup: 'Open / Senior', halfLength: 45 }

  return { ageGroup: raw.trim(), halfLength: 45 }
}

// Infer age group from free-text competition name, e.g. "State Cup U14A Boys".
function extractAgeGroupFromText(text) {
  const u = (text || '').toUpperCase()
  const um = u.match(/\bU(\d+)/)
  if (um) {
    const age = parseInt(um[1], 10)
    if (age >= 21) return 'Open / Senior'
    if (age >= 18) return 'U18'
    if (age >= 15) return 'U16/U15'
    if (age >= 13) return 'U14/U13'
    return 'U12'
  }
  if (/\bOPEN\b|\bSENIOR\b/.test(u)) return 'Open / Senior'
  return ''
}

function halfLengthForAgeGroup(ag) {
  if (ag === 'U18')     return 45
  if (ag === 'U16/U15') return 35
  if (ag === 'U14/U13') return 35
  if (ag === 'U12')     return 25
  return 45
}

// Convert various date representations to YYYY-MM-DD.
// SheetJS with raw:false gives formatted strings (e.g. "25/05/2026", "25 May 2026").
function parseDate(val) {
  if (!val) return ''
  const s = String(val).trim()

  // DD/MM/YYYY
  let m = s.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/)
  if (m) return `${m[3]}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`

  // Already ISO
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s

  // D/M/YY  (2-digit year)
  m = s.match(/^(\d{1,2})\/(\d{1,2})\/(\d{2})$/)
  if (m) return `20${m[3]}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`

  // "25 May 2026" or "25-May-2026"
  const d = new Date(s)
  if (!isNaN(d)) return d.toISOString().slice(0, 10)

  return ''
}

// Extract HH:MM from "14:00 ~ 15:45" or "14:00".
function parseTime(val) {
  if (!val) return ''
  const before = String(val).split('~')[0].trim()
  const m = before.match(/(\d{1,2}):(\d{2})/)
  if (m) return `${m[1].padStart(2, '0')}:${m[2]}`
  return ''
}

function str(val) { return String(val ?? '').trim() }

// Find which row index is the header row (contains a cell equal to "Date").
function findHeaderRow(rows) {
  for (let i = 0; i < Math.min(15, rows.length); i++) {
    if (rows[i].some(c => str(c).toLowerCase() === 'date')) return i
  }
  return 0
}

// Return column index for the first matching header name (case-insensitive).
function colIdx(headers, ...names) {
  for (const name of names) {
    const idx = headers.findIndex(h => str(h).toLowerCase() === name.toLowerCase())
    if (idx >= 0) return idx
  }
  return null
}

function get(row, idx) { return idx !== null ? str(row[idx]) : '' }

export function parseExcelFixtures(buffer) {
  const wb = XLSX.read(buffer, { type: 'array', raw: false, cellDates: false })
  const ws = wb.Sheets[wb.SheetNames[0]]
  const rows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '', raw: false })

  if (rows.length < 2) return { fixtures: [], skipped: 0 }

  const headerIdx = findHeaderRow(rows)
  const headers = rows[headerIdx]

  const C = {
    date:        colIdx(headers, 'Date'),
    time:        colIdx(headers, 'Time'),
    competition: colIdx(headers, 'Competition'),
    league:      colIdx(headers, 'League'),
    venue:       colIdx(headers, 'Venue'),
    referee:     colIdx(headers, 'R'),
    ar1:         colIdx(headers, 'A1'),
    ar2:         colIdx(headers, 'A2'),
    fourth:      colIdx(headers, 'F'),
    status:      colIdx(headers, 'Status'),
  }

  const fixtures = []
  let skipped = 0

  for (let i = headerIdx + 1; i < rows.length; i++) {
    const row = rows[i]

    // Skip entirely blank rows
    if (row.every(c => !str(c))) continue

    // Skip PLAYED rows
    const rowStatus = get(row, C.status).toUpperCase()
    if (rowStatus === 'PLAYED') { skipped++; continue }

    const homeTeam = [str(row[8]), str(row[9])].filter(Boolean).join(' ').slice(0, 4)
    const awayTeam = [str(row[10]), str(row[11])].filter(Boolean).join(' ').slice(0, 4)
    if (!homeTeam && !awayTeam) { skipped++; continue }

    const competition  = get(row, C.competition)
    const leagueRaw    = get(row, C.league)
    const mapped       = mapLeague(leagueRaw)
    const isLeagueSRC  = mapped.ageGroup === null
    const isStateCup   = isLeagueSRC || STATE_CUP_RE.test(competition)

    // Premier League flag based on original league code (before age-group mapping)
    const isPremierLeague = PREMIER_LEAGUE_CODES.has(leagueRaw.toUpperCase().trim())

    // Preserve the exact league code as the displayed grade; only fall back to
    // a derived label for SRC rows where the league code carries no age info.
    const ageGroup = isLeagueSRC
      ? extractAgeGroupFromText(competition)
      : (leagueRaw.trim() || '')

    const halfLength = isLeagueSRC
      ? halfLengthForAgeGroup(ageGroup)
      : mapped.halfLength

    fixtures.push({
      kickoff_date:        parseDate(get(row, C.date)),
      kickoff_time:        parseTime(get(row, C.time)),
      competition,
      age_group:           ageGroup,
      venue:               get(row, C.venue),
      home_team:           homeTeam,
      away_team:           awayTeam,
      referee:             get(row, C.referee),
      ar1:                 get(row, C.ar1),
      ar2:                 get(row, C.ar2),
      fourth_official:     get(row, C.fourth),
      half_length:         halfLength,
      two_yellows_rule:    'red_card',
      dissent_sin_bin:     !isStateCup,
      record_goal_scorers: isPremierLeague || isStateCup,
      extra_time:          false,
      penalties:           isStateCup,
      status:              'pending',
    })
  }

  return { fixtures, skipped }
}
