import { useEffect, useMemo, useState, type FormEvent } from "react"
import { useAuth } from "@/auth/AuthContext"
import { oauthProviders } from "@/lib/api"
import { ArgaliMark } from "@/components/brand"
import { useToast } from "@/components/Toast"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

// The app's one recurring primary-action style (AddSheet, PledgeSheet, ProfileTab,
// the sidebar's Add button) — never the bright solid --primary fill, which this
// app reserves for accents and focus rings, not buttons.
const CTA =
  "cursor-pointer border border-[#4C93A6] bg-[#123945] text-[#C4E7F0] hover:bg-[#174756] disabled:cursor-not-allowed disabled:opacity-60"

// Ghosted tally behind the brand panel — decorative, seeded once per mount so it
// doesn't reshuffle on every keystroke re-render.
function useBrandTally(count: number) {
  return useMemo(
    () => Array.from({ length: count }, () => 22 + Math.round(Math.random() * 78)),
    [count]
  )
}

export default function AuthPage() {
  const { login, register } = useAuth()
  const [mode, setMode] = useState<"login" | "register">("login")
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [googleAvailable, setGoogleAvailable] = useState(false)
  // Assume open until told otherwise, so a slow or failed check never hides the
  // way in on a deployment that does accept new accounts.
  const [registrationOpen, setRegistrationOpen] = useState(true)
  const tally = useBrandTally(24)
  const toast = useToast()

  useEffect(() => {
    oauthProviders()
      .then((p) => {
        setGoogleAvailable(p.google)
        setRegistrationOpen(p.registration)
        if (!p.registration) setMode("login")
      })
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
    <main className="bg-background grid min-h-svh place-items-center p-4 lg:p-8">
      {/* Shell A: branded left panel + 460px form column, collapsing to a single
          card under lg (1024px) — the ghosted tally is decorative and never the
          only carrier of information, so it drops out with the panel, not with it
          left dangling behind the form. */}
      <div className="border-border grid w-full max-w-[900px] border lg:grid-cols-[1fr_460px]">
        <div
          className="relative hidden flex-col justify-between overflow-hidden p-9 lg:flex"
          style={{ background: "#0B0E10", borderRight: "1px solid var(--border)" }}
        >
          <div aria-hidden="true" className="absolute inset-0 flex items-end gap-2.5 px-9 opacity-50">
            {tally.map((h, i) => (
              <span
                key={i}
                className="flex-1"
                style={{ height: `${h}%`, background: i % 5 === 4 ? "#4C93A6" : "#1E2528" }}
              />
            ))}
          </div>
          <div className="relative flex items-center gap-3">
            <ArgaliMark className="text-primary size-[30px]" strokeWidth={7} />
            <span className="font-heading text-[23px] font-semibold tracking-[0.01em]">Argali</span>
          </div>
          <h1 className="font-heading relative max-w-[380px] text-[31px] leading-[1.14] font-semibold tracking-[-0.01em]">
            Every leu counted,
            <br />
            none of them held.
          </h1>
        </div>

        <div className="bg-card flex flex-col justify-center gap-4 p-8 sm:p-9">
          <div className="mb-1 flex items-center gap-3 lg:hidden">
            <ArgaliMark className="text-primary size-7" strokeWidth={7} />
            <span className="font-heading text-[18px] font-semibold">Argali</span>
          </div>
          <h2 className="font-heading text-[24px] font-semibold tracking-tight">
            {mode === "login" ? "Back to the stick" : "Cut a new stick"}
          </h2>

          <form className="flex flex-col gap-4" onSubmit={submit}>
            {mode === "register" && (
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="displayName" className="ledger-label !text-[10px] text-[#C7CDD0]">
                  Name
                </Label>
                <Input id="displayName" name="displayName" required autoComplete="name" className="min-h-11" />
              </div>
            )}
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="email" className="ledger-label !text-[10px] text-[#C7CDD0]">
                Email
              </Label>
              <Input
                id="email"
                name="email"
                type="email"
                placeholder="you@example.com"
                required
                autoComplete="email"
                className="min-h-11"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="password" className="ledger-label !text-[10px] text-[#C7CDD0]">
                Password
              </Label>
              <Input
                id="password"
                name="password"
                type="password"
                placeholder="••••••••"
                required
                minLength={mode === "register" ? 8 : undefined}
                autoComplete={mode === "login" ? "current-password" : "new-password"}
                className="min-h-11"
              />
            </div>
            <button
              type="submit"
              disabled={busy}
              className={`${CTA} mt-1 min-h-12 w-full text-[14.5px] font-medium`}
            >
              {busy
                ? mode === "login"
                  ? "Signing in…"
                  : "Creating account…"
                : mode === "login"
                  ? "Sign in"
                  : "Create account"}
            </button>
          </form>

          {mode === "register" && (
            <p className="text-muted-foreground -mt-1.5 text-[12.5px] text-pretty">
              Argali keeps a record. It never holds your money or moves it on your behalf.
            </p>
          )}

          {googleAvailable && (
            <>
              <div className="flex items-center gap-3">
                <span className="bg-border h-px flex-1" />
                <span className="ledger-label !text-[10px]">or</span>
                <span className="bg-border h-px flex-1" />
              </div>
              <Button
                variant="outline"
                className="min-h-11 w-full"
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
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <div className="flex items-center justify-between gap-3 text-[13px]">
            {mode === "login" ? (
              <button
                type="button"
                className="text-primary min-h-11 cursor-pointer border-none bg-transparent p-0 font-medium"
                onClick={() => toast("Password reset isn't available yet — contact support for now")}
              >
                Forgot password
              </button>
            ) : (
              <span />
            )}
            {registrationOpen ? (
              <button
                type="button"
                className="text-primary min-h-11 cursor-pointer border-none bg-transparent p-0 font-medium"
                onClick={() => {
                  setMode(mode === "login" ? "register" : "login")
                  setError(null)
                }}
              >
                {mode === "login" ? "Create an account" : "Sign in"}
              </button>
            ) : (
              <span className="text-muted-foreground">Closed to new accounts</span>
            )}
          </div>
        </div>
      </div>
    </main>
  )
}
