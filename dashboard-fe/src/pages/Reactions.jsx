import { useState } from 'react'
import { useData } from '../hooks/useData'
import { api } from '../api'
import { ActionBadge } from '../components/Badge'
import { FilterSelect, FilterDateRange } from '../components/Filters'
import Card from '../components/Card'
import Table from '../components/Table'
import Pagination from '../components/Pagination'
import Loading from '../components/Loading'
import Empty from '../components/Empty'

function fmtTs(ts) {
  return ts ? new Date(ts).toLocaleString() : '—'
}

function ttlLabel(seconds) {
  if (seconds == null || seconds <= 0) return 'expired'
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

function IpList({ items, textCls, emptyMsg }) {
  if (items.length === 0) return <Empty message={emptyMsg} />
  return (
    <ul className="divide-y divide-gray-800/50">
      {items.map((entry, i) => (
        <li key={i} className="flex items-center justify-between px-4 py-2 text-xs mono">
          <span className={textCls}>{entry.ip ?? entry}</span>
          {entry.ttl_seconds != null && <span className="text-gray-500">{ttlLabel(entry.ttl_seconds)}</span>}
        </li>
      ))}
    </ul>
  )
}

const toISO = v => v ? new Date(v).toISOString() : undefined

export default function Reactions() {
  const [page, setPage]     = useState(0)
  const [action, setAction] = useState('')
  const [from, setFrom]     = useState('')
  const [to, setTo]         = useState('')
  const [toast, setToast]   = useState(null)

  const { data, loading, error, reload } = useData(
    () => api.getReactions({ action: action || undefined, from: toISO(from), to: toISO(to), page, size: 20 }),
    [action, from, to, page]
  )
  const { data: active, reload: reloadActive } = useData(() => api.getActiveReactions())

  const rows       = data?.content ?? []
  const totalPages = data ? Math.ceil(data.total / data.size) : 0
  const blocklist  = active?.blocklist  ?? []
  const rateLimits = active?.rate_limits ?? []

  function showToast(message, type = 'success') {
    setToast({ message, type })
    setTimeout(() => setToast(null), 2500)
  }

  async function handleLift(id, ip) {
    try {
      await api.liftBlock(id)
      reloadActive()
      reload()
      showToast(`Block lifted: ${ip}`)
    } catch {
      showToast('Failed to lift block', 'error')
    }
  }

  const columns = [
    { label: 'ID',        render: r => r.id,                                    cls: 'text-gray-600' },
    { label: 'Action',    render: r => <ActionBadge value={r.action} /> },
    { label: 'Target',    render: r => r.target ?? r.sourceIp ?? '—',           cls: 'text-gray-300' },
    { label: 'Cause',     render: r => r.detectionType ?? '—',                      cls: 'text-gray-500' },
    { label: 'At',        render: r => fmtTs(r.reactedAt || r.ts),              cls: 'text-gray-500' },
    { label: '', render: r => r.action === 'BLOCK' ? (
      <button
        onClick={() => handleLift(r.id, r.target ?? r.sourceIp ?? r.id)}
        className="text-xs text-red-400 hover:text-red-300 border border-red-800 hover:border-red-600 rounded px-2 py-0.5 transition-colors"
      >
        Lift block
      </button>
    ) : null },
  ]

  return (
    <div className="p-4 sm:p-6 space-y-6">
      <h1 className="text-white text-lg font-semibold">Reactions</h1>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Card title="IP Blocklist">
          <IpList items={blocklist} textCls="text-red-300" emptyMsg="No blocked IPs" />
        </Card>
        <Card title="Rate Limits">
          <IpList items={rateLimits} textCls="text-orange-300" emptyMsg="No rate-limited IPs" />
        </Card>
      </div>

      <Card title="Reaction timeline">
        <div className="px-4 pt-3 flex gap-4 flex-wrap items-end border-b border-gray-800 pb-3">
          <FilterSelect label="Action" value={action} onChange={v => { setAction(v); setPage(0) }}
            options={['BLOCK', 'RATE_LIMIT', 'SCALE_UP']} />
          <FilterDateRange
            fromValue={from} toValue={to}
            onFromChange={v => { setFrom(v); setPage(0) }}
            onToChange={v => { setTo(v); setPage(0) }}
          />
        </div>
        {error && (
          <div className="error-banner m-4">
            <span>{error}</span>
            <button onClick={reload} className="underline shrink-0">Retry</button>
          </div>
        )}
        {loading ? <Loading padded /> : rows.length === 0 ? <Empty /> : (
          <div className="p-4 space-y-2">
            <Table columns={columns} rows={rows} />
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </div>
        )}
      </Card>

      {toast && (
        <div className={`toast${toast.type === 'error' ? ' toast-error' : ''}`}>
          {toast.message}
        </div>
      )}
    </div>
  )
}
