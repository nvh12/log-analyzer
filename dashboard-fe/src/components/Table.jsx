export default function Table({ columns, rows, onRowClick, selectedId }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-xs mono border-collapse">
        <thead>
          <tr className="border-b border-gray-800 text-gray-500 text-left">
            {columns.map(col => (
              <th key={col.label} className="th">{col.label}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-800/50">
          {rows.map((row, i) => (
            <tr
              key={row.id ?? i}
              onClick={() => onRowClick?.(row)}
              className={[
                onRowClick ? 'cursor-pointer hover:bg-gray-800/40' : '',
                'transition-colors',
                selectedId != null && selectedId === row.id ? 'bg-gray-800/30' : '',
              ].filter(Boolean).join(' ')}
            >
              {columns.map(col => (
                <td key={col.label} className={`td ${col.cls ?? ''}`}>
                  {col.render ? col.render(row) : row[col.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
