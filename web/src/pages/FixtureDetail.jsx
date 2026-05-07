import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { pb } from '../lib/pb'
import { kitStyle } from '../lib/colours'
import FixtureFormFields from '../components/FixtureFormFields'

function toForm(f) {
  return {
    competition:       f.competition         || '',
    venue:             f.venue               || '',
    kickoffDate:       f.kickoff_date        || '',
    kickoffTime:       f.kickoff_time        || '',
    homeTeam:          f.home_team           || '',
    homeColour:        f.home_colour         || '#dc2626',
    awayTeam:          f.away_team           || '',
    awayColour:        f.away_colour         || '#2563eb',
    ageGroup:          f.age_group           || 'Open / Senior',
    halfLength:        f.half_length         || 45,
    dissentSinBin:     f.dissent_sin_bin     ?? true,
    recordGoalScorers: f.record_goal_scorers ?? true,
    extraTime:         f.extra_time          ?? false,
    penalties:         f.penalties           ?? false,
    referee:           f.referee             || '',
    ar1:               f.ar1                 || '',
    ar2:               f.ar2                 || '',
    fourthOfficial:    f.fourth_official     || '',
  }
}

function dateLine(f) {
  const parts = []
  if (f.kickoff_date) {
    try {
      parts.push(new Date(f.kickoff_date + 'T00:00:00').toLocaleDateString('en-AU', {
        weekday: 'short', day: 'numeric', month: 'long', year: 'numeric',
      }))
    } catch { parts.push(f.kickoff_date) }
  }
  if (f.kickoff_time) parts.push(`KO ${f.kickoff_time}`)
  return parts.join(' · ') || null
}

export default function FixtureDetail() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [fixture,  setFixture]  = useState(null)
  const [loading,  setLoading]  = useState(true)
  const [error,    setError]    = useState(null)
  const [editing,  setEditing]  = useState(false)
  const [form,     setForm]     = useState(null)
  const [saving,   setSaving]   = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [toast,    setToast]    = useState(null)

  useEffect(() => {
    pb.collection('match_setups').getOne(id, { requestKey: null })
      .then(f => { setFixture(f); setForm(toForm(f)); setLoading(false) })
      .catch(e => { setError(e.message); setLoading(false) })
  }, [id])

  function set(field, value) {
    setForm(f => ({ ...f, [field]: value }))
  }

  function showToast(msg, type = 'success') {
    setToast({ msg, type })
    setTimeout(() => setToast(null), 3000)
  }

  async function handleSave() {
    if (!form.homeTeam.trim() || !form.awayTeam.trim()) {
      showToast('Home and Away team names are required.', 'error')
      return
    }
    setSaving(true)
    try {
      const updated = await pb.collection('match_setups').update(id, {
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
      }, { requestKey: null })
      setFixture(updated)
      setForm(toForm(updated))
      setEditing(false)
      showToast('Fixture saved.')
    } catch (err) {
      showToast(err.message || 'Failed to save.', 'error')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    if (!window.confirm('Delete this fixture? This cannot be undone.')) return
    setDeleting(true)
    try {
      await pb.collection('match_setups').delete(id, { requestKey: null })
      navigate(-1)
    } catch (err) {
      showToast(err.message || 'Failed to delete.', 'error')
      setDeleting(false)
    }
  }

  if (loading) return <div className="loading">Loading…</div>
  if (error)   return <div className="error-state">⚠️ {error}</div>
  if (!fixture) return null

  const f = fixture
  const hasOfficials = f.referee || f.ar1 || f.ar2 || f.fourth_official

  return (
    <div className="page">
      <div className="detail-toolbar">
        <button className="back-btn" onClick={() => navigate(-1)}>← Back</button>
        <div className="detail-toolbar-actions">
          {!editing && (
            <>
              <button className="btn-edit" onClick={() => setEditing(true)}>Edit</button>
              <button className="btn-delete" onClick={handleDelete} disabled={deleting}>
                {deleting ? 'Deleting…' : 'Delete'}
              </button>
            </>
          )}
        </div>
      </div>

      {editing ? (
        <div className="edit-form-wrap">
          <div className="form-card">
            <FixtureFormFields form={form} set={set} />
          </div>
          <div className="edit-actions">
            <button className="btn-ghost" onClick={() => { setForm(toForm(fixture)); setEditing(false) }}>
              Cancel
            </button>
            <button className="btn-submit" style={{ marginTop: 0 }} onClick={handleSave} disabled={saving}>
              {saving ? 'Saving…' : 'Save Changes'}
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="match-detail-header">
            {f.competition && <div className="detail-competition">{f.competition}</div>}
            <div className="detail-meta">
              {dateLine(f) && <span>{dateLine(f)}</span>}
              {f.venue && <span className="detail-venue">📍 {f.venue}</span>}
              {f.age_group && <span className="badge">{f.age_group}</span>}
              {f.half_length && <span>{f.half_length} min halves</span>}
            </div>
            <div className="detail-score">
              <div className="detail-team-block">
                {f.home_colour && <span className="kit-dot" style={{ background: f.home_colour }} />}
                <span className="detail-team" style={kitStyle(f.home_colour)}>
                  {f.home_team || 'Home'}
                </span>
              </div>
              <span className="detail-scoreline fixture-vs-badge">vs</span>
              <div className="detail-team-block detail-team-block-away">
                <span className="detail-team" style={kitStyle(f.away_colour)}>
                  {f.away_team || 'Away'}
                </span>
                {f.away_colour && <span className="kit-dot" style={{ background: f.away_colour }} />}
              </div>
            </div>
          </div>

          {hasOfficials && (
            <div className="officials-section">
              <div className="officials-title">Match Officials</div>
              <div className="officials-list">
                {f.referee         && <OfficialItem role="Referee"      name={f.referee} />}
                {f.ar1             && <OfficialItem role="AR1"          name={f.ar1} />}
                {f.ar2             && <OfficialItem role="AR2"          name={f.ar2} />}
                {f.fourth_official && <OfficialItem role="4th Official" name={f.fourth_official} />}
              </div>
            </div>
          )}

          <div className="fixture-rules-section">
            <div className="officials-title">Rules</div>
            <div className="officials-list">
              <OfficialItem role="2nd Yellow"    name="Red Card" />
              <OfficialItem role="Dissent"       name={f.dissent_sin_bin     ? 'Sin Bin'    : 'Yellow Card'} />
              <OfficialItem role="Goal Scorers"  name={f.record_goal_scorers !== false ? 'Recorded' : 'Not recorded'} />
              {f.extra_time && <OfficialItem role="Extra Time" name="Yes" />}
              {f.penalties  && <OfficialItem role="Penalties"  name="Yes" />}
            </div>
          </div>
        </>
      )}

      {toast && <div className={`toast toast-${toast.type}`}>{toast.msg}</div>}
    </div>
  )
}

function OfficialItem({ role, name }) {
  return (
    <div className="official-item">
      <span className="official-role">{role}</span>
      <span className="official-name">{name}</span>
    </div>
  )
}
