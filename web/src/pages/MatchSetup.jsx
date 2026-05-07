import { useState, useEffect } from 'react'
import { pb } from '../lib/pb'

const AGE_GROUPS = [
  { label: 'Open / Senior', halfLength: 45 },
  { label: 'U16',           halfLength: 35 },
  { label: 'U15',           halfLength: 35 },
  { label: 'U14',           halfLength: 30 },
  { label: 'U12',           halfLength: 25 },
]

const KIT_COLOURS = [
  ['#dc2626', 'Red'],    ['#ea580c', 'Orange'], ['#ca8a04', 'Amber'],
  ['#16a34a', 'Green'],  ['#2563eb', 'Blue'],   ['#7c3aed', 'Purple'],
  ['#db2777', 'Pink'],   ['#0891b2', 'Teal'],   ['#92400e', 'Maroon'],
  ['#ffffff', 'White'],  ['#9ca3af', 'Silver'],  ['#111827', 'Black'],
]

const DEFAULT_FORM = {
  competition:    '',
  venue:          '',
  kickoffDate:    '',
  kickoffTime:    '',
  homeTeam:       '',
  homeColour:     '#dc2626',
  awayTeam:       '',
  awayColour:     '#2563eb',
  ageGroup:       'Open / Senior',
  halfLength:     45,
  twoYellowsRule: 'red_card',
  dissentSinBin:  false,
  referee:        'Sir John',
  ar1:            '',
  ar2:            '',
  fourthOfficial: '',
}

export default function MatchSetup() {
  const [form,             setForm]             = useState(DEFAULT_FORM)
  const [saving,           setSaving]           = useState(false)
  const [toast,            setToast]            = useState(null)
  const [templates,        setTemplates]        = useState([])
  const [showLoadTemplate, setShowLoadTemplate] = useState(false)
  const [showSaveTemplate, setShowSaveTemplate] = useState(false)
  const [templateName,     setTemplateName]     = useState('')
  const [savingTpl,        setSavingTpl]        = useState(false)

  useEffect(() => { fetchTemplates() }, [])

  function fetchTemplates() {
    pb.collection('templates')
      .getList(1, 100, { sort: 'name' })
      .then(r => setTemplates(r.items))
      .catch(() => {})
  }

  function set(field, value) {
    setForm(f => ({ ...f, [field]: value }))
  }

  function onAgeGroupChange(label) {
    const ag = AGE_GROUPS.find(a => a.label === label)
    setForm(f => ({ ...f, ageGroup: label, halfLength: ag?.halfLength ?? f.halfLength }))
  }

  function applyTemplate(t) {
    setForm(f => ({
      ...f,
      competition:    t.competition      || '',
      venue:          t.venue            || '',
      homeTeam:       t.home_team        || '',
      homeColour:     t.home_colour      || '#dc2626',
      awayTeam:       t.away_team        || '',
      awayColour:     t.away_colour      || '#2563eb',
      ageGroup:       t.age_group        || 'Open / Senior',
      halfLength:     t.half_length      || 45,
      twoYellowsRule: t.two_yellows_rule || 'red_card',
      dissentSinBin:  t.dissent_sin_bin  || false,
      referee:        t.referee          || 'Sir John',
      ar1:            t.ar1              || '',
      ar2:            t.ar2              || '',
      fourthOfficial: t.fourth_official  || '',
    }))
    setShowLoadTemplate(false)
    showToast(`Template "${t.name}" loaded`)
  }

  async function handleSaveTemplate() {
    if (!templateName.trim()) return
    setSavingTpl(true)
    try {
      await pb.collection('templates').create({
        name:             templateName.trim(),
        competition:      form.competition,
        home_team:        form.homeTeam,
        away_team:        form.awayTeam,
        home_colour:      form.homeColour,
        away_colour:      form.awayColour,
        age_group:        form.ageGroup,
        half_length:      form.halfLength,
        two_yellows_rule: form.twoYellowsRule,
        dissent_sin_bin:  form.dissentSinBin,
        referee:          form.referee,
        ar1:              form.ar1,
        ar2:              form.ar2,
        fourth_official:  form.fourthOfficial,
        venue:            form.venue,
      })
      fetchTemplates()
      setTemplateName('')
      setShowSaveTemplate(false)
      showToast('Template saved!')
    } catch (err) {
      showToast(err.message || 'Failed to save template', 'error')
    } finally {
      setSavingTpl(false)
    }
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
        venue:            form.venue.trim(),
        kickoff_date:     form.kickoffDate,
        kickoff_time:     form.kickoffTime,
        home_team:        form.homeTeam.trim(),
        home_colour:      form.homeColour,
        away_team:        form.awayTeam.trim(),
        away_colour:      form.awayColour,
        age_group:        form.ageGroup,
        half_length:      form.halfLength,
        two_yellows_rule: form.twoYellowsRule,
        dissent_sin_bin:  form.dissentSinBin,
        referee:          form.referee.trim(),
        ar1:              form.ar1.trim(),
        ar2:              form.ar2.trim(),
        fourth_official:  form.fourthOfficial.trim(),
        status:           'pending',
      })
      showToast('Match setup saved!')
      // Reset fixture-specific fields; keep officials, colours, age group
      setForm(f => ({
        ...f,
        competition: '', venue: '', kickoffDate: '', kickoffTime: '',
        homeTeam: '', awayTeam: '',
      }))
    } catch (err) {
      showToast(err.message || 'Failed to save.', 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page">

      <div className="setup-header">
        <h1 className="page-title" style={{ marginBottom: 0 }}>Match Setup</h1>
        <div className="template-bar">
          <button type="button" className="btn-template" onClick={() => setShowLoadTemplate(true)}>
            Load Template
          </button>
          <button type="button" className="btn-template btn-template-save" onClick={() => setShowSaveTemplate(true)}>
            Save as Template
          </button>
        </div>
      </div>

      <form onSubmit={handleSubmit}>
        <div className="form-card">

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

          {/* ── Competition Rules ── */}
          <div className="section-label">Competition Rules</div>

          <div className="form-group">
            <label className="form-label">2nd Yellow Card</label>
            <div className="segment-control">
              <button type="button"
                className={'segment-btn' + (form.twoYellowsRule === 'red_card' ? ' active' : '')}
                onClick={() => set('twoYellowsRule', 'red_card')}>
                Red Card (2YC = RC)
              </button>
              <button type="button"
                className={'segment-btn' + (form.twoYellowsRule === 'sin_bin' ? ' active' : '')}
                onClick={() => set('twoYellowsRule', 'sin_bin')}>
                Sin Bin (2YC = SB)
              </button>
            </div>
          </div>

          <div className="form-group toggle-row">
            <span className="toggle-label">Dissent = Sin Bin</span>
            <label className="toggle">
              <input type="checkbox" checked={form.dissentSinBin}
                onChange={e => set('dissentSinBin', e.target.checked)} />
              <span className="toggle-track"><span className="toggle-thumb" /></span>
            </label>
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

        </div>

        <button type="submit" className="btn-submit" disabled={saving}>
          {saving ? 'Saving…' : 'Save Match Setup'}
        </button>
      </form>

      {/* Load Template Modal */}
      {showLoadTemplate && (
        <TemplatePickerModal
          templates={templates}
          onSelect={applyTemplate}
          onClose={() => setShowLoadTemplate(false)}
        />
      )}

      {/* Save as Template Modal */}
      {showSaveTemplate && (
        <div className="modal-backdrop" onClick={e => { if (e.target === e.currentTarget) setShowSaveTemplate(false) }}>
          <div className="modal">
            <div className="modal-title">Save as Template</div>
            <div className="form-group">
              <label className="form-label">Template Name</label>
              <input className="form-input" value={templateName}
                onChange={e => setTemplateName(e.target.value)}
                placeholder='e.g. "SSFA Senior", "U15 Metro"'
                autoFocus
                onKeyDown={e => e.key === 'Enter' && handleSaveTemplate()} />
            </div>
            <div className="modal-actions">
              <button className="btn-ghost" onClick={() => setShowSaveTemplate(false)}>Cancel</button>
              <button className="btn-primary-sm" onClick={handleSaveTemplate}
                disabled={savingTpl || !templateName.trim()}>
                {savingTpl ? 'Saving…' : 'Save'}
              </button>
            </div>
          </div>
        </div>
      )}

      {toast && <div className={`toast toast-${toast.type}`}>{toast.msg}</div>}
    </div>
  )
}

// Coloured swatch that opens the native colour picker on click
function KitSwatchBtn({ value, onChange }) {
  return (
    <label className="kit-swatch-btn" title="Choose kit colour">
      <span className="kit-swatch" style={{ background: value }} />
      <input type="color" value={value} onChange={e => onChange(e.target.value)} />
    </label>
  )
}

// Row of preset kit colour dots
function ColourPresets({ value, onChange }) {
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

function TemplatePickerModal({ templates, onSelect, onClose }) {
  return (
    <div className="modal-backdrop" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal modal-tall">
        <div className="modal-title">Load Template</div>
        {templates.length === 0 ? (
          <p style={{ color: 'var(--muted)', fontSize: '0.9rem', padding: '8px 0' }}>
            No templates saved yet. Fill in the form and click "Save as Template".
          </p>
        ) : (
          <div className="template-list">
            {templates.map(t => (
              <button key={t.id} className="template-item" onClick={() => onSelect(t)}>
                <div className="template-item-colours">
                  {t.home_colour && <span className="template-kit-dot" style={{ background: t.home_colour }} />}
                  {t.away_colour && <span className="template-kit-dot" style={{ background: t.away_colour }} />}
                </div>
                <div className="template-item-body">
                  <div className="template-item-name">{t.name}</div>
                  <div className="template-item-detail">
                    {[t.competition, t.age_group, t.venue].filter(Boolean).join(' · ')}
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}
        <div className="modal-actions">
          <button className="btn-ghost" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}
