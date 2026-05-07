import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { initDb } from '../lib/initDb'

export default function Navbar({ onDbInit }) {
  const [showSettings, setShowSettings] = useState(false)

  function handleSettingsClose() {
    setShowSettings(false)
    if (onDbInit) onDbInit()
  }

  return (
    <>
      <nav className="navbar">
        <NavLink to="/" className="nav-logo">Ref6</NavLink>
        <div className="nav-links">
          <NavLink to="/" end className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
            Fixtures
          </NavLink>
          <NavLink to="/history" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
            History
          </NavLink>
          <NavLink to="/setup" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
            Set Up
          </NavLink>
          <button className="settings-btn" onClick={() => setShowSettings(true)} title="Settings">
            ⚙
          </button>
        </div>
      </nav>

      {showSettings && <SettingsModal onClose={handleSettingsClose} />}
    </>
  )
}

function SettingsModal({ onClose }) {
  const [url,          setUrl]          = useState(localStorage.getItem('pb_url')         || 'http://192.168.1.106:8090')
  const [adminEmail,   setAdminEmail]   = useState(localStorage.getItem('pb_admin_email') || '')
  const [adminPass,    setAdminPass]    = useState(localStorage.getItem('pb_admin_pass')  || '')
  const [initializing, setInitializing] = useState(false)
  const [initLog,      setInitLog]      = useState([])
  const [initError,    setInitError]    = useState(null)

  function saveUrl() {
    const clean = url.replace(/\/$/, '')
    localStorage.setItem('pb_url', clean)
    localStorage.setItem('pb_admin_email', adminEmail)
    localStorage.setItem('pb_admin_pass',  adminPass)
    onClose()
    window.location.reload()
  }

  async function handleInit() {
    const clean = url.replace(/\/$/, '')
    localStorage.setItem('pb_admin_email', adminEmail)
    localStorage.setItem('pb_admin_pass',  adminPass)
    setInitializing(true)
    setInitLog([])
    setInitError(null)
    try {
      const log = await initDb(clean, adminEmail, adminPass)
      setInitLog(log)
    } catch (err) {
      setInitError(err.message)
    } finally {
      setInitializing(false)
    }
  }

  const initDone = initLog.length > 0 && !initError

  return (
    <div className="modal-backdrop" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal modal-tall">
        <div className="modal-title">Settings</div>

        {/* ── PocketBase URL ── */}
        <div className="section-label" style={{ marginTop: 0 }}>PocketBase Connection</div>
        <div className="form-group">
          <label className="form-label">Server URL</label>
          <input
            className="form-input"
            value={url}
            onChange={e => setUrl(e.target.value)}
            placeholder="http://192.168.1.106:8090"
            spellCheck={false}
            autoComplete="off"
          />
          <p className="modal-note">
            GitHub Pages (HTTPS) blocks plain HTTP connections. Serve this app locally or enable HTTPS on PocketBase.
          </p>
        </div>

        {/* ── Database Setup ── */}
        <div className="section-label">Database Setup</div>
        <p className="modal-note" style={{ marginBottom: 8 }}>
          Enter PocketBase admin credentials to create or repair collections.
          Credentials are stored locally on this device.
        </p>
        <div className="form-group">
          <label className="form-label">Admin Email</label>
          <input
            className="form-input"
            type="email"
            value={adminEmail}
            onChange={e => setAdminEmail(e.target.value)}
            placeholder="admin@example.com"
            autoComplete="off"
          />
        </div>
        <div className="form-group">
          <label className="form-label">Admin Password</label>
          <input
            className="form-input"
            type="password"
            value={adminPass}
            onChange={e => setAdminPass(e.target.value)}
            placeholder="PocketBase admin password"
            autoComplete="current-password"
          />
        </div>

        {/* Init output */}
        {(initLog.length > 0 || initError) && (
          <div className={`init-log ${initError ? 'init-log-error' : ''}`}>
            {initLog.map((line, i) => <div key={i}>{line}</div>)}
            {initError && <div className="init-log-err-line">✗ {initError}</div>}
          </div>
        )}

        <div className="modal-actions" style={{ flexDirection: 'column', gap: 8 }}>
          <button
            className="btn-primary-sm"
            style={{ width: '100%' }}
            onClick={handleInit}
            disabled={initializing || !adminEmail || !adminPass}
          >
            {initializing ? 'Initializing…' : initDone ? 'Initialize Again' : 'Initialize Database'}
          </button>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn-ghost" onClick={onClose}>
              {initDone ? 'Close' : 'Cancel'}
            </button>
            <button className="btn-primary-sm" style={{ flex: 1 }} onClick={saveUrl}>
              Save URL &amp; Reload
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
