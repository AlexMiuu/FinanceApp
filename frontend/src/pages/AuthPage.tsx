import { useEffect, useState, type FormEvent } from "react"
import { useAuth } from "@/auth/AuthContext"
import { oauthProviders } from "@/lib/api"
import { ArgaliMark } from "@/components/brand"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

export default function AuthPage() {
  const { login, register } = useAuth()
  const [mode, setMode] = useState<"login" | "register">("login")
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [googleAvailable, setGoogleAvailable] = useState(false)

  useEffect(() => {
    oauthProviders()
      .then((p) => setGoogleAvailable(p.google))
      .catch(() => {})
    if (new URLSearchParams(window.location.search).get("login") === "error") {
      setError("Google sign-in failed. Please try again.")
      window.history.replaceState(null, "", "/")
    }
  }, [])

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    const data = new FormData(e.currentTarget)
    const email = String(data.get("email"))
    const password = String(data.get("password"))
    try {
      if (mode === "login") {
        await login(email, password)
      } else {
        await register(email, password, String(data.get("displayName")))
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong")
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="bg-background grid min-h-svh place-items-center p-4 [background-image:radial-gradient(800px_500px_at_50%_-10%,color-mix(in_oklab,var(--primary)_16%,transparent),transparent)]">
      <div className="ledger-card w-full max-w-[400px] p-9 shadow-2xl">
        <div className="flex items-center justify-center gap-3">
          <ArgaliMark className="text-foreground size-9" />
          <span className="text-[17px] font-semibold tracking-[0.22em]">ARGALI</span>
        </div>
        <p className="text-muted-foreground mt-2 text-center text-[13px]">
          {mode === "login" ? "Sign in to your account" : "Create your account"}
        </p>

        <form className="mt-7 flex flex-col gap-3.5" onSubmit={submit}>
          {mode === "register" && (
            <div className="space-y-1.5">
              <Label htmlFor="displayName">Name</Label>
              <Input id="displayName" name="displayName" required autoComplete="name" />
            </div>
          )}
          <div className="space-y-1.5">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              name="email"
              type="email"
              placeholder="you@example.com"
              required
              autoComplete="email"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              name="password"
              type="password"
              placeholder="••••••••"
              required
              minLength={mode === "register" ? 8 : undefined}
              autoComplete={mode === "login" ? "current-password" : "new-password"}
            />
          </div>
          <Button type="submit" className="mt-1 w-full" disabled={busy}>
            {busy
              ? mode === "login"
                ? "Signing in…"
                : "Creating account…"
              : mode === "login"
                ? "Sign in"
                : "Create account"}
          </Button>
        </form>

        {googleAvailable && (
          <>
            <div className="my-3.5 flex items-center gap-3">
              <span className="bg-border h-px flex-1" />
              <span className="text-muted-foreground text-[12px]">OR</span>
              <span className="bg-border h-px flex-1" />
            </div>
            <Button
              variant="outline"
              className="w-full"
              onClick={() => {
                window.location.href = "/api/v1/auth/oauth/google"
              }}
            >
              <span className="text-primary font-mono text-[13px] font-bold">G</span>
              Continue with Google
            </Button>
          </>
        )}

        {error && (
          <Alert variant="destructive" className="mt-3.5">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <p className="text-muted-foreground mt-5 text-center text-[13px]">
          {mode === "login" ? "No account? " : "Have an account? "}
          <button
            type="button"
            className="text-primary cursor-pointer border-none bg-transparent font-medium"
            onClick={() => {
              setMode(mode === "login" ? "register" : "login")
              setError(null)
            }}
          >
            {mode === "login" ? "Create one" : "Sign in"}
          </button>
        </p>
      </div>
    </main>
  )
}
