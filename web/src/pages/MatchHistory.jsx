import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { pb } from '../lib/pb'

const TABS = ['History', 'Bin']

const AEST = { timeZone: 'Australia/Sydney' }

function formatTime12h(hhmm) {
  if (!hhmm) return ''
  const [h, m] = hhmm.split(':').map(Number)
  if (isNaN(h) || isNaN(m)) return hhmm
  const period = h < 12 ? 'AM' : 'PM'
  const hour = h % 12 || 12
  return `${hour}:${String(m).padStart(2, '0')}${period}`
}

export default function MatchHistory() {
  const [tab,        setTab]        = useState('History')
  const [matches,    setMatches]    = useState([])
  const [cardMap,    setCardMap]    = useState(new Map())
  const [loading,    setLoading]    = useState(true)
  const [error,      setError]      = useState(null)
  const [deleteMode, setDeleteMode] = useState(false)
  const [selected,   setSelected]   = useState(new Set())
  const [busy,       setBusy]       = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)
  const navigate = useNavigate()

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    const isBin = tab === 'Bin'
    const filter = isBin
      ? 'status = "completed" && deleted = true'
      : 'status = "completed" && deleted != true'

    const cardsFetch = isBin
      ? Promise.resolve({ items: [] })
      : pb.collection('incidents').getList(1, 2000, {
          filter: '(type = "YELLOW_CARD" || type = "RED_CARD")',
          requestKey: null,
        }).catch(() => ({ items: [] }))

    Promise.all([
      pb.collection('matches').getList(1, 200, { filter, sort: '-date', requestKey: null }),
      cardsFetch,
    ])
      .then(([matchRes, incRes]) => {
        if (cancelled) return
        const map = new Map()
        for (const inc of incRes.items) {
          const cur = map.get(inc.match_id) || { yc: 0, rc: 0 }
          if (inc.type === 'YELLOW_CARD') cur.yc++
          else if (inc.type === 'RED_CARD') cur.rc++
          map.set(inc.match_id, cur)
        }
        setMatches(matchRes.items)
        setCardMap(map)
        setLoading(false)
      })
      .catch(e => { if (!cancelled) { setError(e.message); setLoading(false) } })
    return () => { cancelled = true }
  }, [tab, refreshKey])

  useEffect(() => {
    setDeleteMode(false)
    setSelected(new Set())
  }, [tab])

  function refresh() { setRefreshKey(k => k + 1) }

  function toggleSelect(id) {
    setSelected(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  async function handleSoftDelete() {
    if (!selected.size) return
    const n = selected.size
    if (!window.confirm(`Move ${n} match${n !== 1 ? 'es' : ''} to Bin?`)) return
    setBusy(true)
    try {
      await Promise.all([...selected].map(id => pb.collection('matches').update(id, { deleted: true })))
      setDeleteMode(false)
      setSelected(new Set())
      refresh()
    } catch (e) {
      alert('Error: ' + e.message)
    } finally {
      setBusy(false)
    }
  }

  async function handleRestore(id) {
    setBusy(true)
    try {
      await pb.collection('matches').update(id, { deleted: false })
      refresh()
    } catch (e) {
      alert('Error: ' + e.message)
    } finally {
      setBusy(false)
    }
  }

  async function handlePermDelete(m) {
    const label = [m.home_team, m.away_team].filter(Boolean).join(' v ')
    if (!window.confirm(`Permanently delete "${label}" and all its incidents?\n\nThis cannot be undone.`)) return
    setBusy(true)
    try {
      await pb.collection('matches').delete(m.id)
      refresh()
    } catch (e) {
      alert('Error: ' + e.message)
    } finally {
      setBusy(false)
    }
  }

  const isHistory = tab === 'History'
  const isBin     = tab === 'Bin'

  return (
    <div className="page">
      <div className="mh-header">
        <h1 className="page-title" style={{ margin: 0 }}>Match History</h1>
        {isHistory && !loading && matches.length > 0 && (
          <button
            className={`mh-edit-btn${deleteMode ? ' active' : ''}`}
            onClick={() => { setDeleteMode(d => !d); setSelected(new Set()) }}
          >
            {deleteMode ? 'Done' : 'Edit'}
          </button>
        )}
      </div>

      <div className="tr-tabs" style={{ marginBottom: 14 }}>
        {TABS.map(t => (
          <button key={t} className={`tr-tab${tab === t ? ' tr-tab-active' : ''}`} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </div>

      {loading && <div className="loading">Loading matches…</div>}

      {error && (
        <div className="error-state">
          <div className="empty-icon">⚠️</div>
          <div>Could not connect to PocketBase</div>
          <div className="empty-hint">{error}</div>
          <div className="empty-hint" style={{ marginTop: 12 }}>
            Check the server URL in ⚙ Settings and make sure PocketBase is running.
          </div>
        </div>
      )}

      {!loading && !error && isHistory && (
        matches.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">📋</div>
            <div>No matches yet</div>
            <div className="empty-hint">Completed matches synced from the watch will appear here.</div>
          </div>
        ) : (
          <>
            {deleteMode && (
              <div className="mh-delete-bar">
                <button
                  className="btn-danger-sm"
                  disabled={selected.size === 0 || busy}
                  onClick={handleSoftDelete}
                >
                  {selected.size > 0 ? `Move to Bin (${selected.size})` : 'Move to Bin'}
                </button>
              </div>
            )}
            <div className="match-list">
              {matches.map(m =>
                deleteMode ? (
                  <div key={m.id} className="mc2-wrap">
                    <div className="mc2-check-area" onClick={() => toggleSelect(m.id)}>
                      <input
                        type="checkbox"
                        className="mh-checkbox"
                        checked={selected.has(m.id)}
                        onChange={() => toggleSelect(m.id)}
                        onClick={e => e.stopPropagation()}
                      />
                    </div>
                    <MatchCard
                      match={m}
                      cards={cardMap.get(m.id) || { yc: 0, rc: 0 }}
                      onClick={() => toggleSelect(m.id)}
                    />
                  </div>
                ) : (
                  <MatchCard
                    key={m.id}
                    match={m}
                    cards={cardMap.get(m.id) || { yc: 0, rc: 0 }}
                    onClick={() => navigate(`/match/${m.id}`)}
                  />
                )
              )}
            </div>
          </>
        )
      )}

      {!loading && !error && isBin && (
        matches.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">🗑️</div>
            <div>Bin is empty</div>
            <div className="empty-hint">Soft-deleted matches appear here and can be restored.</div>
          </div>
        ) : (
          <div className="match-list">
            {matches.map(m => (
              <TrashCard
                key={m.id}
                match={m}
                busy={busy}
                onRestore={() => handleRestore(m.id)}
                onDelete={() => handlePermDelete(m)}
              />
            ))}
          </div>
        )
      )}
    </div>
  )
}

function TrashCard({ match: m, busy, onRestore, onDelete }) {
  let day = '—', mon = ''
  const rawDate = m.kickoff_date || m.date
  if (rawDate) {
    try {
      const d = new Date(rawDate.length <= 10 ? rawDate + 'T00:00:00' : rawDate)
      if (!isNaN(d)) {
        day = d.toLocaleDateString('en-AU', { day: 'numeric', ...AEST })
        mon = d.toLocaleDateString('en-AU', { month: 'short', ...AEST }).toUpperCase()
      }
    } catch { /* keep defaults */ }
  }

  return (
    <div className="trash-card">
      <div className="mc2-date">
        <span className="mc2-day">{day}</span>
        {mon && <span className="mc2-mon">{mon}</span>}
      </div>

      <div className="mc2-mid">
        <div className="mc2-row">
          <div className="mc2-home">
            {m.home_colour && <span className="kit-dot kit-dot-sm" style={{ background: m.home_colour }} />}
            <span className="mc2-team">{m.home_team || 'Home'}</span>
          </div>
          <div className="mc2-centre">
            {m.kickoff_time && <div className="mc2-kotime">{formatTime12h(m.kickoff_time)}</div>}
            <div className="mc2-score">{m.final_score || '—'}</div>
          </div>
          <div className="mc2-away">
            <span className="mc2-team">{m.away_team || 'Away'}</span>
            {m.away_colour && <span className="kit-dot kit-dot-sm" style={{ background: m.away_colour }} />}
          </div>
        </div>
      </div>

      <div className="trash-card-actions">
        <button className="trash-restore-btn" disabled={busy} onClick={onRestore}>Restore</button>
        <button className="trash-delete-btn"  disabled={busy} onClick={onDelete}>Delete</button>
      </div>
    </div>
  )
}

function MatchCard({ match: m, cards, onClick }) {
  let day = '—', mon = ''
  const rawDate = m.kickoff_date || m.date
  if (rawDate) {
    try {
      const d = new Date(rawDate.length <= 10 ? rawDate + 'T00:00:00' : rawDate)
      if (!isNaN(d)) {
        day = d.toLocaleDateString('en-AU', { day: 'numeric', ...AEST })
        mon = d.toLocaleDateString('en-AU', { month: 'short', ...AEST }).toUpperCase()
      }
    } catch { /* keep defaults */ }
  }

  const hasHR = m.avg_heart_rate > 0

  return (
    <button className="mc2" onClick={onClick}>
      <div className="mc2-date">
        <span className="mc2-day">{day}</span>
        {mon && <span className="mc2-mon">{mon}</span>}
      </div>

      <div className="mc2-mid">
        <div className="mc2-row">
          <div className="mc2-home">
            {m.home_colour && (
              <span className="kit-dot kit-dot-sm" style={{ background: m.home_colour }} />
            )}
            <span className="mc2-team">{m.home_team || 'Home'}</span>
          </div>

          <div className="mc2-centre">
            {m.kickoff_time && <div className="mc2-kotime">{formatTime12h(m.kickoff_time)}</div>}
            <div className="mc2-score">{m.final_score || '—'}</div>
            {m.venue && <div className="mc2-venue">{m.venue}</div>}
          </div>

          <div className="mc2-away">
            <span className="mc2-team">{m.away_team || 'Away'}</span>
            {m.away_colour && (
              <span className="kit-dot kit-dot-sm" style={{ background: m.away_colour }} />
            )}
          </div>
        </div>

        {hasHR && (
          <div className="mc2-hr">
            ♥ {m.avg_heart_rate} avg{m.max_heart_rate > 0 ? ` · ${m.max_heart_rate} max` : ''} bpm
          </div>
        )}
      </div>

      <div className="mc2-badges">
        {cards.yc > 0 && <span className="mc2-yc">{cards.yc}</span>}
        {cards.rc > 0 && <span className="mc2-rc">{cards.rc}</span>}
      </div>
    </button>
  )
}
