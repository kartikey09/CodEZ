import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import type { StandingsResponse, MyStanding } from '@/lib/types'
import { Loader2, RefreshCw, Radio } from 'lucide-react'
import { useContest } from '@/lib/contest'
import { useRealtime } from '@/lib/realtime'

// The board is addressed by an explicit contest id, defaulted below to the running contest
// once GET /api/contest resolves. Set VITE_CONTEST_ID to override it outright (e.g. to look at
// a past contest's board).
const ENV_CONTEST_ID = import.meta.env.VITE_CONTEST_ID as string | undefined

export function Leaderboard() {
  const { contest } = useContest()
  const { standings: liveBoard, status } = useRealtime()

  const [contestId, setContestId] = useState(ENV_CONTEST_ID ?? '')
  const [data, setData] = useState<StandingsResponse | null>(null)
  const [mine, setMine] = useState<MyStanding | null>(null)
  // The spinner is turned on by the Load button (never inside `load` itself,
  // so the auto-refresh and the mount-time load stay silent).
  const [loading, setLoading] = useState(Boolean(ENV_CONTEST_ID))
  const [error, setError] = useState<string | null>(null)

  // Once the running contest is known, default the input to it (still overridable).
  // Guarded directly in render (not an effect) so it only ever fires once per empty->known transition.
  if (contest && contestId === '') {
    setContestId(String(contest.id))
  }

  const load = useCallback(
    async () => {
      const id = Number(contestId)
      if (!Number.isFinite(id) || contestId === '') return
      try {
        const res = await api.getStandings(id)
        setData(res)
        setError(null)
        // My rank is a bonus row — a failure here shouldn't break the board.
        try {
          setMine(await api.getMyStanding(id))
        } catch {
          setMine(null)
        }
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Failed to load standings.')
      } finally {
        setLoading(false)
      }
    },
    [contestId],
  )

  // Auto-load once a contest id is known (from the env or the running contest).
  useEffect(() => {
    if (contestId === '') return
    const t = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contestId])

  // The WebSocket is the fast path: while it's open for this contest, its pushed
  // standings are rendered directly and the REST poll below stands down.
  const socketLive = status === 'open' && liveBoard != null && String(liveBoard.contestId) === contestId
  const board = socketLive ? liveBoard : data

  // Live-ish refresh: each successful load produces a new `data` object, which
  // re-arms this timer; a failed load leaves `data` unchanged and polling stops.
  // Stands down entirely once the socket is carrying the board.
  useEffect(() => {
    if (!data || socketLive) return
    const t = window.setTimeout(() => void load(), 5000)
    return () => window.clearTimeout(t)
  }, [data, load, socketLive])

  return (
    <div className="flex-1 w-full overflow-y-auto p-8 font-sans">
      <div className="max-w-5xl mx-auto">
        <div className="flex items-center justify-between mb-8 gap-4 flex-wrap">
          <div className="flex items-center gap-3">
            <h2 className="text-3xl font-bold text-white tracking-tight">Leaderboard</h2>
            {board && (
              <span
                className={`inline-flex items-center gap-1.5 rounded border px-2 py-0.5 text-xs font-mono ${
                  socketLive
                    ? 'text-accent border-accent/30 bg-accent/10'
                    : 'text-amber-500 border-amber-500/30 bg-amber-500/10'
                }`}
                title={socketLive ? 'Updating live over WebSocket' : 'WebSocket down — polling every 5s'}
              >
                {socketLive ? <Radio size={12} /> : <RefreshCw size={12} />}
                {socketLive ? 'LIVE' : 'POLLING'}
              </span>
            )}
          </div>
          <div className="flex items-center gap-2">
            <input
              value={contestId}
              onChange={(e) => setContestId(e.target.value.replace(/[^0-9]/g, ''))}
              placeholder="Contest ID"
              className="h-9 w-32 px-3 rounded-md bg-input border border-border text-sm text-foreground focus:outline-none focus:border-accent"
            />
            <button
              onClick={() => {
                if (contestId !== '') setLoading(true)
                void load()
              }}
              className="h-9 px-3 rounded-md bg-accent text-accent-foreground text-sm font-semibold flex items-center gap-1.5 hover:bg-accent/90 transition"
            >
              <RefreshCw size={14} /> Load
            </button>
          </div>
        </div>

        {loading ? (
          <div className="flex items-center justify-center h-40">
            <Loader2 className="animate-spin h-8 w-8 text-accent" />
          </div>
        ) : error ? (
          <div className="text-center text-destructive py-12 bg-card rounded-2xl border border-border">{error}</div>
        ) : !board ? (
          <div className="text-center text-muted-foreground py-12 bg-card rounded-2xl border border-border">
            Enter a contest ID and press Load.
          </div>
        ) : (
          <>
          {mine && (
            <div className="mb-6 bg-card border border-accent/40 rounded-xl px-5 py-4 flex items-center gap-8 flex-wrap">
              <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">My standing</span>
              <div className="flex items-center gap-6 text-sm">
                <span>
                  Rank <span className="font-bold text-accent">{mine.rank ?? '—'}</span>
                </span>
                <span>
                  Solved <span className="font-bold text-foreground">{mine.solved}</span>
                </span>
                <span>
                  Penalty <span className="font-bold text-foreground">{mine.penalty}</span>
                </span>
              </div>
            </div>
          )}
          <div className="overflow-x-auto rounded-xl border border-border">
            <table className="w-full text-sm">
              <thead className="bg-secondary/40 text-muted-foreground">
                <tr className="text-left">
                  <th className="px-4 py-3 font-semibold">#</th>
                  <th className="px-4 py-3 font-semibold">Contestant</th>
                  <th className="px-4 py-3 font-semibold text-center">Solved</th>
                  <th className="px-4 py-3 font-semibold text-center">Penalty</th>
                  {board.problems.map((p) => (
                    <th key={p} className="px-3 py-3 font-semibold text-center">
                      {p}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {board.rows.map((row) => (
                  <tr key={row.userId} className="bg-card hover:bg-secondary/20 transition-colors">
                    <td className="px-4 py-3 font-mono text-muted-foreground">{row.rank}</td>
                    <td className="px-4 py-3 text-foreground font-medium">{row.displayName}</td>
                    <td className="px-4 py-3 text-center font-bold text-accent">{row.solved}</td>
                    <td className="px-4 py-3 text-center text-muted-foreground">{row.penalty}</td>
                    {row.cells.map((c) => (
                      <td
                        key={c.label}
                        className={`px-3 py-3 text-center text-xs font-mono ${
                          c.state === 'solved'
                            ? 'bg-green-500/15 text-green-400'
                            : c.state === 'attempted'
                              ? 'bg-destructive/15 text-destructive'
                              : 'text-muted-foreground/40'
                        }`}
                      >
                        {c.state === 'solved'
                          ? `+${c.attempts > 1 ? c.attempts - 1 : ''} ${c.acMinute ?? ''}`.trim()
                          : c.state === 'attempted'
                            ? `-${c.attempts}`
                            : '·'}
                      </td>
                    ))}
                  </tr>
                ))}
                {board.rows.length === 0 && (
                  <tr>
                    <td colSpan={4 + board.problems.length} className="px-4 py-12 text-center text-muted-foreground bg-card">
                      No judged submissions yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          </>
        )}
      </div>
    </div>
  )
}
