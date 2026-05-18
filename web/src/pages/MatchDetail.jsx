import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { pb } from '../lib/pb'
import { kitStyle } from '../lib/colours'

// ── Constants ─────────────────────────────────────────────────────────────────

export const INCIDENT_COLORS = {
  GOAL:        '#22c55e',
  YELLOW_CARD: '#eab308',
  RED_CARD:    '#ef4444',
  SIN_BIN:     '#f97316',
}

const TYPE_LABEL = {
  GOAL:        '⚽ Goal',
  YELLOW_CARD: 'Yellow Card',
  RED_CARD:    'Red Card',
  SIN_BIN:     'Sin Bin',
}

export function parseGpsTrack(raw) {
  if (!raw) return []
  try { return JSON.parse(raw) } catch { return [] }
}

// ── Page component ────────────────────────────────────────────────────────────

export default function MatchDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [match,     setMatch]     = useState(null)
  const [incidents, setIncidents] = useState([])
  const [loading,   setLoading]   = useState(true)
  const [error,     setError]     = useState(null)
  const [tab,       setTab]       = useState('report')  // 'report' | 'timeline'
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
        if (!cancelled) {
          setMatch(m)
          setForm(toForm(m))
          setIncidents(inc.items)
          setLoading(false)
        }
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
    setSaving(true)
    try {
      const updated = await pb.collection('matches').update(id, {
        competition:     form.competition.trim(),
        venue:           form.venue.trim(),
        kickoff_date:    form.kickoffDate,
        kickoff_time:    form.kickoffTime,
        home_team:       form.homeTeam.trim(),
        home_colour:     form.homeColour,
        away_team:       form.awayTeam.trim(),
        away_colour:     form.awayColour,
        age_group:       form.ageGroup,
        half_length:     form.halfLength,
        referee:         form.referee.trim(),
        ar1:             form.ar1.trim(),
        ar2:             form.ar2.trim(),
        fourth_official: form.fourthOfficial.trim(),
        final_score:     form.finalScore.trim(),
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
          <div className="detail-tab-bar">
            <button
              className={`detail-tab-btn${tab === 'report' ? ' active' : ''}`}
              onClick={() => setTab('report')}
            >
              Report
            </button>
            <button
              className={`detail-tab-btn${tab === 'timeline' ? ' active' : ''}`}
              onClick={() => setTab('timeline')}
            >
              Timeline
            </button>
          </div>
          {tab === 'report'
            ? <MatchReport match={match} incidents={incidents} />
            : <MatchTimeline match={match} incidents={incidents} />
          }
        </>
      )}

      {toast && <div className={`toast toast-${toast.type}`}>{toast.msg}</div>}
    </div>
  )
}

// ── Shared match report view ──────────────────────────────────────────────────

export function MatchReport({ match: m, incidents = [] }) {
  const gpsTrack  = parseGpsTrack(m.gps_track)
  const hasGps    = gpsTrack.length > 0 || incidents.some(i => i.latitude && i.longitude)
  const hasStats  = m.total_distance_km || m.average_speed_kmh || m.max_speed_kmh
                 || m.avg_heart_rate   || m.max_heart_rate
  const dateLabel = buildDateLabel(m)

  return (
    <>
      {/* 1. Date and kickoff time — always shown */}
      <div className="rpt-section rpt-datetime">
        {dateLabel || 'Date not set'}
      </div>

      {/* 2. Competition and Age Group — always shown */}
      <div className="rpt-section rpt-competition-row">
        {m.competition
          ? <span className="rpt-competition">{m.competition}</span>
          : <span className="rpt-competition rpt-muted">Competition not set</span>
        }
        {m.age_group   && <span className="badge">{m.age_group}</span>}
        {m.half_length && <span className="rpt-halves">{m.half_length} min halves</span>}
      </div>

      {/* 3+4. Score + per-team events */}
      <ScoreAndEvents match={m} incidents={incidents} />

      {/* 5. Match status */}
      {m.status && m.status !== 'pending' && (
        <div className="rpt-section rpt-status-row">
          <span className={`status-chip status-${m.status}`}>
            {m.status === 'abandoned' ? 'Abandoned' : 'Completed'}
          </span>
        </div>
      )}

      {/* 6. Performance stats */}
      {hasStats && <PerformanceStats match={m} />}

      {/* 7. GPS pitch heatmap */}
      {hasGps && <PitchMapSection gpsTrack={gpsTrack} incidents={incidents} />}

      {/* 8. Match officials — always shown */}
      <div className="rpt-section">
        <div className="rpt-section-label">Match Officials</div>
        <div className="officials-list">
          <OfficialItem role="Referee"      name={m.referee         || '—'} />
          <OfficialItem role="AR1"          name={m.ar1             || '—'} />
          <OfficialItem role="AR2"          name={m.ar2             || '—'} />
          <OfficialItem role="4th Official" name={m.fourth_official || '—'} />
        </div>
      </div>

      {/* 10. Venue — always shown */}
      <div className="rpt-section rpt-venue">
        <span className="rpt-venue-icon">📍</span>
        <span>{m.venue || 'Venue not set'}</span>
      </div>
    </>
  )
}

// ── Score + per-team incidents ────────────────────────────────────────────────

function ScoreAndEvents({ match: m, incidents }) {
  const homeInc = incidents.filter(i => i.team === m.home_team)
  const awayInc = incidents.filter(i => i.team === m.away_team)

  const homeHasEvents = homeInc.length > 0
  const awayHasEvents = awayInc.length > 0

  return (
    <div className="rpt-section rpt-score-section">
      {/* Score row */}
      <div className="rpt-score-row">
        <div className="rpt-team-block">
          {m.home_colour && <span className="kit-dot" style={{ background: m.home_colour }} />}
          <span className="rpt-team-name" style={kitStyle(m.home_colour)}>
            {m.home_team || 'Home'}
          </span>
        </div>
        <span className="rpt-scoreline">{m.final_score || '–'}</span>
        <div className="rpt-team-block rpt-team-block-away">
          <span className="rpt-team-name" style={kitStyle(m.away_colour)}>
            {m.away_team || 'Away'}
          </span>
          {m.away_colour && <span className="kit-dot" style={{ background: m.away_colour }} />}
        </div>
      </div>

      {/* Per-team events — always shown */}
      <div className="rpt-events-grid">
        <TeamEvents incidents={homeInc} />
        <TeamEvents incidents={awayInc} rightAlign />
      </div>
    </div>
  )
}

function TeamEvents({ incidents, rightAlign = false }) {
  const goals = incidents.filter(i => i.type === 'GOAL')
  const ycs   = incidents.filter(i => i.type === 'YELLOW_CARD')
  const rcs   = incidents.filter(i => i.type === 'RED_CARD')
  const sbs   = incidents.filter(i => i.type === 'SIN_BIN')

  if (!incidents.length) return (
    <div className="rpt-no-incidents">No incidents recorded</div>
  )

  return (
    <div className={`rpt-team-events${rightAlign ? ' rpt-team-events-away' : ''}`}>
      {goals.length > 0 && (
        <EventGroup label="Goals" color="#22c55e" events={goals} rightAlign={rightAlign}
          renderLine={i => <GoalLine incident={i} />} />
      )}
      {ycs.length > 0 && (
        <EventGroup label="Yellow Card" color="#eab308" events={ycs} rightAlign={rightAlign}
          renderLine={i => <CardLine incident={i} />} />
      )}
      {rcs.length > 0 && (
        <EventGroup label="Red Card" color="#ef4444" events={rcs} rightAlign={rightAlign}
          renderLine={i => <CardLine incident={i} />} />
      )}
      {sbs.length > 0 && (
        <EventGroup label="Sin Bin" color="#f97316" events={sbs} rightAlign={rightAlign}
          renderLine={i => <CardLine incident={i} />} />
      )}
    </div>
  )
}

function EventGroup({ label, color, events, rightAlign, renderLine }) {
  return (
    <div className="rpt-event-group">
      <div className={`rpt-event-label${rightAlign ? ' rpt-event-label-away' : ''}`}>
        <span className="rpt-event-dot" style={{ background: color }} />
        {label}
      </div>
      {events.map(i => (
        <div key={i.id} className={`rpt-event-line${rightAlign ? ' rpt-event-line-away' : ''}`}>
          {renderLine(i)}
        </div>
      ))}
    </div>
  )
}

function GoalLine({ incident: i }) {
  const scorer = [
    i.player_name,
    i.player_number && `#${i.player_number}`,
  ].filter(Boolean).join(' ')

  return (
    <>
      <span className="rpt-event-min">{i.minute}'</span>
      <span className="rpt-event-player">{scorer || '—'}</span>
      {i.goal_type && <span className="rpt-event-meta">{i.goal_type}</span>}
    </>
  )
}

function CardLine({ incident: i }) {
  return (
    <>
      <span className="rpt-event-min">{i.minute}'</span>
      {i.player_number && <span className="rpt-event-player">#{i.player_number}</span>}
      {i.player_name   && <span className="rpt-event-player">{i.player_name}</span>}
      {i.offence_description && (
        <span className="rpt-event-meta">{i.offence_description}</span>
      )}
    </>
  )
}

// ── Performance stats ─────────────────────────────────────────────────────────

function PerformanceStats({ match: m }) {
  const stats = [
    m.total_distance_km > 0 && { value: `${Number(m.total_distance_km).toFixed(1)} km`,  label: 'Distance' },
    m.average_speed_kmh > 0 && { value: `${Number(m.average_speed_kmh).toFixed(1)} km/h`, label: 'Avg Speed' },
    m.max_speed_kmh     > 0 && { value: `${Number(m.max_speed_kmh).toFixed(1)} km/h`,     label: 'Max Speed' },
    m.avg_heart_rate    > 0 && { value: `${m.avg_heart_rate} bpm`,                        label: 'Avg HR' },
    m.max_heart_rate    > 0 && { value: `${m.max_heart_rate} bpm`,                        label: 'Max HR' },
  ].filter(Boolean)

  if (!stats.length) return null

  return (
    <div className="rpt-section">
      <div className="rpt-section-label">Performance</div>
      <div className="rpt-stats-row">
        {stats.map(s => (
          <div key={s.label} className="rpt-stat-item">
            <div className="rpt-stat-value">{s.value}</div>
            <div className="rpt-stat-label">{s.label}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

// ── GPS pitch heatmap ─────────────────────────────────────────────────────────

function normalizePt(lat, lng, bounds, svgW, svgH) {
  const latRange = bounds.maxLat - bounds.minLat || 0.0001
  const lngRange = bounds.maxLng - bounds.minLng || 0.0001
  return {
    x: ((lng - bounds.minLng) / lngRange) * svgW,
    y: ((bounds.maxLat - lat) / latRange) * svgH,
  }
}

function PitchMapSection({ gpsTrack, incidents }) {
  const [tooltip, setTooltip] = useState(null)
  const SVG_W = 105
  const SVG_H = 68

  const geoInc = incidents.filter(i => i.latitude && i.longitude)
  const allLats = [...gpsTrack.map(p => p.latitude), ...geoInc.map(i => i.latitude)]
  const allLngs = [...gpsTrack.map(p => p.longitude), ...geoInc.map(i => i.longitude)]

  if (allLats.length === 0) return null

  const rawMinLat = Math.min(...allLats), rawMaxLat = Math.max(...allLats)
  const rawMinLng = Math.min(...allLngs), rawMaxLng = Math.max(...allLngs)
  const latPad = (rawMaxLat - rawMinLat) * 0.08 || 0.0002
  const lngPad = (rawMaxLng - rawMinLng) * 0.08 || 0.0002
  const bounds = {
    minLat: rawMinLat - latPad, maxLat: rawMaxLat + latPad,
    minLng: rawMinLng - lngPad, maxLng: rawMaxLng + lngPad,
  }

  const trackPts = gpsTrack
    .map(p => {
      const { x, y } = normalizePt(p.latitude, p.longitude, bounds, SVG_W, SVG_H)
      return `${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(' ')

  return (
    <div className="rpt-section">
      <div className="rpt-section-label">
        GPS Heatmap
        <span className="rpt-map-legend">
          {Object.entries(INCIDENT_COLORS).map(([type, color]) => (
            <span key={type} className="rpt-legend-item">
              <span className="rpt-legend-dot" style={{ background: color }} />
              {TYPE_LABEL[type].replace('⚽ ', '')}
            </span>
          ))}
        </span>
      </div>
      <div className="pitch-map-wrap">
        <svg
          viewBox={`0 0 ${SVG_W} ${SVG_H}`}
          className="pitch-svg"
          onMouseLeave={() => setTooltip(null)}
        >
          <rect x="0" y="0" width={SVG_W} height={SVG_H} fill="#2d5016" />
          <rect x="0.5" y="0.5" width="104" height="67" fill="none" stroke="rgba(255,255,255,.65)" strokeWidth="0.5" />
          <line x1="52.5" y1="0.5" x2="52.5" y2="67.5" stroke="rgba(255,255,255,.65)" strokeWidth="0.5" />
          <circle cx="52.5" cy="34" r="9.15" fill="none" stroke="rgba(255,255,255,.65)" strokeWidth="0.5" />
          <circle cx="52.5" cy="34" r="0.5" fill="rgba(255,255,255,.65)" />
          <rect x="0.5" y="13.84" width="16.5" height="40.32" fill="none" stroke="rgba(255,255,255,.65)" strokeWidth="0.5" />
          <rect x="88"   y="13.84" width="16.5" height="40.32" fill="none" stroke="rgba(255,255,255,.65)" strokeWidth="0.5" />
          <rect x="0.5" y="24.84" width="5.5"  height="18.32" fill="none" stroke="rgba(255,255,255,.65)" strokeWidth="0.5" />
          <rect x="99"  y="24.84" width="5.5"  height="18.32" fill="none" stroke="rgba(255,255,255,.65)" strokeWidth="0.5" />
          <circle cx="11" cy="34" r="0.5" fill="rgba(255,255,255,.65)" />
          <circle cx="94" cy="34" r="0.5" fill="rgba(255,255,255,.65)" />

          {trackPts && (
            <polyline points={trackPts} fill="none"
              stroke="rgba(255,255,255,.3)" strokeWidth="0.6"
              strokeLinejoin="round" strokeLinecap="round" />
          )}

          {geoInc.map(i => {
            const { x, y } = normalizePt(i.latitude, i.longitude, bounds, SVG_W, SVG_H)
            return (
              <circle key={i.id} cx={x} cy={y} r="2.4"
                fill={INCIDENT_COLORS[i.type] ?? '#888'}
                stroke="white" strokeWidth="0.45" opacity="0.93"
                style={{ cursor: 'default' }}
                onMouseEnter={e => setTooltip({
                  x: e.clientX, y: e.clientY,
                  text: `${i.minute}' ${TYPE_LABEL[i.type] ?? i.type}`,
                })}
                onMouseLeave={() => setTooltip(null)}
              />
            )
          })}
        </svg>
        {tooltip && (
          <div className="pitch-tooltip" style={{ left: tooltip.x + 14, top: tooltip.y - 10 }}>
            {tooltip.text}
          </div>
        )}
      </div>
    </div>
  )
}

// ── Small shared components ───────────────────────────────────────────────────

function OfficialItem({ role, name }) {
  return (
    <div className="official-item">
      <span className="official-role">{role}</span>
      <span className="official-name">{name}</span>
    </div>
  )
}

const AEST = { timeZone: 'Australia/Sydney' }

function buildDateLabel(m) {
  const parts = []
  const fmt = { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', ...AEST }
  if (m.kickoff_date) {
    try {
      parts.push(new Date(m.kickoff_date + 'T00:00:00').toLocaleDateString('en-AU', fmt))
    } catch { parts.push(m.kickoff_date) }
  } else if (m.date) {
    try {
      parts.push(new Date(m.date).toLocaleDateString('en-AU', fmt))
    } catch { parts.push(m.date) }
  }
  if (m.kickoff_time) parts.push(`KO ${m.kickoff_time}`)
  return parts.join(' · ') || null
}

// ── Match Timeline ────────────────────────────────────────────────────────────

function EventIcon({ type }) {
  if (type === 'GOAL') {
    return <span className="tl-icon-goal">⚽</span>
  }
  const fill = type === 'YELLOW_CARD' ? '#eab308'
             : type === 'RED_CARD'    ? '#ef4444'
             : type === 'SIN_BIN'     ? '#f97316'
             : '#6b7280'
  return (
    <svg className="tl-icon-card" viewBox="0 0 10 14" width="10" height="14">
      <rect x="0" y="0" width="10" height="14" rx="2" fill={fill} />
    </svg>
  )
}

function TlMarker({ label, sub, minute, isEnd, isAbandoned }) {
  return (
    <div className={`tl-marker${isEnd ? ' tl-marker-end' : ''}${isAbandoned ? ' tl-marker-abandoned' : ''}`}>
      <span className="tl-marker-line" />
      <span className="tl-marker-body">
        <span className="tl-marker-whistle">{isAbandoned ? '🚨' : '◑'}</span>
        <span className="tl-marker-label">{label}</span>
        {sub    && <span className="tl-marker-sub">{sub}</span>}
        {minute != null && <span className="tl-marker-min">{minute}'</span>}
      </span>
      <span className="tl-marker-line" />
    </div>
  )
}

function EventCell({ incident: i, isAway, homeColour, awayColour }) {
  const kitColor = isAway ? (awayColour || '#2563eb') : (homeColour || '#dc2626')

  const playerLine = [
    i.player_number && `#${i.player_number}`,
    i.player_name,
  ].filter(Boolean).join(' ')

  const note = i.type === 'GOAL' ? i.goal_type : i.offence_description

  if (isAway) {
    return (
      <div className="tl-evt tl-evt-away">
        <div className="tl-evt-body">
          {playerLine && <div className="tl-evt-player">{playerLine}</div>}
          {note        && <div className="tl-evt-note">{note}</div>}
        </div>
        <EventIcon type={i.type} />
        <div className="tl-evt-min">
          <span className="kit-dot kit-dot-sm" style={{ background: kitColor }} />
          {i.minute ?? '—'}'
        </div>
      </div>
    )
  }

  return (
    <div className="tl-evt">
      <div className="tl-evt-min">
        {i.minute ?? '—'}'
        <span className="kit-dot kit-dot-sm" style={{ background: kitColor }} />
      </div>
      <EventIcon type={i.type} />
      <div className="tl-evt-body">
        {playerLine && <div className="tl-evt-player">{playerLine}</div>}
        {note        && <div className="tl-evt-note">{note}</div>}
      </div>
    </div>
  )
}

function HalfSection({ incidents, homeTeam, homeColour, awayColour }) {
  const homeEvts = incidents.filter(i => !i.team || i.team === homeTeam)
  const awayEvts = incidents.filter(i => i.team && i.team !== homeTeam)

  // Pair events by minute — same minute → same row
  const allMinutes = [...new Set([
    ...homeEvts.map(i => i.minute ?? -1),
    ...awayEvts.map(i => i.minute ?? -1),
  ])].sort((a, b) => a - b)

  const rows = []
  for (const min of allMinutes) {
    const homeAtMin = homeEvts.filter(i => (i.minute ?? -1) === min)
    const awayAtMin = awayEvts.filter(i => (i.minute ?? -1) === min)
    const count = Math.max(homeAtMin.length, awayAtMin.length)
    for (let idx = 0; idx < count; idx++) {
      rows.push({ home: homeAtMin[idx] || null, away: awayAtMin[idx] || null })
    }
  }
  if (rows.length === 0) rows.push({ home: null, away: null })

  return (
    <div className="tl-half">
      {rows.map((row, idx) => (
        <div key={idx} className="tl-half-row">
          <div className="tl-col tl-col-home">
            {row.home
              ? <EventCell incident={row.home} isAway={false} homeColour={homeColour} awayColour={awayColour} />
              : <div className="tl-half-empty" />}
          </div>
          <div className="tl-center-divider" />
          <div className="tl-col tl-col-away">
            {row.away
              ? <EventCell incident={row.away} isAway={true} homeColour={homeColour} awayColour={awayColour} />
              : <div className="tl-half-empty" />}
          </div>
        </div>
      ))}
    </div>
  )
}

export function MatchTimeline({ match: m, incidents = [] }) {
  const halfLen = m.half_length || 45

  const sorted = [...incidents].sort((a, b) => {
    const hd = (a.half || 1) - (b.half || 1)
    return hd !== 0 ? hd : (a.minute || 0) - (b.minute || 0)
  })

  const byHalf = {}
  for (const inc of sorted) {
    const h = inc.half || 1
    ;(byHalf[h] = byHalf[h] || []).push(inc)
  }

  const hasET = byHalf[3]?.length > 0 || byHalf[4]?.length > 0
  const isAbandoned = m.status === 'abandoned'
  const endMinute = isAbandoned ? null : hasET ? halfLen * 2 + 30 : halfLen * 2

  return (
    <div className="tl-wrap">
      {/* Score header */}
      <div className="tl-score-bar">
        <span className="tl-score-team">
          {m.home_colour && <span className="kit-dot kit-dot-sm" style={{ background: m.home_colour }} />}
          {m.home_team || 'Home'}
        </span>
        <span className="tl-score-num">{m.final_score || '—'}</span>
        <span className="tl-score-team tl-score-team-away">
          {m.away_team || 'Away'}
          {m.away_colour && <span className="kit-dot kit-dot-sm" style={{ background: m.away_colour }} />}
        </span>
      </div>

      {/* Column headers */}
      <div className="tl-col-headers">
        <div className="tl-col-head-home">
          {m.home_colour && <span className="kit-dot kit-dot-sm" style={{ background: m.home_colour }} />}
          {m.home_team || 'Home'}
        </div>
        <div className="tl-col-head-divider" />
        <div className="tl-col-head-away">
          {m.away_team || 'Away'}
          {m.away_colour && <span className="kit-dot kit-dot-sm" style={{ background: m.away_colour }} />}
        </div>
      </div>

      {/* 1st half */}
      <TlMarker label="Kick Off" sub="1st Half" />
      <HalfSection
        incidents={byHalf[1] || []}
        homeTeam={m.home_team}
        homeColour={m.home_colour}
        awayColour={m.away_colour}
      />
      <TlMarker label="Half Time" minute={halfLen} />

      {/* 2nd half */}
      <TlMarker label="Kick Off" sub="2nd Half" />
      <HalfSection
        incidents={byHalf[2] || []}
        homeTeam={m.home_team}
        homeColour={m.home_colour}
        awayColour={m.away_colour}
      />

      {/* Extra time */}
      {hasET && <>
        <TlMarker label="Full Time" minute={halfLen * 2} />
        <TlMarker label="Extra Time" sub="1st Period" />
        <HalfSection
          incidents={byHalf[3] || []}
          homeTeam={m.home_team}
          homeColour={m.home_colour}
          awayColour={m.away_colour}
        />
        <TlMarker label="Half Time" sub="Extra Time" />
        <TlMarker label="Extra Time" sub="2nd Period" />
        <HalfSection
          incidents={byHalf[4] || []}
          homeTeam={m.home_team}
          homeColour={m.home_colour}
          awayColour={m.away_colour}
        />
      </>}

      {/* Final whistle */}
      <TlMarker
        label={isAbandoned ? 'Abandoned' : 'Full Time'}
        minute={endMinute}
        isEnd
        isAbandoned={isAbandoned}
      />

      {incidents.length === 0 && (
        <p className="tl-empty">No incidents recorded for this match.</p>
      )}
    </div>
  )
}

// ── Edit form ─────────────────────────────────────────────────────────────────

function toForm(m) {
  return {
    competition:    m.competition     || '',
    venue:          m.venue           || '',
    kickoffDate:    m.kickoff_date    || '',
    kickoffTime:    m.kickoff_time    || '',
    homeTeam:       m.home_team       || '',
    homeColour:     m.home_colour     || '#dc2626',
    awayTeam:       m.away_team       || '',
    awayColour:     m.away_colour     || '#2563eb',
    ageGroup:       m.age_group       || 'Open / Senior',
    halfLength:     m.half_length     || 45,
    referee:        m.referee         || '',
    ar1:            m.ar1             || '',
    ar2:            m.ar2             || '',
    fourthOfficial: m.fourth_official || '',
    finalScore:     m.final_score     || '',
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
