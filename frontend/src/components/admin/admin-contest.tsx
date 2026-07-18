import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Loader2, Save, AlertTriangle, CheckCircle2 } from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import type { AdminContest, UpdateContest } from '@/lib/types'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

/**
 * Contest control (Day 13). Lists every contest and lets the admin edit each one's
 * window and state inline — a partial PATCH sends only what changed. Times use the
 * browser's local zone in the picker and are converted to/from UTC ISO for the API.
 */

const STATES = ['draft', 'published', 'running', 'finished']

// <input type="datetime-local"> wants "YYYY-MM-DDTHH:mm" in LOCAL time; the API speaks
// UTC ISO. Convert both ways.
function isoToLocalInput(iso: string): string {
  const d = new Date(iso)
  const off = d.getTimezoneOffset() * 60000
  return new Date(d.getTime() - off).toISOString().slice(0, 16)
}
function localInputToIso(local: string): string {
  return new Date(local).toISOString()
}

export function AdminContest() {
  const [contests, setContests] = useState<AdminContest[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = async () => {
    try {
      setContests(await api.admin.listContests())
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load contests.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const t = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(t)
  }, [])

  return (
    <div className="flex-1 w-full overflow-y-auto p-8 font-sans">
      <div className="max-w-5xl mx-auto">
        <h2 className="text-3xl font-bold text-foreground tracking-tight mb-8">Contest control</h2>

        {loading ? (
          <div className="flex items-center justify-center h-40">
            <Loader2 className="animate-spin h-8 w-8 text-accent" />
          </div>
        ) : error ? (
          <div className="text-center text-destructive py-12 glass rounded-2xl border border-border">{error}</div>
        ) : contests.length === 0 ? (
          <div className="text-center text-muted-foreground py-12 glass rounded-2xl border border-border">
            No contests yet.
          </div>
        ) : (
          <div className="space-y-4">
            {contests.map((c) => (
              <ContestCard key={c.id} contest={c} onSaved={(u) => setContests((prev) => prev.map((x) => (x.id === u.id ? u : x)))} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function ContestCard({ contest, onSaved }: { contest: AdminContest; onSaved: (c: AdminContest) => void }) {
  const [startsAt, setStartsAt] = useState(isoToLocalInput(contest.startsAt))
  const [endsAt, setEndsAt] = useState(isoToLocalInput(contest.endsAt))
  const [state, setState] = useState(contest.state)
  const [saving, setSaving] = useState(false)
  const [msg, setMsg] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null)

  const dirty =
    isoToLocalInput(contest.startsAt) !== startsAt ||
    isoToLocalInput(contest.endsAt) !== endsAt ||
    contest.state !== state

  const save = async () => {
    setSaving(true)
    setMsg(null)
    const patch: UpdateContest = {}
    if (isoToLocalInput(contest.startsAt) !== startsAt) patch.startsAt = localInputToIso(startsAt)
    if (isoToLocalInput(contest.endsAt) !== endsAt) patch.endsAt = localInputToIso(endsAt)
    if (contest.state !== state) patch.state = state
    try {
      const updated = await api.admin.updateContest(contest.id, patch)
      onSaved(updated)
      setMsg({ kind: 'ok', text: 'Saved' })
    } catch (err) {
      setMsg({ kind: 'err', text: err instanceof ApiError ? err.message : 'Save failed.' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="glass rounded-xl border border-border p-5">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <h3 className="text-lg font-semibold text-foreground">{contest.title}</h3>
          <Badge variant={contest.state === 'running' ? 'default' : 'secondary'}>{contest.state}</Badge>
          <span className="text-xs text-muted-foreground font-mono">#{contest.id}</span>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Field label="Starts">
          <input
            type="datetime-local"
            value={startsAt}
            onChange={(e) => setStartsAt(e.target.value)}
            className="w-full bg-input border border-border rounded-md px-3 py-2 text-sm text-foreground outline-none focus:border-accent"
          />
        </Field>
        <Field label="Ends">
          <input
            type="datetime-local"
            value={endsAt}
            onChange={(e) => setEndsAt(e.target.value)}
            className="w-full bg-input border border-border rounded-md px-3 py-2 text-sm text-foreground outline-none focus:border-accent"
          />
        </Field>
        <Field label="State">
          <select
            value={state}
            onChange={(e) => setState(e.target.value)}
            className="w-full bg-input border border-border rounded-md px-3 py-2 text-sm text-foreground outline-none focus:border-accent"
          >
            {STATES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </Field>
      </div>

      <div className="flex items-center justify-end gap-3 mt-4">
        {msg && (
          <span
            className={`flex items-center gap-1.5 text-sm ${
              msg.kind === 'ok' ? 'text-green-500' : 'text-destructive'
            }`}
          >
            {msg.kind === 'ok' ? <CheckCircle2 size={15} /> : <AlertTriangle size={15} />}
            {msg.text}
          </span>
        )}
        <Button size="sm" disabled={!dirty || saving} onClick={() => void save()}>
          {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
          Save changes
        </Button>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="text-xs text-muted-foreground mb-1 block">{label}</span>
      {children}
    </label>
  )
}
