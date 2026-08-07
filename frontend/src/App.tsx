import { Suspense, lazy } from "react"
import { AuthProvider, useAuth } from "@/auth/AuthContext"

// The two halves of the app never render together, so neither belongs in the
// other's download: a signed-out visitor pays for the auth screen alone.
const AuthPage = lazy(() => import("@/pages/AuthPage"))
const HomePage = lazy(() => import("@/pages/HomePage"))

function Loading() {
  return (
    <main className="flex min-h-svh items-center justify-center">
      <p className="text-muted-foreground text-sm">Loading…</p>
    </main>
  )
}

function Shell() {
  const { user, loading } = useAuth()

  if (loading) return <Loading />

  return <Suspense fallback={<Loading />}>{user ? <HomePage /> : <AuthPage />}</Suspense>
}

export default function App() {
  return (
    <AuthProvider>
      <Shell />
    </AuthProvider>
  )
}
