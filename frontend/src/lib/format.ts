/** Verdict display metadata. Names come from the orchestrator's Verdict enum. */
export const VERDICTS: Record<string, { label: string; tone: 'ok' | 'bad' | 'warn' | 'dim' }> = {
  AC: { label: 'Accepted', tone: 'ok' },
  WA: { label: 'Wrong answer', tone: 'bad' },
  TLE: { label: 'Time limit exceeded', tone: 'warn' },
  MLE: { label: 'Memory limit exceeded', tone: 'warn' },
  RE: { label: 'Runtime error', tone: 'bad' },
  CE: { label: 'Compile error', tone: 'warn' },
  IE: { label: 'Judge error', tone: 'dim' },
}

export function verdictMeta(verdict: string | null | undefined) {
  if (!verdict) return { label: '—', tone: 'dim' as const }
  return VERDICTS[verdict] ?? { label: verdict, tone: 'dim' as const }
}

export const TONE_CLASS: Record<'ok' | 'bad' | 'warn' | 'dim', string> = {
  ok: 'text-green-500 bg-green-500/10 border-green-500/30',
  bad: 'text-destructive bg-destructive/10 border-destructive/30',
  warn: 'text-amber-500 bg-amber-500/10 border-amber-500/30',
  dim: 'text-muted-foreground bg-secondary/40 border-border',
}

/** 5025000 -> "1:23:45"; sub-hour -> "23:45". Never negative. */
export function fmtClock(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}

/** ISO instant -> local "14:05:32" (contest ops care about time-of-day). */
export function fmtTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour12: false })
}

/** ISO instant -> local "3 Jul, 14:05". */
export function fmtDateTime(iso: string): string {
  const d = new Date(iso)
  return `${d.toLocaleDateString([], { day: 'numeric', month: 'short' })}, ${d.toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })}`
}
