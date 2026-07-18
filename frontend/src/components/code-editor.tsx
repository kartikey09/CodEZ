import { useState, useRef, useEffect, useCallback } from 'react'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-dark.css'
import { RotateCcw, Loader2, CheckCircle2, XCircle, AlertTriangle, Play } from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import type { Language, SubmissionStatusResponse, Verdict } from '@/lib/types'
import { Button } from '@/components/ui/button'
import { useRealtime } from '@/lib/realtime'

// Run's own rate limit, independent of Submit's cooldown: max 3 per rolling 12s window.
// This mirrors the backend's lazy fixed-window algorithm exactly (RunRateLimiter) purely for
// responsive UX — the server call is always the authoritative check; a 429 resyncs this state.
const RUN_WINDOW_MS = 12_000
const RUN_MAX_PER_WINDOW = 3

const MAX_SOURCE_BYTES = 65536 // matches app.submission.max-source-bytes

const LANGUAGES: { slug: Language; label: string; hljs: string }[] = [
  { slug: 'java', label: 'Java', hljs: 'java' },
  { slug: 'cpp', label: 'C++', hljs: 'cpp' },
  { slug: 'c', label: 'C', hljs: 'c' },
  { slug: 'python', label: 'Python', hljs: 'python' },
]

const BOILERPLATE: Record<Language, string> = {
  c: `#include <stdio.h>

int main(void) {
    // Read input from stdin, write your answer to stdout.
    return 0;
}`,
  cpp: `#include <bits/stdc++.h>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    // Read input from stdin, write your answer to stdout.
    return 0;
}`,
  java: `import java.util.*;
import java.io.*;

// Judge0 runs the class named "Main".
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // Read input from stdin, write your answer to stdout.
    }
}`,
  python: `import sys

def main():
    data = sys.stdin.buffer.read().split()
    # Read input from stdin, write your answer to stdout.

main()`,
}

const VERDICT_META: Record<Verdict, { label: string; tone: 'ok' | 'bad' | 'warn' }> = {
  AC: { label: 'Accepted', tone: 'ok' },
  WA: { label: 'Wrong Answer', tone: 'bad' },
  TLE: { label: 'Time Limit Exceeded', tone: 'warn' },
  MLE: { label: 'Memory Limit Exceeded', tone: 'warn' },
  RE: { label: 'Runtime Error', tone: 'bad' },
  CE: { label: 'Compilation Error', tone: 'bad' },
  IE: { label: 'Internal Error', tone: 'warn' },
}

const codeKey = (problemId: number, lang: Language) => `codez:code:${problemId}:${lang}`
const utf8Bytes = (s: string) => new TextEncoder().encode(s).length

type Action = 'submit' | 'run'

type Phase =
  | { kind: 'idle' }
  | { kind: 'submitting'; action: Action }
  | { kind: 'judging'; id: number; status: string; action: Action }
  | { kind: 'done'; result: SubmissionStatusResponse }
  | { kind: 'error'; message: string }

export function CodeEditor({ problemId }: { problemId: number }) {
  const { onVerdict } = useRealtime()
  const [language, setLanguage] = useState<Language>(() => {
    const saved = localStorage.getItem('codez:lang')
    return (LANGUAGES.some((l) => l.slug === saved) ? saved : 'java') as Language
  })
  const [code, setCode] = useState(
    () => localStorage.getItem(codeKey(problemId, language)) ?? BOILERPLATE[language],
  )
  const [showLangMenu, setShowLangMenu] = useState(false)
  const [phase, setPhase] = useState<Phase>({ kind: 'idle' })
  const [runRetryAt, setRunRetryAt] = useState<number | null>(null)
  const [nowTick, setNowTick] = useState(() => Date.now())

  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const highlightRef = useRef<HTMLPreElement>(null)
  const pollRef = useRef<number | null>(null)
  const runWindowRef = useRef<{ start: number; count: number } | null>(null)

  const stopPolling = useCallback(() => {
    if (pollRef.current !== null) {
      window.clearTimeout(pollRef.current)
      pollRef.current = null
    }
  }, [])

  // Reload saved code (or boilerplate) when the problem or language changes.
  // Done during render rather than in an effect, so the persist effect below
  // never fires with the old code against the new key.
  const storageKey = codeKey(problemId, language)
  const [loadedKey, setLoadedKey] = useState(storageKey)
  if (loadedKey !== storageKey) {
    setLoadedKey(storageKey)
    setCode(localStorage.getItem(storageKey) ?? BOILERPLATE[language])
  }

  useEffect(() => localStorage.setItem('codez:lang', language), [language])

  // Persist edits per problem+language.
  useEffect(() => {
    localStorage.setItem(codeKey(problemId, language), code)
  }, [code, problemId, language])

  // Syntax highlight overlay.
  const hljsLang = LANGUAGES.find((l) => l.slug === language)?.hljs ?? 'plaintext'
  useEffect(() => {
    if (highlightRef.current) {
      highlightRef.current.innerHTML = hljs.highlight(code, { language: hljsLang, ignoreIllegals: true }).value
    }
  }, [code, hljsLang])

  useEffect(() => stopPolling, [stopPolling])

  // Ticks while a Run rate-limit countdown is showing; stops once it elapses.
  useEffect(() => {
    if (runRetryAt == null) return
    const t = window.setInterval(() => setNowTick(Date.now()), 250)
    return () => window.clearInterval(t)
  }, [runRetryAt])

  const runRetrySecondsLeft = runRetryAt != null ? Math.max(0, Math.ceil((runRetryAt - nowTick) / 1000)) : 0
  const runLimited = runRetrySecondsLeft > 0

  /** Local mirror of the server's fixed-window limiter. Returns false if this click should be blocked. */
  const registerRun = () => {
    const now = Date.now()
    const w = runWindowRef.current
    if (!w || now - w.start >= RUN_WINDOW_MS) {
      runWindowRef.current = { start: now, count: 1 }
      return true
    }
    if (w.count < RUN_MAX_PER_WINDOW) {
      w.count += 1
      if (w.count >= RUN_MAX_PER_WINDOW) {
        setRunRetryAt(w.start + RUN_WINDOW_MS)
      }
      return true
    }
    setRunRetryAt(w.start + RUN_WINDOW_MS)
    return false
  }

  // WS fast path: the realtime service pushes {submissionId, verdict} the moment the worker
  // judges it, often faster than the next poll tick. The frame itself is bare, so fetch the
  // full record; only replace the poll result on success (a failed fetch just leaves the
  // existing poll — the authoritative fallback — to catch up next tick).
  const phaseRef = useRef(phase)
  useEffect(() => {
    phaseRef.current = phase
  }, [phase])

  useEffect(() => {
    return onVerdict((v) => {
      const p = phaseRef.current
      if (p.kind !== 'judging' || p.id !== v.submissionId) return
      void api
        .getSubmission(v.submissionId)
        .then((s) => {
          stopPolling()
          setPhase({ kind: 'done', result: s })
        })
        .catch(() => {})
    })
  }, [onVerdict, stopPolling])

  const syncScroll = (e: React.UIEvent<HTMLTextAreaElement>) => {
    if (highlightRef.current) {
      highlightRef.current.scrollTop = e.currentTarget.scrollTop
      highlightRef.current.scrollLeft = e.currentTarget.scrollLeft
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Tab') {
      e.preventDefault()
      const t = e.currentTarget
      const start = t.selectionStart
      const end = t.selectionEnd
      const next = code.slice(0, start) + '    ' + code.slice(end)
      setCode(next)
      requestAnimationFrame(() => {
        if (textareaRef.current) textareaRef.current.selectionStart = textareaRef.current.selectionEnd = start + 4
      })
    }
  }

  const poll = useCallback(
    (id: number, action: Action) => {
      let attempts = 0
      const maxAttempts = 100 // ~2 min at 1.2s
      const tick = async () => {
        attempts += 1
        if (attempts > maxAttempts) {
          setPhase({ kind: 'error', message: 'Still judging after 2 minutes — check My Submissions later.' })
          return
        }
        try {
          const s = await api.getSubmission(id)
          if (s.status === 'done') {
            setPhase({ kind: 'done', result: s })
            return
          }
          setPhase({ kind: 'judging', id, status: s.status, action })
          pollRef.current = window.setTimeout(() => void tick(), 1200)
        } catch (err) {
          setPhase({ kind: 'error', message: err instanceof ApiError ? err.message : 'Lost contact while judging.' })
        }
      }
      pollRef.current = window.setTimeout(() => void tick(), 800)
    },
    [],
  )

  const runAction = async (action: Action) => {
    stopPolling()
    if (utf8Bytes(code) > MAX_SOURCE_BYTES) {
      setPhase({ kind: 'error', message: `Source is over the ${MAX_SOURCE_BYTES / 1024} KB limit.` })
      return
    }
    setPhase({ kind: 'submitting', action })
    try {
      const { submissionId } =
        action === 'submit'
          ? await api.submit(problemId, language, code)
          : await api.runProblem(problemId, language, code)
      setPhase({ kind: 'judging', id: submissionId, status: 'queued', action })
      poll(submissionId, action)
    } catch (err) {
      if (action === 'run' && err instanceof ApiError && err.status === 429) {
        setRunRetryAt(Date.now() + (err.retryAfterSeconds ?? 12) * 1000)
      }
      setPhase({
        kind: 'error',
        message:
          err instanceof ApiError
            ? err.status === 0
              ? 'Cannot reach contest-api (:8080).'
              : err.message
            : action === 'submit'
              ? 'Submission failed.'
              : 'Run failed.',
      })
    }
  }

  const onSubmit = () => void runAction('submit')

  const onRun = () => {
    if (isBusy || runLimited || !registerRun()) return
    void runAction('run')
  }

  const isBusy = phase.kind === 'submitting' || phase.kind === 'judging'
  const lineCount = code.split('\n').length

  return (
    // Locked to the dark palette regardless of site theme: the syntax highlighter
    // (atom-one-dark) needs a dark surface to stay legible, so this subtree always
    // resolves the `--card`/`--border`/etc. tokens from the `.dark` scope below.
    <div className="dark flex flex-col h-full bg-card border border-border rounded-lg overflow-hidden">
      {/* Toolbar */}
      <div className="flex items-center justify-between px-4 h-[64px] border-b border-border/50 gap-4 bg-secondary/20 shrink-0">
        <div className="relative">
          <button
            onClick={() => setShowLangMenu((s) => !s)}
            className="flex items-center gap-2 px-4 py-2 rounded bg-secondary border border-border text-foreground text-sm font-medium hover:bg-secondary/80 transition"
          >
            {LANGUAGES.find((l) => l.slug === language)?.label}
          </button>
          {showLangMenu && (
            <div className="absolute top-full left-0 mt-2 w-40 bg-secondary border border-border rounded-lg shadow-lg z-50 overflow-hidden">
              {LANGUAGES.map((l) => (
                <button
                  key={l.slug}
                  onClick={() => {
                    setLanguage(l.slug)
                    setShowLangMenu(false)
                  }}
                  className={`w-full text-left px-4 py-2 text-sm transition ${
                    language === l.slug ? 'bg-accent text-accent-foreground' : 'text-foreground hover:bg-secondary/60'
                  }`}
                >
                  {l.label}
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setCode(BOILERPLATE[language])}
            className="text-muted-foreground hover:text-foreground transition p-2"
            title="Reset to template"
          >
            <RotateCcw size={16} />
          </button>
          <button
            onClick={onRun}
            disabled={isBusy || runLimited}
            title={runLimited ? `Try again in ${runRetrySecondsLeft}s` : 'Run against the sample tests only'}
            className="h-9 px-4 rounded-md border border-border text-foreground text-sm font-semibold hover:bg-secondary/60 disabled:opacity-40 disabled:cursor-not-allowed transition flex items-center gap-2"
          >
            {phase.kind === 'submitting' && phase.action === 'run' ? (
              <>
                <Loader2 size={16} className="animate-spin motion-reduce:animate-none" />
                Running…
              </>
            ) : phase.kind === 'judging' && phase.action === 'run' ? (
              <>
                <Loader2 size={16} className="animate-spin motion-reduce:animate-none" />
                Judging…
              </>
            ) : runLimited ? (
              `Retry in ${runRetrySecondsLeft}s`
            ) : (
              <>
                <Play size={16} />
                Run
              </>
            )}
          </button>
          <Button
            onClick={onSubmit}
            disabled={isBusy}
            className="bg-accent text-accent-foreground hover:bg-accent/90 font-semibold"
          >
            {phase.kind === 'submitting' && phase.action === 'submit' ? (
              <>
                <Loader2 size={16} className="animate-spin motion-reduce:animate-none" />
                Submitting…
              </>
            ) : phase.kind === 'judging' && phase.action === 'submit' ? (
              <>
                <Loader2 size={16} className="animate-spin motion-reduce:animate-none" />
                Judging…
              </>
            ) : (
              'Submit'
            )}
          </Button>
        </div>
      </div>

      {/* Editor */}
      <div className="flex-1 flex overflow-hidden relative min-h-0">
        <div className="bg-secondary/30 shrink-0 overflow-hidden pt-3 flex flex-col items-end pr-3 w-12">
          <div className="font-mono text-xs text-muted-foreground/70 select-none">
            {Array.from({ length: lineCount }, (_, i) => (
              <div key={i} className="h-[1.5rem] leading-[1.5rem]">
                {i + 1}
              </div>
            ))}
          </div>
        </div>
        <div className="flex-1 relative overflow-hidden bg-card">
          <pre
            ref={highlightRef}
            className="hljs absolute inset-0 pt-3 pb-4 px-4 font-mono overflow-hidden pointer-events-none whitespace-pre"
            style={{ fontSize: '14px', lineHeight: '1.5rem', backgroundColor: 'transparent' }}
          />
          <textarea
            ref={textareaRef}
            value={code}
            onChange={(e) => setCode(e.target.value)}
            onKeyDown={handleKeyDown}
            onScroll={syncScroll}
            spellCheck={false}
            className="relative pt-3 pb-4 px-4 font-mono bg-transparent text-transparent resize-none focus:outline-none w-full h-full overflow-auto whitespace-pre"
            style={{ fontSize: '14px', lineHeight: '1.5rem', caretColor: '#d4af28' }}
          />
        </div>
      </div>

      {/* Result panel */}
      <div className="border-t border-border bg-[#1A1A1A] shrink-0 min-h-[140px] max-h-[40%] overflow-auto p-6">
        <ResultView phase={phase} />
      </div>
    </div>
  )
}

function ResultView({ phase }: { phase: Phase }) {
  if (phase.kind === 'idle') {
    return <p className="text-sm text-muted-foreground italic">Run against the samples, or submit your solution to see the verdict.</p>
  }
  if (phase.kind === 'submitting') {
    return <p className="text-sm text-muted-foreground">{phase.action === 'run' ? 'Queuing your run…' : 'Queuing your submission…'}</p>
  }
  if (phase.kind === 'judging') {
    return (
      <div className="flex items-center gap-2 text-sm text-accent">
        <Loader2 size={16} className="animate-spin motion-reduce:animate-none" />
        <span>
          {phase.action === 'run' ? 'Run' : 'Submission'} #{phase.id} — {phase.status}…
        </span>
      </div>
    )
  }
  if (phase.kind === 'error') {
    return (
      <div className="flex items-start gap-2 text-sm text-destructive">
        <AlertTriangle size={16} className="mt-0.5 shrink-0" />
        <span>{phase.message}</span>
      </div>
    )
  }

  // done
  if (phase.result.kind === 'run') {
    return <RunResultView result={phase.result} />
  }

  const r = phase.result
  const meta = r.verdict ? VERDICT_META[r.verdict] : { label: r.status, tone: 'warn' as const }
  const toneClass =
    meta.tone === 'ok' ? 'text-green-500' : meta.tone === 'bad' ? 'text-destructive' : 'text-amber-500'
  const Icon = meta.tone === 'ok' ? CheckCircle2 : meta.tone === 'bad' ? XCircle : AlertTriangle

  return (
    <div className="flex flex-col gap-3">
      <div className={`flex items-center gap-2 text-base font-bold ${toneClass}`}>
        <Icon size={18} />
        <span>Verdict: {meta.label}</span>
      </div>
      <div className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-muted-foreground font-mono">
        <span>Submission #{r.id}</span>
        {r.totalTests != null && (
          <span>
            {r.passedTests ?? 0}/{r.totalTests} tests passed
          </span>
        )}
        {r.execTimeMs != null && <span>{r.execTimeMs} ms</span>}
        {r.memoryKb != null && <span>{(r.memoryKb / 1024).toFixed(1)} MB</span>}
      </div>
    </div>
  )
}

/** Run's own results: per-sample-test breakdown (never shown for a real Submit). */
function RunResultView({ result }: { result: SubmissionStatusResponse }) {
  const passed = result.passedTests ?? 0
  const total = result.totalTests ?? result.tests.length

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-2 text-base font-bold text-foreground">
        <span>
          {passed}/{total} sample tests passed
        </span>
      </div>
      <div className="flex flex-col gap-1.5">
        {result.tests.map((t) => {
          const meta = VERDICT_META[t.verdict]
          const ok = t.verdict === 'AC'
          const Icon = ok ? CheckCircle2 : XCircle
          return (
            <div key={t.index} className={`flex items-center gap-2 text-sm ${ok ? 'text-green-500' : 'text-destructive'}`}>
              <Icon size={15} className="shrink-0" />
              <span className="font-mono">Sample {t.index}</span>
              <span className="text-muted-foreground">{meta.label}</span>
              {t.execTimeMs != null && (
                <span className="text-xs text-muted-foreground/70 font-mono ml-auto">{t.execTimeMs} ms</span>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
