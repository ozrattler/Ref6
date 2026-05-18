import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { pb } from '../lib/pb'
import { kitStyle } from '../lib/colours'
import FixtureFormFields from '../components/FixtureFormFields'
import { MatchReport } from './MatchDetail'

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

export default function FixtureDetail() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [fixture,   setFixture]   = useState(null)
  const [match,     setMatch]     = useState(null)   // linked completed match, if any
  const [incidents, setIncidents] = useState([])
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState(null)
  const [editing,   setEditing]   = useState(false)
  const [form,      setForm]      = useState(null)
  const [saving,    setSaving]    = useState(false)
  const [deleting,  setDeleting]  = useState(false)
  const [toast,     setToast]     = useState(null)

  useEffect(() => {
    let cancelled = false

    Promise.all([
      pb.collection('match_setups').getOne(id, { requestKey: null }),
      pb.collection('matches').getList(1, 1, {
        filter: `match_setup_id = "${id}"`,
        sort: '-date',
        requestKey: null,
      }).catch(() => ({ items: [] })),
    ])
      .then(([f, matchRes]) => {
        if (cancelled) return
        setFixture(f)
        setForm(toForm(f))
        const linked = matchRes.items?.[0] ?? null
        setMatch(linked)
        if (linked) {
          // Load incidents for the linked match
          return pb.collection('incidents').getList(1, 200, {
            filter: `match_id = "${linked.id}"`,
            sort: 'minute,type',
            requestKey: null,
          })
        }
      })
      .then(incRes => {
        if (!cancelled && incRes) setIncidents(incRes.items ?? [])
        if (!cancelled) setLoading(false)
      })
      .catch(e => { if (!cancelled) { setError(e.message); setLoading(false) } })

    return () => { cancelled = true }
  }, [id])

  function set(field, value) { setForm(f => ({ ...f, [field]: value })) }

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

  return (
    <div className="page">
      <div className="detail-toolbar">
        <button className="back-btn" onClick={() => navigate(-1)}>← Back</button>
        <div className="detail-toolbar-actions">
          {!editing && (
            <>
              <button className="btn-edit" onClick={() => setEditing(true)}>Edit Fixture</button>
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
      ) : match ? (
        // Linked match exists — show full match report
        <>
          <MatchReport match={match} incidents={incidents} />
          {match.id && (
            <button className="btn-ghost fixture-view-raw"
              onClick={() => navigate(`/match/${match.id}`)}>
              Open match record →
            </button>
          )}
        </>
      ) : (
        // Pre-match fixture — show fixture detail
        <FixtureView fixture={fixture} />
      )}

      {toast && <div className={`toast toast-${toast.type}`}>{toast.msg}</div>}
    </div>
  )
}

// ── Pre-match fixture view ────────────────────────────────────────────────────

function FixtureView({ fixture: f }) {
  const hasOfficials = f.referee || f.ar1 || f.ar2 || f.fourth_official

  const dateLabel = (() => {
    const parts = []
    if (f.kickoff_date) {
      try {
        parts.push(new Date(f.kickoff_date + 'T00:00:00').toLocaleDateString('en-AU', {
          weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Australia/Sydney',
        }))
      } catch { parts.push(f.kickoff_date) }
    }
    if (f.kickoff_time) parts.push(`KO ${f.kickoff_time}`)
    return parts.join(' · ') || null
  })()

  return (
    <>
      {dateLabel && (
        <div className="rpt-section rpt-datetime">{dateLabel}</div>
      )}

      {(f.competition || f.age_group) && (
        <div className="rpt-section rpt-competition-row">
          {f.competition && <span className="rpt-competition">{f.competition}</span>}
          {f.age_group   && <span className="badge">{f.age_group}</span>}
          {f.half_length && <span className="rpt-halves">{f.half_length} min halves</span>}
        </div>
      )}

      <div className="rpt-section rpt-score-section">
        <div className="rpt-score-row">
          <div className="rpt-team-block">
            {f.home_colour && <span className="kit-dot" style={{ background: f.home_colour }} />}
            <span className="rpt-team-name" style={kitStyle(f.home_colour)}>
              {f.home_team || 'Home'}
            </span>
          </div>
          <span className="rpt-scoreline rpt-scoreline-vs">vs</span>
          <div className="rpt-team-block rpt-team-block-away">
            <span className="rpt-team-name" style={kitStyle(f.away_colour)}>
              {f.away_team || 'Away'}
            </span>
            {f.away_colour && <span className="kit-dot" style={{ background: f.away_colour }} />}
          </div>
        </div>
      </div>

      {hasOfficials && (
        <div className="rpt-section">
          <div className="rpt-section-label">Match Officials</div>
          <div className="officials-list">
            {f.referee         && <OfficialItem role="Referee"      name={f.referee} />}
            {f.ar1             && <OfficialItem role="AR1"          name={f.ar1} />}
            {f.ar2             && <OfficialItem role="AR2"          name={f.ar2} />}
            {f.fourth_official && <OfficialItem role="4th Official" name={f.fourth_official} />}
          </div>
        </div>
      )}

      <div className="rpt-section">
        <div className="rpt-section-label">Rules</div>
        <div className="officials-list">
          <OfficialItem role="2nd Yellow"   name="Red Card" />
          <OfficialItem role="Dissent"      name={f.dissent_sin_bin     ? 'Sin Bin'    : 'Yellow Card'} />
          <OfficialItem role="Goal Scorers" name={f.record_goal_scorers !== false ? 'Recorded' : 'Not recorded'} />
          {f.extra_time && <OfficialItem role="Extra Time" name="Yes" />}
          {f.penalties  && <OfficialItem role="Penalties"  name="Yes" />}
        </div>
      </div>

      {f.venue && (
        <div className="rpt-section rpt-venue">
          <span className="rpt-venue-icon">📍</span>
          <span>{f.venue}</span>
        </div>
      )}
    </>
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
