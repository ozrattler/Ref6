import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { pb } from '../lib/pb'
import { kitStyle } from '../lib/colours'

const TYPE_META = {
  GOAL:        { label: '⚽ Goal',  cls: 'goal' },
  YELLOW_CARD: { label: 'Yellow',   cls: 'yellow-card' },
  RED_CARD:    { label: 'Red Card', cls: 'red-card' },
  SIN_BIN:     { label: 'Sin Bin',  cls: 'sin-bin' },
}

export default function MatchDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [match,     setMatch]     = useState(null)
  const [incidents, setIncidents] = useState([])
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState(null)
  const [editing,   setEditing]   = useState(false)
  const [form,      setForm]      = useState(null)
  const [saving,    setSaving]    = useState(false)
  const [toast,     setToast]     = useState(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([
      pb.collection('matches').getOne(id),
      pb.collection('incidents').getList(1, 200, {
        filter: `match_id = "${id}"`,
        sort: 'minute,type',
      }),
    ])
      .then(([m, inc]) => {
        if (!cancelled) { setMatch(m); setForm(toForm(m)); setIncidents(inc.items); setLoading(false) }
      })
      .catch(e => { if (!cancelled) { setError(e.message); setLoading(false) } })
    return () => { cancelled = true }
  }, [id])

  function set(field, value) {
    setForm(f => ({ ...f, [field]: value }))
  }

  function showToast(msg, type = 'success') {
    setToast({ msg, type })
    setTimeout(() => setToast(null), 3000)
  }

  async function handleSave() {
    setSaving(true)
    try {
      const updated = await pb.collection('matches').update(id, {
        competition:    form.competition.trim(),
        venue:          form.venue.trim(),
        kickoff_date:   form.kickoffDate,
        kickoff_time:   form.kickoffTime,
        home_team:      form.homeTeam.trim(),
        home_colour:    form.homeColour,
        away_team:      form.awayTeam.trim(),
        away_colour:    form.awayColour,
        age_group:      form.ageGroup,
        half_length:    form.halfLength,
        referee:        form.referee.trim(),
        ar1:            form.ar1.trim(),
        ar2:            form.ar2.trim(),
        fourth_official: form.fourthOfficial.trim(),
        final_score:    form.finalScore.trim(),
      })
      setMatch(updated)
      setForm(toForm(updated))
      setEditing(false)
      showToast('Match saved.')
    } catch (err) {
      showToast(err.message || 'Failed to save.', 'error')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="loading">Loading…</div>
  if (error)   return <div className="error-state">⚠️ {error}</div>
  if (!match)  return null

  const halfLen = match.half_length || 45
  const allHaveHalf = incidents.length > 0 && incidents.every(i => i.half === 1 || i.half === 2)
  const first  = allHaveHalf ? incidents.filter(i => i.half === 1) : incidents.filter(i => i.minute <= halfLen)
  const second = allHaveHalf ? incidents.filter(i => i.half === 2) : incidents.filter(i => i.minute > halfLen)
  const hasOfficials = match.referee || match.ar1 || match.ar2 || match.fourth_official

  const dl = (() => {
    const parts = []
    if (match.kickoff_date) {
      try {
        parts.push(new Date(match.kickoff_date + 'T00:00:00').toLocaleDateString('en-AU', {
          weekday: 'short', day: 'numeric', month: 'long', year: 'numeric',
        }))
      } catch { parts.push(match.kickoff_date) }
    } else if (match.date) {
      try {
        parts.push(new Date(match.date).toLocaleDateString('en-AU', {
          weekday: 'short', day: 'numeric', month: 'long', year: 'numeric',
        }))
      } catch { parts.push(match.date) }
    }
    if (match.kickoff_time) parts.push(`KO ${match.kickoff_time}`)
    return parts.join(' · ') || null
  })()

  return (
    <div className="page">
      <div className="detail-toolbar">
        <button className="back-btn" onClick={() => navigate(-1)}>← Back</button>
        {!editing && (
          <button className="btn-edit" onClick={() => setEditing(true)}>Edit</button>
        )}
      </div>

      {editing ? (
        <EditForm form={form} set={set} onSave={handleSave} saving={saving}
          onCancel={() => { setForm(toForm(match)); setEditing(false) }} />
      ) : (
        <>
          <div className="match-detail-header">
            {match.competition && <div className="detail-competition">{match.competition}</div>}
            <div className="detail-meta">
              {dl && <span>{dl}</span>}
              {match.venue && <span className="detail-venue">📍 {match.venue}</span>}
              {match.age_group && <span className="badge">{match.age_group}</span>}
              {match.half_length && <span>{match.half_length} min halves</span>}
            </div>
            <div className="detail-score">
              <div className="detail-team-block">
                {match.home_colour && (
                  <span className="kit-dot" style={{ background: match.home_colour }} />
                )}
                <span className="detail-team" style={kitStyle(match.home_colour)}>
                  {match.home_team || 'Home'}
                </span>
              </div>
              <span className="detail-scoreline">{match.final_score || '–'}</span>
              <div className="detail-team-block detail-team-block-away">
                <span className="detail-team" style={kitStyle(match.away_colour)}>
                  {match.away_team || 'Away'}
                </span>
                {match.away_colour && (
                  <span className="kit-dot" style={{ background: match.away_colour }} />
                )}
              </div>
            </div>
          </div>

          {hasOfficials && (
            <div className="officials-section">
              <div className="officials-title">Match Officials</div>
              <div className="officials-list">
                {match.referee         && <OfficialItem role="Referee"      name={match.referee} />}
                {match.ar1             && <OfficialItem role="AR1"          name={match.ar1} />}
                {match.ar2             && <OfficialItem role="AR2"          name={match.ar2} />}
                {match.fourth_official && <OfficialItem role="4th Official" name={match.fourth_official} />}
              </div>
            </div>
          )}

          <div className="incidents-section">
            {incidents.length === 0 ? (
              <p className="no-incidents">No incidents recorded for this match.</p>
            ) : (
              <>
                <HalfSection label="1st Half" incidents={first}
                  homeTeam={match.home_team} homeColour={match.home_colour}
                  awayTeam={match.away_team} awayColour={match.away_colour} />
                <HalfSection label="2nd Half" incidents={second}
                  homeTeam={match.home_team} homeColour={match.home_colour}
                  awayTeam={match.away_team} awayColour={match.away_colour} />
              </>
            )}
          </div>
        </>
      )}

      {toast && <div className={`toast toast-${toast.type}`}>{toast.msg}</div>}
    </div>
  )
}

function toForm(m) {
  return {
    competition:    m.competition      || '',
    venue:          m.venue            || '',
    kickoffDate:    m.kickoff_date     || '',
    kickoffTime:    m.kickoff_time     || '',
    homeTeam:       m.home_team        || '',
    homeColour:     m.home_colour      || '#dc2626',
    awayTeam:       m.away_team        || '',
    awayColour:     m.away_colour      || '#2563eb',
    ageGroup:       m.age_group        || 'Open / Senior',
    halfLength:     m.half_length      || 45,
    referee:        m.referee          || '',
    ar1:            m.ar1              || '',
    ar2:            m.ar2              || '',
    fourthOfficial: m.fourth_official  || '',
    finalScore:     m.final_score      || '',
  }
}

function EditForm({ form, set, onSave, saving, onCancel }) {
  return (
    <div className="edit-form-wrap">
      <div className="form-card">
        <div className="section-label">Score</div>
        <div className="form-group">
          <label className="form-label">Final Score</label>
          <input className="form-input" value={form.finalScore}
            onChange={e => set('finalScore', e.target.value)}
            placeholder="e.g. 2–1" autoComplete="off" />
        </div>

        <div className="section-label">Competition Details</div>
        <div className="form-group">
          <label className="form-label">Competition</label>
          <input className="form-input" value={form.competition}
            onChange={e => set('competition', e.target.value)} autoComplete="off" />
        </div>
        <div className="form-group">
          <label className="form-label">Venue</label>
          <input className="form-input" value={form.venue}
            onChange={e => set('venue', e.target.value)} autoComplete="off" />
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

        <div className="section-label">Match Officials</div>
        <div className="officials-grid">
          <div className="form-group">
            <label className="form-label">Referee</label>
            <input className="form-input" value={form.referee}
              onChange={e => set('referee', e.target.value)} autoComplete="off" />
          </div>
          <div className="form-group">
            <label className="form-label">AR1</label>
            <input className="form-input" value={form.ar1}
              onChange={e => set('ar1', e.target.value)} autoComplete="off" />
          </div>
          <div className="form-group">
            <label className="form-label">AR2</label>
            <input className="form-input" value={form.ar2}
              onChange={e => set('ar2', e.target.value)} autoComplete="off" />
          </div>
          <div className="form-group">
            <label className="form-label">4th Official</label>
            <input className="form-input" value={form.fourthOfficial}
              onChange={e => set('fourthOfficial', e.target.value)} autoComplete="off" />
          </div>
        </div>
      </div>
      <div className="edit-actions">
        <button className="btn-ghost" onClick={onCancel}>Cancel</button>
        <button className="btn-submit" style={{ marginTop: 0 }} onClick={onSave} disabled={saving}>
          {saving ? 'Saving…' : 'Save Changes'}
        </button>
      </div>
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

function HalfSection({ label, incidents, homeTeam, homeColour, awayTeam, awayColour }) {
  if (!incidents.length) return null
  return (
    <div className="incident-half">
      <h3 className="incident-half-title">{label}</h3>
      {incidents.map(i => (
        <IncidentRow key={i.id} incident={i}
          homeTeam={homeTeam} homeColour={homeColour}
          awayTeam={awayTeam} awayColour={awayColour} />
      ))}
    </div>
  )
}

function IncidentRow({ incident, homeTeam, homeColour, awayTeam, awayColour }) {
  const meta   = TYPE_META[incident.type] ?? { label: incident.type, cls: '' }
  const player = [
    incident.player_number && `#${incident.player_number}`,
    incident.player_name,
  ].filter(Boolean).join(' ')

  const teamColour = incident.team === homeTeam ? homeColour
    : incident.team === awayTeam ? awayColour
    : null

  return (
    <div className="incident-row">
      <span className="incident-minute">{incident.minute}'</span>
      <span className={`incident-badge type-${meta.cls}`}>{meta.label}</span>
      <div className="incident-info">
        <span className="incident-team" style={kitStyle(teamColour)}>{incident.team}</span>
        {player && <span className="incident-player">{player}</span>}
        {incident.offence_description && (
          <span className="incident-offence">{incident.offence_description}</span>
        )}
      </div>
    </div>
  )
}
