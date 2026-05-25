export default function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null

  function go(n) {
    onChange(n)
    document.querySelector('main')?.scrollTo({ top: 0, behavior: 'instant' })
  }

  return (
    <div className="flex items-center gap-2 text-sm text-gray-400 mt-3">
      <button
        disabled={page === 0}
        onClick={() => go(page - 1)}
        className="px-2 py-1 rounded bg-gray-800 hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed"
      >
        ‹ Prev
      </button>
      <span className="mono text-xs">
        {page + 1} / {totalPages}
      </span>
      <button
        disabled={page >= totalPages - 1}
        onClick={() => go(page + 1)}
        className="px-2 py-1 rounded bg-gray-800 hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed"
      >
        Next ›
      </button>
    </div>
  )
}
