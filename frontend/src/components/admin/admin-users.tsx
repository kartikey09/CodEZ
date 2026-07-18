import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import {
  Loader2,
  UserPlus,
  Upload,
  KeyRound,
  ShieldCheck,
  Shield,
  UserCheck,
  UserX,
  AlertTriangle,
} from 'lucide-react'
import { api, ApiError } from '@/lib/api'
import type { AdminUser } from '@/lib/types'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { CredentialsPanel, type Credential } from './credentials-panel'

/**
 * Admin user management (Day 12). Lists every account and drives the full lifecycle:
 * create one, bulk-import a roster CSV, reset a password, activate/deactivate, and flip
 * role. Generated one-time passwords surface in the CredentialsPanel above the table.
 * All calls hit /auth/admin/users, which the auth-service filter gates to admins.
 */

type Panel = { title: string; creds: Credential[] } | null

export function AdminUsers() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [panel, setPanel] = useState<Panel>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = async () => {
    try {
      setUsers(await api.admin.listUsers())
      setError(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load users.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const t = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(t)
  }, [])

  const guard = async (fn: () => Promise<void>) => {
    setActionError(null)
    try {
      await fn()
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Action failed.')
    }
  }

  const resetPassword = (u: AdminUser) =>
    guard(async () => {
      setBusyId(u.id)
      try {
        const res = await api.admin.resetPassword(u.id)
        setPanel({ title: `New password for ${u.loginId}`, creds: [{ loginId: res.loginId, password: res.initialPassword }] })
        await load()
      } finally {
        setBusyId(null)
      }
    })

  const toggleActive = (u: AdminUser) =>
    guard(async () => {
      setBusyId(u.id)
      try {
        const updated = await api.admin.setActive(u.id, !u.active)
        setUsers((prev) => prev.map((x) => (x.id === u.id ? updated : x)))
      } finally {
        setBusyId(null)
      }
    })

  const toggleRole = (u: AdminUser) =>
    guard(async () => {
      setBusyId(u.id)
      try {
        const updated = await api.admin.setRole(u.id, u.role === 'admin' ? 'student' : 'admin')
        setUsers((prev) => prev.map((x) => (x.id === u.id ? updated : x)))
      } finally {
        setBusyId(null)
      }
    })

  return (
    <div className="flex-1 w-full overflow-y-auto p-8 font-sans">
      <div className="max-w-5xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <h2 className="text-3xl font-bold text-foreground tracking-tight">User management</h2>
          <div className="flex gap-2">
            <CreateUserButton
              onCreated={(cred) => {
                if (cred) setPanel({ title: `Credentials for ${cred.loginId}`, creds: [cred] })
                void load()
              }}
              onError={setActionError}
            />
            <ImportButton
              onImported={(creds, title) => {
                if (creds.length) setPanel({ title, creds })
                void load()
              }}
              onError={setActionError}
            />
          </div>
        </div>

        {panel && (
          <CredentialsPanel title={panel.title} credentials={panel.creds} onDismiss={() => setPanel(null)} />
        )}

        {actionError && (
          <div className="flex items-center gap-2 text-sm text-destructive bg-destructive/10 border border-destructive/30 rounded-lg px-4 py-2.5 mb-4">
            <AlertTriangle size={15} /> {actionError}
          </div>
        )}

        {loading ? (
          <div className="flex items-center justify-center h-40">
            <Loader2 className="animate-spin h-8 w-8 text-accent" />
          </div>
        ) : error ? (
          <div className="text-center text-destructive py-12 glass rounded-2xl border border-border">{error}</div>
        ) : (
          <div className="overflow-hidden rounded-xl border border-border">
            <table className="w-full text-sm">
              <thead className="bg-secondary/40 text-muted-foreground">
                <tr className="text-left">
                  <th className="px-4 py-3 font-semibold">Login ID</th>
                  <th className="px-4 py-3 font-semibold">Name</th>
                  <th className="px-4 py-3 font-semibold">Role</th>
                  <th className="px-4 py-3 font-semibold">Status</th>
                  <th className="px-4 py-3 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {users.map((u) => (
                  <tr key={u.id} className="bg-card hover:bg-secondary/20 transition-colors">
                    <td className="px-4 py-3 font-mono text-foreground">{u.loginId}</td>
                    <td className="px-4 py-3 text-foreground">{u.displayName}</td>
                    <td className="px-4 py-3">
                      <Badge variant={u.role === 'admin' ? 'default' : 'secondary'}>{u.role}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      {u.active ? (
                        <span className="text-xs text-green-500">Active</span>
                      ) : (
                        <span className="text-xs text-muted-foreground">Disabled</span>
                      )}
                      {u.mustChangePassword && (
                        <span className="ml-2 text-[11px] text-amber-500">must change pw</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-1">
                        <IconAction
                          title="Reset password"
                          onClick={() => resetPassword(u)}
                          busy={busyId === u.id}
                          icon={<KeyRound size={15} />}
                        />
                        <IconAction
                          title={u.role === 'admin' ? 'Demote to student' : 'Promote to admin'}
                          onClick={() => toggleRole(u)}
                          busy={busyId === u.id}
                          icon={u.role === 'admin' ? <Shield size={15} /> : <ShieldCheck size={15} />}
                        />
                        <IconAction
                          title={u.active ? 'Deactivate' : 'Activate'}
                          onClick={() => toggleActive(u)}
                          busy={busyId === u.id}
                          icon={u.active ? <UserX size={15} /> : <UserCheck size={15} />}
                          danger={u.active}
                        />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

function IconAction({
  title,
  onClick,
  busy,
  icon,
  danger,
}: {
  title: string
  onClick: () => void
  busy: boolean
  icon: ReactNode
  danger?: boolean
}) {
  return (
    <button
      type="button"
      title={title}
      aria-label={title}
      disabled={busy}
      onClick={onClick}
      className={`p-2 rounded-md transition disabled:opacity-40 ${
        danger
          ? 'text-muted-foreground hover:text-destructive hover:bg-destructive/10'
          : 'text-muted-foreground hover:text-accent hover:bg-accent/10'
      }`}
    >
      {icon}
    </button>
  )
}

function CreateUserButton({
  onCreated,
  onError,
}: {
  onCreated: (cred: Credential | null) => void
  onError: (msg: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [loginId, setLoginId] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [role, setRole] = useState('student')
  const [submitting, setSubmitting] = useState(false)

  const submit = async () => {
    setSubmitting(true)
    try {
      const created = await api.admin.createUser(loginId.trim(), displayName.trim(), role)
      onCreated(created.initialPassword ? { loginId: created.loginId, password: created.initialPassword } : null)
      setLoginId('')
      setDisplayName('')
      setRole('student')
      setOpen(false)
    } catch (err) {
      onError(err instanceof ApiError ? err.message : 'Could not create user.')
    } finally {
      setSubmitting(false)
    }
  }

  if (!open) {
    return (
      <Button onClick={() => setOpen(true)}>
        <UserPlus size={15} /> New user
      </Button>
    )
  }

  const valid = loginId.trim() !== '' && displayName.trim() !== ''

  return (
    <div className="absolute right-8 mt-12 z-20 w-80 rounded-xl border border-border glass p-4 shadow-xl">
      <h3 className="text-sm font-semibold text-foreground mb-3">Create account</h3>
      <div className="space-y-2">
        <input
          value={loginId}
          onChange={(e) => setLoginId(e.target.value)}
          placeholder="Login ID (e.g. stud042)"
          className="w-full bg-input border border-border rounded-md px-3 py-2 text-sm text-foreground outline-none focus:border-accent"
        />
        <input
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          placeholder="Display name"
          className="w-full bg-input border border-border rounded-md px-3 py-2 text-sm text-foreground outline-none focus:border-accent"
        />
        <select
          value={role}
          onChange={(e) => setRole(e.target.value)}
          className="w-full bg-input border border-border rounded-md px-3 py-2 text-sm text-foreground outline-none focus:border-accent"
        >
          <option value="student">student</option>
          <option value="admin">admin</option>
        </select>
        <p className="text-[11px] text-muted-foreground">
          A random one-time password is generated and shown after creation.
        </p>
      </div>
      <div className="flex justify-end gap-2 mt-3">
        <Button variant="ghost" size="sm" onClick={() => setOpen(false)}>
          Cancel
        </Button>
        <Button size="sm" disabled={!valid || submitting} onClick={() => void submit()}>
          {submitting ? <Loader2 size={14} className="animate-spin" /> : 'Create'}
        </Button>
      </div>
    </div>
  )
}

function ImportButton({
  onImported,
  onError,
}: {
  onImported: (creds: Credential[], title: string) => void
  onError: (msg: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [csv, setCsv] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [skipped, setSkipped] = useState<{ line: number; loginId: string; reason: string }[]>([])

  const submit = async () => {
    setSubmitting(true)
    setSkipped([])
    try {
      const result = await api.admin.importUsers(csv)
      onImported(
        result.created.map((c) => ({ loginId: c.loginId, password: c.initialPassword ?? '' })),
        `${result.created.length} account${result.created.length === 1 ? '' : 's'} imported`,
      )
      setSkipped(result.skipped)
      if (result.skipped.length === 0) {
        setCsv('')
        setOpen(false)
      }
    } catch (err) {
      onError(err instanceof ApiError ? err.message : 'Import failed.')
    } finally {
      setSubmitting(false)
    }
  }

  const onFile = (file: File | undefined) => {
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => setCsv(String(reader.result ?? ''))
    reader.readAsText(file)
  }

  if (!open) {
    return (
      <Button variant="outline" onClick={() => setOpen(true)}>
        <Upload size={15} /> Import CSV
      </Button>
    )
  }

  return (
    <div className="absolute right-8 mt-12 z-20 w-96 rounded-xl border border-border glass p-4 shadow-xl">
      <h3 className="text-sm font-semibold text-foreground mb-1">Import roster (CSV)</h3>
      <p className="text-[11px] text-muted-foreground mb-3">
        Columns: <span className="font-mono">loginId,displayName,role</span> — role optional
        (defaults to student). A header row is skipped automatically.
      </p>
      <textarea
        value={csv}
        onChange={(e) => setCsv(e.target.value)}
        rows={6}
        placeholder={'loginId,displayName,role\nstud101,Ada Lovelace,student\nstud102,Alan Turing,student'}
        className="w-full bg-input border border-border rounded-md px-3 py-2 text-xs font-mono text-foreground outline-none focus:border-accent resize-y"
      />
      <div className="flex items-center justify-between mt-2">
        <label className="text-xs text-muted-foreground cursor-pointer hover:text-foreground">
          <input
            type="file"
            accept=".csv,text/csv"
            className="hidden"
            onChange={(e) => onFile(e.target.files?.[0])}
          />
          Load from file…
        </label>
      </div>

      {skipped.length > 0 && (
        <div className="mt-3 text-xs text-amber-500 border border-amber-500/30 bg-amber-500/10 rounded-md p-2 max-h-28 overflow-y-auto">
          <p className="font-semibold mb-1">{skipped.length} row(s) skipped:</p>
          {skipped.map((s, i) => (
            <div key={i} className="font-mono">
              line {s.line}: {s.loginId || '(blank)'} — {s.reason}
            </div>
          ))}
        </div>
      )}

      <div className="flex justify-end gap-2 mt-3">
        <Button variant="ghost" size="sm" onClick={() => setOpen(false)}>
          Close
        </Button>
        <Button size="sm" disabled={csv.trim() === '' || submitting} onClick={() => void submit()}>
          {submitting ? <Loader2 size={14} className="animate-spin" /> : 'Import'}
        </Button>
      </div>
    </div>
  )
}
