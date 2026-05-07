import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { pb } from '../lib/pb'
import { kitStyle } from '../lib/colours'

// "Saturday 16 May" — no year unless different from current year
function formatDayHeader(dateStr) {
  if (!dateStr) return 'Unknown date'
  try {
    const d = new Date(dateStr + 'T00:00:00')
    const opts = { weekday: 'long', day: 'numeric', month: 'long' }
    if (d.getFullYear() !== new Date().getFullYear()) opts.year = 'numeric'
    return d.toLocaleDateString('en-AU', opts)
  } catch {
    return dateStr
  }
}

// Returns a countdown string relative to now.
function countdown(dateStr, timeStr) {
  if (!dateStr) return null
  const koTime = timeStr || '00:00'
  let koDate
  try {
    koDate = new Date(`${dateStr}T${koTime}`)
    if (isNaN(koDate)) return null
  } catch {
    return null
  }
  const now = new Date()
  const diffMs = koDate - now
  const diffMins = Math.round(diffMs / 60000)

  if (diffMins < -120) return null               // more than 2 hrs past — hide
  if (diffMins < 0)    return { label: 'Kick-off passed', cls: 'cd-past' }
  if (diffMins < 60)   return { label: `${diffMins} min away`, cls: 'cd-urgent' }
  if (diffMins < 120)  return { label: `${Math.round(diffMins / 60)} hr away`, cls: 'cd-urgent' }
  if (diffMins < 240)  return { label: `${Math.round(diffMins / 60)} hrs away`, cls: 'cd-soon' }

  // Check if tomorrow
  const todayStr = now.toISOString().slice(0, 10)
  const tmrw = new Date(now); tmrw.setDate(tmrw.getDate() + 1)
  const tmrwStr = tmrw.toISOString().slice(0, 10)
  if (dateStr === tmrwStr) return { label: 'Tomorrow', cls: 'cd-soon' }
  if (dateStr === todayStr) return { label: 'Today', cls: 'cd-soon' }

  const diffDays = Math.ceil(diffMs / 86400000)
  if (diffDays <= 7) return { label: `${diffDays} days away`, cls: 'cd-future' }
  return null
}

export default function Fixtures() {
  const [fixtures, setFixtures] = useState([])
  const [loading,  setLoading]  = useState(true)
  const [error,    setError]    = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    pb.collection('match_setups')
      .getList(1, 500, { requestKey: null })
      .then(r => {
        const pending = r.items
          .filter(f => !f.status || f.status === 'pending')
          .sort((a, b) => {
            const da = (a.kickoff_date || '9999-12-31') + 'T' + (a.kickoff_time || '00:00')
            const db = (b.kickoff_date || '9999-12-31') + 'T' + (b.kickoff_time || '00:00')
            return da.localeCompare(db)
          })
        setFixtures(pending)
        setLoading(false)
      })
      .catch(e => { setError(e.message); setLoading(false) })
  }, [])

  if (loading) return <div className="loading">Loading…</div>
  if (error)   return <div className="error-state">⚠️ {error}</div>

  if (fixtures.length === 0) {
    return (
      <div className="page">
        <h1 className="page-title">Fixtures</h1>
        <p className="empty-state">No upcoming fixtures. Add one via Match Setup.</p>
      </div>
    )
  }

  // Group by kickoff_date
  const groups = []
  const seen = {}
  fixtures.forEach(f => {
    const key = f.kickoff_date || '__nodate__'
    if (!seen[key]) { seen[key] = true; groups.push({ key, items: [] }) }
    groups[groups.length - 1].items.push(f)
  })

  return (
    <div className="page">
      <h1 className="page-title">Fixtures</h1>
      {groups.map(g => (
        <div key={g.key} className="fixture-day-group">
          <div className="fixture-day-header">{formatDayHeader(g.key === '__nodate__' ? null : g.key)}</div>
          {g.items.map(f => (
            <FixtureCard key={f.id} fixture={f} onClick={() => navigate(`/fixture/${f.id}`)} />
          ))}
        </div>
      ))}
    </div>
  )
}

function FixtureCard({ fixture: f, onClick }) {
  const cd = countdown(f.kickoff_date, f.kickoff_time)

  return (
    <div className="fixture-card" onClick={onClick} role="button" tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && onClick()}>
      <div className="fixture-card-top">
        {f.competition && <span className="fixture-competition">{f.competition}</span>}
        {cd && <span className={`fixture-countdown ${cd.cls}`}>{cd.label}</span>}
        {f.kickoff_time && <span className="fixture-kotime">{f.kickoff_time}</span>}
      </div>
      <div className="fixture-teams">
        <div className="fixture-team-block">
          {f.home_colour && <span className="kit-dot" style={{ background: f.home_colour }} />}
          <span className="fixture-team-name" style={kitStyle(f.home_colour)}>
            {f.home_team || 'Home'}
          </span>
        </div>
        <span className="fixture-vs">vs</span>
        <div className="fixture-team-block fixture-team-block-away">
          <span className="fixture-team-name" style={kitStyle(f.away_colour)}>
            {f.away_team || 'Away'}
          </span>
          {f.away_colour && <span className="kit-dot" style={{ background: f.away_colour }} />}
        </div>
      </div>
      <div className="fixture-card-meta">
        {f.venue    && <span className="fixture-venue">📍 {f.venue}</span>}
        {f.age_group && <span className="badge">{f.age_group}</span>}
        {f.referee  && <span className="fixture-official">🟡 {f.referee}</span>}
      </div>
    </div>
  )
}
