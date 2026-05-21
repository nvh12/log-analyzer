import { useState } from 'react'
import { useData } from '../hooks/useData'
import { api } from '../api'
import { FilterInput } from '../components/Filters'
import Table from '../components/Table'
import DetailDrawer from '../components/DetailDrawer'
import Pagination from '../components/Pagination'
import Loading from '../components/Loading'
import Empty from '../components/Empty'

function fmtTs(ts) {
  return ts ? new Date(ts).toLocaleString() : '—'
}

function statusColor(code) {
  if (code >= 400) return 'text-red-400'
  if (code >= 300) return 'text-yellow-400'
  return 'text-green-400'
}

function RecordDetail({ record }) {
  return Object.entries(record).map(([k, v]) =>
    v !== null && v !== undefined ? (
      <div key={k} className="flex gap-3">
        <span className="text-gray-600 w-32 shrink-0">{k}</span>
        <span className="text-gray-300 break-all">
          {typeof v === 'object' ? JSON.stringify(v) : String(v)}
        </span>
      </div>
    ) : null
  )
}

const HTTP_COLS = [
  { label: 'Timestamp', render: r => fmtTs(r.logTimestamp || r.createdAt), cls: 'text-gray-500' },
  { label: 'Source IP',  render: r => r.sourceIp ?? '—',                   cls: 'text-gray-300' },
  { label: 'Method',     render: r => r.method ?? '—',                      cls: 'text-gray-400' },
  { label: 'Path',       render: r => <span className="max-w-xs truncate block">{r.path ?? r.requestPath ?? '—'}</span>, cls: 'text-gray-400' },
  { label: 'Status',     render: r => <span className={statusColor(r.statusCode)}>{r.statusCode ?? '—'}</span> },
  { label: 'Bytes',      render: r => r.bytes ?? r.responseBytes ?? '—',   cls: 'text-gray-500' },
]

const FLOW_COLS = [
  { label: 'Timestamp', render: r => fmtTs(r.logTimestamp || r.createdAt),       cls: 'text-gray-500' },
  { label: 'Src IP',    render: r => r.sourceIp ?? '—',                           cls: 'text-gray-300' },
  { label: 'Dst IP',    render: r => r.destIp ?? '—',                             cls: 'text-gray-400' },
  { label: 'Dst port',  render: r => r.destPort ?? '—',                           cls: 'text-gray-400' },
  { label: 'Duration',  render: r => r.duration ?? '—',                           cls: 'text-gray-500' },
  { label: 'Packets',   render: r => r.totalPackets ?? r.packets ?? '—',          cls: 'text-gray-500' },
  { label: 'Bytes',     render: r => r.totalBytes ?? r.bytes ?? '—',              cls: 'text-gray-500' },
]

function LogTab({ columns, fetcher, drawerTitle }) {
  const [page, setPage]       = useState(0)
  const [selected, setSelected] = useState(null)
  const [filters, setFilters]  = useState({})

  const { data, loading } = useData(
    () => fetcher({ ...filters, page, size: 20 }),
    [filters, page]
  )

  const rows       = data?.content    ?? []
  const totalPages = data?.totalPages ?? 0

  function set(key) {
    return v => setFilters(prev => ({ ...prev, [key]: v || undefined }))
  }

  const isHttp = columns === HTTP_COLS

  return (
    <div className="space-y-4">
      <div className="flex gap-4 flex-wrap">
        {isHttp ? (
          <>
            <FilterInput label="Source IP"   value={filters.ip     ?? ''} onChange={set('ip')}     placeholder="0.0.0.0" />
            <FilterInput label="Status code" value={filters.status ?? ''} onChange={set('status')} placeholder="200" />
          </>
        ) : (
          <>
            <FilterInput label="Source IP" value={filters.srcIp  ?? ''} onChange={set('srcIp')}  placeholder="0.0.0.0" />
            <FilterInput label="Dst port"  value={filters.dstPort ?? ''} onChange={set('dstPort')} placeholder="443" />
          </>
        )}
      </div>

      {loading ? <Loading /> : rows.length === 0 ? <Empty /> : (
        <>
          <Table
            columns={columns}
            rows={rows}
            onRowClick={r => setSelected(selected?.id === r.id ? null : r)}
            selectedId={selected?.id}
          />
          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}

      {selected && (
        <DetailDrawer title={`${drawerTitle} #${selected.id}`} onClose={() => setSelected(null)}>
          <div className="space-y-2 text-xs mono">
            <RecordDetail record={selected} />
          </div>
        </DetailDrawer>
      )}
    </div>
  )
}

export default function Logs() {
  const [tab, setTab] = useState('http')

  return (
    <div className="p-4 sm:p-6 space-y-4">
      <h1 className="text-white text-lg font-semibold">Logs</h1>
      <div className="flex gap-2 border-b border-gray-800">
        {['http', 'flow'].map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`mono text-xs px-4 py-2 border-b-2 -mb-px transition-colors ${
              tab === t ? 'border-white text-white' : 'border-transparent text-gray-500 hover:text-gray-300'
            }`}
          >
            {t.toUpperCase()}
          </button>
        ))}
      </div>
      {tab === 'http'
        ? <LogTab columns={HTTP_COLS} fetcher={api.getHttpLogs} drawerTitle="HTTP Log" />
        : <LogTab columns={FLOW_COLS} fetcher={api.getFlowLogs} drawerTitle="Flow Record" />
      }
    </div>
  )
}
