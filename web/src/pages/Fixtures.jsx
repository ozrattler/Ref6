import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { pb } from '../lib/pb'
import { kitStyle } from '../lib/colours'
import { syncCalendar, getLastSync, formatSyncTime } from '../lib/calendarSync'

const AEST = { timeZone: 'Australia/Sydney' }

function formatDayHeader(dateStr) {
  if (!dateStr) return 'Unknown date'
  try {
    const d = new Date(dateStr + 'T00:00:00')
    const opts = { weekday: 'long', day: 'numeric', month: 'long', ...AEST }
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

  const aestFmt = new Intl.DateTimeFormat('en-CA', AEST)
  const todayStr = aestFmt.format(now)
  const tmrwStr  = aestFmt.format(new Date(now.getTime() + 86400000))
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
  const [deleting,      setDeleting]      = useState(null)   // id being deleted
  const [importModal,   setImportModal]   = useState(null)   // null | parsed state
  const fileInputRef = useRef(null)
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
        const cutoff = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
        const loaded = r.items
          .filter(f => f.status === 'loaded' && new Date(f.updated) >= cutoff)
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

  async function handleDeleteLoaded(id) {
    if (!window.confirm('Permanently delete this record from PocketBase? This cannot be undone.')) return
    setDeleting(id)
    try {
      await pb.collection('match_setups').delete(id, { requestKey: null })
      setLoadedSetups(prev => prev.filter(f => f.id !== id))
    } catch (err) {
      console.error('Delete failed:', err)
    } finally {
      setDeleting(null)
    }
  }

  useEffect(() => { loadFixtures() }, [loadFixtures])

  async function handleFileSelected(e) {
    const file = e.target.files[0]
    e.target.value = ''          // reset so same file can be re-selected
    if (!file) return

    setImportModal({ step: 'parsing' })
    try {
      const buffer = await file.arrayBuffer()
      const { parseExcelFixtures } = await import('../lib/excelImport.js')
      const { fixtures: rows, skipped } = parseExcelFixtures(buffer)
      setImportModal({ step: 'preview', rows, skipped })
    } catch (err) {
      setImportModal({ step: 'error', message: err.message })
    }
  }

  async function handleImportConfirm(rows) {
    setImportModal(prev => ({ ...prev, step: 'importing' }))
    let imported = 0, errors = 0
    for (const row of rows) {
      try {
        await pb.collection('match_setups').create(row, { requestKey: null })
        imported++
      } catch {
        errors++
      }
    }
    setImportModal({ step: 'done', imported, errors })
    if (imported > 0) loadFixtures()
  }

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
          <input
            ref={fileInputRef}
            type="file"
            accept=".xlsx"
            style={{ display: 'none' }}
            onChange={handleFileSelected}
          />
          <button
            className="btn-sync"
            onClick={() => fileInputRef.current?.click()}
            title="Import fixtures from an Excel spreadsheet"
          >
            Import Excel
          </button>
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

      {importModal && (
        <ExcelImportModal
          state={importModal}
          onConfirm={handleImportConfirm}
          onClose={() => setImportModal(null)}
        />
      )}

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
              deleting={deleting === f.id}
              onRestore={() => handleRestore(f.id)}
              onEdit={() => navigate(`/fixture/${f.id}`)}
              onDelete={() => handleDeleteLoaded(f.id)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function ExcelImportModal({ state, onConfirm, onClose }) {
  const { step } = state

  return (
    <div className="modal-backdrop" onClick={e => { if (e.target === e.currentTarget && step !== 'importing') onClose() }}>
      <div className="modal">
        <div className="modal-title">Import from Excel</div>

        {step === 'parsing' && (
          <p style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>Reading file…</p>
        )}

        {step === 'preview' && (
          <>
            <div className="import-summary">
              <span className="import-count-ok">{state.rows.length} fixture{state.rows.length !== 1 ? 's' : ''} ready to import</span>
              {state.skipped > 0 && (
                <span className="import-count-skip">{state.skipped} skipped (PLAYED / empty)</span>
              )}
            </div>
            {state.rows.length > 0 && (
              <div className="import-preview">
                {state.rows.slice(0, 5).map((r, i) => (
                  <div key={i} className="import-preview-row">
                    <span className="import-preview-date">{r.kickoff_date || '—'}</span>
                    <span className="import-preview-teams">
                      {r.home_team || '?'} <span style={{ color: 'var(--muted)' }}>vs</span> {r.away_team || '?'}
                    </span>
                    {r.age_group && <span className="badge">{r.age_group}</span>}
                  </div>
                ))}
                {state.rows.length > 5 && (
                  <p style={{ color: 'var(--muted)', fontSize: '0.8rem', marginTop: 6 }}>
                    …and {state.rows.length - 5} more
                  </p>
                )}
              </div>
            )}
            <div className="modal-actions">
              <button className="btn-ghost" onClick={onClose}>Cancel</button>
              {state.rows.length > 0 && (
                <button className="btn-primary-sm" onClick={() => onConfirm(state.rows)}>
                  Import {state.rows.length} Fixture{state.rows.length !== 1 ? 's' : ''}
                </button>
              )}
            </div>
          </>
        )}

        {step === 'importing' && (
          <p style={{ color: 'var(--muted)', fontSize: '0.9rem' }}>Importing…</p>
        )}

        {step === 'done' && (
          <>
            <div className="import-summary">
              <span className="import-count-ok">{state.imported} imported</span>
              {state.errors > 0 && (
                <span className="import-count-skip">{state.errors} failed</span>
              )}
            </div>
            <div className="modal-actions">
              <button className="btn-primary-sm" onClick={onClose}>Done</button>
            </div>
          </>
        )}

        {step === 'error' && (
          <>
            <p style={{ color: '#f87171', fontSize: '0.9rem' }}>
              Could not read file: {state.message}
            </p>
            <div className="modal-actions">
              <button className="btn-ghost" onClick={onClose}>Close</button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

function LoadedFixtureCard({ fixture: f, restoring, deleting, onRestore, onEdit, onDelete }) {
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
        <button className="btn-restore" onClick={onRestore} disabled={restoring || deleting}>
          {restoring ? 'Restoring…' : 'Restore'}
        </button>
        <button className="btn-edit" onClick={onEdit} disabled={deleting}>Edit</button>
        <button className="btn-delete-sm" onClick={onDelete} disabled={restoring || deleting}>
          {deleting ? '…' : 'Delete'}
        </button>
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
