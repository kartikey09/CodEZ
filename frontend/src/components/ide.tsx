import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, ApiError } from '@/lib/api'
import type { ProblemDetail } from '@/lib/types'
import { ProblemPanel } from './problem-panel'
import { CodeEditor } from './code-editor'
import { Loader2 } from 'lucide-react'

export function IDE() {
  const { id } = useParams<{ id: string }>()
  const problemId = Number(id)
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Reset to the loading state during render (not in the effect) when the
  // route's problem id changes, so stale content never flashes.
  const [loadedId, setLoadedId] = useState(problemId)
  if (loadedId !== problemId) {
    setLoadedId(problemId)
    setProblem(null)
    setError(null)
    setLoading(true)
  }

  useEffect(() => {
    if (!Number.isFinite(problemId)) return
    let cancelled = false
    void (async () => {
      try {
        const p = await api.getProblem(problemId)
        if (!cancelled) setProblem(p)
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof ApiError
              ? err.code === 'CONTEST_NOT_STARTED'
                ? 'The contest has not started yet.'
                : err.code === 'NOT_FOUND'
                  ? 'This problem is not available.'
                  : err.message
              : 'Failed to load the problem.',
          )
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [problemId])

  if (!Number.isFinite(problemId)) {
    return <div className="w-full h-full flex items-center justify-center text-destructive">Invalid problem id.</div>
  }

  if (loading) {
    return (
      <div className="w-full h-full flex items-center justify-center text-muted-foreground gap-2">
        <Loader2 className="animate-spin" size={18} /> Loading problem…
      </div>
    )
  }
  if (error || !problem) {
    return <div className="w-full h-full flex items-center justify-center text-destructive">{error ?? 'Not found.'}</div>
  }

  return (
    <div className="flex w-full h-full gap-4">
      <div className="w-1/2 overflow-hidden">
        <ProblemPanel problem={problem} />
      </div>
      <div className="w-1/2 overflow-hidden">
        <CodeEditor problemId={problem.id} />
      </div>
    </div>
  )
}
