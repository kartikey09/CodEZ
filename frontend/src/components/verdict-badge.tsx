import { verdictMeta, TONE_CLASS } from '@/lib/format'

/** Small colored chip for a verdict (AC green, WA/RE red, TLE/MLE/CE amber, IE dim). */
export function VerdictBadge({ verdict }: { verdict: string | null | undefined }) {
  const meta = verdictMeta(verdict)
  return (
    <span
      className={`inline-flex items-center gap-1 rounded border px-2 py-0.5 font-mono text-xs font-semibold ${TONE_CLASS[meta.tone]}`}
      title={meta.label}
    >
      {verdict ?? '—'}
    </span>
  )
}
