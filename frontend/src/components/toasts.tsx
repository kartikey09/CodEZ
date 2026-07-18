import { useEffect, useState } from 'react'
import { useRealtime } from '@/lib/realtime'
import { verdictMeta } from '@/lib/format'
import { VerdictBadge } from './verdict-badge'

// Bottom-right toasts for the WebSocket verdict frames — the worker publishes
// {submissionId, verdict} on ch:user:{userId}, the realtime service forwards it only to this
// user's sockets, and this component shows it for four seconds. The frame deliberately carries
// no problem info (the payload is the worker's, verbatim); the submissions page has the full row.

interface Toast {
  key: number
  submissionId: number
  verdict: string
}

export function Toasts() {
  const { onVerdict } = useRealtime()
  const [toasts, setToasts] = useState<Toast[]>([])

  useEffect(() => {
    let seq = 0
    return onVerdict((v) => {
      const key = ++seq + Date.now()
      setToasts((ts) => [...ts, { key, submissionId: v.submissionId, verdict: v.verdict }])
      setTimeout(() => setToasts((ts) => ts.filter((t) => t.key !== key)), 4000)
    })
  }, [onVerdict])

  if (toasts.length === 0) return null

  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((t) => (
        <div
          key={t.key}
          className="flex items-center gap-3 rounded-lg border border-border glass px-4 py-3 shadow-lg"
        >
          <VerdictBadge verdict={t.verdict} />
          <div className="text-sm">
            <span className="text-foreground">Submission #{t.submissionId}</span>
            <span className="text-muted-foreground"> — {verdictMeta(t.verdict).label}</span>
          </div>
        </div>
      ))}
    </div>
  )
}
