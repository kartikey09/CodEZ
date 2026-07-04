import { Routes, Route, Navigate, Link, useNavigate, useLocation } from 'react-router-dom'
import { LogOut, Loader2 } from 'lucide-react'
import { useAuth } from '@/auth/AuthContext'
import { ContestProvider, useContest } from '@/lib/contest'
import { RealtimeProvider, useRealtime } from '@/lib/realtime'
import { Login } from '@/components/login'
import { ChangePassword } from '@/components/change-password'
import { ProblemList } from '@/components/problem-list'
import { IDE } from '@/components/ide'
import { Submissions } from '@/components/submissions'
import { Leaderboard } from '@/components/leaderboard'
import { Admin } from '@/components/admin'
import { AdminUsers } from '@/components/admin/admin-users'
import { Countdown } from '@/components/countdown'
import { Toasts } from '@/components/toasts'

const STATUS_DOT: Record<string, { cls: string; title: string }> = {
  open: { cls: 'bg-accent animate-pulse', title: 'Live — WebSocket connected' },
  connecting: { cls: 'bg-amber-500', title: 'Connecting…' },
  closed: { cls: 'bg-destructive', title: 'Offline — polling fallback' },
}

function LiveStatus() {
  const { contest, now } = useContest()
  const { status } = useRealtime()
  const dot = STATUS_DOT[status] ?? STATUS_DOT.closed

  return (
    <div className="flex items-center gap-3">
      {contest && <Countdown contest={contest} now={now} />}
      <span className={`w-2 h-2 rounded-full ${dot.cls}`} title={dot.title} aria-label={dot.title} />
    </div>
  )
}

function NavLink({ to, label }: { to: string; label: string }) {
  const { pathname } = useLocation()
  const active = pathname === to || (to !== '/problems' && pathname.startsWith(`${to}/`))
  return (
    <Link
      to={to}
      className={`text-sm font-medium h-full flex items-center border-b-2 transition ${
        active
          ? 'text-foreground border-accent'
          : 'text-muted-foreground border-transparent hover:text-foreground hover:border-border'
      }`}
    >
      {label}
    </Link>
  )
}

export default function App() {
  const { user, loading, logout } = useAuth()
  const navigate = useNavigate()

  if (loading) {
    return (
      <div className="min-h-screen bg-background text-muted-foreground flex items-center justify-center gap-2">
        <Loader2 className="animate-spin" size={18} /> Loading…
      </div>
    )
  }

  if (!user) return <Login />
  if (user.mustChangePassword) return <ChangePassword />

  const isAdmin = user.role === 'admin'

  return (
    <ContestProvider>
      <RealtimeProvider>
        <div className="min-h-screen bg-background text-foreground flex flex-col">
          <header className="border-b border-border bg-card h-16 flex items-center shrink-0">
            <div className="max-w-full w-full mx-auto px-6 flex items-center justify-between h-full">
              <div className="flex items-center gap-8 h-full">
                <Link to="/problems" className="flex items-center gap-2">
                  <div className="w-9 h-9 bg-accent rounded flex items-center justify-center">
                    <span className="text-accent-foreground font-mono font-bold text-xs">{'</>'}</span>
                  </div>
                  <span className="text-lg font-bold tracking-tight">CodEZ</span>
                </Link>
                <nav className="flex items-center gap-6 h-full">
                  <NavLink to="/problems" label="Problems" />
                  <NavLink to="/submissions" label="My Submissions" />
                  <NavLink to="/leaderboard" label="Leaderboard" />
                  {isAdmin && <NavLink to="/admin" label="Admin" />}
                  {isAdmin && <NavLink to="/admin/users" label="Users" />}
                </nav>
              </div>

              <div className="flex items-center gap-4">
                <LiveStatus />
                <div className="flex flex-col items-end leading-tight">
                  <span className="text-sm font-medium text-foreground">{user.displayName}</span>
                  <span className="text-[11px] text-muted-foreground uppercase tracking-wider">{user.role}</span>
                </div>
                <button
                  onClick={async () => {
                    await logout()
                    navigate('/')
                  }}
                  className="text-sm font-medium text-muted-foreground hover:text-foreground transition flex items-center gap-2"
                >
                  <LogOut size={16} /> Log out
                </button>
              </div>
            </div>
          </header>

          <main className="flex-1 w-full flex overflow-hidden">
            <Routes>
              <Route path="/" element={<Navigate to="/problems" replace />} />
              <Route
                path="/problems"
                element={<ProblemList onSelectProblem={(id) => navigate(`/problems/${id}`)} />}
              />
              <Route
                path="/problems/:id"
                element={
                  <div className="h-full w-full flex overflow-hidden px-4 py-6">
                    <IDE />
                  </div>
                }
              />
              <Route path="/submissions" element={<Submissions />} />
              <Route path="/leaderboard" element={<Leaderboard />} />
              {isAdmin && <Route path="/admin" element={<Admin />} />}
              {isAdmin && <Route path="/admin/users" element={<AdminUsers />} />}
              <Route path="*" element={<Navigate to="/problems" replace />} />
            </Routes>
          </main>

          <Toasts />
        </div>
      </RealtimeProvider>
    </ContestProvider>
  )
}
