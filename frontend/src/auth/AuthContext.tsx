import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react"
import * as apiClient from "@/lib/api"
import type { User } from "@/lib/api"
import { clearArgaliStorage } from "@/lib/storage"

type AuthState = {
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // Bootstrap the session from the refresh cookie (also completes OAuth logins).
    apiClient
      .refreshSession()
      .then((session) => setUser(session?.user ?? null))
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const session = await apiClient.login(email, password)
    setUser(session.user)
  }, [])

  const register = useCallback(
    async (email: string, password: string, displayName: string) => {
      const session = await apiClient.register(email, password, displayName)
      setUser(session.user)
    },
    []
  )

  const logout = useCallback(async () => {
    await apiClient.logout()
    // D9: sign-out has to leave the browser clean, or the next person to sign in
    // on this machine inherits the previous user's cached state.
    clearArgaliStorage()
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider")
  return ctx
}
