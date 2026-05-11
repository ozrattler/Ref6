import * as XLSX from 'xlsx'

const PLM_PLR = new Set(['PLM', 'PLR', 'SPL', 'SPLR'])

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

    const ageGroup = get(row, C.league)
    const isPremierLeague = PLM_PLR.has(ageGroup.toUpperCase())

    fixtures.push({
      kickoff_date:        parseDate(get(row, C.date)),
      kickoff_time:        parseTime(get(row, C.time)),
      competition:         get(row, C.competition),
      age_group:           ageGroup,
      venue:               get(row, C.venue),
      home_team:           homeTeam,
      away_team:           awayTeam,
      referee:             get(row, C.referee),
      ar1:                 get(row, C.ar1),
      ar2:                 get(row, C.ar2),
      fourth_official:     get(row, C.fourth),
      // rule defaults
      half_length:         45,
      two_yellows_rule:    'red_card',
      dissent_sin_bin:     true,
      record_goal_scorers: isPremierLeague,
      extra_time:          false,
      penalties:           false,
      status:              'pending',
    })
  }

  return { fixtures, skipped }
}
