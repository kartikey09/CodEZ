import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import type { ContestPageResponse } from '@/lib/types'
import { Loader2, ChevronLeft, ChevronRight } from 'lucide-react'
import { fmtDateTime } from '@/lib/format'

const STATE_TONE: Record<string, string> = {
  running: 'text-accent border-accent/30 bg-accent/10',
  published: 'text-green-500 border-green-500/30 bg-green-500/10',
  finished: 'text-muted-foreground border-border bg-secondary/40',
  draft: 'text-amber-500 border-amber-500/30 bg-amber-500/10',
}

const PAGE_SIZE = 10

export function Contests() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<ContestPageResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const t = window.setTimeout(() => {
      setLoading(true)
      void api
        .getContests(page, PAGE_SIZE)
        .then((res) => {
          if (!cancelled) {
            setData(res)
            setError(null)
          }
        })
        .catch((err) => {
          if (!cancelled) setError(err instanceof ApiError ? err.message : 'Failed to load contests.')
        })
        .finally(() => {
          if (!cancelled) setLoading(false)
        })
    }, 0)
    return () => {
      cancelled = true
      window.clearTimeout(t)
    }
  }, [page])

  return (
    <div className="flex-1 w-full overflow-y-auto p-8 font-sans">
      <div className="max-w-5xl mx-auto">
        <h2 className="text-3xl font-bold mb-8 text-foreground tracking-tight">Contests</h2>

        {loading ? (
          <div className="flex items-center justify-center h-40">
            <Loader2 className="animate-spin h-8 w-8 text-accent" />
          </div>
        ) : error ? (
          <div className="text-center text-destructive py-12 glass rounded-2xl border border-border">{error}</div>
        ) : !data || data.items.length === 0 ? (
          <div className="text-center text-muted-foreground py-12 glass rounded-2xl border border-border">
            No contests yet.
          </div>
        ) : (
          <>
            <div className="overflow-hidden rounded-xl border border-border">
              <table className="w-full text-sm">
                <thead className="bg-secondary/40 text-muted-foreground">
                  <tr className="text-left">
                    <th className="px-4 py-3 font-semibold">#</th>
                    <th className="px-4 py-3 font-semibold">Title</th>
                    <th className="px-4 py-3 font-semibold">Starts</th>
                    <th className="px-4 py-3 font-semibold">Ends</th>
                    <th className="px-4 py-3 font-semibold">State</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {data.items.map((c) => (
                    <tr key={c.id} className="bg-card hover:bg-secondary/20 transition-colors">
                      <td className="px-4 py-3 font-mono text-muted-foreground">{c.id}</td>
                      <td className="px-4 py-3 text-foreground font-medium">{c.title}</td>
                      <td className="px-4 py-3 text-muted-foreground">{fmtDateTime(c.startsAt)}</td>
                      <td className="px-4 py-3 text-muted-foreground">{fmtDateTime(c.endsAt)}</td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex rounded border px-2 py-0.5 text-xs font-mono uppercase ${
                            STATE_TONE[c.state] ?? STATE_TONE.draft
                          }`}
                        >
                          {c.state}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex items-center justify-between mt-4">
              <span className="text-xs text-muted-foreground">
                Page {data.page + 1} of {Math.max(data.totalPages, 1)} — {data.totalElements} contest
                {data.totalElements === 1 ? '' : 's'}
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(p - 1, 0))}
                  disabled={data.page <= 0}
                  className="h-9 px-3 rounded-md border border-border text-sm font-medium text-foreground disabled:opacity-40 disabled:cursor-not-allowed hover:bg-secondary/60 transition flex items-center gap-1"
                >
                  <ChevronLeft size={14} /> Prev
                </button>
                <button
                  onClick={() => setPage((p) => p + 1)}
                  disabled={data.page + 1 >= data.totalPages}
                  className="h-9 px-3 rounded-md border border-border text-sm font-medium text-foreground disabled:opacity-40 disabled:cursor-not-allowed hover:bg-secondary/60 transition flex items-center gap-1"
                >
                  Next <ChevronRight size={14} />
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
