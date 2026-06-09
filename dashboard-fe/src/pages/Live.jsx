import { useState, useRef, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'
import { useSSE } from '../hooks/useSSE'
import { useData } from '../hooks/useData'
import { api } from '../api'
import { UcBadge, SeverityBadge, ActionBadge } from '../components/Badge'
import { FilterSelect, FilterInput } from '../components/Filters'
import Card from '../components/Card'
import Table from '../components/Table'
import StatusDot from '../components/StatusDot'
import Loading from '../components/Loading'
import Empty from '../components/Empty'

const UC_LIST = ['TRAFFIC', 'DDOS', 'WEB_ATTACK', 'BRUTE_FORCE']
const UC_DOT  = { TRAFFIC: 'orange', DDOS: 'red', WEB_ATTACK: 'purple', BRUTE_FORCE: 'yellow' }

const RING       = 100
const MAX_POINTS = 900  // 30 min @ 2s intervals

const WINDOWS = [
  { label: '1m',  points: 30  },
  { label: '5m',  points: 150 },
  { label: '10m', points: 300 },
  { label: '30m', points: 900 },
]

// `now` is passed in (rather than read via Date.now() here) so the displayed
// age re-renders on a tick instead of freezing at the value from the last SSE event.
function ago(ts, now) {
  if (!ts) return '—'
  const s = Math.max(0, Math.round((now - new Date(ts).getTime()) / 1000))
  return s < 60 ? `${s}s ago` : `${Math.floor(s / 60)}m ago`
}

function ttlLabel(seconds) {
  if (seconds == null || seconds <= 0) return 'expired'
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

const TZ = { timeZone: 'Asia/Ho_Chi_Minh' }

function fmtTs(ts) {
  return ts ? new Date(ts).toLocaleTimeString(undefined, TZ) : '—'
}

function fmtLogTs(r) {
  if (r.timestamp) return new Date(r.timestamp * 1000).toLocaleTimeString(undefined, TZ)
  if (r.processedAt) return fmtTs(r.processedAt)
  return '—'
}

function statusColor(code) {
  if (code >= 400) return 'text-red-400'
  if (code >= 300) return 'text-yellow-400'
  return 'text-green-400'
}

// Compact column sets for the Live previews — fewer columns than the full
// Detections/Reactions/Logs pages so each card stays glanceable.
const PREVIEW_DETECTION_COLS = [
  { label: 'Type',      render: r => <UcBadge value={r.detectionType} /> },
  { label: 'Severity',  render: r => <SeverityBadge value={r.severity} /> },
  { label: 'Source IP', render: r => r.sourceIp ?? '—',           cls: 'text-gray-300' },
  { label: 'At',        render: r => fmtTs(r.detectedAt),         cls: 'text-gray-500' },
]

const PREVIEW_REACTION_COLS = [
  { label: 'Action', render: r => <ActionBadge value={r.action} /> },
  { label: 'Target', render: r => r.target ?? r.sourceIp ?? '—',  cls: 'text-gray-300' },
  { label: 'Cause',  render: r => r.detectionType ?? '—',         cls: 'text-gray-500' },
  { label: 'At',     render: r => fmtTs(r.reactedAt || r.ts),     cls: 'text-gray-500' },
]

const PREVIEW_HTTP_COLS = [
  { label: 'At',        render: r => fmtLogTs(r),                                                              cls: 'text-gray-500' },
  { label: 'Source IP', render: r => r.ip ?? r.sourceIp ?? '—',                                                cls: 'text-gray-300' },
  { label: 'Method',    render: r => r.method ?? '—',                                                          cls: 'text-gray-400' },
  { label: 'Path',      render: r => <span className="max-w-[9rem] truncate block">{r.url ?? r.path ?? '—'}</span>, cls: 'text-gray-400' },
  { label: 'Status',    render: r => <span className={statusColor(r.statusCode)}>{r.statusCode ?? '—'}</span> },
]

const PREVIEW_FLOW_COLS = [
  { label: 'At',       render: r => fmtLogTs(r),       cls: 'text-gray-500' },
  { label: 'Src IP',   render: r => r.sourceIp ?? '—', cls: 'text-gray-300' },
  { label: 'Dst IP',   render: r => r.destIp ?? '—',   cls: 'text-gray-400' },
  { label: 'Dst port', render: r => r.destPort ?? '—', cls: 'text-gray-400' },
]

function PreviewHeader({ title, to }) {
  return (
    <div className="flex items-center justify-between w-full">
      <span>{title}</span>
      <Link to={to} className="normal-case font-normal text-gray-500 hover:text-gray-300 transition-colors">
        View all →
      </Link>
    </div>
  )
}

function PreviewTable({ loading, error, rows, columns }) {
  if (loading) return <Loading padded />
  if (error) return <div className="error-banner m-4"><span>{error}</span></div>
  if (rows.length === 0) return <Empty />
  return <div className="p-4"><Table columns={columns} rows={rows} /></div>
}

// Server sends a heartbeat every 15s (SseEmitterRegistry) — flag the connection
// as stale if we miss roughly two beats, so a half-open connection doesn't keep
// showing "Connected" indefinitely.
const HEARTBEAT_TIMEOUT_MS = 35_000

export default function Live() {
  const [events, setEvents]               = useState([])
  const [ucState, setUcState]             = useState({})
  const [throughputPoints, setThroughput] = useState([])
  const [windowIdx, setWindowIdx]         = useState(1)  // default 5m
  const [lastHeartbeat, setLastHeartbeat] = useState(null)
  const [now, setNow]                     = useState(Date.now())
  const [typeFilter, setTypeFilter]       = useState('')
  const [severityFilter, setSeverityFilter] = useState('')
  const [ipFilter, setIpFilter]           = useState('')
  const [logTab, setLogTab]               = useState('http')
  const [detTypeFilter, setDetTypeFilter]         = useState('')
  const [detSeverityFilter, setDetSeverityFilter] = useState('')
  const [reactActionFilter, setReactActionFilter] = useState('')
  const [logIpFilter, setLogIpFilter]             = useState('')
  const pausedRef = useRef(false)

  const { data: active, reload: reloadActive } = useData(() => api.getActiveReactions())
  const { data: recentDetections, loading: detLoading, error: detError, reload: reloadDetections } =
    useData(() => api.getDetections({
      detectionType: detTypeFilter || undefined,
      severity: detSeverityFilter || undefined,
      size: 5,
    }), [detTypeFilter, detSeverityFilter])
  const { data: recentReactions, loading: reactLoading, error: reactError, reload: reloadReactions } =
    useData(() => api.getReactions({
      action: reactActionFilter || undefined,
      size: 5,
    }), [reactActionFilter])
  const { data: recentHttpLogs, loading: httpLoading, error: httpError, reload: reloadHttpLogs } =
    useData(() => api.getHttpLogs({
      ip: logIpFilter || undefined,
      size: 5,
    }), [logIpFilter])
  const { data: recentFlowLogs, loading: flowLoading, error: flowError, reload: reloadFlowLogs } =
    useData(() => api.getFlowLogs({
      srcIp: logIpFilter || undefined,
      size: 5,
    }), [logIpFilter])

  // No SSE event carries raw log records (only aggregate log_throughput counts),
  // so the logs preview polls the REST endpoint directly, mirroring Simulation.jsx's
  // fetchStatus/fetchBaseline pattern.
  useEffect(() => {
    const id = setInterval(() => { reloadHttpLogs(); reloadFlowLogs() }, 5000)
    return () => clearInterval(id)
  }, [reloadHttpLogs, reloadFlowLogs])

  // Drives "Xs ago" / heartbeat-staleness re-renders independent of new SSE events.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  const pushEvent = useCallback((entry) => {
    if (pausedRef.current) return
    setEvents(prev => {
      const next = [entry, ...prev]
      return next.length > RING ? next.slice(0, RING) : next
    })
  }, [])

  const { connected } = useSSE({
    detection: (d) => {
      pushEvent({ kind: 'detection', ...d })
      setUcState(prev => ({
        ...prev,
        [d.detection_type]: { severity: d.severity, anomaly: d.anomaly, confidence: d.confidence, ts: d.ts },
      }))
      reloadDetections()
    },
    reaction: (r) => {
      pushEvent({ kind: 'reaction', ...r })
      reloadActive()
      reloadReactions()
    },
    log_throughput: (t) => {
      setThroughput(prev => {
        const next = [...prev, { time: fmtTs(t.ts), http: t.http_per_sec, flow: t.flow_per_sec }]
        return next.length > MAX_POINTS ? next.slice(-MAX_POINTS) : next
      })
    },
    heartbeat: (h) => setLastHeartbeat(h.ts),
  })

  // Drop the stale heartbeat timestamp on disconnect so a fresh reconnect
  // starts "live" instead of immediately flashing as stale.
  useEffect(() => {
    if (!connected) setLastHeartbeat(null)
  }, [connected])

  const stale = connected && lastHeartbeat != null
    && (now - new Date(lastHeartbeat).getTime()) > HEARTBEAT_TIMEOUT_MS

  const filteredEvents = events.filter(e => {
    if (typeFilter) {
      if (typeFilter === 'REACTION') {
        if (e.kind !== 'reaction') return false
      } else if (e.kind !== 'detection' || e.detection_type !== typeFilter) {
        return false
      }
    }
    if (severityFilter && (e.kind !== 'detection' || e.severity !== severityFilter)) return false
    if (ipFilter) {
      const ip = e.kind === 'detection' ? e.source_ip : e.target
      if (!ip || !ip.toLowerCase().includes(ipFilter.trim().toLowerCase())) return false
    }
    return true
  })

  const blocklist  = active?.blocklist  ?? []
  const rateLimits = active?.rate_limits ?? []

  return (
    <div className="p-4 sm:p-6 space-y-6">
      <div className="flex items-center gap-3">
        <h1 className="text-white text-lg font-semibold">Live</h1>
        <div className="flex items-center gap-1.5">
          <StatusDot color={!connected ? 'red' : stale ? 'yellow' : 'green'} pulse={connected && !stale} />
          <span className="text-xs text-gray-500">
            {!connected ? 'Reconnecting…' : stale ? 'Connected — no heartbeat' : 'Connected'}
          </span>
        </div>
      </div>

      {/* UC tiles */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {UC_LIST.map(uc => {
          const s = ucState[uc]
          const firing = s?.anomaly === true
          return (
            <Card key={uc} className="p-4 space-y-2">
              <div className="flex items-center justify-between">
                <UcBadge value={uc} />
                <StatusDot color={firing ? UC_DOT[uc] : 'gray'} pulse={firing} />
              </div>
              <div className="text-xs text-gray-500">{s ? (firing ? 'detecting' : 'idle') : 'idle'}</div>
              {s && (
                <div className="flex items-center gap-2">
                  <SeverityBadge value={s.severity} />
                  {s.confidence != null && (
                    <span className="mono text-xs text-gray-600">{s.confidence.toFixed(2)}</span>
                  )}
                </div>
              )}
              <div className="mono text-xs text-gray-600">{s ? ago(s.ts, now) : '—'}</div>
            </Card>
          )
        })}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Event stream */}
        <Card className="flex flex-col">
          <div className="flex items-center justify-between px-4 py-2 border-b border-gray-800">
            <span className="text-xs font-medium text-gray-400 uppercase tracking-wide">Event stream</span>
            <span className="text-xs text-gray-600 italic">pauses on hover</span>
          </div>
          <div className="flex gap-3 flex-wrap items-end px-4 py-2 border-b border-gray-800/50">
            <FilterSelect label="Type" value={typeFilter} onChange={setTypeFilter} options={[...UC_LIST, 'REACTION']} />
            <FilterSelect label="Severity" value={severityFilter} onChange={setSeverityFilter}
              options={['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'NONE']} />
            <FilterInput label="IP" value={ipFilter} onChange={setIpFilter} placeholder="10.0.0.1" />
          </div>
          <div
            className="overflow-y-auto flex-1 min-h-0 max-h-96"
            onMouseEnter={() => { pausedRef.current = true }}
            onMouseLeave={() => { pausedRef.current = false }}
          >
            {filteredEvents.length === 0 ? (
              <Empty message={
                events.length === 0
                  ? (connected ? 'No events yet — system healthy' : 'Waiting for SSE connection…')
                  : 'No events match the filter'
              } />
            ) : (
              <ul className="divide-y divide-gray-800">
                {filteredEvents.map((e, i) => (
                  <li key={i} className="px-4 py-2 text-xs mono flex items-center gap-3">
                    <span className="text-gray-600 shrink-0">{fmtTs(e.ts || e.detectedAt)}</span>
                    {e.kind === 'detection' ? (
                      <>
                        <UcBadge value={e.detection_type} />
                        <SeverityBadge value={e.severity} />
                        <span className="text-gray-400">{e.source_ip ?? '—'}</span>
                        {e.confidence != null && (
                          <span className="text-gray-600 ml-auto">{e.confidence.toFixed(2)}</span>
                        )}
                      </>
                    ) : (
                      <>
                        <ActionBadge value={e.action} />
                        <span className="text-gray-400">{e.target ?? '—'}</span>
                        {e.ttl_seconds != null && <span className="text-gray-600">{ttlLabel(e.ttl_seconds)}</span>}
                        {e.reaction_id != null && <span className="text-gray-700 ml-auto">#{e.reaction_id}</span>}
                      </>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>

        {/* Throughput chart */}
        <Card className="flex flex-col">
          <div className="flex items-center justify-between px-4 py-2 border-b border-gray-800">
            <span className="text-xs font-medium text-gray-400 uppercase tracking-wide">Queue throughput (req/s)</span>
            <div className="flex gap-1">
              {WINDOWS.map((w, i) => (
                <button
                  key={w.label}
                  onClick={() => setWindowIdx(i)}
                  className={`mono text-xs px-2 py-0.5 rounded transition-colors ${
                    i === windowIdx
                      ? 'bg-gray-700 text-white'
                      : 'text-gray-500 hover:text-gray-300'
                  }`}
                >
                  {w.label}
                </button>
              ))}
            </div>
          </div>
          <div className="flex-1 p-3">
            {throughputPoints.length === 0 ? (
              <Empty message="Waiting for data…" />
            ) : (
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={throughputPoints.slice(-WINDOWS[windowIdx].points)} isAnimationActive={false}>
                  <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#6b7280' }} interval="preserveStartEnd" />
                  <YAxis tick={{ fontSize: 10, fill: '#6b7280' }} width={36} />
                  <Tooltip contentStyle={{ background: '#111827', border: '1px solid #374151', fontSize: 11 }}
                    labelStyle={{ color: '#9ca3af' }} />
                  <Line type="linear" dataKey="http" stroke="#f97316" dot={false} name="HTTP" strokeWidth={1.5} isAnimationActive={false} />
                  <Line type="linear" dataKey="flow" stroke="#60a5fa" dot={false} name="Flow" strokeWidth={1.5} isAnimationActive={false} />
                </LineChart>
              </ResponsiveContainer>
            )}
          </div>
        </Card>
      </div>

      {/* Active reactions */}
      <Card title="Active reactions">
        {blocklist.length === 0 && rateLimits.length === 0 ? (
          <Empty message="No active blocks or rate limits" />
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 p-4">
            <div>
              <div className="text-xs text-gray-500 mb-2">Blocked ({blocklist.length})</div>
              {blocklist.length === 0 ? <Empty message="No active blocks" /> : (
                <ul className="space-y-1.5">
                  {blocklist.map((b, i) => (
                    <li key={i} className="flex items-center justify-between gap-3 mono text-xs
                                            bg-red-900/20 border border-red-800/60 rounded px-2.5 py-1.5">
                      <span className="text-red-300">{b.ip ?? b}</span>
                      {b.ttl_seconds != null && <span className="text-red-400/70 shrink-0">{ttlLabel(b.ttl_seconds)}</span>}
                    </li>
                  ))}
                </ul>
              )}
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-2">Rate-limited ({rateLimits.length})</div>
              {rateLimits.length === 0 ? <Empty message="No active rate limits" /> : (
                <ul className="space-y-1.5">
                  {rateLimits.map((r, i) => (
                    <li key={i} className="flex items-center justify-between gap-3 mono text-xs
                                            bg-orange-900/20 border border-orange-800/60 rounded px-2.5 py-1.5">
                      <span className="text-orange-300">{r.ip ?? r}</span>
                      <span className="text-orange-400/70 flex gap-3 shrink-0">
                        {r.requests_per_minute != null && <span>{r.requests_per_minute} req/min</span>}
                        {r.ttl_seconds != null && <span>{ttlLabel(r.ttl_seconds)}</span>}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}
      </Card>

      {/* Recent activity — compact previews of the Logs/Detections/Reactions detail pages */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card title={<PreviewHeader title="Recent detections" to="/detections" />}>
          <div className="flex gap-3 flex-wrap items-end px-4 pt-2 pb-2 border-b border-gray-800/50">
            <FilterSelect label="Type" value={detTypeFilter} onChange={setDetTypeFilter} options={UC_LIST} />
            <FilterSelect label="Severity" value={detSeverityFilter} onChange={setDetSeverityFilter}
              options={['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'NONE']} />
          </div>
          <PreviewTable
            loading={detLoading} error={detError}
            rows={recentDetections?.content ?? []} columns={PREVIEW_DETECTION_COLS}
          />
        </Card>

        <Card title={<PreviewHeader title="Recent reactions" to="/reactions" />}>
          <div className="flex gap-3 flex-wrap items-end px-4 pt-2 pb-2 border-b border-gray-800/50">
            <FilterSelect label="Action" value={reactActionFilter} onChange={setReactActionFilter}
              options={['BLOCK', 'RATE_LIMIT', 'SCALE_UP']} />
          </div>
          <PreviewTable
            loading={reactLoading} error={reactError}
            rows={recentReactions?.content ?? []} columns={PREVIEW_REACTION_COLS}
          />
        </Card>

        <Card title={<PreviewHeader title="Recent logs" to="/logs" />}>
          <div className="flex gap-2 px-4 pt-2 border-b border-gray-800/50">
            {['http', 'flow'].map(t => (
              <button
                key={t}
                onClick={() => setLogTab(t)}
                className={`mono text-xs px-3 py-1.5 border-b-2 -mb-px transition-colors ${
                  logTab === t ? 'border-white text-white' : 'border-transparent text-gray-500 hover:text-gray-300'
                }`}
              >
                {t.toUpperCase()}
              </button>
            ))}
          </div>
          <div className="flex gap-3 flex-wrap items-end px-4 pt-2 pb-2 border-b border-gray-800/50">
            <FilterInput label="Source IP" value={logIpFilter} onChange={setLogIpFilter} placeholder="0.0.0.0" />
          </div>
          {logTab === 'http' ? (
            <PreviewTable
              loading={httpLoading} error={httpError}
              rows={recentHttpLogs?.content ?? []} columns={PREVIEW_HTTP_COLS}
            />
          ) : (
            <PreviewTable
              loading={flowLoading} error={flowError}
              rows={recentFlowLogs?.content ?? []} columns={PREVIEW_FLOW_COLS}
            />
          )}
        </Card>
      </div>
    </div>
  )
}
