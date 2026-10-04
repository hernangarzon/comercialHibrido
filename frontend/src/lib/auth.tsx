import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, loadSession, saveSession, setUnauthorizedHandler, type Session } from './api'

interface AuthValue {
  session: Session | null
  login: (email: string, password: string) => Promise<void>
  logout: () => void
  /** Actualiza datos de la sesión guardada (p. ej. tras cambiar la contraseña temporal). */
  updateSession: (changes: Partial<Session>) => void
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => loadSession())

  const logout = useCallback(() => {
    saveSession(null)
    setSession(null)
  }, [])

  useEffect(() => setUnauthorizedHandler(logout), [logout])

  const login = useCallback(async (email: string, password: string) => {
    const result = await api.login(email, password)
    saveSession(result)
    setSession(result)
  }, [])

  const updateSession = useCallback((changes: Partial<Session>) => {
    setSession((current) => {
      if (!current) return current
      const next = { ...current, ...changes }
      saveSession(next)
      return next
    })
  }, [])

  const value = useMemo(() => ({ session, login, logout, updateSession }), [session, login, logout, updateSession])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return ctx
}

/** Sesión garantizada: solo para componentes que se renderizan tras el login. */
export function useSession(): Session {
  const { session } = useAuth()
  if (!session) throw new Error('No hay sesión activa')
  return session
}
