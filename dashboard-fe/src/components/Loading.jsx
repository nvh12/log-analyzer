export default function Loading({ padded = false }) {
  return (
    <div className={`flex items-center justify-center h-24 text-sm text-gray-500 animate-pulse ${padded ? 'p-4' : ''}`}>
      Loading…
    </div>
  )
}
