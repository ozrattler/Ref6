import { useState, useEffect, useCallback } from 'react'
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom'
import Navbar from './components/Navbar'
import Fixtures from './pages/Fixtures'
import FixtureDetail from './pages/FixtureDetail'
import MatchHistory from './pages/MatchHistory'
import MatchDetail from './pages/MatchDetail'
import MatchSetup from './pages/MatchSetup'
import { pb } from './lib/pb'
import { checkDb } from './lib/initDb'

const DB_MSGS = {
  missing:     'PocketBase collections not found.',
  permissions: 'PocketBase collection permissions need updating.',
  connection:  'Cannot connect to PocketBase — check the URL.',
  error:       'PocketBase returned an unexpected error.',
}

function DbBanner({ status }) {
  if (!status || status === 'ok') return null
  return (
    <div className="db-banner">
      ⚠️ {DB_MSGS[status] || DB_MSGS.error}{' '}
      Open <strong>Settings ⚙</strong> and click <strong>Initialize Database</strong> to fix this.
    </div>
  )
}

export default function App() {
  const [dbStatus, setDbStatus] = useState(null)

  const recheckDb = useCallback(() => {
    checkDb(pb.baseUrl).then(setDbStatus)
  }, [])

  useEffect(() => { recheckDb() }, [recheckDb])

  return (
    <HashRouter>
      <Navbar onDbInit={recheckDb} />
      <main className="main-content">
        <DbBanner status={dbStatus} />
        <Routes>
          <Route path="/"            element={<Fixtures />} />
          <Route path="/fixture/:id" element={<FixtureDetail />} />
          <Route path="/history"     element={<MatchHistory />} />
          <Route path="/match/:id"   element={<MatchDetail />} />
          <Route path="/setup"       element={<MatchSetup />} />
          <Route path="*"            element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </HashRouter>
  )
}
