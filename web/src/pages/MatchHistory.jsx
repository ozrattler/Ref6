import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { pb } from '../lib/pb'

export default function MatchHistory() {
  const [matches,  setMatches]  = useState([])
  const [cardMap,  setCardMap]  = useState(new Map())
  const [loading,  setLoading]  = useState(true)
  const [error,    setError]    = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    let cancelled = false
    Promise.all([
      pb.collection('matches').getList(1, 200, {
        filter: 'status = "completed"',
        sort: '-date',
        requestKey: null,
      }),
      pb.collection('incidents').getList(1, 2000, {
        filter: '(type = "YELLOW_CARD" || type = "RED_CARD")',
        requestKey: null,
      }).catch(() => ({ items: [] })),
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
  }, [])

  if (loading) return <div className="loading">Loading matches…</div>

  if (error) return (
    <div className="error-state">
      <div className="empty-icon">⚠️</div>
      <div>Could not connect to PocketBase</div>
      <div className="empty-hint">{error}</div>
      <div className="empty-hint" style={{ marginTop: 12 }}>
        Check the server URL in ⚙ Settings and make sure PocketBase is running.
      </div>
    </div>
  )

  if (!matches.length) return (
    <div className="empty-state">
      <div className="empty-icon">📋</div>
      <div>No matches yet</div>
      <div className="empty-hint">Completed matches synced from the watch will appear here.</div>
    </div>
  )

  return (
    <div className="page">
      <h1 className="page-title">Match History</h1>
      <div className="match-list">
        {matches.map(m => (
          <MatchCard
            key={m.id}
            match={m}
            cards={cardMap.get(m.id) || { yc: 0, rc: 0 }}
            onClick={() => navigate(`/match/${m.id}`)}
          />
        ))}
      </div>
    </div>
  )
}

const AEST = { timeZone: 'Australia/Sydney' }

function formatTime12h(hhmm) {
  if (!hhmm) return ''
  const [h, m] = hhmm.split(':').map(Number)
  if (isNaN(h) || isNaN(m)) return hhmm
  const period = h < 12 ? 'AM' : 'PM'
  const hour = h % 12 || 12
  return `${hour}:${String(m).padStart(2, '0')}${period}`
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
