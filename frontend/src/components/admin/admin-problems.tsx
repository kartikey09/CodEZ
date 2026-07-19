import { useEffect, useState } from 'react'
import {
  Loader2,
  Save,
  AlertTriangle,
  CheckCircle2,
  RefreshCcw,
  Trash2,
  Plus,
  FlaskConical,
  Eye,
  EyeOff,
  FileText,
} from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import type {
  AdminContest,
  AdminProblemDetail,
  AdminTestCase,
  ProblemAdminResponse,
  UpdateProblem,
} from '@/lib/types'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

/**
 * Problem management (Day 14). The Build tab creates things from scratch; this one manages what
 * already exists: pick a contest, see its problems, edit statement/limits in place, inspect and
 * curate test cases, and rejudge.
 *
 * Hidden test data is never sent to the browser — the server returns sizes for hidden rows, so
 * "edit a hidden test" is delete + re-add. That's deliberate: an admin session shouldn't double as
 * a way to read the answer key.
 */

const inputCls =
  'h-10 px-3 w-full rounded-md bg-input border border-border text-sm text-foreground focus:outline-none focus:border-accent focus:ring-2 focus:ring-accent/30 transition-colors'
const textareaCls =
  'px-3 py-2 w-full rounded-md bg-input border border-border text-sm text-foreground font-mono focus:outline-none focus:border-accent focus:ring-2 focus:ring-accent/30 transition-colors resize-y'

function errMsg(err: unknown): string {
  return err instanceof ApiError ? err.message : 'Something went wrong.'
}

export function AdminProblems() {
  const [contests, setContests] = useState<AdminContest[]>([])
  const [contestId, setContestId] = useState<number | null>(null)
  const [problems, setProblems] = useState<ProblemAdminResponse[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [rejudging, setRejudging] = useState(false)

  useEffect(() => {
    void (async () => {
      try {
        const cs = await api.admin.listContests()
        setContests(cs)
        const running = cs.find((c) => c.state === 'running') ?? cs[0]
        setContestId(running ? running.id : null)
      } catch (err) {
        setError(errMsg(err))
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  const loadProblems = async (cid: number) => {
    try {
      const list = await api.admin.listProblems(cid)
      setProblems(list)
      setSelectedId((prev) => (list.some((p) => p.id === prev) ? prev : (list[0]?.id ?? null)))
      setError(null)
    } catch (err) {
      setError(errMsg(err))
    }
  }

  useEffect(() => {
    if (contestId == null) return
    const t = window.setTimeout(() => void loadProblems(contestId), 0)
    return () => window.clearTimeout(t)
  }, [contestId])

  const rejudgeWholeContest = async () => {
    if (contestId == null) return
    setRejudging(true)
    setNotice(null)
    try {
      const res = await api.admin.rejudgeContest(contestId)
      setNotice(
        res.requeued === 0
          ? 'Nothing to rejudge — no judged submissions in this contest yet.'
          : `Requeued ${res.requeued} submission${res.requeued === 1 ? '' : 's'}. The board updates as verdicts land.`,
      )
    } catch (err) {
      setError(errMsg(err))
    } finally {
      setRejudging(false)
    }
  }

  return (
    <div className="flex-1 w-full overflow-y-auto p-8 font-sans">
      <div className="max-w-5xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <h2 className="text-3xl font-bold text-foreground tracking-tight">Problems</h2>
          <Button variant="outline" size="sm" disabled={contestId == null || rejudging} onClick={() => void rejudgeWholeContest()}>
            {rejudging ? <Loader2 size={14} className="animate-spin" /> : <RefreshCcw size={14} />}
            Rejudge contest
          </Button>
        </div>

        {loading ? (
          <div className="flex items-center justify-center h-40">
            <Loader2 className="animate-spin h-8 w-8 text-accent" />
          </div>
        ) : (
          <>
            <div className="flex items-center gap-3 mb-4">
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

            {notice && (
              <div className="flex items-center gap-2 text-sm text-green-500 bg-green-500/10 border border-green-500/30 rounded-lg px-4 py-2.5 mb-4">
                <CheckCircle2 size={15} /> {notice}
              </div>
            )}
            {error && (
              <div className="flex items-center gap-2 text-sm text-destructive bg-destructive/10 border border-destructive/30 rounded-lg px-4 py-2.5 mb-4">
                <AlertTriangle size={15} /> {error}
              </div>
            )}

            {problems.length === 0 ? (
              <div className="text-center text-muted-foreground py-12 bg-card rounded-2xl border border-border">
                <FileText size={20} className="mx-auto mb-2" />
                No problems in this contest yet — create one from the Build tab.
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-[200px_1fr] gap-6">
                <nav className="space-y-1">
                  {problems.map((p) => (
                    <button
                      key={p.id}
                      type="button"
                      onClick={() => setSelectedId(p.id)}
                      className={`w-full text-left px-3 py-2 rounded-lg border text-sm transition ${
                        p.id === selectedId
                          ? 'bg-accent/10 border-accent/40 text-foreground'
                          : 'bg-card border-border text-muted-foreground hover:text-foreground'
                      }`}
                    >
                      <span className="font-mono font-semibold mr-2">{p.label}</span>
                      {p.title}
                    </button>
                  ))}
                </nav>

                {selectedId != null && (
                  <ProblemEditor
                    key={selectedId}
                    problemId={selectedId}
                    onSaved={() => contestId != null && void loadProblems(contestId)}
                  />
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}

function ProblemEditor({ problemId, onSaved }: { problemId: number; onSaved: () => void }) {
  const [detail, setDetail] = useState<AdminProblemDetail | null>(null)
  const [label, setLabel] = useState('')
  const [title, setTitle] = useState('')
  const [statementMd, setStatementMd] = useState('')
  const [timeLimitMs, setTimeLimitMs] = useState(1000)
  const [memoryLimitMb, setMemoryLimitMb] = useState(256)
  const [saving, setSaving] = useState(false)
  const [msg, setMsg] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null)

  const load = async () => {
    try {
      const d = await api.admin.getProblem(problemId)
      setDetail(d)
      setLabel(d.label)
      setTitle(d.title)
      setStatementMd(d.statementMd)
      setTimeLimitMs(d.timeLimitMs)
      setMemoryLimitMb(d.memoryLimitMb)
    } catch (err) {
      setMsg({ kind: 'err', text: errMsg(err) })
    }
  }

  useEffect(() => {
    const t = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(t)
  }, [problemId])

  if (!detail) {
    return (
      <div className="flex items-center justify-center h-40">
        <Loader2 className="animate-spin h-6 w-6 text-accent" />
      </div>
    )
  }

  const dirty =
    label !== detail.label ||
    title !== detail.title ||
    statementMd !== detail.statementMd ||
    timeLimitMs !== detail.timeLimitMs ||
    memoryLimitMb !== detail.memoryLimitMb

  const save = async () => {
    setSaving(true)
    setMsg(null)
    const patch: UpdateProblem = {}
    if (label !== detail.label) patch.label = label
    if (title !== detail.title) patch.title = title
    if (statementMd !== detail.statementMd) patch.statementMd = statementMd
    if (timeLimitMs !== detail.timeLimitMs) patch.timeLimitMs = timeLimitMs
    if (memoryLimitMb !== detail.memoryLimitMb) patch.memoryLimitMb = memoryLimitMb
    try {
      await api.admin.updateProblem(problemId, patch)
      setMsg({ kind: 'ok', text: 'Saved' })
      await load()
      onSaved()
    } catch (err) {
      setMsg({ kind: 'err', text: errMsg(err) })
    } finally {
      setSaving(false)
    }
  }

  const rejudgeThis = async () => {
    setMsg(null)
    try {
      const res = await api.admin.rejudgeProblem(problemId)
      setMsg({
        kind: 'ok',
        text:
          res.requeued === 0
            ? 'Nothing to rejudge — no judged submissions for this problem.'
            : `Requeued ${res.requeued} submission${res.requeued === 1 ? '' : 's'}.`,
      })
      await load()
    } catch (err) {
      setMsg({ kind: 'err', text: errMsg(err) })
    }
  }

  return (
    <div className="space-y-6">
      <div className="bg-card rounded-xl border border-border p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-foreground">Statement &amp; limits</h3>
          <div className="flex items-center gap-2">
            <Badge variant="secondary">tests v{detail.testDataVersion}</Badge>
            {detail.pendingSubmissions > 0 && (
              <Badge variant="outline">{detail.pendingSubmissions} judging</Badge>
            )}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3 mb-3">
          <label className="block">
            <span className="text-xs text-muted-foreground mb-1 block">Label</span>
            <input value={label} onChange={(e) => setLabel(e.target.value)} className={inputCls} />
          </label>
          <label className="block md:col-span-3">
            <span className="text-xs text-muted-foreground mb-1 block">Title</span>
            <input value={title} onChange={(e) => setTitle(e.target.value)} className={inputCls} />
          </label>
        </div>

        <label className="block mb-3">
          <span className="text-xs text-muted-foreground mb-1 block">Statement (Markdown)</span>
          <textarea
            value={statementMd}
            onChange={(e) => setStatementMd(e.target.value)}
            rows={10}
            className={textareaCls}
          />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="block">
            <span className="text-xs text-muted-foreground mb-1 block">Time limit (ms)</span>
            <input
              type="number"
              value={timeLimitMs}
              onChange={(e) => setTimeLimitMs(Number(e.target.value))}
              className={inputCls}
            />
          </label>
          <label className="block">
            <span className="text-xs text-muted-foreground mb-1 block">Memory limit (MB)</span>
            <input
              type="number"
              value={memoryLimitMb}
              onChange={(e) => setMemoryLimitMb(Number(e.target.value))}
              className={inputCls}
            />
          </label>
        </div>

        <div className="flex items-center justify-end gap-3 mt-4">
          {msg && (
            <span className={`flex items-center gap-1.5 text-sm ${msg.kind === 'ok' ? 'text-green-500' : 'text-destructive'}`}>
              {msg.kind === 'ok' ? <CheckCircle2 size={15} /> : <AlertTriangle size={15} />}
              {msg.text}
            </span>
          )}
          <Button variant="outline" size="sm" onClick={() => void rejudgeThis()}>
            <RefreshCcw size={14} /> Rejudge problem
          </Button>
          <Button size="sm" disabled={!dirty || saving} onClick={() => void save()}>
            {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
            Save changes
          </Button>
        </div>
      </div>

      <TestCasePanel problemId={problemId} tests={detail.tests} onChanged={() => void load()} />
    </div>
  )
}

function TestCasePanel({
  problemId,
  tests,
  onChanged,
}: {
  problemId: number
  tests: AdminTestCase[]
  onChanged: () => void
}) {
  const [adding, setAdding] = useState(false)
  const [ordinal, setOrdinal] = useState(() => (tests.length ? Math.max(...tests.map((t) => t.ordinal)) + 1 : 1))
  const [input, setInput] = useState('')
  const [expectedOutput, setExpectedOutput] = useState('')
  const [sample, setSample] = useState(false)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  const add = async () => {
    setBusy(true)
    setErr(null)
    try {
      await api.adminUploadTests(problemId, [{ ordinal, input, expectedOutput, sample }])
      setInput('')
      setExpectedOutput('')
      setOrdinal(ordinal + 1)
      setAdding(false)
      onChanged()
    } catch (e) {
      setErr(errMsg(e))
    } finally {
      setBusy(false)
    }
  }

  const remove = async (t: AdminTestCase) => {
    setErr(null)
    try {
      await api.admin.deleteTestCase(problemId, t.ordinal)
      onChanged()
    } catch (e) {
      setErr(errMsg(e))
    }
  }

  return (
    <div className="bg-card rounded-xl border border-border p-5">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <FlaskConical size={16} className="text-accent" />
          <h3 className="text-lg font-semibold text-foreground">Test cases</h3>
          <span className="text-xs text-muted-foreground">({tests.length})</span>
        </div>
        <Button size="sm" variant="outline" onClick={() => setAdding((v) => !v)}>
          <Plus size={14} /> Add test
        </Button>
      </div>

      <p className="text-[11px] text-muted-foreground mb-3">
        Hidden test data stays on the server — only sizes are shown here. To change a hidden test,
        delete it and add it again. Every change bumps the test-data version so workers stop serving
        cached tests.
      </p>

      {err && (
        <div className="flex items-center gap-2 text-sm text-destructive mb-3">
          <AlertTriangle size={15} /> {err}
        </div>
      )}

      {adding && (
        <div className="rounded-lg border border-border p-4 mb-4 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <label className="block">
              <span className="text-xs text-muted-foreground mb-1 block">Ordinal</span>
              <input
                type="number"
                value={ordinal}
                onChange={(e) => setOrdinal(Number(e.target.value))}
                className={inputCls}
              />
            </label>
            <label className="flex items-end gap-2 pb-2">
              <input type="checkbox" checked={sample} onChange={(e) => setSample(e.target.checked)} />
              <span className="text-sm text-muted-foreground">Sample (visible to students)</span>
            </label>
          </div>
          <label className="block">
            <span className="text-xs text-muted-foreground mb-1 block">Input</span>
            <textarea value={input} onChange={(e) => setInput(e.target.value)} rows={3} className={textareaCls} />
          </label>
          <label className="block">
            <span className="text-xs text-muted-foreground mb-1 block">Expected output</span>
            <textarea
              value={expectedOutput}
              onChange={(e) => setExpectedOutput(e.target.value)}
              rows={3}
              className={textareaCls}
            />
          </label>
          <div className="flex justify-end">
            <Button size="sm" disabled={busy || input === '' || expectedOutput === ''} onClick={() => void add()}>
              {busy ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
              Add
            </Button>
          </div>
        </div>
      )}

      {tests.length === 0 ? (
        <p className="text-sm text-muted-foreground py-4 text-center">No test cases yet.</p>
      ) : (
        <div className="overflow-hidden rounded-lg border border-border">
          <table className="w-full text-sm">
            <thead className="bg-secondary/40 text-muted-foreground">
              <tr className="text-left">
                <th className="px-3 py-2 font-semibold">#</th>
                <th className="px-3 py-2 font-semibold">Kind</th>
                <th className="px-3 py-2 font-semibold">Input</th>
                <th className="px-3 py-2 font-semibold">Expected</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {tests.map((t) => (
                <tr key={t.id} className="bg-card hover:bg-secondary/20 transition-colors align-top">
                  <td className="px-3 py-2 font-mono">{t.ordinal}</td>
                  <td className="px-3 py-2">
                    {t.sample ? (
                      <span className="inline-flex items-center gap-1 text-xs text-accent">
                        <Eye size={12} /> sample
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                        <EyeOff size={12} /> hidden
                      </span>
                    )}
                  </td>
                  <td className="px-3 py-2 font-mono text-xs max-w-[200px] truncate">
                    {t.input !== null ? t.input : <span className="text-muted-foreground">{t.inputBytes} B</span>}
                  </td>
                  <td className="px-3 py-2 font-mono text-xs max-w-[200px] truncate">
                    {t.expectedOutput !== null ? (
                      t.expectedOutput
                    ) : (
                      <span className="text-muted-foreground">{t.expectedOutputBytes} B</span>
                    )}
                  </td>
                  <td className="px-3 py-2 text-right">
                    <button
                      type="button"
                      title={`Delete test ${t.ordinal}`}
                      aria-label={`Delete test ${t.ordinal}`}
                      onClick={() => void remove(t)}
                      className="p-2 rounded-md text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition"
                    >
                      <Trash2 size={15} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
