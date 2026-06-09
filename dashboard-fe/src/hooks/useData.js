import { useState, useEffect, useCallback, useRef } from 'react'

export function useData(fetcher, deps = []) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  // Only show the loading spinner on the very first fetch (no data yet).
  // Re-fetches triggered by dep changes update data in-place to avoid jitter.
  const seenData = useRef(false)

  const load = useCallback(async () => {
    if (!seenData.current) setLoading(true)
    setError(null)
    try {
      const result = await fetcher()
      seenData.current = true
      setData(result)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => { load() }, [load])

  return { data, loading, error, reload: load }
}
