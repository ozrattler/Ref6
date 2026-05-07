import { useState } from 'react'
import { NavLink } from 'react-router-dom'

export default function Navbar() {
  const [showSettings, setShowSettings] = useState(false)

  return (
    <>
      <nav className="navbar">
        <NavLink to="/" className="nav-logo">Ref6</NavLink>
        <div className="nav-links">
          <NavLink to="/" end className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
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

      {showSettings && <SettingsModal onClose={() => setShowSettings(false)} />}
    </>
  )
}

function SettingsModal({ onClose }) {
  const [url, setUrl] = useState(localStorage.getItem('pb_url') || 'http://192.168.1.106:8090')

  function save() {
    localStorage.setItem('pb_url', url.replace(/\/$/, ''))
    onClose()
    window.location.reload()
  }

  return (
    <div className="modal-backdrop" onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal">
        <div className="modal-title">PocketBase Connection</div>
        <div className="form-group">
          <label className="form-label">Server URL</label>
          <input
            className="form-input"
            value={url}
            onChange={e => setUrl(e.target.value)}
            placeholder="http://192.168.1.106:8090"
            spellCheck={false}
          />
          <p className="modal-note">
            When using GitHub Pages (HTTPS), the browser will block connections to plain HTTP.
            Either serve this app locally over HTTP, or configure HTTPS on your PocketBase instance.
          </p>
        </div>
        <div className="modal-actions">
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary-sm" onClick={save}>Save &amp; Reload</button>
        </div>
      </div>
    </div>
  )
}
