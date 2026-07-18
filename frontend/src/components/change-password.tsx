import { useState } from 'react'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/button'
import { Loader2, KeyRound, AlertCircle } from 'lucide-react'
import { ThemeToggle } from '@/components/theme-toggle'

/** Shown when the signed-in user still has mustChangePassword = true. */
export function ChangePassword() {
  const { refresh, logout } = useAuth()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (loading) return
    setError(null)

    if (newPassword.length < 8) {
      setError('New password must be at least 8 characters.')
      return
    }
    if (newPassword !== confirm) {
      setError('New password and confirmation do not match.')
      return
    }

    setLoading(true)
    try {
      await api.changePassword(currentPassword, newPassword)
      await refresh() // mustChangePassword flips to false on the same session
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Try again.')
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-background text-foreground flex items-center justify-center px-4 py-10 font-sans relative">
      <ThemeToggle className="absolute top-4 right-4" />
      <div className="w-full max-w-sm">
        <div className="flex items-center gap-2.5 mb-8 justify-center">
          <div className="w-9 h-9 bg-accent rounded-md flex items-center justify-center">
            <KeyRound size={18} className="text-accent-foreground" />
          </div>
          <span className="text-xl font-bold tracking-tight">Set a new password</span>
        </div>

        <div className="glass border border-border rounded-xl p-7">
          <p className="text-sm text-muted-foreground mb-5">
            Your account uses a temporary password. Choose a new one to continue.
          </p>

          <form onSubmit={submit} className="flex flex-col gap-4">
            {(
              [
                ['Current password', currentPassword, setCurrentPassword, 'current-password'],
                ['New password', newPassword, setNewPassword, 'new-password'],
                ['Confirm new password', confirm, setConfirm, 'new-password'],
              ] as const
            ).map(([label, value, setter, autoComplete]) => (
              <div key={label} className="flex flex-col gap-1.5">
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">{label}</label>
                <input
                  type="password"
                  autoComplete={autoComplete}
                  value={value}
                  onChange={(e) => setter(e.target.value)}
                  className="h-10 px-3 rounded-md bg-input border border-border text-sm text-foreground focus:outline-none focus:border-accent focus:ring-2 focus:ring-accent/30 transition-colors"
                />
              </div>
            ))}

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
                  Saving…
                </>
              ) : (
                'Save and continue'
              )}
            </Button>

            <button
              type="button"
              onClick={() => void logout()}
              className="text-xs text-muted-foreground hover:text-accent transition-colors text-center"
            >
              Sign out
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
