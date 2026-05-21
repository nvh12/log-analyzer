import { useState } from 'react'
import { useData } from '../hooks/useData'
import { api } from '../api'
import { UcBadge, SeverityBadge } from '../components/Badge'
import { FilterSelect } from '../components/Filters'
import Table from '../components/Table'
import DetailDrawer from '../components/DetailDrawer'
import Pagination from '../components/Pagination'
import Loading from '../components/Loading'
import Empty from '../components/Empty'

function fmtTs(ts) {
  return ts ? new Date(ts).toLocaleString() : '—'
}

function TrafficDetail({ d }) {
  const flags = d.payload?.method_flags ?? d.methodFlags ?? {}
  return (
    <div className="space-y-2">
      <div className="grid grid-cols-2 gap-2 text-xs mono text-gray-400">
        <span>Window: {fmtTs(d.windowStart)} → {fmtTs(d.windowEnd)}</span>
        <span>Confidence: {d.confidence?.toFixed(3) ?? '—'}</span>
      </div>
      {Object.keys(flags).length > 0 && (
        <div>
          <div className="text-xs text-gray-500 mb-1">Method flags</div>
          <div className="flex gap-2 flex-wrap">
            {Object.entries(flags).map(([k, v]) => (
              <span key={k} className={`mono text-xs px-1.5 py-0.5 rounded border ${
                v ? 'border-orange-700 text-orange-300 bg-orange-900/40' : 'border-gray-700 text-gray-500'
              }`}>{k}</span>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function FlowDetail({ d }) {
  return (
    <div className="grid grid-cols-2 gap-2 text-xs mono text-gray-400">
      <span>Source IP: {d.sourceIp ?? '—'}</span>
      <span>Dest IP: {d.destIp ?? '—'}</span>
      <span>Dest port: {d.destPort ?? '—'}</span>
      <span>Confidence: {d.confidence?.toFixed(3) ?? '—'}</span>
    </div>
  )
}

function WebAttackDetail({ d }) {
  return (
    <div className="space-y-1 text-xs mono text-gray-400">
      <div>Source IP: {d.sourceIp ?? '—'}</div>
      <div>Confidence: {d.confidence?.toFixed(3) ?? '—'}</div>
      <div>Log timestamp: {fmtTs(d.logTimestamp)}</div>
    </div>
  )
}

function DetailPanel({ id, onClose }) {
  const { data: d, loading } = useData(() => api.getDetection(id), [id])

  return (
    <DetailDrawer title={`Detection #${id}`} onClose={onClose}>
      {loading ? <Loading /> : d && (
        <>
          <div className="flex items-center gap-2">
            <UcBadge value={d.detectionType} />
            <SeverityBadge value={d.severity} />
            <span className="mono text-xs text-gray-500">{fmtTs(d.detectedAt)}</span>
          </div>
          {d.detectionType === 'TRAFFIC'                                           && <TrafficDetail d={d} />}
          {(d.detectionType === 'DDOS' || d.detectionType === 'BRUTE_FORCE')      && <FlowDetail d={d} />}
          {d.detectionType === 'WEB_ATTACK'                                        && <WebAttackDetail d={d} />}
          {d.payload && Object.keys(d.payload).length > 0 && (
            <details className="text-xs">
              <summary className="text-gray-500 cursor-pointer hover:text-gray-300">Raw payload</summary>
              <pre className="mono mt-2 bg-gray-800 rounded p-3 text-gray-400 overflow-x-auto text-xs">
                {JSON.stringify(d.payload, null, 2)}
              </pre>
            </details>
          )}
        </>
      )}
    </DetailDrawer>
  )
}

const COLUMNS = [
  { label: 'ID',          render: r => r.id,                                                             cls: 'text-gray-600' },
  { label: 'Type',        render: r => <UcBadge value={r.detectionType} /> },
  { label: 'Severity',    render: r => <SeverityBadge value={r.severity} /> },
  { label: 'Anomaly',     render: r => <span className={r.anomaly ? 'text-red-400' : 'text-green-400'}>{r.anomaly ? 'yes' : 'no'}</span> },
  { label: 'Confidence',  render: r => r.confidence?.toFixed(3) ?? '—',                                  cls: 'text-gray-400' },
  { label: 'Source IP',   render: r => r.sourceIp ?? '—',                                                cls: 'text-gray-300' },
  { label: 'Detected at', render: r => fmtTs(r.detectedAt),                                              cls: 'text-gray-500' },
]

export default function Detections() {
  const [uc, setUc]         = useState('')
  const [severity, setSev]  = useState('')
  const [page, setPage]     = useState(0)
  const [selectedId, setSel] = useState(null)

  const { data, loading } = useData(
    () => api.getDetections({ uc: uc || undefined, severity: severity || undefined, page, size: 20 }),
    [uc, severity, page]
  )

  const rows       = data?.content    ?? []
  const totalPages = data?.totalPages ?? 0

  return (
    <div className="p-4 sm:p-6 space-y-4">
      <h1 className="text-white text-lg font-semibold">Detections</h1>

      <div className="flex gap-4 flex-wrap">
        <FilterSelect label="Use case" value={uc} onChange={v => { setUc(v); setPage(0) }}
          options={['TRAFFIC', 'DDOS', 'WEB_ATTACK', 'BRUTE_FORCE']} />
        <FilterSelect label="Severity" value={severity} onChange={v => { setSev(v); setPage(0) }}
          options={['HIGH', 'MEDIUM', 'LOW', 'NONE']} />
      </div>

      {loading ? <Loading /> : rows.length === 0 ? <Empty /> : (
        <>
          <Table
            columns={COLUMNS}
            rows={rows}
            onRowClick={r => setSel(selectedId === r.id ? null : r.id)}
            selectedId={selectedId}
          />
          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}

      {selectedId && <DetailPanel id={selectedId} onClose={() => setSel(null)} />}
    </div>
  )
}
