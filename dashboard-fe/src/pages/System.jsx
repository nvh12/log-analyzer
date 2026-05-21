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
  const { data: health, loading: healthLoading } = useData(() => api.getSystemHealth())
  const { data: config, loading: configLoading } = useData(() => api.getSystemConfig())

  const queues = health?.queue_depths ?? []
  const redis  = health?.redis        ?? {}

  function prettyConfig(raw) {
    if (!raw) return null
    if (typeof raw !== 'string') return JSON.stringify(raw, null, 2)
    try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw }
  }

  return (
    <div className="p-4 sm:p-6 space-y-6">
      <h1 className="text-white text-lg font-semibold">System</h1>

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

          <Card title="Redis">
            {healthLoading ? <Loading padded /> : (
              <div className="p-4">
                <Metric label="Keyspace hits"   value={redis.keyspace_hits} />
                <Metric label="Keyspace misses" value={redis.keyspace_misses} />
                <Metric label="Hit rate"        value={redis.hit_rate != null ? (redis.hit_rate * 100).toFixed(1) : null} unit="%" />
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
