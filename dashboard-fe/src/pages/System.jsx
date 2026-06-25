import { useData } from '../hooks/useData'
import { api } from '../api'
import Card from '../components/Card'
import Loading from '../components/Loading'
import Empty from '../components/Empty'

function Metric({ label, value, unit = '' }) {
  return (
    <div className="flex justify-between items-baseline py-1.5 border-b border-gray-800/50 last:border-0">
      <span className="text-xs text-gray-500">{label}</span>
      <span className="mono text-xs text-gray-200">{value ?? '—'}{unit && value != null ? unit : ''}</span>
    </div>
  )
}

export default function System() {
  const { data: health, loading: healthLoading, error: healthError, reload: reloadHealth } = useData(() => api.getSystemHealth())
  const { data: config, loading: configLoading, error: configError, reload: reloadConfig } = useData(() => api.getSystemConfig())

  const queues  = health?.queue_depths ?? []
  const workers = health?.workers      ?? {}
  const hasScaleInfo = workers.max != null && workers.max > 0

  function prettyConfig(raw) {
    if (!raw) return null
    if (typeof raw !== 'string') return JSON.stringify(raw, null, 2)
    try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw }
  }

  return (
    <div className="p-4 sm:p-6 space-y-6">
      <h1 className="text-white text-lg font-semibold">System</h1>

      {healthError && (
        <div className="error-banner">
          <span>Health check failed: {healthError}</span>
          <button onClick={reloadHealth} className="underline shrink-0">Retry</button>
        </div>
      )}
      {configError && (
        <div className="error-banner">
          <span>Config unavailable: {configError}</span>
          <button onClick={reloadConfig} className="underline shrink-0">Retry</button>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="space-y-4">
          <Card title="RabbitMQ queue depths">
            {healthLoading ? <Loading padded /> : queues.length === 0 ? (
              <Empty message="RabbitMQ management API unavailable" />
            ) : (
              <div className="p-4">
                {queues.map(q => <Metric key={q.name} label={q.name} value={q.messages} unit=" msgs" />)}
              </div>
            )}
          </Card>

          <Card title="Worker scale">
            {healthLoading ? <Loading padded /> : !hasScaleInfo ? (
              <Empty message="Simulation service unavailable" />
            ) : (
              <div className="p-4">
                <Metric label="Current workers" value={workers.current} />
                {workers.target !== workers.current && (
                  <Metric label="Target workers" value={workers.target} />
                )}
                <Metric label="Min" value={workers.min} />
                <Metric label="Max" value={workers.max} />
                <p className="text-xs text-gray-400 pt-2">
                  {workers.available > 0
                    ? `${workers.available} more worker${workers.available === 1 ? '' : 's'} available — scale up?`
                    : 'At max capacity'}
                </p>
              </div>
            )}
          </Card>
        </div>

        <Card title="Detection config" className="flex flex-col">
          {configLoading ? <Loading padded /> : !config?.detection ? (
            <Empty message="Detection service config unavailable" />
          ) : (
            <div className="flex-1 overflow-auto p-4">
              <pre className="mono text-xs text-gray-400 whitespace-pre-wrap break-all">
                {prettyConfig(config.detection)}
              </pre>
            </div>
          )}
        </Card>
      </div>
    </div>
  )
}
