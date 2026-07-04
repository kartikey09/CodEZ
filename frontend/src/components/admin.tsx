import { useState } from 'react'
import { api, ApiError } from '@/lib/api'
import type { ContestState, TestCaseUpload } from '@/lib/types'
import { Button } from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import {
  Loader2,
  Trophy,
  FileText,
  FlaskConical,
  RefreshCcw,
  Plus,
  Trash2,
  CheckCircle2,
  AlertCircle,
} from 'lucide-react'

// ----- small shared pieces -----

function Field({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">{label}</label>
      {children}
    </div>
  )
}

const inputCls =
  'h-10 px-3 rounded-md bg-input border border-border text-sm text-foreground focus:outline-none focus:border-accent focus:ring-2 focus:ring-accent/30 transition-colors'

const textareaCls =
  'px-3 py-2 rounded-md bg-input border border-border text-sm text-foreground font-mono focus:outline-none focus:border-accent focus:ring-2 focus:ring-accent/30 transition-colors resize-y'

function Feedback({ ok, error }: { ok: string | null; error: string | null }) {
  if (!ok && !error) return null
  return ok ? (
    <div
      role="status"
      className="flex items-start gap-2 text-sm text-green-400 bg-green-500/10 border border-green-500/30 rounded-md px-3 py-2"
    >
      <CheckCircle2 size={16} className="mt-0.5 shrink-0" />
      <span>{ok}</span>
    </div>
  ) : (
    <div
      role="alert"
      className="flex items-start gap-2 text-sm text-destructive bg-destructive/10 border border-destructive/30 rounded-md px-3 py-2"
    >
      <AlertCircle size={16} className="mt-0.5 shrink-0" />
      <span>{error}</span>
    </div>
  )
}

function SubmitButton({ loading, label }: { loading: boolean; label: string }) {
  return (
    <Button
      type="submit"
      disabled={loading}
      className="h-10 mt-1 bg-accent text-accent-foreground hover:bg-accent/90 font-semibold self-start px-6"
    >
      {loading ? (
        <>
          <Loader2 size={16} className="animate-spin motion-reduce:animate-none" />
          Working…
        </>
      ) : (
        label
      )}
    </Button>
  )
}

function errMsg(err: unknown): string {
  return err instanceof ApiError ? err.message : 'Something went wrong. Try again.'
}

/** datetime-local value ("2026-07-02T10:00") -> ISO instant the backend expects. */
function toInstant(local: string): string {
  return new Date(local).toISOString()
}

// ----- Create Contest -----

function CreateContest() {
  const [title, setTitle] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [state, setState] = useState<ContestState>('draft')
  const [loading, setLoading] = useState(false)
  const [ok, setOk] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (loading) return
    setOk(null)
    setError(null)

    if (!title.trim() || !startsAt || !endsAt) {
      setError('Title, start and end are all required.')
      return
    }
    if (new Date(endsAt) <= new Date(startsAt)) {
      setError('End must be after start.')
      return
    }

    setLoading(true)
    try {
      const c = await api.adminCreateContest({
        title: title.trim(),
        startsAt: toInstant(startsAt),
        endsAt: toInstant(endsAt),
        state,
      })
      setOk(`Contest #${c.id} "${c.title}" created (${c.state}).`)
      setTitle('')
      setStartsAt('')
      setEndsAt('')
      setState('draft')
    } catch (err) {
      setError(errMsg(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4 max-w-xl">
      <Field label="Title">
        <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputCls} placeholder="IIIT-B Spring Contest" />
      </Field>
      <div className="grid grid-cols-2 gap-4">
        <Field label="Starts at">
          <input type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} className={inputCls} />
        </Field>
        <Field label="Ends at">
          <input type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} className={inputCls} />
        </Field>
      </div>
      <Field label="State">
        <select value={state} onChange={(e) => setState(e.target.value as ContestState)} className={inputCls}>
          <option value="draft">draft</option>
          <option value="published">published</option>
          <option value="running">running</option>
          <option value="finished">finished</option>
        </select>
      </Field>
      <Feedback ok={ok} error={error} />
      <SubmitButton loading={loading} label="Create contest" />
    </form>
  )
}

// ----- Create Problem -----

function CreateProblem() {
  const [contestId, setContestId] = useState('')
  const [label, setLabel] = useState('')
  const [title, setTitle] = useState('')
  const [statementMd, setStatementMd] = useState('')
  const [timeLimitMs, setTimeLimitMs] = useState('1000')
  const [memoryLimitMb, setMemoryLimitMb] = useState('256')
  const [loading, setLoading] = useState(false)
  const [ok, setOk] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (loading) return
    setOk(null)
    setError(null)

    const cid = Number(contestId)
    if (!Number.isFinite(cid) || contestId === '') {
      setError('Contest ID must be a number.')
      return
    }
    if (!label.trim() || !title.trim() || !statementMd.trim()) {
      setError('Label, title and statement are all required.')
      return
    }

    setLoading(true)
    try {
      const p = await api.adminCreateProblem({
        contestId: cid,
        label: label.trim(),
        title: title.trim(),
        statementMd,
        timeLimitMs: Number(timeLimitMs) || 0,
        memoryLimitMb: Number(memoryLimitMb) || 0,
      })
      setOk(`Problem #${p.id} (${p.label} — ${p.title}) created in contest ${p.contestId}. Use this ID to upload test cases.`)
      setLabel('')
      setTitle('')
      setStatementMd('')
    } catch (err) {
      setError(errMsg(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4 max-w-2xl">
      <div className="grid grid-cols-2 gap-4">
        <Field label="Contest ID">
          <input
            value={contestId}
            onChange={(e) => setContestId(e.target.value.replace(/[^0-9]/g, ''))}
            className={inputCls}
            placeholder="1"
          />
        </Field>
        <Field label="Label">
          <input value={label} onChange={(e) => setLabel(e.target.value)} className={inputCls} placeholder="A" />
        </Field>
      </div>
      <Field label="Title">
        <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputCls} placeholder="Two Sum" />
      </Field>
      <Field label="Statement (Markdown)">
        <textarea
          value={statementMd}
          onChange={(e) => setStatementMd(e.target.value)}
          className={textareaCls}
          rows={10}
          placeholder={'## Problem\n\nGiven an array…\n\n### Input\n…\n\n### Output\n…'}
        />
      </Field>
      <div className="grid grid-cols-2 gap-4">
        <Field label="Time limit (ms)">
          <input
            value={timeLimitMs}
            onChange={(e) => setTimeLimitMs(e.target.value.replace(/[^0-9]/g, ''))}
            className={inputCls}
          />
        </Field>
        <Field label="Memory limit (MB)">
          <input
            value={memoryLimitMb}
            onChange={(e) => setMemoryLimitMb(e.target.value.replace(/[^0-9]/g, ''))}
            className={inputCls}
          />
        </Field>
      </div>
      <Feedback ok={ok} error={error} />
      <SubmitButton loading={loading} label="Create problem" />
    </form>
  )
}

// ----- Upload Test Cases -----

interface TestDraft {
  input: string
  expectedOutput: string
  sample: boolean
}

function UploadTests() {
  const [problemId, setProblemId] = useState('')
  const [startOrdinal, setStartOrdinal] = useState('1')
  const [tests, setTests] = useState<TestDraft[]>([{ input: '', expectedOutput: '', sample: true }])
  const [loading, setLoading] = useState(false)
  const [ok, setOk] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const update = (i: number, patch: Partial<TestDraft>) =>
    setTests((ts) => ts.map((t, j) => (j === i ? { ...t, ...patch } : t)))

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (loading) return
    setOk(null)
    setError(null)

    const pid = Number(problemId)
    if (!Number.isFinite(pid) || problemId === '') {
      setError('Problem ID must be a number.')
      return
    }
    if (tests.some((t) => t.expectedOutput === '')) {
      setError('Every test needs an expected output.')
      return
    }

    const base = Number(startOrdinal) || 1
    const payload: TestCaseUpload[] = tests.map((t, i) => ({
      ordinal: base + i,
      input: t.input,
      expectedOutput: t.expectedOutput,
      sample: t.sample,
    }))

    setLoading(true)
    try {
      const res = await api.adminUploadTests(pid, payload)
      setOk(`${res.added} test case${res.added === 1 ? '' : 's'} added to problem ${pid}.`)
      setTests([{ input: '', expectedOutput: '', sample: false }])
      setStartOrdinal(String(base + payload.length))
    } catch (err) {
      setError(errMsg(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4 max-w-3xl">
      <div className="grid grid-cols-2 gap-4 max-w-md">
        <Field label="Problem ID">
          <input
            value={problemId}
            onChange={(e) => setProblemId(e.target.value.replace(/[^0-9]/g, ''))}
            className={inputCls}
            placeholder="1"
          />
        </Field>
        <Field label="First ordinal">
          <input
            value={startOrdinal}
            onChange={(e) => setStartOrdinal(e.target.value.replace(/[^0-9]/g, ''))}
            className={inputCls}
          />
        </Field>
      </div>

      <p className="text-xs text-muted-foreground">
        Uploads append — existing tests are never overwritten. Ordinals are assigned sequentially from the first
        ordinal. Mark a test as sample to show it on the problem page.
      </p>

      {tests.map((t, i) => (
        <div key={i} className="border border-border rounded-xl p-4 bg-card flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold text-foreground">
              Test #{(Number(startOrdinal) || 1) + i}
            </span>
            <div className="flex items-center gap-4">
              <label className="flex items-center gap-2 text-sm text-muted-foreground cursor-pointer">
                <input
                  type="checkbox"
                  checked={t.sample}
                  onChange={(e) => update(i, { sample: e.target.checked })}
                  className="accent-[var(--accent)]"
                />
                Sample (visible to contestants)
              </label>
              {tests.length > 1 && (
                <button
                  type="button"
                  onClick={() => setTests((ts) => ts.filter((_, j) => j !== i))}
                  className="text-muted-foreground hover:text-destructive transition"
                  aria-label={`Remove test ${i + 1}`}
                >
                  <Trash2 size={16} />
                </button>
              )}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Input (stdin)">
              <textarea
                value={t.input}
                onChange={(e) => update(i, { input: e.target.value })}
                className={textareaCls}
                rows={4}
              />
            </Field>
            <Field label="Expected output (stdout)">
              <textarea
                value={t.expectedOutput}
                onChange={(e) => update(i, { expectedOutput: e.target.value })}
                className={textareaCls}
                rows={4}
              />
            </Field>
          </div>
        </div>
      ))}

      <button
        type="button"
        onClick={() => setTests((ts) => [...ts, { input: '', expectedOutput: '', sample: false }])}
        className="self-start flex items-center gap-1.5 text-sm font-medium text-accent hover:text-accent/80 transition"
      >
        <Plus size={16} /> Add another test
      </button>

      <Feedback ok={ok} error={error} />
      <SubmitButton loading={loading} label={`Upload ${tests.length} test${tests.length === 1 ? '' : 's'}`} />
    </form>
  )
}

// ----- Rebuild Standings -----

function RebuildStandings() {
  const [contestId, setContestId] = useState('')
  const [loading, setLoading] = useState(false)
  const [ok, setOk] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (loading) return
    setOk(null)
    setError(null)

    const cid = Number(contestId)
    if (!Number.isFinite(cid) || contestId === '') {
      setError('Contest ID must be a number.')
      return
    }

    setLoading(true)
    try {
      const res = await api.adminRebuildStandings(cid)
      setOk(`Standings for contest ${cid} rebuilt — ${res.users} user${res.users === 1 ? '' : 's'} rescored.`)
    } catch (err) {
      setError(errMsg(err))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4 max-w-md">
      <p className="text-sm text-muted-foreground">
        Forces a full recompute of a contest's leaderboard from the submissions table. Rarely needed — the board
        self-rebuilds — but useful after a bulk data fix or a rejudge.
      </p>
      <Field label="Contest ID">
        <input
          value={contestId}
          onChange={(e) => setContestId(e.target.value.replace(/[^0-9]/g, ''))}
          className={inputCls}
          placeholder="1"
        />
      </Field>
      <Feedback ok={ok} error={error} />
      <SubmitButton loading={loading} label="Rebuild standings" />
    </form>
  )
}

// ----- page -----

const tabTriggerCls =
  'gap-2 rounded-md data-[state=active]:bg-accent data-[state=active]:text-accent-foreground text-muted-foreground hover:text-foreground'

export function Admin() {
  return (
    <div className="flex-1 w-full overflow-y-auto p-8 font-sans">
      <div className="max-w-5xl mx-auto">
        <h2 className="text-3xl font-bold text-white tracking-tight mb-2">Admin</h2>
        <p className="text-sm text-muted-foreground mb-8">
          Build a contest: create the contest, add problems, upload test cases, and rebuild standings if needed.
        </p>

        <Tabs defaultValue="contest">
          <TabsList className="gap-1 bg-secondary/40 rounded-lg p-1 self-start mb-6">
            <TabsTrigger value="contest" className={tabTriggerCls}>
              <Trophy size={15} /> Create contest
            </TabsTrigger>
            <TabsTrigger value="problem" className={tabTriggerCls}>
              <FileText size={15} /> Create problem
            </TabsTrigger>
            <TabsTrigger value="tests" className={tabTriggerCls}>
              <FlaskConical size={15} /> Upload tests
            </TabsTrigger>
            <TabsTrigger value="rebuild" className={tabTriggerCls}>
              <RefreshCcw size={15} /> Rebuild standings
            </TabsTrigger>
          </TabsList>

          <TabsContent value="contest">
            <CreateContest />
          </TabsContent>
          <TabsContent value="problem">
            <CreateProblem />
          </TabsContent>
          <TabsContent value="tests">
            <UploadTests />
          </TabsContent>
          <TabsContent value="rebuild">
            <RebuildStandings />
          </TabsContent>
        </Tabs>
      </div>
    </div>
  )
}
