export const AGE_GROUPS = [
  { label: 'Open / Senior', halfLength: 45 },
  { label: 'U16',           halfLength: 35 },
  { label: 'U15',           halfLength: 35 },
  { label: 'U14',           halfLength: 30 },
  { label: 'U12',           halfLength: 25 },
]

export const KIT_COLOURS = [
  ['#dc2626', 'Red'],    ['#ea580c', 'Orange'], ['#ca8a04', 'Amber'],
  ['#16a34a', 'Green'],  ['#2563eb', 'Blue'],   ['#7c3aed', 'Purple'],
  ['#db2777', 'Pink'],   ['#0891b2', 'Teal'],   ['#92400e', 'Maroon'],
  ['#ffffff', 'White'],  ['#9ca3af', 'Silver'],  ['#111827', 'Black'],
]

// Coloured swatch that opens the native colour picker on click
export function KitSwatchBtn({ value, onChange }) {
  return (
    <label className="kit-swatch-btn" title="Choose kit colour">
      <span className="kit-swatch" style={{ background: value }} />
      <input type="color" value={value} onChange={e => onChange(e.target.value)} />
    </label>
  )
}

// Row of preset kit colour dots
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

// All form fields shared between MatchSetup and FixtureDetail edit form.
// `form` is the current values object; `set(field, value)` updates a single field.
export default function FixtureFormFields({ form, set }) {
  function onAgeGroupChange(label) {
    const ag = AGE_GROUPS.find(a => a.label === label)
    set('ageGroup', label)
    if (ag) set('halfLength', ag.halfLength)
  }

  return (
    <>
      {/* ── Competition Details ── */}
      <div className="section-label">Competition Details</div>

      <div className="form-group">
        <label className="form-label">Competition <span className="form-optional">(optional)</span></label>
        <input className="form-input" value={form.competition}
          onChange={e => set('competition', e.target.value)}
          placeholder="e.g. U15 Premier League" autoComplete="off" />
      </div>

      <div className="form-group">
        <label className="form-label">Venue <span className="form-optional">(optional)</span></label>
        <input className="form-input" value={form.venue}
          onChange={e => set('venue', e.target.value)}
          placeholder="Ground name" autoComplete="off" />
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

      <div className="form-group toggle-row">
        <span className="toggle-label">Dissent = Sin Bin</span>
        <label className="toggle">
          <input type="checkbox" checked={form.dissentSinBin}
            onChange={e => set('dissentSinBin', e.target.checked)} />
          <span className="toggle-track"><span className="toggle-thumb" /></span>
        </label>
      </div>
    </>
  )
}
