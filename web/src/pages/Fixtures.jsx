import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { pb } from '../lib/pb'
import { kitStyle } from '../lib/colours'
import { syncCalendar, getLastSync, formatSyncTime } from '../lib/calendarSync'

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

  if (diffMins < -120) return null
  if (diffMins < 0)    return { label: 'Kick-off passed', cls: 'cd-past' }
  if (diffMins < 60)   return { label: `${diffMins} min away`, cls: 'cd-urgent' }
  if (diffMins < 120)  return { label: `${Math.round(diffMins / 60)} hr away`, cls: 'cd-urgent' }
  if (diffMins < 240)  return { label: `${Math.round(diffMins / 60)} hrs away`, cls: 'cd-soon' }

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
  const [fixtures,      setFixtures]      = useState([])
  const [loadedSetups,  setLoadedSetups]  = useState([])
  const [loading,       setLoading]       = useState(true)
  const [error,         setError]         = useState(null)
  const [syncing,       setSyncing]       = useState(false)
  const [syncMsg,       setSyncMsg]       = useState(null)   // {text, ok}
  const [lastSync,      setLastSync]      = useState(() => getLastSync())
  const [restoring,     setRestoring]     = useState(null)   // id being restored
  const navigate = useNavigate()

  const loadFixtures = useCallback(() => {
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
        const loaded = r.items
          .filter(f => f.status === 'loaded')
          .sort((a, b) => b.updated?.localeCompare(a.updated ?? '') ?? 0)
        setFixtures(pending)
        setLoadedSetups(loaded)
        setLoading(false)
      })
      .catch(e => { setError(e.message); setLoading(false) })
  }, [])

  async function handleRestore(id) {
    setRestoring(id)
    try {
      await pb.collection('match_setups').update(id, { status: 'pending' })
      loadFixtures()
    } finally {
      setRestoring(null)
    }
  }

  useEffect(() => { loadFixtures() }, [loadFixtures])

  async function handleSync() {
    setSyncing(true)
    setSyncMsg(null)
    try {
      const result = await syncCalendar()
      const now = new Date()
      setLastSync(now)
      const { created, updated } = result
      const parts = []
      if (created) parts.push(`${created} added`)
      if (updated) parts.push(`${updated} updated`)
      setSyncMsg({ text: parts.length ? parts.join(', ') : 'Up to date', ok: true })
      if (created || updated) loadFixtures()
    } catch (err) {
      setSyncMsg({ text: err.message, ok: false })
    } finally {
      setSyncing(false)
    }
  }

  if (loading) return <div className="loading">Loading…</div>
  if (error)   return <div className="error-state">⚠️ {error}</div>

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
      <div className="fixtures-header">
        <h1 className="page-title">Fixtures</h1>
        <div className="sync-bar">
          {syncMsg && (
            <span className={`sync-status ${syncMsg.ok ? 'sync-status-ok' : 'sync-status-err'}`}>
              {syncMsg.text}
            </span>
          )}
          {!syncMsg && lastSync && (
            <span className="sync-status">Synced {formatSyncTime(lastSync)}</span>
          )}
          <button
            className="btn-sync"
            onClick={handleSync}
            disabled={syncing}
            title="Import fixtures from Google Calendar"
          >
            {syncing ? 'Syncing…' : 'Sync Calendar'}
          </button>
        </div>
      </div>

      {fixtures.length === 0 ? (
        <p className="empty-state">No upcoming fixtures. Add one via Match Setup or sync your calendar.</p>
      ) : (
        groups.map(g => (
          <div key={g.key} className="fixture-day-group">
            <div className="fixture-day-header">
              {formatDayHeader(g.key === '__nodate__' ? null : g.key)}
            </div>
            {g.items.map(f => (
              <FixtureCard key={f.id} fixture={f} onClick={() => navigate(`/fixture/${f.id}`)} />
            ))}
          </div>
        ))
      )}

      {loadedSetups.length > 0 && (
        <div className="loaded-section">
          <div className="loaded-section-header">Recently Loaded on Watch</div>
          {loadedSetups.map(f => (
            <LoadedFixtureCard
              key={f.id}
              fixture={f}
              restoring={restoring === f.id}
              onRestore={() => handleRestore(f.id)}
              onEdit={() => navigate(`/fixture/${f.id}`)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function LoadedFixtureCard({ fixture: f, restoring, onRestore, onEdit }) {
  return (
    <div className="loaded-fixture-card">
      <div className="loaded-fixture-info">
        <div className="loaded-fixture-teams">
          {f.home_colour && <span className="kit-dot" style={{ background: f.home_colour }} />}
          <span className="loaded-fixture-team">{f.home_team || 'Home'}</span>
          <span className="loaded-fixture-vs">vs</span>
          <span className="loaded-fixture-team">{f.away_team || 'Away'}</span>
          {f.away_colour && <span className="kit-dot" style={{ background: f.away_colour }} />}
        </div>
        {(f.competition || f.age_group) && (
          <div className="loaded-fixture-meta">
            {f.competition && <span>{f.competition}</span>}
            {f.age_group   && <span className="badge">{f.age_group}</span>}
          </div>
        )}
      </div>
      <div className="loaded-fixture-actions">
        <button className="btn-restore" onClick={onRestore} disabled={restoring}>
          {restoring ? 'Restoring…' : 'Restore'}
        </button>
        <button className="btn-edit" onClick={onEdit}>Edit</button>
      </div>
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
