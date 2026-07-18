import { useEffect, useRef, useState } from 'react'
import { Megaphone, X } from 'lucide-react'
import { api } from '@/lib/api'
import type { Announcement } from '@/lib/types'

/**
 * Student announcement banner (Day 13). Polls GET /api/announcements every 30s (this
 * frontend polls rather than using the realtime socket) and shows the active notices for
 * the running contest. Each can be dismissed for the session; a fresh notice reappears.
 * Renders nothing when there's nothing to show, so it's safe to always mount.
 */

const POLL_MS = 30000

export function AnnouncementBanner() {
  const [items, setItems] = useState<Announcement[]>([])
  const [dismissed, setDismissed] = useState<Set<number>>(new Set())
  const timer = useRef<number | null>(null)

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      try {
        const data = await api.getAnnouncements()
        if (!cancelled) setItems(data)
      } catch {
        // banner is best-effort; ignore transient failures
      } finally {
        if (!cancelled) timer.current = window.setTimeout(load, POLL_MS)
      }
    }
    const t = window.setTimeout(() => void load(), 0)
    return () => {
      cancelled = true
      window.clearTimeout(t)
      if (timer.current !== null) window.clearTimeout(timer.current)
    }
  }, [])

  const visible = items.filter((a) => a.active && !dismissed.has(a.id))
  if (visible.length === 0) return null

  return (
    <div className="shrink-0 border-b border-accent/30 bg-accent/10">
      <div className="max-w-5xl mx-auto px-6 py-2 space-y-1">
        {visible.map((a) => (
          <div key={a.id} className="flex items-start gap-3 py-1">
            <Megaphone size={15} className="text-accent mt-0.5 shrink-0" />
            <p className="text-sm text-foreground flex-1">{a.message}</p>
            <button
              type="button"
              onClick={() => setDismissed((prev) => new Set(prev).add(a.id))}
              className="text-muted-foreground hover:text-foreground transition shrink-0"
              aria-label="Dismiss announcement"
            >
              <X size={15} />
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}
