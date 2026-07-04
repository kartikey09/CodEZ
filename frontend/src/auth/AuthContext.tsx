import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, ApiError } from '@/lib/api'
import type { Me } from '@/lib/types'

interface AuthState {
  user: Me | null
  loading: boolean
  /** Re-read /auth/me (call after login or change-password). */
  refresh: () => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = async () => {
    try {
      setUser(await api.me())
    } catch (err) {
      // 401 (no session) or a network hiccup both mean "not signed in" here.
      if (!(err instanceof ApiError)) throw err
      setUser(null)
    }
  }

  useEffect(() => {
    void (async () => {
      await refresh()
      setLoading(false)
    })()
  }, [])

  const logout = async () => {
    try {
      await api.logout()
    } catch {
      // Even if the call fails, drop local state so the UI returns to login.
    }
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, loading, refresh, logout }}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>')
  return ctx
}
