import { useState, useEffect, useCallback, useRef } from 'react'

export function useData(fetcher, deps = []) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  // Only show the loading spinner on the very first fetch (no data yet).
  // Re-fetches triggered by dep changes update data in-place to avoid jitter.
  const seenData = useRef(false)
  // Guards against an in-flight request resolving after a newer one (e.g. deps
  // changing while a fetch is pending) and clobbering fresher state.
  const requestIdRef = useRef(0)

  const load = useCallback(async () => {
    const requestId = ++requestIdRef.current
    if (!seenData.current) setLoading(true)
    setError(null)
    try {
      const result = await fetcher()
      if (requestIdRef.current !== requestId) return
      seenData.current = true
      setData(result)
    } catch (e) {
      if (requestIdRef.current !== requestId) return
      setError(e.message)
    } finally {
      if (requestIdRef.current === requestId) setLoading(false)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => { load() }, [load])

  return { data, loading, error, reload: load }
}
