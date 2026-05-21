import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import Live from './pages/Live'
import Logs from './pages/Logs'
import Detections from './pages/Detections'
import Reactions from './pages/Reactions'
import System from './pages/System'
import './App.css'

export default function App() {
  return (
    <BrowserRouter>
      <Layout>
        <Routes>
          <Route path="/" element={<Live />} />
          <Route path="/logs" element={<Logs />} />
          <Route path="/detections" element={<Detections />} />
          <Route path="/reactions" element={<Reactions />} />
          <Route path="/system" element={<System />} />
        </Routes>
      </Layout>
    </BrowserRouter>
  )
}
