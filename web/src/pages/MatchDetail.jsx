import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { pb } from '../lib/pb'

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
        if (!cancelled) {
          setMatch(m)
          setIncidents(inc.items)
          setLoading(false)
        }
      })
      .catch(e => { if (!cancelled) { setError(e.message); setLoading(false) } })
    return () => { cancelled = true }
  }, [id])

  if (loading) return <div className="loading">Loading…</div>
  if (error)   return <div className="error-state">⚠️ {error}</div>
  if (!match)  return null

  const date = match.date
    ? new Date(match.date).toLocaleDateString('en-AU', {
        weekday: 'short', day: 'numeric', month: 'long', year: 'numeric',
      })
    : '—'

  const halfLen = match.half_length || 45

  // Use the half field when available; fall back to minute-based split
  const allHaveHalf = incidents.length > 0 &&
    incidents.every(i => i.half === 1 || i.half === 2)
  const first  = allHaveHalf
    ? incidents.filter(i => i.half === 1)
    : incidents.filter(i => i.minute <= halfLen)
  const second = allHaveHalf
    ? incidents.filter(i => i.half === 2)
    : incidents.filter(i => i.minute > halfLen)

  return (
    <div className="page">
      <button className="back-btn" onClick={() => navigate(-1)}>← Back</button>

      <div className="match-detail-header">
        {match.competition && (
          <div className="detail-competition">{match.competition}</div>
        )}
        <div className="detail-meta">
          <span>{date}</span>
          {match.age_group   && <span className="badge">{match.age_group}</span>}
          {match.half_length && <span>{match.half_length} min halves</span>}
        </div>
        <div className="detail-score">
          <span className="detail-team">{match.home_team || 'Home'}</span>
          <span className="detail-scoreline">{match.final_score || '–'}</span>
          <span className="detail-team">{match.away_team || 'Away'}</span>
        </div>
      </div>

      <div className="incidents-section">
        {incidents.length === 0 ? (
          <p className="no-incidents">No incidents recorded for this match.</p>
        ) : (
          <>
            <HalfSection label="1st Half" incidents={first} />
            <HalfSection label="2nd Half" incidents={second} />
          </>
        )}
      </div>
    </div>
  )
}

function HalfSection({ label, incidents }) {
  if (!incidents.length) return null
  return (
    <div className="incident-half">
      <h3 className="incident-half-title">{label}</h3>
      {incidents.map(i => <IncidentRow key={i.id} incident={i} />)}
    </div>
  )
}

function IncidentRow({ incident }) {
  const meta   = TYPE_META[incident.type] ?? { label: incident.type, cls: '' }
  const player = [
    incident.player_number && `#${incident.player_number}`,
    incident.player_name,
  ].filter(Boolean).join(' ')

  return (
    <div className="incident-row">
      <span className="incident-minute">{incident.minute}'</span>
      <span className={`incident-badge type-${meta.cls}`}>{meta.label}</span>
      <div className="incident-info">
        <span className="incident-team">{incident.team}</span>
        {player && <span className="incident-player">{player}</span>}
        {incident.offence_description && (
          <span className="incident-offence">{incident.offence_description}</span>
        )}
      </div>
    </div>
  )
}
