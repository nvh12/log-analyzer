import { useEffect, useRef } from 'react'

export function useSSE(handlers) {
  const handlersRef = useRef(handlers)
  handlersRef.current = handlers

  useEffect(() => {
    let es
    let dead = false

    function connect() {
      if (dead) return
      es = new EventSource('/api/stream')

      es.addEventListener('detection', (e) => {
        try { handlersRef.current.detection?.(JSON.parse(e.data)) } catch {}
      })
      es.addEventListener('reaction', (e) => {
        try { handlersRef.current.reaction?.(JSON.parse(e.data)) } catch {}
      })
      es.addEventListener('log_throughput', (e) => {
        try { handlersRef.current.log_throughput?.(JSON.parse(e.data)) } catch {}
      })
      es.addEventListener('heartbeat', (e) => {
        try { handlersRef.current.heartbeat?.(JSON.parse(e.data)) } catch {}
      })
      es.onerror = () => {
        es.close()
        if (!dead) setTimeout(connect, 3000)
      }
    }

    connect()
    return () => {
      dead = true
      es?.close()
    }
  }, [])
}
