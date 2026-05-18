import { useState, useEffect } from 'react'
import { pb } from '../lib/pb'
import FixtureFormFields from '../components/FixtureFormFields'
import TemplateManager from '../components/TemplateManager'

const PRESET_TEMPLATES = [
  {
    id:                  '__preset_state_cup__',
    name:                'State Cup',
    competition:         'State Cup',
    age_group:           'Open / Senior',
    half_length:         45,
    dissent_sin_bin:     false,
    record_goal_scorers: true,
    extra_time:          false,
    penalties:           true,
  },
]

const DEFAULT_FORM = {
  competition:       'SSFA Winter 2026',
  venue:             '',
  kickoffDate:       '',
  kickoffTime:       '',
  homeTeam:          '',
  homeColour:        '#dc2626',
  awayTeam:          '',
  awayColour:        '#2563eb',
  ageGroup:          'Open / Senior',
  halfLength:        45,
  dissentSinBin:     true,
  recordGoalScorers: false,
  extraTime:         false,
  penalties:         false,
  referee:           'Sir John',
  ar1:               '',
  ar2:               '',
  fourthOfficial:    '',
}

export default function MatchSetup() {
  const [form,                setForm]                = useState(DEFAULT_FORM)
  const [saving,              setSaving]              = useState(false)
  const [toast,               setToast]               = useState(null)
  const [templates,           setTemplates]           = useState([])
  const [showLoadTemplate,    setShowLoadTemplate]    = useState(false)
  const [showSaveTemplate,    setShowSaveTemplate]    = useState(false)
  const [showTemplateManager, setShowTemplateManager] = useState(false)
  const [templateName,        setTemplateName]        = useState('')
  const [savingTpl,           setSavingTpl]           = useState(false)

  useEffect(() => { fetchTemplates() }, [])

  function fetchTemplates() {
    pb.collection('templates')
      .getList(1, 200, { requestKey: null })
      .then(r => setTemplates(r.items.sort((a, b) => a.name.localeCompare(b.name))))
      .catch(() => {})
  }

  function set(field, value) {
    setForm(f => ({ ...f, [field]: value }))
  }

  function applyTemplate(t) {
    setForm(f => ({
      ...f,
      // match-specific fields always reset to blank
      venue:             '',
      kickoffDate:       '',
      kickoffTime:       '',
      homeTeam:          '',
      homeColour:        '#dc2626',
      awayTeam:          '',
      awayColour:        '#2563eb',
      // competition rules & settings from template
      competition:       t.competition         || '',
      ageGroup:          t.age_group           || 'Open / Senior',
      halfLength:        t.half_length         || 45,
      dissentSinBin:     t.dissent_sin_bin     ?? true,
      recordGoalScorers: t.record_goal_scorers ?? true,
      extraTime:         t.extra_time          ?? false,
      penalties:         t.penalties           ?? false,
      referee:           t.referee             || 'Sir John',
      ar1:               t.ar1                 || '',
      ar2:               t.ar2                 || '',
      fourthOfficial:    t.fourth_official     || '',
    }))
    setShowLoadTemplate(false)
    showToast(`Template "${t.name}" loaded`)
  }

  async function handleSaveTemplate() {
    if (!templateName.trim()) return
    setSavingTpl(true)
    try {
      await pb.collection('templates').create({
        name:                templateName.trim(),
        competition:         form.competition,
        age_group:           form.ageGroup,
        half_length:         Number(form.halfLength),
        two_yellows_rule:    'red_card',
        dissent_sin_bin:     Boolean(form.dissentSinBin),
        record_goal_scorers: Boolean(form.recordGoalScorers),
        extra_time:          Boolean(form.extraTime),
        penalties:           Boolean(form.penalties),
        referee:             form.referee,
        ar1:                 form.ar1,
        ar2:                 form.ar2,
        fourth_official:     form.fourthOfficial,
      }, { requestKey: null })
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
        competition:         form.competition.trim(),
        venue:               form.venue.trim(),
        kickoff_date:        form.kickoffDate,
        kickoff_time:        form.kickoffTime,
        home_team:           form.homeTeam.trim(),
        home_colour:         form.homeColour,
        away_team:           form.awayTeam.trim(),
        away_colour:         form.awayColour,
        age_group:           form.ageGroup,
        half_length:         Number(form.halfLength),
        two_yellows_rule:    'red_card',
        dissent_sin_bin:     Boolean(form.dissentSinBin),
        record_goal_scorers: Boolean(form.recordGoalScorers),
        extra_time:          Boolean(form.extraTime),
        penalties:           Boolean(form.penalties),
        referee:             (form.referee        || '').trim(),
        ar1:                 (form.ar1            || '').trim(),
        ar2:                 (form.ar2            || '').trim(),
        fourth_official:     (form.fourthOfficial || '').trim(),
        status:              'pending',
      }, { requestKey: null })
      showToast('Match setup saved!')
      setForm(f => ({
        ...f,
        venue: '', kickoffDate: '', kickoffTime: '',
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
          <button type="button" className="btn-template" onClick={() => setShowTemplateManager(true)}>
            Manage
          </button>
        </div>
      </div>

      <form onSubmit={handleSubmit}>
        <div className="form-card">
          <FixtureFormFields form={form} set={set} />
        </div>
        <button type="submit" className="btn-submit" disabled={saving}>
          {saving ? 'Saving…' : 'Save Match Setup'}
        </button>
      </form>

      {showLoadTemplate && (
        <TemplatePickerModal
          templates={templates}
          onSelect={applyTemplate}
          onClose={() => setShowLoadTemplate(false)}
        />
      )}

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
            <p className="modal-note" style={{ marginTop: 4 }}>
              Saves: competition, age group, half length, rules, and officials. Not team names or match dates.
            </p>
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

      {showTemplateManager && (
        <TemplateManager
          templates={templates}
          onClose={() => setShowTemplateManager(false)}
          onRefresh={fetchTemplates}
        />
      )}

      {toast && <div className={`toast toast-${toast.type}`}>{toast.msg}</div>}
    </div>
  )
}

function TemplatePickerModal({ templates, onSelect, onClose }) {
  return (
    <div className="modal-backdrop" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal modal-tall">
        <div className="modal-title">Load Template</div>

        <div className="template-group-label">Built-in</div>
        <div className="template-list">
          {PRESET_TEMPLATES.map(t => (
            <button key={t.id} className="template-item" onClick={() => onSelect(t)}>
              <div className="template-item-body">
                <div className="template-item-name">{t.name}</div>
                <div className="template-item-detail">
                  Sin bin off · Penalties on · Goal scorers on
                </div>
              </div>
            </button>
          ))}
        </div>

        <div className="template-group-label">Saved</div>
        {templates.length === 0 ? (
          <p style={{ color: 'var(--muted)', fontSize: '0.9rem', padding: '4px 0 8px' }}>
            No templates saved yet. Fill in the form and click "Save as Template".
          </p>
        ) : (
          <div className="template-list">
            {templates.map(t => (
              <button key={t.id} className="template-item" onClick={() => onSelect(t)}>
                <div className="template-item-body">
                  <div className="template-item-name">{t.name}</div>
                  <div className="template-item-detail">
                    {[t.competition, t.age_group, t.referee && `Ref: ${t.referee}`].filter(Boolean).join(' · ')}
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
