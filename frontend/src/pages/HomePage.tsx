import { useState } from "react"
import { useAuth } from "@/auth/AuthContext"
import { api } from "@/lib/api"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"

const SERVICES = [
  { name: "user-service", hello: "/api/v1/auth/hello" },
  { name: "expense-service", hello: "/api/v1/expenses/hello" },
  { name: "report-service", hello: "/api/v1/reports/hello" },
  { name: "quest-service", hello: "/api/v1/quests/hello" },
  { name: "notification-service", hello: "/api/v1/notifications/hello" },
]

type Status = "unknown" | "checking" | "up" | "down"

function StatusBadge({ status }: { status: Status }) {
  if (status === "up") return <Badge>up</Badge>
  if (status === "down") return <Badge variant="destructive">down</Badge>
  if (status === "checking") return <Badge variant="secondary">checking…</Badge>
  return <Badge variant="outline">unknown</Badge>
}

export default function HomePage() {
  const { user, logout } = useAuth()
  const [statuses, setStatuses] = useState<Record<string, Status>>({})

  async function checkAll() {
    setStatuses(Object.fromEntries(SERVICES.map((s) => [s.name, "checking"])))
    await Promise.all(
      SERVICES.map(async ({ name, hello }) => {
        try {
          const res = await api<{ status: string }>(hello)
          setStatuses((prev) => ({
            ...prev,
            [name]: res.status === "ok" ? "up" : "down",
          }))
        } catch {
          setStatuses((prev) => ({ ...prev, [name]: "down" }))
        }
      })
    )
  }

  return (
    <main className="mx-auto flex min-h-svh max-w-2xl flex-col gap-6 p-8">
      <header className="flex items-start justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">
            Personal Finance App
          </h1>
          <p className="text-muted-foreground text-sm">
            Signed in as {user?.displayName} ({user?.email})
          </p>
        </div>
        <Button variant="outline" onClick={logout}>
          Sign out
        </Button>
      </header>

      <Button onClick={checkAll} className="w-fit">
        Check services through gateway
      </Button>

      <section className="grid gap-3 sm:grid-cols-2">
        {SERVICES.map(({ name }) => (
          <Card key={name}>
            <CardHeader>
              <CardTitle className="flex items-center justify-between text-base">
                {name}
                <StatusBadge status={statuses[name] ?? "unknown"} />
              </CardTitle>
              <CardDescription>authenticated via gateway JWT</CardDescription>
            </CardHeader>
          </Card>
        ))}
      </section>
    </main>
  )
}
