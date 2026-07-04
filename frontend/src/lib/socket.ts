import type { StandingsResponse } from '@/lib/types'

// Client for the realtime service. One socket per contest:
//
//   ws(s)://<same-origin>/ws/scoreboard?contestId=N      (Vite proxies /ws -> :8083)
//
// The handshake carries the sid cookie (same-origin GET), so the realtime service authenticates
// it against the shared Redis session. Frames are a {"type":"standings"|"verdict","data":<payload>}
// envelope. On connect the server immediately replays the latest standings snapshot, so a page
// that (re)connects is never blank.
//
// Reconnect: exponential backoff, 1s doubling to a 10s cap, forever until close().
// Keepalive: a "ping" text frame every 30s (the handler answers "pong") so idle sockets aren't
// reaped by intermediaries during a quiet contest.

export type SocketStatus = 'connecting' | 'open' | 'closed'

export interface VerdictEvent {
  submissionId: number
  verdict: string
}

interface Handlers {
  onStandings?: (s: StandingsResponse) => void
  onVerdict?: (v: VerdictEvent) => void
  onStatus?: (s: SocketStatus) => void
}

export class ScoreboardSocket {
  private contestId: number
  private handlers: Handlers
  private ws: WebSocket | null = null
  private stopped = false
  private attempts = 0
  private pingTimer: ReturnType<typeof setInterval> | null = null
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null

  constructor(contestId: number, handlers: Handlers) {
    this.contestId = contestId
    this.handlers = handlers
  }

  connect(): void {
    this.stopped = false
    this.open()
  }

  close(): void {
    this.stopped = true
    this.clearTimers()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.ws?.close()
    this.ws = null
  }

  private open(): void {
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${proto}://${window.location.host}/ws/scoreboard?contestId=${this.contestId}`
    this.handlers.onStatus?.('connecting')

    const ws = new WebSocket(url)
    this.ws = ws

    ws.onopen = () => {
      this.attempts = 0
      this.handlers.onStatus?.('open')
      this.pingTimer = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) ws.send('ping')
      }, 30_000)
    }

    ws.onmessage = (ev: MessageEvent) => {
      let frame: { type?: string; data?: unknown }
      try {
        frame = JSON.parse(String(ev.data))
      } catch {
        return // "pong" and anything non-JSON
      }
      if (frame.type === 'standings') this.handlers.onStandings?.(frame.data as StandingsResponse)
      else if (frame.type === 'verdict') this.handlers.onVerdict?.(frame.data as VerdictEvent)
    }

    ws.onclose = () => {
      this.clearTimers()
      this.handlers.onStatus?.('closed')
      if (!this.stopped) this.scheduleReconnect()
    }

    ws.onerror = () => {
      /* the paired onclose drives reconnection */
    }
  }

  private scheduleReconnect(): void {
    const delay = Math.min(10_000, 1000 * 2 ** this.attempts)
    this.attempts += 1
    this.reconnectTimer = setTimeout(() => this.open(), delay)
  }

  private clearTimers(): void {
    if (this.pingTimer) {
      clearInterval(this.pingTimer)
      this.pingTimer = null
    }
  }
}
