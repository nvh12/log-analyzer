import { useState, useEffect } from 'react'
import { api } from '../api'
import Card from '../components/Card'
import StatusDot from '../components/StatusDot'

const SCENARIOS = ['NORMAL', 'TRAFFIC_SPIKE', 'DDOS', 'BRUTE_FORCE', 'WEB_ATTACK']
const LOG_TYPES = ['HTTP', 'FLOW', 'MIXED']

// Per-scenario default attack % shown in the UI (= 100 - _BENIGN_RATIO*100 for attack scenarios)
const SCENARIO_ATTACK_PCT = {
  NORMAL: 100, TRAFFIC_SPIKE: 100, DDOS: 70, BRUTE_FORCE: 80, WEB_ATTACK: 70,
}

// DDOS and BRUTE_FORCE detectors run on the FLOW track (45-feature ML models).
// All other scenarios work on HTTP logs.
const SCENARIO_LOG_TYPE = {
  DDOS: 'FLOW', BRUTE_FORCE: 'FLOW',
}

// Default rate per scenario. NORMAL sets a measurable baseline; TRAFFIC_SPIKE is
// 10x higher so the statistical detectors (z-score, IQR, EMA, seasonal) all fire.
const SCENARIO_RATE = {
  NORMAL: 5, TRAFFIC_SPIKE: 50, DDOS: 20, BRUTE_FORCE: 10, WEB_ATTACK: 10,
}

const DEFAULTS = {
  scenario: 'NORMAL',
  log_type: 'HTTP',
  count: 100,
  rate_per_second: SCENARIO_RATE.NORMAL,
  target_ip: '192.168.100.100',
  attack_pct: 100,
}

const REPLAY_DEFAULTS = {
  source_key: '',
  count: 0,
  rate_per_second: 10,
  source_ip: '',
  dest_ip: '',
}

export default function Simulation() {
  const [status, setStatus]     = useState(null)
  const [form, setForm]         = useState(DEFAULTS)
  const [replayForm, setReplay] = useState(REPLAY_DEFAULTS)
  const [busy, setBusy]         = useState(false)
  const [toast, setToast]       = useState(null)

  function showToast(message, type = 'success') {
    setToast({ message, type })
    setTimeout(() => setToast(null), 2500)
  }

  async function fetchStatus() {
    try {
      const s = await api.simulationStatus()
      setStatus(s)
    } catch { /* simulation service unreachable */ }
  }

  useEffect(() => {
    api.simulationStatus().then(s => setStatus(s)).catch(() => {})
  }, [])

  // Poll every 2s while running; clear interval when idle or unmounted
  useEffect(() => {
    if (status?.state !== 'running') return
    const id = setInterval(() => {
      api.simulationStatus().then(s => setStatus(s)).catch(() => {})
    }, 2000)
    return () => clearInterval(id)
  }, [status?.state])

  async function handleStart() {
    setBusy(true)
    try {
      await api.simulationStart({
        scenario:        form.scenario,
        log_type:        form.log_type,
        count:           Number(form.count),
        rate_per_second: Number(form.rate_per_second),
        target_ip:       form.target_ip,
        attack_ratio:    Number(form.attack_pct) / 100,
      })
      await fetchStatus()
      showToast('Simulation started')
    } catch (e) {
      showToast(e.response?.data?.detail ?? 'Failed to start simulation', 'error')
    } finally {
      setBusy(false)
    }
  }

  async function handleReplay() {
    if (!replayForm.source_key.trim()) {
      showToast('Source key is required', 'error')
      return
    }
    setBusy(true)
    try {
      const res = await api.simulationReplay({
        source_key:      replayForm.source_key.trim(),
        count:           Number(replayForm.count),
        rate_per_second: Number(replayForm.rate_per_second),
        source_ip:       replayForm.source_ip.trim() || null,
        dest_ip:         replayForm.dest_ip.trim()   || null,
      })
      await fetchStatus()
      showToast(`Replay started — ${res.rows_loaded} rows loaded`)
    } catch (e) {
      showToast(e.response?.data?.detail ?? 'Failed to start replay', 'error')
    } finally {
      setBusy(false)
    }
  }

  async function handleStop() {
    setBusy(true)
    try {
      await api.simulationStop()
      await fetchStatus()
      showToast('Simulation stopped')
    } catch {
      showToast('Failed to stop simulation', 'error')
    } finally {
      setBusy(false)
    }
  }

  const running = status?.state === 'running'

  function field(key) {
    return {
      value: form[key],
      onChange: e => setForm(prev => ({ ...prev, [key]: e.target.value })),
      disabled: running || busy,
    }
  }

  function replayField(key) {
    return {
      value: replayForm[key],
      onChange: e => setReplay(prev => ({ ...prev, [key]: e.target.value })),
      disabled: running || busy,
    }
  }

  return (
    <div className="p-4 sm:p-6 space-y-6">
      <h1 className="text-white text-lg font-semibold">Simulation</h1>

      <Card title="Status">
        {!status ? (
          <div className="p-4 text-xs text-gray-500">Connecting to simulation service…</div>
        ) : (
          <div className="p-4 space-y-3">
            <div className="flex items-center gap-2">
              <StatusDot color={running ? 'green' : 'gray'} pulse={running} />
              <span className="mono text-sm text-gray-200">{status.state}</span>
            </div>
            {running && (
              <div className="grid grid-cols-2 sm:grid-cols-5 gap-4 text-xs mono">
                <div>
                  <div className="text-gray-500 mb-0.5">Sent</div>
                  <div className="text-gray-200">{status.sent}</div>
                </div>
                <div>
                  <div className="text-gray-500 mb-0.5">Scenario</div>
                  <div className="text-gray-200">{status.scenario}</div>
                </div>
                <div>
                  <div className="text-gray-500 mb-0.5">Log type</div>
                  <div className="text-gray-200">{status.log_type}</div>
                </div>
                <div>
                  <div className="text-gray-500 mb-0.5">Target IP</div>
                  <div className="text-gray-200">{status.target_ip}</div>
                </div>
                <div>
                  <div className="text-gray-500 mb-0.5">Attack %</div>
                  <div className="text-gray-200">
                    {status.attack_ratio != null
                      ? `${Math.round(status.attack_ratio * 100)}%`
                      : 'default'}
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </Card>

      <Card title="Controls">
        <div className="p-4 space-y-4">
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
            <label className="flex flex-col gap-1 text-xs text-gray-500">
              Scenario
              <select
                className="field"
                value={form.scenario}
                disabled={running || busy}
                onChange={e => setForm(prev => ({
                  ...prev,
                  scenario: e.target.value,
                  attack_pct: SCENARIO_ATTACK_PCT[e.target.value] ?? 70,
                  log_type: SCENARIO_LOG_TYPE[e.target.value] ?? 'HTTP',
                  rate_per_second: SCENARIO_RATE[e.target.value] ?? 10,
                }))}
              >
                {SCENARIOS.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </label>

            <label className="flex flex-col gap-1 text-xs text-gray-500">
              Log type
              <select className="field" {...field('log_type')}>
                {LOG_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </label>

            <label className="flex flex-col gap-1 text-xs text-gray-500">
              Count
              <span className="text-gray-600 -mt-0.5">(0 = unlimited)</span>
              <input type="number" min="0" className="field" {...field('count')} />
            </label>

            <label className="flex flex-col gap-1 text-xs text-gray-500">
              Rate (req/s)
              <input type="number" min="0.1" max="10000" step="0.1" className="field" {...field('rate_per_second')} />
            </label>

            <label className="flex flex-col gap-1 text-xs text-gray-500">
              Source IP
              <span className="text-gray-600 -mt-0.5">attacker IP — gets blocked</span>
              <input type="text" placeholder="192.168.100.100" className="field" {...field('target_ip')} />
            </label>

            <label className="flex flex-col gap-1 text-xs text-gray-500">
              Attack %
              <input
                type="number" min="0" max="100" step="5"
                className="field"
                {...field('attack_pct')}
              />
            </label>
          </div>

          <div className="flex gap-3 pt-1">
            <button
              onClick={handleStart}
              disabled={running || busy}
              className="mono text-xs px-4 py-2 rounded bg-green-900 border border-green-700 text-green-300
                         hover:bg-green-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Start
            </button>
            <button
              onClick={handleStop}
              disabled={!running || busy}
              className="mono text-xs px-4 py-2 rounded bg-red-900 border border-red-700 text-red-300
                         hover:bg-red-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Stop
            </button>
          </div>
        </div>
      </Card>

      <Card title="Replay">
        <div className="p-4 space-y-4">
          <p className="text-xs text-gray-500">
            Replay actual flow records from a CSV stored in MinIO. Non-feature columns (label, Timestamp) are
            ignored. Use <span className="mono text-gray-400">count=0</span> to send all rows once, or set a
            count to cycle through rows repeatedly.
          </p>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
            <label className="flex flex-col gap-1 text-xs text-gray-500 sm:col-span-2">
              Source key
              <span className="text-gray-600 -mt-0.5">MinIO object key, e.g. flow/ddos/train.csv</span>
              <input
                type="text"
                placeholder="flow/ddos/train.csv"
                className="field"
                {...replayField('source_key')}
              />
            </label>

            <label className="flex flex-col gap-1 text-xs text-gray-500">
              Rate (req/s)
              <input type="number" min="0.1" max="10000" step="0.1" className="field" {...replayField('rate_per_second')} />
            </label>

            <label className="flex flex-col gap-1 text-xs text-gray-500">
              Count
              <span className="text-gray-600 -mt-0.5">(0 = all rows once)</span>
              <input type="number" min="0" className="field" {...replayField('count')} />
            </label>

            <label className="flex flex-col gap-1 text-xs text-gray-500">
              Source IP override
              <span className="text-gray-600 -mt-0.5">optional — random if blank</span>
              <input type="text" placeholder="10.0.0.1" className="field" {...replayField('source_ip')} />
            </label>

            <label className="flex flex-col gap-1 text-xs text-gray-500">
              Dest IP override
              <span className="text-gray-600 -mt-0.5">optional — random if blank</span>
              <input type="text" placeholder="192.168.1.1" className="field" {...replayField('dest_ip')} />
            </label>
          </div>

          <div className="flex gap-3 pt-1">
            <button
              onClick={handleReplay}
              disabled={running || busy}
              className="mono text-xs px-4 py-2 rounded bg-blue-900 border border-blue-700 text-blue-300
                         hover:bg-blue-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Replay
            </button>
            <button
              onClick={handleStop}
              disabled={!running || busy}
              className="mono text-xs px-4 py-2 rounded bg-red-900 border border-red-700 text-red-300
                         hover:bg-red-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Stop
            </button>
          </div>
        </div>
      </Card>

      {toast && (
        <div className={`toast${toast.type === 'error' ? ' toast-error' : ''}`}>
          {toast.message}
        </div>
      )}
    </div>
  )
}
