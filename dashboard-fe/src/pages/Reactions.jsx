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

const TZ = { timeZone: 'Asia/Ho_Chi_Minh' }

function fmtTs(ts) {
  return ts ? new Date(ts).toLocaleString(undefined, TZ) : '—'
}

function ttlLabel(seconds) {
  if (seconds == null || seconds <= 0) return 'expired'
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

const toISO = v => v ? new Date(v).toISOString() : undefined

export default function Reactions() {
  const [page, setPage]     = useState(0)
  const [action, setAction] = useState('')
  const [from, setFrom]     = useState('')
  const [to, setTo]         = useState('')
  const [toast, setToast]   = useState(null)

  // pending state for batch apply
  const [liftChecked, setLiftChecked] = useState(new Set())
  const [pendingWl, setPendingWl]     = useState(null) // null = no whitelist changes yet
  const [wlAddIp, setWlAddIp]         = useState('')
  const [applying, setApplying]       = useState(false)

  const { data, loading, error, reload } = useData(
    () => api.getReactions({ action: action || undefined, from: toISO(from), to: toISO(to), page, size: 20 }),
    [action, from, to, page]
  )
  const { data: active, reload: reloadActive } = useData(() => api.getActiveReactions())
  const { data: whitelist, reload: reloadWhitelist } = useData(() => api.getWhitelist())

  const rows         = data?.content ?? []
  const totalPages   = data ? Math.ceil(data.total / data.size) : 0
  const blocklist    = active?.blocklist  ?? []
  const rateLimits   = active?.rate_limits ?? []
  const whitelistIps = whitelist ?? []

  // effective whitelist = pending state (if any changes) or server state
  const effectiveWl     = pendingWl ?? new Set(whitelistIps)
  const hasPendingChanges = pendingWl !== null || liftChecked.size > 0

  function showToast(message, type = 'success') {
    setToast({ message, type })
    setTimeout(() => setToast(null), 2500)
  }

  function toggleLift(ip) {
    setLiftChecked(prev => {
      const next = new Set(prev)
      if (next.has(ip)) next.delete(ip); else next.add(ip)
      return next
    })
  }

  function toggleWlFromBlocklist(ip) {
    setPendingWl(prev => {
      const set = new Set(prev ?? whitelistIps)
      if (set.has(ip)) set.delete(ip); else set.add(ip)
      return set
    })
  }

  function removeFromWl(ip) {
    setPendingWl(prev => {
      const set = new Set(prev ?? whitelistIps)
      set.delete(ip)
      return set
    })
  }

  function addToWl(e) {
    e.preventDefault()
    const trimmed = wlAddIp.trim()
    if (!trimmed) return
    setPendingWl(prev => {
      const set = new Set(prev ?? whitelistIps)
      set.add(trimmed)
      return set
    })
    setWlAddIp('')
  }

  async function handleApply() {
    setApplying(true)
    try {
      await Promise.all([
        api.replaceWhitelist(Array.from(effectiveWl)),
        api.liftBlocks(Array.from(liftChecked)),
      ])
      setLiftChecked(new Set())
      setPendingWl(null)
      reloadActive()
      reloadWhitelist()
      reload()
      showToast('Changes applied')
    } catch {
      showToast('Failed to apply changes', 'error')
    } finally {
      setApplying(false)
    }
  }

  // individual lift from timeline table (by DB reaction ID)
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
    { label: 'ID',     render: r => r.id,                                  cls: 'text-gray-600' },
    { label: 'Action', render: r => <ActionBadge value={r.action} /> },
    { label: 'Target', render: r => r.target ?? r.sourceIp ?? '—',         cls: 'text-gray-300' },
    { label: 'Cause',  render: r => r.detectionType ?? '—',                cls: 'text-gray-500' },
    { label: 'At',     render: r => fmtTs(r.reactedAt || r.ts),            cls: 'text-gray-500' },
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

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">

        {/* Blocklist — lift + whitelist checkboxes */}
        <Card title={`IP Blocklist (${blocklist.length})`}>
          {blocklist.length === 0 ? <Empty message="No blocked IPs" /> : (
            <ul className="divide-y divide-gray-800/50">
              {blocklist.map((entry, i) => {
                const ip   = entry.ip ?? entry
                const isLift = liftChecked.has(ip)
                const isWl   = effectiveWl.has(ip)
                return (
                  <li key={i} className="flex items-center gap-2 px-3 py-2 text-xs mono">
                    <label className="flex items-center gap-1 cursor-pointer select-none shrink-0">
                      <input
                        type="checkbox"
                        checked={isLift}
                        onChange={() => toggleLift(ip)}
                        className="accent-red-500"
                      />
                      <span className="text-red-400">Lift</span>
                    </label>
                    <label className="flex items-center gap-1 cursor-pointer select-none shrink-0">
                      <input
                        type="checkbox"
                        checked={isWl}
                        onChange={() => toggleWlFromBlocklist(ip)}
                        className="accent-green-500"
                      />
                      <span className="text-green-400">WL</span>
                    </label>
                    <span className="text-red-300 flex-1 truncate">{ip}</span>
                    <span className="text-gray-500 shrink-0">
                      {entry.ttl_seconds != null && ttlLabel(entry.ttl_seconds)}
                    </span>
                  </li>
                )
              })}
            </ul>
          )}
        </Card>

        {/* Rate Limits — read only */}
        <Card title="Rate Limits">
          {rateLimits.length === 0 ? <Empty message="No rate-limited IPs" /> : (
            <ul className="divide-y divide-gray-800/50">
              {rateLimits.map((entry, i) => (
                <li key={i} className="flex items-center justify-between px-4 py-2 text-xs mono">
                  <span className="text-orange-300">{entry.ip ?? entry}</span>
                  <span className="text-gray-500">
                    {entry.requests_per_minute != null && `${entry.requests_per_minute} req/min`}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* Whitelist — shows effective (pending) state */}
        <Card title="Whitelist">
          <form onSubmit={addToWl} className="flex gap-2 px-4 pt-3 pb-2">
            <input
              type="text"
              value={wlAddIp}
              onChange={e => setWlAddIp(e.target.value)}
              placeholder="10.0.0.1"
              className="field flex-1"
            />
            <button
              type="submit"
              className="text-xs text-green-400 hover:text-green-300 border border-green-800 hover:border-green-600 rounded px-3 py-1 transition-colors"
            >
              Add
            </button>
          </form>
          {effectiveWl.size === 0 ? <Empty message="No whitelisted IPs" /> : (
            <ul className="divide-y divide-gray-800/50">
              {Array.from(effectiveWl).map((ip, i) => {
                const isPending = !whitelistIps.includes(ip)
                return (
                  <li key={i} className="flex items-center justify-between px-4 py-2 text-xs mono">
                    <span className={isPending ? 'text-green-400 italic' : 'text-green-300'}>{ip}</span>
                    <button
                      onClick={() => removeFromWl(ip)}
                      className="text-xs text-red-400 hover:text-red-300 border border-red-800 hover:border-red-600 rounded px-2 py-0.5 transition-colors"
                    >
                      Remove
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </Card>
      </div>

      {hasPendingChanges && (
        <div className="flex justify-end">
          <button
            onClick={handleApply}
            disabled={applying}
            className="mono text-xs px-4 py-2 rounded bg-green-900 border border-green-700 text-green-300 hover:bg-green-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors font-medium"
          >
            {applying ? 'Applying…' : 'Apply changes'}
          </button>
        </div>
      )}

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
        {loading ? <Loading padded /> : error ? null : rows.length === 0 ? <Empty /> : (
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
