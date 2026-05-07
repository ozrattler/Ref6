import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { pb } from '../lib/pb'

export default function MatchHistory() {
  const [matches, setMatches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError]   = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    let cancelled = false
    pb.collection('matches')
      .getList(1, 100, { filter: 'status = "completed"', sort: '-date' })
      .then(r => { if (!cancelled) { setMatches(r.items); setLoading(false) } })
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
          <MatchCard key={m.id} match={m} onClick={() => navigate(`/match/${m.id}`)} />
        ))}
      </div>
    </div>
  )
}

function MatchCard({ match, onClick }) {
  const date = match.date
    ? new Date(match.date).toLocaleDateString('en-AU', {
        day: 'numeric', month: 'short', year: 'numeric',
      })
    : '—'

  return (
    <button className="match-card" onClick={onClick}>
      <div className="mc-top">
        <span className="mc-date">{date}</span>
        {match.age_group && <span className="badge">{match.age_group}</span>}
      </div>
      <div className="mc-competition">{match.competition || <>&nbsp;</>}</div>
      <div className="mc-score-row">
        <span className="mc-team home">{match.home_team || 'Home'}</span>
        <span className="mc-scoreline">{match.final_score || '—'}</span>
        <span className="mc-team away">{match.away_team || 'Away'}</span>
      </div>
      <div className="mc-footer">
        <span className="mc-detail">
          {match.half_length ? `${match.half_length} min halves` : ''}
        </span>
        <span className="mc-chevron">›</span>
      </div>
    </button>
  )
}
