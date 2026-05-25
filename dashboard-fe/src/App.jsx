import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import ErrorBoundary from './components/ErrorBoundary'
import Live from './pages/Live'
import Logs from './pages/Logs'
import Detections from './pages/Detections'
import Reactions from './pages/Reactions'
import System from './pages/System'
import Simulation from './pages/Simulation'
import './App.css'

export default function App() {
  return (
    <BrowserRouter>
      <Layout>
        <ErrorBoundary>
          <Routes>
            <Route path="/" element={<Live />} />
            <Route path="/logs" element={<Logs />} />
            <Route path="/detections" element={<Detections />} />
            <Route path="/reactions" element={<Reactions />} />
            <Route path="/system" element={<System />} />
            <Route path="/simulation" element={<Simulation />} />
          </Routes>
        </ErrorBoundary>
      </Layout>
    </BrowserRouter>
  )
}
