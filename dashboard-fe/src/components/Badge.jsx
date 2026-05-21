const ucColors = {
  TRAFFIC:     'bg-orange-900/50 text-orange-300 border-orange-700',
  DDOS:        'bg-red-900/50 text-red-300 border-red-700',
  WEB_ATTACK:  'bg-purple-900/50 text-purple-300 border-purple-700',
  BRUTE_FORCE: 'bg-yellow-900/50 text-yellow-300 border-yellow-700',
}

const severityColors = {
  HIGH:   'bg-red-900/50 text-red-300 border-red-700',
  MEDIUM: 'bg-orange-900/50 text-orange-300 border-orange-700',
  LOW:    'bg-yellow-900/50 text-yellow-300 border-yellow-700',
  NONE:   'bg-gray-800 text-gray-400 border-gray-700',
}

const actionColors = {
  BLOCK_IP:    'bg-red-900/50 text-red-300 border-red-700',
  RATE_LIMIT:  'bg-orange-900/50 text-orange-300 border-orange-700',
  SCALE:       'bg-blue-900/50 text-blue-300 border-blue-700',
}

export function UcBadge({ value }) {
  const cls = ucColors[value] ?? 'bg-gray-800 text-gray-400 border-gray-700'
  return <span className={`mono text-xs px-1.5 py-0.5 rounded border ${cls}`}>{value}</span>
}

export function SeverityBadge({ value }) {
  const cls = severityColors[value] ?? severityColors.NONE
  return <span className={`mono text-xs px-1.5 py-0.5 rounded border ${cls}`}>{value}</span>
}

export function ActionBadge({ value }) {
  const cls = actionColors[value] ?? 'bg-gray-800 text-gray-400 border-gray-700'
  return <span className={`mono text-xs px-1.5 py-0.5 rounded border ${cls}`}>{value}</span>
}
