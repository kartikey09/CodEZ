import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { ScoreboardSocket, type SocketStatus, type VerdictEvent } from '@/lib/socket'
import type { StandingsResponse } from '@/lib/types'
import { useContest } from '@/lib/contest'

// Owns exactly one ScoreboardSocket for the whole app (per contest id). Standings frames land
// in shared state that the leaderboard renders; verdict frames fan out to whoever subscribed
// via onVerdict — the editor uses it as the fast path for "my submission just got judged", the
// toasts show it, the submissions page refreshes on it. status is surfaced so the top bar can
// show the live dot and the leaderboard can drop to REST polling when the socket is down.

interface RealtimeState {
  standings: StandingsResponse | null
  status: SocketStatus
  onVerdict: (fn: (v: VerdictEvent) => void) => () => void
}

const RealtimeContext = createContext<RealtimeState | null>(null)

export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { contest } = useContest()
  const contestId = contest?.id ?? null

  const [standings, setStandings] = useState<StandingsResponse | null>(null)
  const [status, setStatus] = useState<SocketStatus>('closed')
  const listeners = useRef(new Set<(v: VerdictEvent) => void>())

  useEffect(() => {
    if (contestId == null) return
    const socket = new ScoreboardSocket(contestId, {
      onStandings: setStandings,
      onVerdict: (v) => listeners.current.forEach((fn) => fn(v)),
      onStatus: setStatus,
    })
    socket.connect()
    return () => {
      socket.close()
      setStatus('closed')
    }
  }, [contestId])

  const onVerdict = useCallback((fn: (v: VerdictEvent) => void) => {
    listeners.current.add(fn)
    return () => {
      listeners.current.delete(fn)
    }
  }, [])

  const value = useMemo(() => ({ standings, status, onVerdict }), [standings, status, onVerdict])

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useRealtime(): RealtimeState {
  const ctx = useContext(RealtimeContext)
  if (!ctx) throw new Error('useRealtime must be used within <RealtimeProvider>')
  return ctx
}
