export default function DetailDrawer({ title, onClose, children }) {
  return (
    <>
      <div className="fixed inset-0 bg-black/40 z-40" onClick={onClose} />
      <div className="fixed top-0 right-0 bottom-0 z-50 w-full max-w-lg bg-gray-900 border-l border-gray-700 flex flex-col shadow-xl">
        <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800 shrink-0">
          <span className="text-sm text-gray-300 font-medium">{title}</span>
          <button onClick={onClose} className="close-btn">✕</button>
        </div>
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {children}
        </div>
      </div>
    </>
  )
}
