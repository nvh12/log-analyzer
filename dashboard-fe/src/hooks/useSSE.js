import { useEffect, useRef, useState } from 'react'

export function useSSE(handlers) {
  const handlersRef = useRef(handlers)
  handlersRef.current = handlers
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    let es
    let dead = false

    function connect() {
      if (dead) return
      es = new EventSource('/api/stream')

      es.onopen = () => setConnected(true)

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
        if (dead) return
        setConnected(false)
        es.close()
        setTimeout(connect, 3000)
      }
    }

    connect()
    return () => {
      dead = true
      es?.close()
    }
  }, [])

  return { connected }
}
