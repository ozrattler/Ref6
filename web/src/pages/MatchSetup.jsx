import { useState, useEffect } from 'react'
import { pb } from '../lib/pb'
import FixtureFormFields from '../components/FixtureFormFields'

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
  dissentSinBin:  true,
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
      dissentSinBin:  t.dissent_sin_bin  ?? true,
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
        two_yellows_rule: 'red_card',
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
        two_yellows_rule: 'red_card',
        dissent_sin_bin:  form.dissentSinBin,
        referee:          form.referee.trim(),
        ar1:              form.ar1.trim(),
        ar2:              form.ar2.trim(),
        fourth_official:  form.fourthOfficial.trim(),
        status:           'pending',
      })
      showToast('Match setup saved!')
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
          <FixtureFormFields form={form} set={set} />
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
