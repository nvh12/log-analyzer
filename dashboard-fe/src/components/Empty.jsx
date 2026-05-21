export default function Empty({ message = 'No data' }) {
  return (
    <div className="flex items-center justify-center h-24 text-sm text-gray-500 italic">
      {message}
    </div>
  )
}
