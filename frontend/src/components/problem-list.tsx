import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import type { ProblemSummary } from '@/lib/types'
import { Loader2, Clock, FileQuestion } from 'lucide-react'

export function ProblemList({ onSelectProblem }: { onSelectProblem: (id: number) => void }) {
  const [problems, setProblems] = useState<ProblemSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [notice, setNotice] = useState<{ kind: 'waiting' | 'none' | 'error'; text: string } | null>(null)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const data = await api.getProblems()
        if (!cancelled) {
          setProblems(data)
          setNotice(null)
        }
      } catch (err) {
        if (cancelled) return
        if (err instanceof ApiError && err.code === 'CONTEST_NOT_STARTED') {
          setNotice({ kind: 'waiting', text: 'The contest has not started yet. Problems appear when it begins.' })
        } else if (err instanceof ApiError && (err.code === 'NO_CONTEST_FOUND' || err.code === 'NOT_FOUND')) {
          setNotice({ kind: 'none', text: 'No active contest right now.' })
        } else {
          setNotice({ kind: 'error', text: err instanceof ApiError ? err.message : 'Failed to load problems.' })
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="flex-1 w-full overflow-y-auto p-8 font-sans">
      <div className="max-w-4xl mx-auto">
        <h2 className="text-3xl font-bold mb-8 text-white tracking-tight">Problems</h2>

        {loading ? (
          <div className="flex items-center justify-center h-40">
            <Loader2 className="animate-spin h-8 w-8 text-accent" />
          </div>
        ) : notice ? (
          <div className="flex flex-col items-center justify-center gap-3 text-center text-muted-foreground py-16 bg-card rounded-2xl border border-border">
            {notice.kind === 'waiting' ? (
              <Clock size={28} className="text-accent" />
            ) : (
              <FileQuestion size={28} className="text-muted-foreground" />
            )}
            <p className={notice.kind === 'error' ? 'text-destructive' : ''}>{notice.text}</p>
          </div>
        ) : (
          <div className="grid gap-4">
            {problems.map((prob) => (
              <div
                key={prob.id}
                onClick={() => onSelectProblem(prob.id)}
                className="group relative flex items-center justify-between p-6 bg-card border border-border rounded-2xl cursor-pointer hover:border-accent/50 hover:bg-secondary/40 transition-all overflow-hidden"
              >
                <div className="absolute left-0 top-0 bottom-0 w-1 bg-accent opacity-0 group-hover:opacity-100 transition-opacity" />
                <div className="flex items-center gap-6">
                  <div className="w-10 h-10 rounded-lg bg-secondary flex items-center justify-center text-lg font-bold text-accent shrink-0">
                    {prob.label}
                  </div>
                  <h3 className="text-lg font-bold text-gray-200 group-hover:text-accent transition-colors">
                    {prob.title}
                  </h3>
                </div>
                <button className="px-4 py-2 text-sm font-bold bg-accent/10 text-accent rounded-lg group-hover:bg-accent group-hover:text-accent-foreground transition-colors">
                  Solve
                </button>
              </div>
            ))}
            {problems.length === 0 && (
              <div className="text-center text-muted-foreground py-12 bg-card rounded-2xl border border-border">
                No problems in this contest.
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
