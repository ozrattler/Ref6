import { HashRouter, Routes, Route, Navigate } from 'react-router-dom'
import Navbar from './components/Navbar'
import Fixtures from './pages/Fixtures'
import FixtureDetail from './pages/FixtureDetail'
import MatchHistory from './pages/MatchHistory'
import MatchDetail from './pages/MatchDetail'
import MatchSetup from './pages/MatchSetup'

export default function App() {
  return (
    <HashRouter>
      <Navbar />
      <main className="main-content">
        <Routes>
          <Route path="/"           element={<Fixtures />} />
          <Route path="/fixture/:id" element={<FixtureDetail />} />
          <Route path="/history"    element={<MatchHistory />} />
          <Route path="/match/:id"  element={<MatchDetail />} />
          <Route path="/setup"      element={<MatchSetup />} />
          <Route path="*"           element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </HashRouter>
  )
}
