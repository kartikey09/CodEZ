import { useState } from 'react'
import { Check, Copy, KeyRound, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'

/**
 * One-time credential display. Generated passwords are shown ONCE (the server never
 * stores or returns them again), so this panel makes them easy to copy before they're
 * gone. Kept dependency-free (no dialog primitive) — it's an inline callout the admin
 * dismisses when done handing out credentials.
 */

export interface Credential {
  loginId: string
  password: string
}

function CopyButton({ text, label }: { text: string; label: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      onClick={() => {
        void navigator.clipboard.writeText(text).then(() => {
          setCopied(true)
          setTimeout(() => setCopied(false), 1200)
        })
      }}
      className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition"
      aria-label={label}
    >
      {copied ? <Check size={13} className="text-green-500" /> : <Copy size={13} />}
      {copied ? 'Copied' : 'Copy'}
    </button>
  )
}

export function CredentialsPanel({
  title,
  credentials,
  onDismiss,
}: {
  title: string
  credentials: Credential[]
  onDismiss: () => void
}) {
  if (credentials.length === 0) return null

  const asCsv = credentials.map((c) => `${c.loginId},${c.password}`).join('\n')

  return (
    <div className={cn('rounded-xl border border-accent/40 bg-accent/5 p-4 mb-6')}>
      <div className="flex items-start justify-between gap-4 mb-3">
        <div className="flex items-center gap-2">
          <KeyRound size={16} className="text-accent" />
          <div>
            <h3 className="text-sm font-semibold text-foreground">{title}</h3>
            <p className="text-xs text-muted-foreground">
              Shown once — copy and hand out now. Each user must change it on first login.
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={onDismiss}
          className="text-muted-foreground hover:text-foreground transition"
          aria-label="Dismiss"
        >
          <X size={16} />
        </button>
      </div>

      <div className="rounded-lg border border-border overflow-hidden">
        <table className="w-full text-sm">
          <tbody className="divide-y divide-border">
            {credentials.map((c) => (
              <tr key={c.loginId} className="bg-card">
                <td className="px-3 py-2 font-mono text-foreground">{c.loginId}</td>
                <td className="px-3 py-2 font-mono text-accent select-all">{c.password}</td>
                <td className="px-3 py-2 text-right">
                  <CopyButton text={c.password} label={`Copy password for ${c.loginId}`} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {credentials.length > 1 && (
        <div className="flex justify-end mt-3">
          <Button variant="outline" size="sm" onClick={() => void navigator.clipboard.writeText(asCsv)}>
            <Copy size={13} /> Copy all as CSV
          </Button>
        </div>
      )}
    </div>
  )
}
