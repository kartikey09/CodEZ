import { useEffect, useRef, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import type { SubmissionSummary, Verdict } from '@/lib/types'
import { Loader2 } from 'lucide-react'
import { useRealtime } from '@/lib/realtime'

const VERDICT_LABEL: Record<Verdict, string> = {
  AC: 'Accepted',
  WA: 'Wrong Answer',
  TLE: 'Time Limit Exceeded',
  MLE: 'Memory Limit Exceeded',
  RE: 'Runtime Error',
  CE: 'Compilation Error',
  IE: 'Internal Error',
}

function verdictClass(v: Verdict | null, status: string): string {
  if (status !== 'done') return 'text-accent'
  if (v === 'AC') return 'text-green-500'
  if (v === 'TLE' || v === 'MLE' || v === 'IE') return 'text-amber-500'
  return 'text-destructive'
}

export function Submissions() {
  const { onVerdict } = useRealtime()
  const [subs, setSubs] = useState<SubmissionSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const timer = useRef<number | null>(null)

  // WS fast path: refresh immediately when one of this user's submissions gets judged,
  // instead of waiting for the next 2s poll tick below.
  useEffect(() => {
    return onVerdict(() => {
      void api.getMySubmissions().then(setSubs).catch(() => {})
    })
  }, [onVerdict])

  useEffect(() => {
    let cancelled = false

    const load = async () => {
      try {
        const data = await api.getMySubmissions()
        if (cancelled) return
        setSubs(data)
        setError(null)
        // Keep refreshing while anything is still queued/running.
        if (data.some((s) => s.status !== 'done')) {
          timer.current = window.setTimeout(load, 2000)
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof ApiError ? err.message : 'Failed to load submissions.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
      if (timer.current !== null) window.clearTimeout(timer.current)
    }
  }, [])

  return (
    <div className="flex-1 w-full overflow-y-auto p-8 font-sans">
      <div className="max-w-4xl mx-auto">
        <h2 className="text-3xl font-bold mb-8 text-white tracking-tight">My submissions</h2>

        {loading ? (
          <div className="flex items-center justify-center h-40">
            <Loader2 className="animate-spin h-8 w-8 text-accent" />
          </div>
        ) : error ? (
          <div className="text-center text-destructive py-12 bg-card rounded-2xl border border-border">{error}</div>
        ) : subs.length === 0 ? (
          <div className="text-center text-muted-foreground py-12 bg-card rounded-2xl border border-border">
            No submissions yet.
          </div>
        ) : (
          <div className="overflow-hidden rounded-xl border border-border">
            <table className="w-full text-sm">
              <thead className="bg-secondary/40 text-muted-foreground">
                <tr className="text-left">
                  <th className="px-4 py-3 font-semibold">#</th>
                  <th className="px-4 py-3 font-semibold">Problem</th>
                  <th className="px-4 py-3 font-semibold">Language</th>
                  <th className="px-4 py-3 font-semibold">Result</th>
                  <th className="px-4 py-3 font-semibold">When</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {subs.map((s) => (
                  <tr key={s.id} className="bg-card hover:bg-secondary/20 transition-colors">
                    <td className="px-4 py-3 font-mono text-muted-foreground">{s.id}</td>
                    <td className="px-4 py-3 text-foreground">#{s.problemId}</td>
                    <td className="px-4 py-3 uppercase text-muted-foreground font-mono text-xs">{s.language}</td>
                    <td className={`px-4 py-3 font-semibold ${verdictClass(s.verdict, s.status)}`}>
                      {s.status === 'done' ? (s.verdict ? VERDICT_LABEL[s.verdict] : 'Done') : s.status}
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">{new Date(s.createdAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
