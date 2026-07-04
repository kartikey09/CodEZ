import { useEffect, useState } from 'react'
import type { ContestInfo } from '@/lib/types'
import { fmtClock } from '@/lib/format'

// Contest clock, ticking once a second against the *server* clock (now() carries the skew
// computed in ContestProvider). Three phases:
//   before startsAt  -> amber  "starts in mm:ss"
//   inside window    -> green  "hh:mm:ss left"
//   after endsAt     -> dim    "contest over"
export function Countdown({ contest, now }: { contest: ContestInfo; now: () => number }) {
  const [, force] = useState(0)
  useEffect(() => {
    const t = setInterval(() => force((n) => n + 1), 1000)
    return () => clearInterval(t)
  }, [])

  const n = now()
  const start = Date.parse(contest.startsAt)
  const end = Date.parse(contest.endsAt)

  let text: string
  let cls: string
  if (n < start) {
    text = `starts in ${fmtClock(start - n)}`
    cls = 'text-amber-500 border-amber-500/30 bg-amber-500/10'
  } else if (n < end) {
    text = `${fmtClock(end - n)} left`
    cls = 'text-accent border-accent/30 bg-accent/10'
  } else {
    text = 'contest over'
    cls = 'text-muted-foreground border-border bg-secondary/40'
  }

  return (
    <span className={`inline-flex items-center rounded border px-2.5 py-1 font-mono text-xs ${cls}`}>
      {text}
    </span>
  )
}
