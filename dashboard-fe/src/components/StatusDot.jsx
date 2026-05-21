const colors = {
  green:  'bg-green-500',
  red:    'bg-red-500',
  yellow: 'bg-yellow-400',
  orange: 'bg-orange-500',
  gray:   'bg-gray-600',
}

export default function StatusDot({ color = 'gray', pulse = false }) {
  return (
    <span className={`inline-block w-2 h-2 rounded-full ${colors[color]} ${pulse ? 'animate-pulse' : ''}`} />
  )
}
