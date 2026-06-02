import { useState } from 'react'
import { useData } from '../hooks/useData'
import { api } from '../api'
import { FilterInput, FilterDateRange } from '../components/Filters'
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

function fmtLogTs(r) {
  if (r.timestamp) return new Date(r.timestamp * 1000).toLocaleString()
  if (r.processedAt) return new Date(r.processedAt).toLocaleString()
  return '—'
}

const HTTP_COLS = [
  { label: 'Timestamp', render: r => fmtLogTs(r),                                                                      cls: 'text-gray-500' },
  { label: 'Source IP',  render: r => r.ip ?? r.sourceIp ?? '—',                                                       cls: 'text-gray-300' },
  { label: 'Method',     render: r => r.method ?? '—',                                                                  cls: 'text-gray-400' },
  { label: 'Path',       render: r => <span className="max-w-xs truncate block">{r.url ?? r.path ?? '—'}</span>,        cls: 'text-gray-400' },
  { label: 'Status',     render: r => <span className={statusColor(r.statusCode)}>{r.statusCode ?? '—'}</span> },
  { label: 'Bytes',      render: r => r.responseSize ?? r.bytes ?? '—',                                                 cls: 'text-gray-500' },
]

const FLOW_COLS = [
  { label: 'Timestamp', render: r => fmtLogTs(r),                                                                                              cls: 'text-gray-500' },
  { label: 'Src IP',    render: r => r.sourceIp ?? '—',                                                                                        cls: 'text-gray-300' },
  { label: 'Src port',  render: r => r.sourcePort ?? '—',                                                                                      cls: 'text-gray-400' },
  { label: 'Dst IP',    render: r => r.destIp ?? '—',                                                                                          cls: 'text-gray-400' },
  { label: 'Dst port',  render: r => r.destPort ?? '—',                                                                                        cls: 'text-gray-400' },
  { label: 'Pkt/s',     render: r => r.features?.['Flow Packets/s'] != null ? r.features['Flow Packets/s'].toFixed(1) : '—',                   cls: 'text-gray-500' },
  { label: 'Bytes/s',   render: r => r.features?.['Flow Bytes/s'] != null ? r.features['Flow Bytes/s'].toFixed(0) : '—',                       cls: 'text-gray-500' },
  { label: 'Fwd bytes', render: r => r.features?.['Total Length of Fwd Packets'] != null ? r.features['Total Length of Fwd Packets'].toFixed(0) : '—', cls: 'text-gray-500' },
]

const toISO = v => v ? new Date(v).toISOString() : undefined

function LogTab({ columns, fetcher, drawerTitle }) {
  const [page, setPage]         = useState(0)
  const [selected, setSelected] = useState(null)
  const [filters, setFilters]   = useState({})
  const [from, setFrom]         = useState('')
  const [to, setTo]             = useState('')

  const { data, loading, error, reload } = useData(
    () => fetcher({ ...filters, from: toISO(from), to: toISO(to), page, size: 20 }),
    [fetcher, filters, from, to, page]
  )

  const rows       = data?.content ?? []
  const totalPages = data ? Math.ceil(data.total / data.size) : 0

  function set(key) {
    return v => { setFilters(prev => ({ ...prev, [key]: v || undefined })); setPage(0) }
  }

  const isHttp = columns === HTTP_COLS

  return (
    <div className="space-y-4">
      {error && (
        <div className="error-banner">
          <span>{error}</span>
          <button onClick={reload} className="underline shrink-0">Retry</button>
        </div>
      )}
      <div className="flex gap-4 flex-wrap items-end">
        {isHttp ? (
          <>
            <FilterInput label="Source IP"   value={filters.ip     ?? ''} onChange={set('ip')}     placeholder="0.0.0.0" />
            <FilterInput label="Status code" value={filters.status ?? ''} onChange={set('status')} placeholder="200" />
          </>
        ) : (
          <>
            <FilterInput label="Source IP" value={filters.srcIp   ?? ''} onChange={set('srcIp')}   placeholder="0.0.0.0" />
            <FilterInput label="Dst port"  value={filters.dstPort ?? ''} onChange={set('dstPort')} placeholder="443" />
          </>
        )}
        <FilterDateRange
          fromValue={from} toValue={to}
          onFromChange={v => { setFrom(v); setPage(0) }}
          onToChange={v => { setTo(v); setPage(0) }}
        />
      </div>

      {loading ? <Loading /> : error ? null : rows.length === 0 ? <Empty /> : (
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
      <LogTab
        key={tab}
        columns={tab === 'http' ? HTTP_COLS : FLOW_COLS}
        fetcher={tab === 'http' ? api.getHttpLogs : api.getFlowLogs}
        drawerTitle={tab === 'http' ? "HTTP Log" : "Flow Record"}
      />
    </div>
  )
}
