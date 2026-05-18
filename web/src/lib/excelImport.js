import * as XLSX from 'xlsx'

const PLM_PLR = new Set(['PLM', 'PLR', 'SPL', 'SPLR'])
const STATE_CUP_RE = /state\s*cup|(?<!\w)src(?!\w)/i

// Map a League column value to a normalised age_group string.
// Returns null for SRC (caller handles competition field instead).
function mapLeagueToAgeGroup(league) {
  const u = league.toUpperCase().trim()
  if (!u) return ''
  if (u === 'PLM' || u === 'SPL')  return 'PLM'
  if (u === 'PLR' || u === 'SPLR') return 'PLR'
  if (u === 'SRC') return null   // not an age group — caller flags as State Cup
  if (u.startsWith('AW'))        return 'Open / Senior'

  // U<n> or W<n> — U18D, W18B, etc.
  const um = u.match(/^[UW](\d+)/)
  if (um) {
    const age = parseInt(um[1], 10)
    if (age >= 21) return 'Open / Senior'
    if (age >= 18) return 'U18'
    if (age >= 16) return 'U16'
    if (age >= 15) return 'U15'
    if (age >= 14) return 'U14'
    return 'U12'
  }

  // Anything else containing a number >= 21 (e.g. "21A")
  const nm = u.match(/(\d+)/)
  if (nm && parseInt(nm[1], 10) >= 21) return 'Open / Senior'

  return league  // unrecognised — keep as-is
}

// Try to infer an age group from a free-text competition name, e.g. "State Cup U14A Boys".
function extractAgeGroupFromText(text) {
  const u = (text || '').toUpperCase()
  const um = u.match(/\bU(\d+)/)
  if (um) {
    const age = parseInt(um[1], 10)
    if (age >= 21) return 'Open / Senior'
    if (age >= 18) return 'U18'
    if (age >= 16) return 'U16'
    if (age >= 15) return 'U15'
    if (age >= 14) return 'U14'
    return 'U12'
  }
  if (/\bOPEN\b|\bSENIOR\b/.test(u)) return 'Open / Senior'
  return ''
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

// Extract HH:MM from a value that may look like "14:00 ~ 15:45" or just "14:00".
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

// Return column index for a header name (case-insensitive).
function colIdx(headers, name) {
  const idx = headers.findIndex(h => str(h).toLowerCase() === name.toLowerCase())
  return idx >= 0 ? idx : null
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
    homeClub:    colIdx(headers, 'Home club'),
    awayClub:    colIdx(headers, 'Away club'),
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

    const homeTeam = get(row, C.homeClub)
    const awayTeam = get(row, C.awayClub)
    if (!homeTeam && !awayTeam) { skipped++; continue }

    const competition   = get(row, C.competition)
    const leagueRaw     = get(row, C.league)
    const mappedAge     = mapLeagueToAgeGroup(leagueRaw)
    const isLeagueSRC   = mappedAge === null                        // SRC returns null
    const isStateCup    = isLeagueSRC || STATE_CUP_RE.test(competition)
    const ageGroup      = isLeagueSRC
      ? extractAgeGroupFromText(competition)   // infer from competition name
      : (mappedAge ?? leagueRaw)               // use mapped value or original
    const isPremierLeague = PLM_PLR.has((ageGroup || '').toUpperCase())

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
      // rule defaults — State Cup overrides sin bin and penalties
      half_length:         45,
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
