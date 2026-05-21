import { useState, useRef, useCallback } from 'react'
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'
import { useSSE } from '../hooks/useSSE'
import { useData } from '../hooks/useData'
import { api } from '../api'
import { UcBadge, SeverityBadge, ActionBadge } from '../components/Badge'
import Card from '../components/Card'
import StatusDot from '../components/StatusDot'
import Empty from '../components/Empty'

const UC_LIST = ['TRAFFIC', 'DDOS', 'WEB_ATTACK', 'BRUTE_FORCE']
const UC_DOT  = { TRAFFIC: 'orange', DDOS: 'red', WEB_ATTACK: 'gray', BRUTE_FORCE: 'yellow' }

const RING         = 100
const CHART_WINDOW = 30

function ago(ts) {
  if (!ts) return '—'
  const s = Math.round((Date.now() - new Date(ts)) / 1000)
  return s < 60 ? `${s}s ago` : `${Math.floor(s / 60)}m ago`
}

function fmtTs(ts) {
  return ts ? new Date(ts).toLocaleTimeString() : '—'
}

export default function Live() {
  const [events, setEvents]               = useState([])
  const [ucState, setUcState]             = useState({})
  const [throughputPoints, setThroughput] = useState([])
  const pausedRef = useRef(false)

  const { data: active, reload: reloadActive } = useData(() => api.getActiveReactions())

  const pushEvent = useCallback((entry) => {
    if (pausedRef.current) return
    setEvents(prev => {
      const next = [entry, ...prev]
      return next.length > RING ? next.slice(0, RING) : next
    })
  }, [])

  useSSE({
    detection: (d) => {
      pushEvent({ kind: 'detection', ...d })
      setUcState(prev => ({ ...prev, [d.detection_type]: { severity: d.severity, anomaly: d.anomaly, ts: d.ts } }))
    },
    reaction: (r) => {
      pushEvent({ kind: 'reaction', ...r })
      reloadActive()
    },
    log_throughput: (t) => {
      setThroughput(prev => {
        const next = [...prev, { time: fmtTs(t.ts), http: t.http_per_sec, flow: t.flow_per_sec }]
        return next.length > CHART_WINDOW ? next.slice(-CHART_WINDOW) : next
      })
    },
  })

  const blocklist  = active?.blocklist  ?? []
  const rateLimits = active?.rate_limits ?? []

  return (
    <div className="p-4 sm:p-6 space-y-6">
      <h1 className="text-white text-lg font-semibold">Live</h1>

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
              {s && <SeverityBadge value={s.severity} />}
              <div className="mono text-xs text-gray-600">{s ? ago(s.ts) : '—'}</div>
            </Card>
          )
        })}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Event stream */}
        <Card className="flex flex-col">
          <div className="flex items-center justify-between px-4 py-2 border-b border-gray-800">
            <span className="text-xs font-medium text-gray-400 uppercase tracking-wide">Event stream</span>
            <button className="text-xs text-gray-500 hover:text-gray-300"
              onClick={() => { pausedRef.current = !pausedRef.current }}>
              pause on hover
            </button>
          </div>
          <div
            className="overflow-y-auto flex-1 min-h-0 max-h-96"
            onMouseEnter={() => { pausedRef.current = true }}
            onMouseLeave={() => { pausedRef.current = false }}
          >
            {events.length === 0 ? (
              <Empty message="No detections in the last 5 minutes — system healthy" />
            ) : (
              <ul className="divide-y divide-gray-800">
                {events.map((e, i) => (
                  <li key={i} className="px-4 py-2 text-xs mono flex items-start gap-3">
                    <span className="text-gray-600 shrink-0">{fmtTs(e.ts || e.detectedAt)}</span>
                    {e.kind === 'detection' ? (
                      <>
                        <UcBadge value={e.detection_type} />
                        <SeverityBadge value={e.severity} />
                        <span className="text-gray-400">{e.source_ip ?? '—'}</span>
                      </>
                    ) : (
                      <>
                        <ActionBadge value={e.action} />
                        <span className="text-gray-400">{e.target ?? '—'}</span>
                      </>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>

        {/* Throughput chart */}
        <Card title="Queue throughput (req/s)" className="flex flex-col">
          <div className="flex-1 p-3">
            {throughputPoints.length === 0 ? (
              <Empty message="Waiting for data…" />
            ) : (
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={throughputPoints} isAnimationActive={false}>
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
          <div className="flex gap-6 p-4 flex-wrap">
            {blocklist.map((b, i) => (
              <span key={i} className="mono text-xs bg-red-900/30 border border-red-800 rounded px-2 py-1 text-red-300">
                BLOCKED {b.ip ?? b}
              </span>
            ))}
            {rateLimits.map((r, i) => (
              <span key={i} className="mono text-xs bg-orange-900/30 border border-orange-800 rounded px-2 py-1 text-orange-300">
                RATE-LIMITED {r.ip ?? r}
              </span>
            ))}
          </div>
        )}
      </Card>
    </div>
  )
}
