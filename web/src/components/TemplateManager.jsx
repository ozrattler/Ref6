import { useState } from 'react'
import { pb } from '../lib/pb'
import { AGE_GROUPS } from './FixtureFormFields'

function toEditForm(t) {
  return {
    name:              t.name              || '',
    competition:       t.competition       || '',
    ageGroup:          t.age_group         || 'Open / Senior',
    halfLength:        t.half_length       || 45,
    dissentSinBin:     t.dissent_sin_bin   ?? true,
    recordGoalScorers: t.record_goal_scorers ?? true,
    extraTime:         t.extra_time        ?? false,
    penalties:         t.penalties         ?? false,
    referee:           t.referee           || '',
    ar1:               t.ar1               || '',
    ar2:               t.ar2               || '',
    fourthOfficial:    t.fourth_official   || '',
  }
}

const TOGGLES = [
  ['dissentSinBin',     'Dissent = Sin Bin'],
  ['recordGoalScorers', 'Record Goal Scorers'],
  ['extraTime',         'Extra Time'],
  ['penalties',         'Penalties'],
]

export default function TemplateManager({ templates, onClose, onRefresh }) {
  const [mode,       setMode]       = useState('list')   // 'list' | 'edit'
  const [editTarget, setEditTarget] = useState(null)
  const [editForm,   setEditForm]   = useState({})
  const [saving,     setSaving]     = useState(false)
  const [toast,      setToast]      = useState(null)

  function showToast(msg, type = 'success') {
    setToast({ msg, type })
    setTimeout(() => setToast(null), 3000)
  }

  function beginEdit(t) {
    setEditTarget(t)
    setEditForm(toEditForm(t))
    setMode('edit')
  }

  function cancelEdit() {
    setMode('list')
    setEditTarget(null)
    setEditForm({})
  }

  function set(field, value) {
    setEditForm(f => ({ ...f, [field]: value }))
  }

  function onAgeGroupChange(label) {
    const ag = AGE_GROUPS.find(a => a.label === label)
    set('ageGroup', label)
    if (ag) set('halfLength', ag.halfLength)
  }

  async function saveEdit() {
    if (!editForm.name.trim()) return
    setSaving(true)
    try {
      await pb.collection('templates').update(editTarget.id, {
        name:                editForm.name.trim(),
        competition:         editForm.competition,
        age_group:           editForm.ageGroup,
        half_length:         Number(editForm.halfLength),
        two_yellows_rule:    'red_card',
        dissent_sin_bin:     Boolean(editForm.dissentSinBin),
        record_goal_scorers: Boolean(editForm.recordGoalScorers),
        extra_time:          Boolean(editForm.extraTime),
        penalties:           Boolean(editForm.penalties),
        referee:             editForm.referee,
        ar1:                 editForm.ar1,
        ar2:                 editForm.ar2,
        fourth_official:     editForm.fourthOfficial,
      }, { requestKey: null })
      cancelEdit()
      onRefresh()
      showToast('Template saved.')
    } catch (err) {
      showToast(err.message || 'Failed to save.', 'error')
    } finally {
      setSaving(false)
    }
  }

  async function deleteTemplate(t) {
    if (!window.confirm(`Delete template "${t.name}"?`)) return
    try {
      await pb.collection('templates').delete(t.id, { requestKey: null })
      onRefresh()
      showToast('Template deleted.')
    } catch (err) {
      showToast(err.message || 'Failed to delete.', 'error')
    }
  }

  return (
    <div className="modal-backdrop" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal modal-tall">

        <div className="modal-title">
          {mode === 'edit' ? (
            <span>
              <button className="back-btn-inline" onClick={cancelEdit}>←</button>
              {' '}Edit Template
            </span>
          ) : 'Manage Templates'}
        </div>

        {mode === 'list' ? (
          <div className="template-list">
            {templates.length === 0 ? (
              <p style={{ color: 'var(--muted)', fontSize: '0.9rem', padding: '8px 0' }}>
                No templates saved yet. Fill in the setup form and click "Save as Template".
              </p>
            ) : templates.map(t => (
              <div key={t.id} className="template-manager-item">
                <div className="template-item-body">
                  <div className="template-item-name">{t.name}</div>
                  <div className="template-item-detail">
                    {[t.competition, t.age_group].filter(Boolean).join(' · ')}
                  </div>
                </div>
                <div className="template-manager-actions">
                  <button className="btn-edit" onClick={() => beginEdit(t)}>Edit</button>
                  <button className="btn-delete" onClick={() => deleteTemplate(t)}>Delete</button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="template-edit-scroll">
            <div className="form-group">
              <label className="form-label">Template Name</label>
              <input className="form-input" value={editForm.name}
                onChange={e => set('name', e.target.value)}
                autoComplete="off" autoFocus />
            </div>

            <div className="section-label">Competition Details</div>
            <div className="form-group">
              <label className="form-label">Competition Name <span className="form-optional">(optional)</span></label>
              <input className="form-input" value={editForm.competition}
                onChange={e => set('competition', e.target.value)}
                placeholder="e.g. SSFA Premier League" autoComplete="off" />
            </div>

            <div className="section-label">Match Details</div>
            <div className="teams-row">
              <div className="form-group">
                <label className="form-label">Age Group</label>
                <div className="form-select-wrap">
                  <select className="form-select" value={editForm.ageGroup}
                    onChange={e => onAgeGroupChange(e.target.value)}>
                    {AGE_GROUPS.map(ag => (
                      <option key={ag.label} value={ag.label}>{ag.label}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">
                  Half Length — <strong style={{ color: 'var(--text)', fontWeight: 700 }}>{editForm.halfLength} min</strong>
                </label>
                <input type="range" className="range-input" min={10} max={60} step={5}
                  value={editForm.halfLength}
                  onChange={e => set('halfLength', Number(e.target.value))} />
              </div>
            </div>

            <div className="section-label">Competition Rules</div>
            {TOGGLES.map(([field, label]) => (
              <div key={field} className="form-group toggle-row">
                <span className="toggle-label">{label}</span>
                <label className="toggle">
                  <input type="checkbox" checked={!!editForm[field]}
                    onChange={e => set(field, e.target.checked)} />
                  <span className="toggle-track"><span className="toggle-thumb" /></span>
                </label>
              </div>
            ))}

            <div className="section-label">Match Officials</div>
            <div className="officials-grid">
              {[
                ['referee',        'Referee',      'Referee name'],
                ['ar1',            'AR1',          'AR1 name'],
                ['ar2',            'AR2',          'AR2 name'],
                ['fourthOfficial', '4th Official', '4th Official'],
              ].map(([field, label, ph]) => (
                <div key={field} className="form-group">
                  <label className="form-label">{label}</label>
                  <input className="form-input" value={editForm[field] || ''}
                    onChange={e => set(field, e.target.value)}
                    placeholder={ph} autoComplete="off" />
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="modal-actions">
          {mode === 'edit' ? (
            <>
              <button className="btn-ghost" onClick={cancelEdit}>Cancel</button>
              <button className="btn-primary-sm" onClick={saveEdit}
                disabled={saving || !editForm.name?.trim()}>
                {saving ? 'Saving…' : 'Save'}
              </button>
            </>
          ) : (
            <button className="btn-ghost" onClick={onClose}>Close</button>
          )}
        </div>

        {toast && <div className={`toast toast-${toast.type}`}>{toast.msg}</div>}
      </div>
    </div>
  )
}
