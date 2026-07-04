import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api, ApiError } from '@/lib/api'
import type { ContestInfo } from '@/lib/types'

// The running contest (GET /api/contest) plus a server-corrected clock. skew =
// serverEpochMillis - Date.now() at fetch time; every countdown uses now() = Date.now() + skew,
// so a student whose laptop clock is off still sees the real remaining time. error === 'none'
// means the server answered 404: no contest is in the running state.

interface ContestState {
  contest: ContestInfo | null
  error: 'none' | 'network' | null
  now: () => number
  refresh: () => Promise<void>
}

const ContestContext = createContext<ContestState | null>(null)

export function ContestProvider({ children }: { children: ReactNode }) {
  const [contest, setContest] = useState<ContestInfo | null>(null)
  const [error, setError] = useState<'none' | 'network' | null>(null)
  const skewRef = useRef(0)

  const refresh = useCallback(async () => {
    try {
      const c = await api.getContest()
      skewRef.current = c.serverEpochMillis - Date.now()
      setContest(c)
      setError(null)
    } catch (err) {
      setContest(null)
      setError(err instanceof ApiError && err.status === 404 ? 'none' : 'network')
    }
  }, [])

  useEffect(() => {
    void (async () => {
      await refresh()
    })()
  }, [refresh])

  const now = useCallback(() => Date.now() + skewRef.current, [])

  const value = useMemo(() => ({ contest, error, now, refresh }), [contest, error, now, refresh])

  return <ContestContext.Provider value={value}>{children}</ContestContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useContest(): ContestState {
  const ctx = useContext(ContestContext)
  if (!ctx) throw new Error('useContest must be used within <ContestProvider>')
  return ctx
}
