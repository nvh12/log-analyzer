export default function DetailDrawer({ title, onClose, children }) {
  return (
    <div className="bg-gray-900 border border-gray-700 rounded-lg p-4 space-y-3">
      <div className="flex items-center justify-between">
        <span className="text-sm text-gray-300 font-medium">{title}</span>
        <button onClick={onClose} className="close-btn">✕</button>
      </div>
      {children}
    </div>
  )
}
