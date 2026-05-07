import { useState } from 'react'
import { pb } from '../lib/pb'

const AGE_GROUPS = [
  { label: 'Open / Senior', halfLength: 45 },
  { label: 'U16',           halfLength: 35 },
  { label: 'U15',           halfLength: 35 },
  { label: 'U14',           halfLength: 30 },
  { label: 'U12',           halfLength: 25 },
]

const DEFAULT_FORM = {
  competition:    '',
  homeTeam:       '',
  awayTeam:       '',
  ageGroup:       'Open / Senior',
  halfLength:     45,
  twoYellowsRule: 'red_card',
  dissentSinBin:  false,
}

export default function MatchSetup() {
  const [form,   setForm]   = useState(DEFAULT_FORM)
  const [saving, setSaving] = useState(false)
  const [toast,  setToast]  = useState(null)

  function set(field, value) {
    setForm(f => ({ ...f, [field]: value }))
  }

  function onAgeGroupChange(label) {
    const ag = AGE_GROUPS.find(a => a.label === label)
    setForm(f => ({ ...f, ageGroup: label, halfLength: ag?.halfLength ?? f.halfLength }))
  }

  function showToast(msg, type = 'success') {
    setToast({ msg, type })
    setTimeout(() => setToast(null), 3000)
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!form.homeTeam.trim() || !form.awayTeam.trim()) {
      showToast('Home and Away team names are required.', 'error')
      return
    }
    setSaving(true)
    try {
      await pb.collection('match_setups').create({
        competition:      form.competition.trim(),
        home_team:        form.homeTeam.trim(),
        away_team:        form.awayTeam.trim(),
        age_group:        form.ageGroup,
        half_length:      form.halfLength,
        two_yellows_rule: form.twoYellowsRule,
        dissent_sin_bin:  form.dissentSinBin,
        status:           'pending',
      })
      showToast('Match setup saved!')
      setForm(f => ({ ...DEFAULT_FORM, ageGroup: f.ageGroup, halfLength: f.halfLength }))
    } catch (err) {
      showToast(err.message || 'Failed to save.', 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page">
      <h1 className="page-title">Match Setup</h1>
      <form onSubmit={handleSubmit}>
        <div className="form-card">

          {/* Competition */}
          <div className="form-group">
            <label className="form-label">
              Competition <span className="form-optional">(optional)</span>
            </label>
            <input
              className="form-input"
              value={form.competition}
              onChange={e => set('competition', e.target.value)}
              placeholder="e.g. U15 Premier League"
              autoComplete="off"
            />
          </div>

          {/* Teams */}
          <div className="teams-row">
            <div className="form-group">
              <label className="form-label">Home Team</label>
              <input
                className="form-input"
                value={form.homeTeam}
                onChange={e => set('homeTeam', e.target.value)}
                placeholder="Home"
                autoComplete="off"
              />
            </div>
            <div className="form-group">
              <label className="form-label">Away Team</label>
              <input
                className="form-input"
                value={form.awayTeam}
                onChange={e => set('awayTeam', e.target.value)}
                placeholder="Away"
                autoComplete="off"
              />
            </div>
          </div>

          {/* Age group */}
          <div className="form-group">
            <label className="form-label">Age Group</label>
            <div className="form-select-wrap">
              <select
                className="form-select"
                value={form.ageGroup}
                onChange={e => onAgeGroupChange(e.target.value)}
              >
                {AGE_GROUPS.map(ag => (
                  <option key={ag.label} value={ag.label}>{ag.label}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Half length */}
          <div className="form-group">
            <label className="form-label">
              Half Length — <strong style={{ color: 'var(--text)' }}>{form.halfLength} min</strong>
            </label>
            <input
              type="range"
              className="range-input"
              min={10} max={60} step={5}
              value={form.halfLength}
              onChange={e => set('halfLength', Number(e.target.value))}
            />
          </div>

          {/* Rules */}
          <div className="section-label">Competition Rules</div>

          <div className="form-group">
            <label className="form-label">2nd Yellow Card</label>
            <div className="segment-control">
              <button
                type="button"
                className={'segment-btn' + (form.twoYellowsRule === 'red_card' ? ' active' : '')}
                onClick={() => set('twoYellowsRule', 'red_card')}
              >
                Red Card (2YC = RC)
              </button>
              <button
                type="button"
                className={'segment-btn' + (form.twoYellowsRule === 'sin_bin' ? ' active' : '')}
                onClick={() => set('twoYellowsRule', 'sin_bin')}
              >
                Sin Bin (2YC = SB)
              </button>
            </div>
          </div>

          <div className="form-group toggle-row">
            <span className="toggle-label">Dissent = Sin Bin</span>
            <label className="toggle">
              <input
                type="checkbox"
                checked={form.dissentSinBin}
                onChange={e => set('dissentSinBin', e.target.checked)}
              />
              <span className="toggle-track">
                <span className="toggle-thumb" />
              </span>
            </label>
          </div>

        </div>

        <button type="submit" className="btn-submit" disabled={saving}>
          {saving ? 'Saving…' : 'Save Match Setup'}
        </button>
      </form>

      {toast && (
        <div className={`toast toast-${toast.type}`}>{toast.msg}</div>
      )}
    </div>
  )
}
