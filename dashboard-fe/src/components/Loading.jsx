export default function Loading({ padded = false }) {
  return (
    <div className={`text-sm text-gray-500 animate-pulse ${padded ? 'p-4' : ''}`}>
      Loading…
    </div>
  )
}
