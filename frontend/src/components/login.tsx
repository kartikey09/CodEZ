import { useState } from 'react'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/button'
import { Eye, EyeOff, Loader2, Terminal, AlertCircle } from 'lucide-react'

export function Login() {
  const { refresh } = useAuth()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (loading) return
    setError(null)

    if (!loginId.trim() || !password) {
      setError('Enter your login ID and password.')
      return
    }

    setLoading(true)
    try {
      // Sets the httpOnly `sid` cookie; refresh() then hydrates auth state.
      await api.login(loginId.trim(), password)
      await refresh()
    } catch (err) {
      if (err instanceof ApiError) {
        setError(
          err.status === 0
            ? 'Cannot reach the server. Are auth-service (:8081) and contest-api (:8080) running?'
            : err.message,
        )
      } else {
        setError('Something went wrong. Try again.')
      }
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-background text-foreground flex items-center justify-center px-4 py-10 font-sans">
      <div className="w-full max-w-sm">
        <div className="flex items-center gap-2.5 mb-8 justify-center">
          <div className="w-9 h-9 bg-accent rounded-md flex items-center justify-center">
            <span className="text-accent-foreground font-mono font-bold text-sm">{'</>'}</span>
          </div>
          <span className="text-xl font-bold tracking-tight">CodEZ</span>
        </div>

        <div className="bg-card border border-border rounded-xl overflow-hidden">
          <div className="px-7 pt-7 pb-1">
            <h1 className="text-lg font-bold text-white">Sign in</h1>
            <p className="text-sm text-muted-foreground mt-1">
              Use the login ID and password issued for the contest.
            </p>
          </div>

          <form onSubmit={submit} className="px-7 py-6 flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="loginId" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                Login ID
              </label>
              <input
                id="loginId"
                type="text"
                autoComplete="username"
                autoFocus
                value={loginId}
                onChange={(e) => setLoginId(e.target.value)}
                placeholder="e.g. imt2022001"
                className="h-10 px-3 rounded-md bg-input border border-border text-sm text-foreground placeholder:text-muted-foreground/60 focus:outline-none focus:border-accent focus:ring-2 focus:ring-accent/30 transition-colors"
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="password" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                Password
              </label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="h-10 w-full pl-3 pr-10 rounded-md bg-input border border-border text-sm text-foreground placeholder:text-muted-foreground/60 focus:outline-none focus:border-accent focus:ring-2 focus:ring-accent/30 transition-colors"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((s) => !s)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors p-1"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            {error && (
              <div
                role="alert"
                aria-live="polite"
                className="flex items-start gap-2 text-sm text-destructive bg-destructive/10 border border-destructive/30 rounded-md px-3 py-2"
              >
                <AlertCircle size={16} className="mt-0.5 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <Button
              type="submit"
              disabled={loading}
              className="h-10 mt-1 bg-accent text-accent-foreground hover:bg-accent/90 font-semibold"
            >
              {loading ? (
                <>
                  <Loader2 size={16} className="animate-spin motion-reduce:animate-none" />
                  Signing in…
                </>
              ) : (
                'Sign in'
              )}
            </Button>
          </form>

          <div className="border-t border-border bg-secondary/30 px-7 py-3 flex items-center gap-2 font-mono text-xs text-muted-foreground">
            <Terminal size={13} className="text-accent" />
            <span>student@codez:~$</span>
            <span
              className="w-1.5 h-3.5 bg-accent inline-block animate-pulse motion-reduce:animate-none"
              aria-hidden="true"
            />
          </div>
        </div>

        <p className="text-center text-xs text-muted-foreground/70 mt-6">
          IIIT-B · Timed Contest Platform
        </p>
      </div>
    </div>
  )
}
