import { useState, useEffect } from 'react'
import { pb } from '../lib/pb'

const TABS = ['Overview', 'Misconduct', 'Distance', 'Speed']

function parseScore(s) {
  const m = s?.match(/^(\d+)\s*[-–]\s*(\d+)$/)
  return m ? { home: +m[1], away: +m[2] } : null
}

function fmt1(n) { return Number(n).toFixed(1) }
function fmtPct(n, total) { return total > 0 ? `${Math.round(n / total * 100)}%` : '0%' }

function StatCard({ value, label, sub }) {
  return (
    <div className="tr-stat">
      <div className="tr-stat-value">{value}</div>
      <div className="tr-stat-label">{label}</div>
      {sub && <div className="tr-stat-sub">{sub}</div>}
    </div>
  )
}

function BarRow({ label, value, total, color, showPct = true }) {
  const pct = total > 0 ? value / total * 100 : 0
  return (
    <div className="tr-bar-row">
      <div className="tr-bar-label">{label}</div>
      <div className="tr-bar-track">
        <div className="tr-bar-fill" style={{ width: `${pct}%`, background: color }} />
      </div>
      <div className="tr-bar-count">
        {value}
        {showPct && total > 0 && <span className="tr-bar-pct"> {fmtPct(value, total)}</span>}
      </div>
    </div>
  )
}

export default function Trends() {
  const [tab,      setTab]      = useState('Overview')
  const [matches,  setMatches]  = useState(null)
  const [incidents,setIncidents]= useState(null)
  const [loading,  setLoading]  = useState(true)
  const [error,    setError]    = useState(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([
      pb.collection('matches').getList(1, 500, {
        filter: 'status = "completed"',
        sort: '-date',
        requestKey: null,
      }),
      pb.collection('incidents').getList(1, 5000, {
        fields: 'id,match_id,type',
        requestKey: null,
      }).catch(() => ({ items: [] })),
    ])
      .then(([mRes, iRes]) => {
        if (!cancelled) {
          setMatches(mRes.items)
          setIncidents(iRes.items)
          setLoading(false)
        }
      })
      .catch(e => { if (!cancelled) { setError(e.message); setLoading(false) } })
    return () => { cancelled = true }
  }, [])

  if (loading) return <div className="loading">Loading…</div>
  if (error)   return <div className="error-state">⚠️ {error}</div>

  const n = matches.length

  // ── Goals ────────────────────────────────────────────────────────────────────
  const scores = matches.map(m => parseScore(m.final_score)).filter(Boolean)
  const homeGoals = scores.reduce((s, r) => s + r.home, 0)
  const awayGoals = scores.reduce((s, r) => s + r.away, 0)
  const totalGoals = homeGoals + awayGoals
  const avgGoals = scores.length ? (totalGoals / scores.length) : 0

  // ── Results ──────────────────────────────────────────────────────────────────
  const homeWins  = scores.filter(s => s.home > s.away).length
  const draws     = scores.filter(s => s.home === s.away).length
  const awayWins  = scores.filter(s => s.away > s.home).length
  const resultsN  = scores.length

  // ── Avg per month ─────────────────────────────────────────────────────────────
  let avgPerMonth = '—'
  if (n > 0) {
    const dates = matches.map(m => new Date(m.date)).filter(d => !isNaN(d))
    if (dates.length >= 2) {
      const span = (Math.max(...dates) - Math.min(...dates)) / (1000 * 60 * 60 * 24 * 30.44)
      avgPerMonth = fmt1(n / Math.max(span, 1))
    } else {
      avgPerMonth = n
    }
  }

  // ── Incidents ────────────────────────────────────────────────────────────────
  const yc = incidents.filter(i => i.type === 'YELLOW_CARD').length
  const rc = incidents.filter(i => i.type === 'RED_CARD').length
  const sb = incidents.filter(i => i.type === 'SIN_BIN').length
  const avgYc = n ? fmt1(yc / n) : '—'
  const avgRc = n ? fmt1(rc / n) : '—'
  const avgSb = n ? fmt1(sb / n) : '—'

  // ── Distance ─────────────────────────────────────────────────────────────────
  const dists = matches.map(m => Number(m.total_distance_km)).filter(d => d > 0)
  const totalDist = dists.reduce((s, d) => s + d, 0)
  const avgDist   = dists.length ? totalDist / dists.length : 0

  // ── Speed ────────────────────────────────────────────────────────────────────
  const avgSpeeds = matches.map(m => Number(m.average_speed_kmh)).filter(s => s > 0)
  const maxSpeeds = matches.map(m => Number(m.max_speed_kmh)).filter(s => s > 0)
  const avgOfAvg  = avgSpeeds.length ? avgSpeeds.reduce((s, v) => s + v, 0) / avgSpeeds.length : 0
  const maxOfMax  = maxSpeeds.length ? Math.max(...maxSpeeds) : 0

  return (
    <div className="page">
      <h1 className="page-title">Trends</h1>

      <div className="tr-tabs">
        {TABS.map(t => (
          <button
            key={t}
            className={`tr-tab${tab === t ? ' tr-tab-active' : ''}`}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 'Overview' && (
        <>
          {/* Totals */}
          <div className="rpt-section">
            <div className="rpt-section-label">Matches</div>
            <div className="tr-stat-grid">
              <StatCard value={n} label="Total" />
              <StatCard value={avgPerMonth} label="Avg / Month" />
            </div>
          </div>

          {/* Goals */}
          <div className="rpt-section">
            <div className="rpt-section-label">Goals</div>
            <div className="tr-stat-grid">
              <StatCard value={totalGoals} label="Total Goals" />
              <StatCard value={scores.length ? fmt1(avgGoals) : '—'} label="Avg per Game" />
            </div>
            {totalGoals > 0 && (
              <div className="tr-bars">
                <BarRow label="Home" value={homeGoals} total={totalGoals} color="var(--accent)" />
                <BarRow label="Away" value={awayGoals} total={totalGoals} color="#3b82f6" />
              </div>
            )}
          </div>

          {/* Results */}
          <div className="rpt-section">
            <div className="rpt-section-label">Results</div>
            <div className="tr-stat-grid">
              <StatCard value={homeWins} label="Home Wins" />
              <StatCard value={draws}    label="Draws" />
              <StatCard value={awayWins} label="Away Wins" />
            </div>
            {resultsN > 0 && (
              <div className="tr-bars">
                <BarRow label="Home Win" value={homeWins} total={resultsN} color="var(--accent)" />
                <BarRow label="Draw"     value={draws}    total={resultsN} color="#6b7280" />
                <BarRow label="Away Win" value={awayWins} total={resultsN} color="#3b82f6" />
              </div>
            )}
          </div>
        </>
      )}

      {tab === 'Misconduct' && (
        <>
          <div className="rpt-section">
            <div className="rpt-section-label">Yellow Cards</div>
            <div className="tr-stat-grid">
              <StatCard value={yc}    label="Total"    />
              <StatCard value={avgYc} label="Avg / Game" />
            </div>
          </div>
          <div className="rpt-section">
            <div className="rpt-section-label">Red Cards</div>
            <div className="tr-stat-grid">
              <StatCard value={rc}    label="Total"    />
              <StatCard value={avgRc} label="Avg / Game" />
            </div>
          </div>
          <div className="rpt-section">
            <div className="rpt-section-label">Sin Bins</div>
            <div className="tr-stat-grid">
              <StatCard value={sb}    label="Total"    />
              <StatCard value={avgSb} label="Avg / Game" />
            </div>
          </div>
          {(yc + rc + sb) > 0 && (
            <div className="rpt-section">
              <div className="rpt-section-label">Breakdown</div>
              <div className="tr-bars">
                <BarRow label="Yellow" value={yc} total={yc + rc + sb} color="var(--yc-text)" />
                <BarRow label="Red"    value={rc} total={yc + rc + sb} color="var(--rc-text)" />
                <BarRow label="Sin Bin"value={sb} total={yc + rc + sb} color="var(--sb-text)" />
              </div>
            </div>
          )}
        </>
      )}

      {tab === 'Distance' && (
        <div className="rpt-section">
          <div className="rpt-section-label">Distance Covered</div>
          {dists.length === 0 ? (
            <p className="tr-empty">No GPS distance data recorded yet.</p>
          ) : (
            <div className="tr-stat-grid">
              <StatCard
                value={`${fmt1(totalDist)} km`}
                label="Total Distance"
                sub={`across ${dists.length} match${dists.length !== 1 ? 'es' : ''} with GPS`}
              />
              <StatCard
                value={`${fmt1(avgDist)} km`}
                label="Avg per Match"
              />
            </div>
          )}
        </div>
      )}

      {tab === 'Speed' && (
        <div className="rpt-section">
          <div className="rpt-section-label">Speed</div>
          {avgSpeeds.length === 0 ? (
            <p className="tr-empty">No GPS speed data recorded yet.</p>
          ) : (
            <div className="tr-stat-grid">
              <StatCard
                value={`${fmt1(avgOfAvg)} km/h`}
                label="Avg Speed"
                sub="average of match averages"
              />
              <StatCard
                value={`${fmt1(maxOfMax)} km/h`}
                label="Top Speed"
                sub="fastest recorded"
              />
            </div>
          )}
        </div>
      )}

      {n === 0 && (
        <div className="empty-state" style={{ marginTop: 32 }}>
          <div className="empty-icon">📊</div>
          <div>No completed matches yet</div>
          <div className="empty-hint">Stats will appear here once matches are synced from the watch.</div>
        </div>
      )}
    </div>
  )
}
