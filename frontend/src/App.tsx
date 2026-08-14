import { Suspense, lazy } from "react"
import { AuthProvider, useAuth } from "@/auth/AuthContext"
import { ROUTES, usePath } from "@/lib/route"

// The halves of the app never render together, so none belongs in another's
// download: a signed-out visitor pays for the screen they actually landed on.
const AuthPage = lazy(() => import("@/pages/AuthPage"))
const HomePage = lazy(() => import("@/pages/HomePage"))
const SalaryCalculatorPage = lazy(() => import("@/pages/SalaryCalculatorPage"))

function Loading() {
  return (
    <main className="flex min-h-svh items-center justify-center">
      <p className="text-muted-foreground text-sm">Loading…</p>
    </main>
  )
}

function Shell() {
  const { user, loading } = useAuth()
  const path = usePath()

  // Answered before the session is known: the calculator is the one page that
  // does not care whether anyone is signed in, and waiting on the auth check
  // would make a stranger stare at a spinner for no reason.
  if (path === ROUTES.salaryCalculator) {
    return (
      <Suspense fallback={<Loading />}>
        <SalaryCalculatorPage />
      </Suspense>
    )
  }

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
