import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { pb } from '../lib/pb'

const TYPE_META = {
  GOAL:        { label: '⚽ Goal',  cls: 'goal' },
  YELLOW_CARD: { label: 'Yellow',   cls: 'yellow-card' },
  RED_CARD:    { label: 'Red Card', cls: 'red-card' },
  SIN_BIN:     { label: 'Sin Bin',  cls: 'sin-bin' },
}

// Return a CSS colour + text-shadow style for a hex kit colour.
// Very light colours get a dark shadow so they're readable on our dark bg.
function kitStyle(hex) {
  if (!hex || !/^#[0-9a-f]{6}$/i.test(hex)) return {}
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  const lum = (r * 299 + g * 587 + b * 114) / 1000
  return {
    color: hex,
    textShadow: lum > 160 ? '0 0 8px rgba(0,0,0,.95), 0 0 3px rgba(0,0,0,.8)' : undefined,
  }
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
        if (!cancelled) { setMatch(m); setIncidents(inc.items); setLoading(false) }
      })
      .catch(e => { if (!cancelled) { setError(e.message); setLoading(false) } })
    return () => { cancelled = true }
  }, [id])

  if (loading) return <div className="loading">Loading…</div>
  if (error)   return <div className="error-state">⚠️ {error}</div>
  if (!match)  return null

  const halfLen = match.half_length || 45

  // Use half field when present; fall back to minute-based split
  const allHaveHalf = incidents.length > 0 && incidents.every(i => i.half === 1 || i.half === 2)
  const first  = allHaveHalf ? incidents.filter(i => i.half === 1) : incidents.filter(i => i.minute <= halfLen)
  const second = allHaveHalf ? incidents.filter(i => i.half === 2) : incidents.filter(i => i.minute > halfLen)

  const hasOfficials = match.referee || match.ar1 || match.ar2 || match.fourth_official

  // Build a human-readable date/time line
  const dateLine = (() => {
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
      <button className="back-btn" onClick={() => navigate(-1)}>← Back</button>

      {/* ── Match header ── */}
      <div className="match-detail-header">
        {match.competition && <div className="detail-competition">{match.competition}</div>}

        <div className="detail-meta">
          {dateLine && <span>{dateLine}</span>}
          {match.venue && (
            <span className="detail-venue">📍 {match.venue}</span>
          )}
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

      {/* ── Match Officials ── */}
      {hasOfficials && (
        <div className="officials-section">
          <div className="officials-title">Match Officials</div>
          <div className="officials-list">
            {match.referee        && <OfficialItem role="Referee"      name={match.referee} />}
            {match.ar1            && <OfficialItem role="AR1"          name={match.ar1} />}
            {match.ar2            && <OfficialItem role="AR2"          name={match.ar2} />}
            {match.fourth_official && <OfficialItem role="4th Official" name={match.fourth_official} />}
          </div>
        </div>
      )}

      {/* ── Incidents ── */}
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
