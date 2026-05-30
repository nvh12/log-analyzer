import { useState, useEffect } from 'react'
import { api } from '../api'
import Card from '../components/Card'
import StatusDot from '../components/StatusDot'

const SCENARIOS = ['NORMAL', 'TRAFFIC_SPIKE', 'DDOS', 'BRUTE_FORCE', 'WEB_ATTACK']

// Mirrors SCENARIO_LOG_TYPE in simulation/domain/services/log_generator.py.
// Log type is enforced server-side — shown here as read-only context only.
const SCENARIO_LOG_TYPE = {
  NORMAL:        'MIXED',
  TRAFFIC_SPIKE: 'HTTP',
  DDOS:          'FLOW',
  BRUTE_FORCE:   'FLOW',
  WEB_ATTACK:    'HTTP',
}

// Default rate per scenario. TRAFFIC_SPIKE is 10× normal so the statistical
// detectors (z-score, IQR, EMA, seasonal) all fire reliably.
const SCENARIO_RATE = {
  NORMAL: 5, TRAFFIC_SPIKE: 50, DDOS: 20, BRUTE_FORCE: 10, WEB_ATTACK: 10,
}

// Per-scenario default attack % (= 100 − _BENIGN_RATIO × 100 for attack scenarios)
const SCENARIO_ATTACK_PCT = {
  NORMAL: 100, TRAFFIC_SPIKE: 100, DDOS: 70, BRUTE_FORCE: 80, WEB_ATTACK: 70,
}

// target_ip is only read by _generate_http/_generate_flow for BRUTE_FORCE and WEB_ATTACK.
// NORMAL, TRAFFIC_SPIKE, and DDOS always call _random_ip() — target_ip is silently ignored.
// Adding a new scenario: update this filter predicate to keep it accurate.
const USES_TARGET_IP = new Set(SCENARIOS.filter(s => !['NORMAL', 'TRAFFIC_SPIKE', 'DDOS'].includes(s)))

// attack_pct has no effect for NORMAL: effective scenario is always NORMAL regardless.
// All other scenarios (including TRAFFIC_SPIKE) are meaningfully affected.
const USES_ATTACK_PCT = new Set(SCENARIOS.filter(s => s !== 'NORMAL'))

const DEFAULTS = {
  scenario:        'NORMAL',
  count:           100,
  rate_per_second: SCENARIO_RATE.NORMAL,
  target_ip:       '192.168.100.100',
  attack_pct:      100,
}

const REPLAY_DEFAULTS = {
  source_key:      '',
  count:           0,
  rate_per_second: 10,
  source_ip:       '',
  dest_ip:         '',
}

export default function Simulation() {
  const [status,       setStatus]       = useState(null)
  const [baseline,     setBaseline]     = useState(null)
  const [form,         setForm]         = useState(DEFAULTS)
  const [replayForm,   setReplay]       = useState(REPLAY_DEFAULTS)
  const [busy,         setBusy]         = useState(false)
  const [baselineBusy, setBaselineBusy] = useState(false)
  const [toast,        setToast]        = useState(null)

  function showToast(message, type = 'success') {
    setToast({ message, type })
    setTimeout(() => setToast(null), 2500)
  }

  async function fetchStatus()   { try { setStatus(await api.simulationStatus())         } catch {} }
  async function fetchBaseline() { try { setBaseline(await api.simulationBaselineStatus()) } catch {} }

  useEffect(() => { fetchStatus(); fetchBaseline() }, [])

  // Poll main simulation every 2 s while running
  useEffect(() => {
    if (status?.state !== 'running') return
    const id = setInterval(fetchStatus, 2000)
    return () => clearInterval(id)
  }, [status?.state])

  // Poll baseline every 5 s while running; initial fetch already done on mount
  useEffect(() => {
    if (baseline?.state !== 'running') return
    const id = setInterval(fetchBaseline, 5000)
    return () => clearInterval(id)
  }, [baseline?.state])

  async function handleStart() {
    setBusy(true)
    try {
      // log_type is derived server-side from the scenario — do not send it
      await api.simulationStart({
        scenario:        form.scenario,
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

  async function handleStopBaseline() {
    setBaselineBusy(true)
    try {
      await api.simulationBaselineStop()
      await fetchBaseline()
      showToast('Baseline stop signal sent')
    } catch {
      showToast('Failed to stop baseline', 'error')
    } finally {
      setBaselineBusy(false)
    }
  }

  async function handleReplay() {
    if (!replayForm.source_key.trim()) { showToast('Source key is required', 'error'); return }
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

  const running         = status?.state   === 'running'
  const baselineRunning = baseline?.state === 'running'
  const derivedLogType  = SCENARIO_LOG_TYPE[form.scenario] ?? 'HTTP'
  const targetIpActive  = USES_TARGET_IP.has(form.scenario)
  const attackPctActive = USES_ATTACK_PCT.has(form.scenario)

  function field(key) {
    return {
      value:    form[key],
      onChange: e => setForm(prev => ({ ...prev, [key]: e.target.value })),
      disabled: running || busy,
    }
  }

  function replayField(key) {
    return {
      value:    replayForm[key],
      onChange: e => setReplay(prev => ({ ...prev, [key]: e.target.value })),
      disabled: running || busy,
    }
  }

  return (
    <div className="p-4 sm:p-6 space-y-6">
      <h1 className="text-white text-lg font-semibold">Simulation</h1>

      {/* ── Baseline ──────────────────────────────────────────────────────── */}
      <Card title="Baseline">
        {!baseline ? (
          <div className="p-4 text-xs text-gray-500">Connecting to simulation service…</div>
        ) : (
          <div className="p-4 space-y-3">
            <p className="text-xs text-gray-500">
              Continuous NORMAL / MIXED traffic started automatically at service launch.
              Keeps HTTP and FLOW detection baselines warm between manual simulations.
            </p>
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-4">
                <div className="flex items-center gap-2">
                  <StatusDot color={baselineRunning ? 'green' : 'gray'} pulse={baselineRunning} />
                  <span className="mono text-sm text-gray-200">{baseline.state}</span>
                </div>
                {baselineRunning && (
                  <div className="grid grid-cols-2 gap-4 text-xs mono">
                    <div>
                      <div className="text-gray-500 mb-0.5">Sent</div>
                      <div className="text-gray-200">{baseline.sent}</div>
                    </div>
                    <div>
                      <div className="text-gray-500 mb-0.5">Log type</div>
                      <div className="text-gray-200">{baseline.log_type ?? 'MIXED'}</div>
                    </div>
                  </div>
                )}
              </div>
              {baselineRunning && (
                <button
                  onClick={handleStopBaseline}
                  disabled={baselineBusy}
                  className="mono text-xs px-3 py-1.5 rounded bg-red-900 border border-red-700 text-red-300
                             hover:bg-red-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  Stop baseline
                </button>
              )}
            </div>
          </div>
        )}
      </Card>

      {/* ── Main simulation status ────────────────────────────────────────── */}
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

      {/* ── Controls ─────────────────────────────────────────────────────── */}
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
                  scenario:        e.target.value,
                  attack_pct:      SCENARIO_ATTACK_PCT[e.target.value] ?? 70,
                  rate_per_second: SCENARIO_RATE[e.target.value] ?? 10,
                }))}
              >
                {SCENARIOS.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
              {/* Log type is derived server-side — shown as read-only context */}
              <span className="mono text-gray-600 -mt-0.5">→ {derivedLogType}</span>
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

            <label className={`flex flex-col gap-1 text-xs ${targetIpActive ? 'text-gray-500' : 'text-gray-600'}`}>
              Source IP
              <span className="-mt-0.5 text-gray-600">
                {targetIpActive ? 'primary attacker — gets blocked' : 'n/a — this scenario uses random IPs'}
              </span>
              <input
                type="text"
                placeholder="192.168.100.100"
                className="field"
                disabled={!targetIpActive || running || busy}
                value={form.target_ip}
                onChange={e => setForm(prev => ({ ...prev, target_ip: e.target.value }))}
              />
            </label>

            <label className={`flex flex-col gap-1 text-xs ${attackPctActive ? 'text-gray-500' : 'text-gray-600'}`}>
              Attack %
              {!attackPctActive && (
                <span className="-mt-0.5 text-gray-600">n/a — NORMAL always produces benign traffic</span>
              )}
              <input
                type="number"
                min="0"
                max="100"
                step="5"
                className="field"
                disabled={!attackPctActive || running || busy}
                value={form.attack_pct}
                onChange={e => setForm(prev => ({ ...prev, attack_pct: e.target.value }))}
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

      {/* ── Replay ───────────────────────────────────────────────────────── */}
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

