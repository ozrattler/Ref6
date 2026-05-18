import { useState, useEffect, useRef } from 'react'

// SPL/SPLR kept for backward compat with records created before rename
export const SPL_AGE_GROUPS = new Set(['PLM', 'PLR', 'SPL', 'SPLR'])

export function isStateCupCompetition(s) {
  const u = (s || '').toUpperCase()
  return u.includes('STATE CUP') || /(?<!\w)SRC(?!\w)/.test(u)
}

export const AGE_GROUPS = [
  { label: 'PLM',           halfLength: 45 },
  { label: 'PLR',           halfLength: 45 },
  { label: 'Open / Senior', halfLength: 45 },
  { label: 'U18',           halfLength: 40 },
  { label: 'U16',           halfLength: 35 },
  { label: 'U15',           halfLength: 35 },
  { label: 'U14',           halfLength: 30 },
  { label: 'U12',           halfLength: 25 },
]

// Infer an age group from a free-text competition/description string.
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

export const KIT_COLOURS = [
  ['#166534', 'Dark Green'],   ['#16a34a', 'Light Green'],
  ['#1e3a8a', 'Dark Blue'],    ['#0ea5e9', 'Sky Blue'],
  ['#991b1b', 'Dark Red'],     ['#7f1d1d', 'Maroon'],
  ['#854d0e', 'Dark Yellow'],  ['#d97706', 'Gold'],
  ['#581c87', 'Dark Purple'],  ['#a78bfa', 'Lavender'],
  ['#9a3412', 'Dark Orange'],  ['#f59e0b', 'Amber'],
  ['#111827', 'Black'],        ['#ffffff', 'White'],
  ['#6b7280', 'Grey'],         ['#374151', 'Dark Grey'],
]

export function KitSwatchBtn({ value, onChange }) {
  return (
    <label className="kit-swatch-btn" title="Choose kit colour">
      <span className="kit-swatch" style={{ background: value }} />
      <input type="color" value={value} onChange={e => onChange(e.target.value)} />
    </label>
  )
}

export function ColourPresets({ value, onChange }) {
  return (
    <div className="colour-presets">
      {KIT_COLOURS.map(([hex, name]) => (
        <button key={hex} type="button" title={name}
          className={'colour-dot' + (hex.toLowerCase() === value?.toLowerCase() ? ' selected' : '')}
          style={{ background: hex }}
          onClick={() => onChange(hex)}
        />
      ))}
    </div>
  )
}

// Venue field with Nominatim (OpenStreetMap) place-name autocomplete
function VenuePicker({ value, onChange }) {
  const [query,       setQuery]       = useState(value || '')
  const [suggestions, setSuggestions] = useState([])
  const [open,        setOpen]        = useState(false)
  const [searching,   setSearching]   = useState(false)
  const debounce = useRef(null)

  // Sync when parent updates the value (e.g. calendar import fills the field)
  useEffect(() => { setQuery(value || '') }, [value])

  function handleChange(e) {
    const v = e.target.value
    setQuery(v)
    onChange(v)
    clearTimeout(debounce.current)
    if (v.length < 3) { setSuggestions([]); setOpen(false); return }
    debounce.current = setTimeout(() => fetchSuggestions(v), 420)
  }

  async function fetchSuggestions(q) {
    setSearching(true)
    try {
      const url = `https://nominatim.openstreetmap.org/search?` +
        `q=${encodeURIComponent(q)}&format=json&limit=6&addressdetails=1&countrycodes=au`
      const res  = await fetch(url, { headers: { 'Accept-Language': 'en-AU' } })
      const data = await res.json()
      const results = data
        .map(item => ({
          name:    item.name || item.display_name.split(',')[0].trim(),
          suburb:  [item.address?.suburb, item.address?.city_district,
                    item.address?.city || item.address?.town || item.address?.village]
                    .filter(Boolean)[0] || '',
        }))
        .filter(s => s.name)
      setSuggestions(results)
      setOpen(results.length > 0)
    } catch {
      setSuggestions([])
    } finally {
      setSearching(false)
    }
  }

  function pick(name) {
    setQuery(name)
    onChange(name)
    setSuggestions([])
    setOpen(false)
  }

  return (
    <div className="venue-picker">
      <div className="venue-input-row">
        <input className="form-input" value={query}
          onChange={handleChange}
          onBlur={() => setTimeout(() => setOpen(false), 160)}
          onFocus={() => suggestions.length > 0 && setOpen(true)}
          placeholder="Ground name or address"
          autoComplete="off"
        />
        {searching && <span className="venue-searching">…</span>}
      </div>
      {open && suggestions.length > 0 && (
        <div className="venue-suggestions">
          {suggestions.map((s, i) => (
            <button key={i} type="button" className="venue-suggestion-item"
              onMouseDown={() => pick(s.name)}>
              <span className="venue-suggestion-name">{s.name}</span>
              {s.suburb && <span className="venue-suggestion-addr">{s.suburb}</span>}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export default function FixtureFormFields({ form, set }) {
  function onAgeGroupChange(label) {
    const ag = AGE_GROUPS.find(a => a.label === label)
    set('ageGroup', label)
    if (ag) {
      set('halfLength', ag.halfLength)
      // PLM/PLR → goal scorers ON; all others → OFF
      set('recordGoalScorers', SPL_AGE_GROUPS.has(label))
    }
  }

  function onCompetitionChange(value) {
    set('competition', value)
    if (isStateCupCompetition(value)) {
      set('dissentSinBin',     false)
      set('penalties',         true)
      set('recordGoalScorers', true)
      // Auto-set age group from competition text (e.g. "State Cup U14A" → U14)
      const ag = extractAgeGroupFromText(value)
      if (ag) {
        set('ageGroup', ag)
        const agObj = AGE_GROUPS.find(a => a.label === ag)
        if (agObj) set('halfLength', agObj.halfLength)
      }
    }
  }

  return (
    <>
      {/* ── Competition Details ── */}
      <div className="section-label">Competition Details</div>

      <div className="form-group">
        <label className="form-label">Competition <span className="form-optional">(optional)</span></label>
        <input className="form-input" value={form.competition}
          onChange={e => onCompetitionChange(e.target.value)}
          placeholder="e.g. SSFA Winter 2026" autoComplete="off" />
      </div>

      <div className="form-group">
        <label className="form-label">Venue <span className="form-optional">(optional)</span></label>
        <VenuePicker value={form.venue} onChange={v => set('venue', v)} />
      </div>

      <div className="teams-row">
        <div className="form-group">
          <label className="form-label">Date</label>
          <input className="form-input" type="date" value={form.kickoffDate}
            onChange={e => set('kickoffDate', e.target.value)} />
        </div>
        <div className="form-group">
          <label className="form-label">Kick-off</label>
          <input className="form-input" type="time" value={form.kickoffTime}
            onChange={e => set('kickoffTime', e.target.value)} />
        </div>
      </div>

      {/* ── Teams ── */}
      <div className="section-label">Teams &amp; Kit Colours</div>

      <div className="teams-row">
        <div className="form-group">
          <label className="form-label">Home Team</label>
          <div className="team-input-row">
            <KitSwatchBtn value={form.homeColour} onChange={v => set('homeColour', v)} />
            <input className="form-input" value={form.homeTeam}
              onChange={e => set('homeTeam', e.target.value)}
              placeholder="Home" autoComplete="off" />
          </div>
          <ColourPresets value={form.homeColour} onChange={v => set('homeColour', v)} />
        </div>
        <div className="form-group">
          <label className="form-label">Away Team</label>
          <div className="team-input-row">
            <KitSwatchBtn value={form.awayColour} onChange={v => set('awayColour', v)} />
            <input className="form-input" value={form.awayTeam}
              onChange={e => set('awayTeam', e.target.value)}
              placeholder="Away" autoComplete="off" />
          </div>
          <ColourPresets value={form.awayColour} onChange={v => set('awayColour', v)} />
        </div>
      </div>

      {/* ── Match Details ── */}
      <div className="section-label">Match Details</div>

      <div className="teams-row">
        <div className="form-group">
          <label className="form-label">Age Group</label>
          <div className="form-select-wrap">
            <select className="form-select" value={form.ageGroup}
              onChange={e => onAgeGroupChange(e.target.value)}>
              {AGE_GROUPS.map(ag => (
                <option key={ag.label} value={ag.label}>{ag.label}</option>
              ))}
            </select>
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">
            Half Length — <strong style={{ color: 'var(--text)', fontWeight: 700 }}>{form.halfLength} min</strong>
          </label>
          <input type="range" className="range-input" min={10} max={60} step={5}
            value={form.halfLength} onChange={e => set('halfLength', Number(e.target.value))} />
        </div>
      </div>

      {/* ── Match Officials ── */}
      <div className="section-label">Match Officials</div>

      <div className="officials-grid">
        <div className="form-group">
          <label className="form-label">Referee</label>
          <input className="form-input" value={form.referee}
            onChange={e => set('referee', e.target.value)}
            placeholder="Referee name" autoComplete="off" />
        </div>
        <div className="form-group">
          <label className="form-label">AR1</label>
          <input className="form-input" value={form.ar1}
            onChange={e => set('ar1', e.target.value)}
            placeholder="AR1 name" autoComplete="off" />
        </div>
        <div className="form-group">
          <label className="form-label">AR2</label>
          <input className="form-input" value={form.ar2}
            onChange={e => set('ar2', e.target.value)}
            placeholder="AR2 name" autoComplete="off" />
        </div>
        <div className="form-group">
          <label className="form-label">4th Official <span className="form-optional">(optional)</span></label>
          <input className="form-input" value={form.fourthOfficial}
            onChange={e => set('fourthOfficial', e.target.value)}
            placeholder="4th Official" autoComplete="off" />
        </div>
      </div>

      {/* ── Competition Rules ── */}
      <div className="section-label">Competition Rules</div>

      {[
        ['dissentSinBin',     'Dissent = Sin Bin'],
        ['recordGoalScorers', SPL_AGE_GROUPS.has(form.ageGroup) ? 'Record Goal Scorers (+ Goal Type)' : 'Record Goal Scorers'],
        ['extraTime',         'Extra Time'],
        ['penalties',         'Penalties'],
      ].map(([field, label]) => (
        <div key={field} className="form-group toggle-row">
          <span className="toggle-label">{label}</span>
          <label className="toggle">
            <input type="checkbox" checked={!!form[field]}
              onChange={e => set(field, e.target.checked)} />
            <span className="toggle-track"><span className="toggle-thumb" /></span>
          </label>
        </div>
      ))}
    </>
  )
}
