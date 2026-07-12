import { AuthProvider, useAuth } from "@/auth/AuthContext"
import AuthPage from "@/pages/AuthPage"
import HomePage from "@/pages/HomePage"

function Shell() {
  const { user, loading } = useAuth()

  if (loading) {
    return (
      <main className="flex min-h-svh items-center justify-center">
        <p className="text-muted-foreground text-sm">Loading…</p>
      </main>
    )
  }

  return user ? <HomePage /> : <AuthPage />
}

export default function App() {
  return (
    <AuthProvider>
      <Shell />
    </AuthProvider>
  )
}
