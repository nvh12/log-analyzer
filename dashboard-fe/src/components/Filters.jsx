import { useState, useEffect, useRef } from 'react'
import { useDebounce } from '../hooks/useDebounce'

export function FilterInput({ label, value: initial = '', onChange, placeholder }) {
  const [raw, setRaw] = useState(initial)
  const debounced = useDebounce(raw)
  const firstRun = useRef(true)

  useEffect(() => {
    if (firstRun.current) { firstRun.current = false; return }
    onChange(debounced)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced])

  return (
    <label className="flex flex-col gap-1 text-xs text-gray-500">
      {label}
      <input
        type="text"
        value={raw}
        onChange={e => setRaw(e.target.value)}
        placeholder={placeholder}
        className="field"
      />
    </label>
  )
}

export function FilterSelect({ label, value, onChange, options }) {
  return (
    <label className="flex flex-col gap-1 text-xs text-gray-500">
      {label}
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        className="field"
      >
        <option value="">All</option>
        {options.map(o => <option key={o} value={o}>{o}</option>)}
      </select>
    </label>
  )
}

export function FilterDateRange({ fromValue, toValue, onFromChange, onToChange }) {
  return (
    <>
      <label className="flex flex-col gap-1 text-xs text-gray-500">
        From
        <input
          type="datetime-local"
          value={fromValue}
          onChange={e => onFromChange(e.target.value)}
          className="field"
        />
      </label>
      <label className="flex flex-col gap-1 text-xs text-gray-500">
        To
        <input
          type="datetime-local"
          value={toValue}
          onChange={e => onToChange(e.target.value)}
          className="field"
        />
      </label>
    </>
  )
}
