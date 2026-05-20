import { useState, useEffect } from 'react'
import { pb } from '../lib/pb'
import { kitStyle } from '../lib/colours'

const SIN_BIN_MINUTES = 10

const INCIDENT_COLORS = {
  GOAL:        '#22c55e',
  YELLOW_CARD: '#eab308',
  RED_CARD:    '#ef4444',
  SIN_BIN:     '#f97316',
}

const INCIDENT_LABEL = {
  GOAL:        'Goal',
  YELLOW_CARD: 'Yellow Card',
  RED_CARD:    'Red Card',
  SIN_BIN:     'Sin Bin',
}

const HALF_LABEL = { 1: '1st Half', 2: '2nd Half', 3: 'Extra Time 1', 4: 'Extra Time 2' }

function parseKickoff(match) {
  if (!match.kickoff_date || !match.kickoff_time) return null
  try { return new Date(`${match.kickoff_date}T${match.kickoff_time}:00`) } catch { return null }
}

function formatClock(totalSeconds) {
  const s = Math.max(0, totalSeconds)
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

function formatRemaining(ms) {
  const s = Math.max(0, Math.floor(ms / 1000))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

function currentHalfLabel(incidents) {
  if (!incidents.length) return '1st Half'
  const max = Math.max(...incidents.map(i => i.half || 1))
  return HALF_LABEL[max] ?? '1st Half'
}

function IncidentIcon({ type }) {
  if (type === 'GOAL') return <span style={{ fontSize: '1.05rem', lineHeight: 1 }}>⚽</span>
  const fill = INCIDENT_COLORS[type] ?? '#6b7280'
  return (
    <svg viewBox="0 0 10 14" width="10" height="14" style={{ display: 'block', flexShrink: 0 }}>
      <rect x="0" y="0" width="10" height="14" rx="2" fill={fill} />
    </svg>
  )
}

export default function LiveMatch() {
  const [match,     setMatch]     = useState(null)
  const [incidents, setIncidents] = useState([])
  const [loading,   setLoading]   = useState(true)
  const [now,       setNow]       = useState(Date.now())

  // Poll PocketBase every 5 seconds
  useEffect(() => {
    let cancelled = false

    async function fetchData() {
      try {
        const result = await pb.collection('matches').getList(1, 1, {
          filter: 'status = "active"',
          requestKey: null,
        })
        if (cancelled) return
        const m = result.items[0] ?? null
        setMatch(m)
        if (m) {
          const inc = await pb.collection('incidents').getList(1, 200, {
            filter: `match_id = "${m.id}"`,
            sort: 'half,minute',
            requestKey: null,
          })
          if (!cancelled) setIncidents(inc.items)
        } else {
          if (!cancelled) setIncidents([])
        }
      } catch (_) {
        // keep stale data on connection error
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    fetchData()
    const poll = setInterval(fetchData, 5000)
    return () => { cancelled = true; clearInterval(poll) }
  }, [])

  // Tick every second for clock and sin bin countdowns
  useEffect(() => {
    const tick = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(tick)
  }, [])

  if (loading) return <div className="loading">Loading…</div>

  if (!match) {
    return (
      <div className="page">
        <div className="live-no-match">
          <div className="live-no-match-icon">◑</div>
          <div className="live-no-match-title">No Active Match</div>
          <div className="live-no-match-sub">Start a match on the watch to see it here.</div>
        </div>
      </div>
    )
  }

  const kickoff    = parseKickoff(match)
  const elapsedSec = kickoff ? Math.max(0, Math.floor((now - kickoff.getTime()) / 1000)) : null
  const halfLabel  = currentHalfLabel(incidents)
  const homeGoals  = incidents.filter(i => i.type === 'GOAL' && i.team === match.home_team).length
  const awayGoals  = incidents.filter(i => i.type === 'GOAL' && i.team === match.away_team).length

  const activeSinBins = incidents
    .filter(i => i.type === 'SIN_BIN')
    .map(i => {
      const remainingMs = Math.max(0, SIN_BIN_MINUTES * 60_000 - (now - new Date(i.created).getTime()))
      return { ...i, remainingMs }
    })
    .filter(i => i.remainingMs > 0)

  const sortedIncidents = [...incidents].sort((a, b) => {
    const hd = (a.half || 1) - (b.half || 1)
    return hd !== 0 ? hd : (a.minute ?? 0) - (b.minute ?? 0)
  })

  return (
    <div className="page">
      {/* Header */}
      <div className="live-header">
        <span className="live-badge">
          <span className="live-dot" />
          LIVE
        </span>
        <span className="live-half-label">{halfLabel}</span>
        {match.competition && <span className="live-competition">{match.competition}</span>}
        {match.age_group   && <span className="badge">{match.age_group}</span>}
      </div>

      {/* Clock */}
      {elapsedSec !== null && (
        <div className="live-clock-wrap">
          <div className="live-clock">{formatClock(elapsedSec)}</div>
          {match.half_length && (
            <div className="live-clock-sub">{match.half_length} min halves</div>
          )}
        </div>
      )}

      {/* Score */}
      <div className="rpt-section live-score-card">
        <div className="live-score-row">
          <div className="live-team-block">
            {match.home_colour && <span className="kit-dot" style={{ background: match.home_colour }} />}
            <span className="live-team-name" style={kitStyle(match.home_colour)}>
              {match.home_team || 'Home'}
            </span>
          </div>
          <div className="live-score-centre">
            <span className="live-score-num">{homeGoals}</span>
            <span className="live-score-sep">–</span>
            <span className="live-score-num">{awayGoals}</span>
          </div>
          <div className="live-team-block live-team-block-away">
            <span className="live-team-name" style={kitStyle(match.away_colour)}>
              {match.away_team || 'Away'}
            </span>
            {match.away_colour && <span className="kit-dot" style={{ background: match.away_colour }} />}
          </div>
        </div>
      </div>

      {/* Active sin bins */}
      {activeSinBins.length > 0 && (
        <div className="rpt-section">
          <div className="rpt-section-label" style={{ color: '#f97316' }}>Sin Bins Active</div>
          {activeSinBins.map(i => {
            const isHome = i.team === match.home_team
            const dotColor = isHome ? (match.home_colour || '#dc2626') : (match.away_colour || '#2563eb')
            const player = [i.player_number && `#${i.player_number}`, i.player_name].filter(Boolean).join(' ')
            return (
              <div key={i.id} className="live-sinbin-row">
                <span className="kit-dot kit-dot-sm" style={{ background: dotColor }} />
                <span className="live-sinbin-team">{i.team || '—'}</span>
                {player && <span className="live-sinbin-player">{player}</span>}
                <span className="live-sinbin-timer">{formatRemaining(i.remainingMs)}</span>
              </div>
            )
          })}
        </div>
      )}

      {/* Distance */}
      {match.total_distance_km > 0 && (
        <div className="rpt-section live-dist-row">
          <span className="live-dist-value">{Number(match.total_distance_km).toFixed(1)}</span>
          <span className="live-dist-unit">km</span>
          <span className="live-dist-label">Distance</span>
        </div>
      )}

      {/* Incidents */}
      <div className="rpt-section">
        <div className="rpt-section-label">Incidents</div>
        {sortedIncidents.length === 0 ? (
          <div className="live-empty-incidents">No incidents recorded yet.</div>
        ) : (
          <div className="live-inc-list">
            {sortedIncidents.map((i, idx) => {
              const isHome  = i.team === match.home_team
              const prevInc = idx > 0 ? sortedIncidents[idx - 1] : null
              const showSep = prevInc && (i.half || 1) !== (prevInc.half || 1)
              const player  = [i.player_number && `#${i.player_number}`, i.player_name].filter(Boolean).join(' ')
              const note    = i.type === 'GOAL' ? i.goal_type : i.offence_description
              const dotColor = isHome ? (match.home_colour || '#dc2626') : (match.away_colour || '#2563eb')

              return (
                <div key={i.id}>
                  {showSep && (
                    <div className="live-half-sep">
                      {HALF_LABEL[i.half || 1] ?? `Half ${i.half}`}
                    </div>
                  )}
                  <div className={`live-inc-row${isHome ? '' : ' live-inc-row-away'}`}>
                    <span className="kit-dot kit-dot-sm" style={{ background: dotColor }} />
                    <span className="live-inc-min">{i.minute ?? '—'}'</span>
                    <span className="live-inc-icon"><IncidentIcon type={i.type} /></span>
                    <span className="live-inc-type">{INCIDENT_LABEL[i.type] ?? i.type}</span>
                    {player && <span className="live-inc-player">{player}</span>}
                    {note   && <span className="live-inc-note">{note}</span>}
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
