import { useEffect, useState } from 'react'
import { Loader2, Send, AlertTriangle, Megaphone } from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import type { AdminContest, Announcement } from '@/lib/types'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

/**
 * Announcement management (Day 13). Pick a contest, post a notice, and see/retract the
 * ones already posted. Retracting is a soft delete — the row stays but drops out of the
 * student banner feed. Defaults the selected contest to the running one if there is one.
 */

export function AdminAnnouncements() {
  const [contests, setContests] = useState<AdminContest[]>([])
  const [contestId, setContestId] = useState<number | null>(null)
  const [items, setItems] = useState<Announcement[]>([])
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [posting, setPosting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Load contests once, default to the running one.
  useEffect(() => {
    const t = window.setTimeout(() => {
      void (async () => {
        try {
          const cs = await api.admin.listContests()
          setContests(cs)
          const running = cs.find((c) => c.state === 'running') ?? cs[0]
          setContestId(running ? running.id : null)
        } catch (err) {
          setError(err instanceof ApiError ? err.message : 'Failed to load contests.')
        } finally {
          setLoading(false)
        }
      })()
    }, 0)
    return () => window.clearTimeout(t)
  }, [])

  // Load announcements whenever the selected contest changes.
  useEffect(() => {
    if (contestId == null) return
    const t = window.setTimeout(() => {
      void (async () => {
        try {
          setItems(await api.admin.listAnnouncements(contestId))
          setError(null)
        } catch (err) {
          setError(err instanceof ApiError ? err.message : 'Failed to load announcements.')
        }
      })()
    }, 0)
    return () => window.clearTimeout(t)
  }, [contestId])

  const post = async () => {
    if (contestId == null || message.trim() === '') return
    setPosting(true)
    setError(null)
    try {
      const created = await api.admin.createAnnouncement(contestId, message.trim())
      setItems((prev) => [created, ...prev])
      setMessage('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not post.')
    } finally {
      setPosting(false)
    }
  }

  const retract = async (a: Announcement) => {
    try {
      const updated = await api.admin.deactivateAnnouncement(a.id)
      setItems((prev) => prev.map((x) => (x.id === a.id ? updated : x)))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not retract.')
    }
  }

  return (
    <div className="flex-1 w-full overflow-y-auto p-8 font-sans">
      <div className="max-w-3xl mx-auto">
        <h2 className="text-3xl font-bold text-foreground tracking-tight mb-8">Announcements</h2>

        {loading ? (
          <div className="flex items-center justify-center h-40">
            <Loader2 className="animate-spin h-8 w-8 text-accent" />
          </div>
        ) : (
          <>
            <div className="glass rounded-xl border border-border p-5 mb-6">
              <div className="flex items-center gap-3 mb-3">
                <span className="text-sm text-muted-foreground">Contest</span>
                <select
                  value={contestId ?? ''}
                  onChange={(e) => setContestId(Number(e.target.value))}
                  className="bg-input border border-border rounded-md px-3 py-1.5 text-sm text-foreground outline-none focus:border-accent"
                >
                  {contests.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.title} ({c.state})
                    </option>
                  ))}
                </select>
              </div>
              <textarea
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                rows={3}
                placeholder="Type a notice to broadcast to everyone in this contest…"
                className="w-full bg-input border border-border rounded-md px-3 py-2 text-sm text-foreground outline-none focus:border-accent resize-y"
              />
              <div className="flex items-center justify-between mt-3">
                {error ? (
                  <span className="flex items-center gap-1.5 text-sm text-destructive">
                    <AlertTriangle size={15} /> {error}
                  </span>
                ) : (
                  <span />
                )}
                <Button size="sm" disabled={contestId == null || message.trim() === '' || posting} onClick={() => void post()}>
                  {posting ? <Loader2 size={14} className="animate-spin" /> : <Send size={14} />}
                  Post
                </Button>
              </div>
            </div>

            {items.length === 0 ? (
              <div className="text-center text-muted-foreground py-10 glass rounded-2xl border border-border">
                <Megaphone size={20} className="mx-auto mb-2" />
                No announcements for this contest yet.
              </div>
            ) : (
              <div className="space-y-2">
                {items.map((a) => (
                  <div
                    key={a.id}
                    className={`flex items-start justify-between gap-4 rounded-lg border border-border p-4 ${
                      a.active ? 'bg-card' : 'bg-card/40'
                    }`}
                  >
                    <div className="min-w-0">
                      <p className={`text-sm ${a.active ? 'text-foreground' : 'text-muted-foreground line-through'}`}>
                        {a.message}
                      </p>
                      <p className="text-[11px] text-muted-foreground mt-1">
                        {new Date(a.createdAt).toLocaleString()}
                      </p>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      {a.active ? (
                        <>
                          <Badge variant="secondary">live</Badge>
                          <Button variant="ghost" size="sm" onClick={() => void retract(a)}>
                            Retract
                          </Button>
                        </>
                      ) : (
                        <Badge variant="outline">retracted</Badge>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
