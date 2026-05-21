import { useState } from 'react'
import { useData } from '../hooks/useData'
import { api } from '../api'
import { ActionBadge } from '../components/Badge'
import Card from '../components/Card'
import Table from '../components/Table'
import Pagination from '../components/Pagination'
import Loading from '../components/Loading'
import Empty from '../components/Empty'

function fmtTs(ts) {
  return ts ? new Date(ts).toLocaleString() : '—'
}

function ttlLabel(expiresAt) {
  if (!expiresAt) return '—'
  const s = Math.round((new Date(expiresAt) - Date.now()) / 1000)
  if (s <= 0) return 'expired'
  return s < 60 ? `${s}s` : `${Math.floor(s / 60)}m ${s % 60}s`
}

function IpList({ items, textCls, emptyMsg }) {
  if (items.length === 0) return <Empty message={emptyMsg} />
  return (
    <ul className="divide-y divide-gray-800/50">
      {items.map((entry, i) => (
        <li key={i} className="flex items-center justify-between px-4 py-2 text-xs mono">
          <span className={textCls}>{entry.ip ?? entry}</span>
          {entry.expiresAt && <span className="text-gray-500">{ttlLabel(entry.expiresAt)}</span>}
        </li>
      ))}
    </ul>
  )
}

export default function Reactions() {
  const [page, setPage] = useState(0)

  const { data, loading, reload }     = useData(() => api.getReactions({ page, size: 20 }), [page])
  const { data: active, reload: reloadActive } = useData(() => api.getActiveReactions())

  const rows       = data?.content    ?? []
  const totalPages = data?.totalPages ?? 0
  const blocklist  = active?.blocklist  ?? []
  const rateLimits = active?.rate_limits ?? []

  async function handleLift(id) {
    try { await api.liftBlock(id); reloadActive(); reload() } catch {}
  }

  const columns = [
    { label: 'ID',        render: r => r.id,                                    cls: 'text-gray-600' },
    { label: 'Action',    render: r => <ActionBadge value={r.action} /> },
    { label: 'Target',    render: r => r.target ?? r.sourceIp ?? '—',           cls: 'text-gray-300' },
    { label: 'Detection', render: r => r.detectionId ?? r.sourceDetectionId ?? '—', cls: 'text-gray-500' },
    { label: 'At',        render: r => fmtTs(r.reactedAt || r.ts),              cls: 'text-gray-500' },
    { label: '', render: r => r.action === 'BLOCK_IP' ? (
      <button
        onClick={() => handleLift(r.id)}
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
        {loading ? <Loading padded /> : rows.length === 0 ? <Empty /> : (
          <div className="p-4 space-y-2">
            <Table columns={columns} rows={rows} />
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </div>
        )}
      </Card>
    </div>
  )
}
