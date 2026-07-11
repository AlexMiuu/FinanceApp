import { useState } from "react"
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

export default function App() {
  const [statuses, setStatuses] = useState<Record<string, Status>>({})

  async function checkAll() {
    setStatuses(Object.fromEntries(SERVICES.map((s) => [s.name, "checking"])))
    await Promise.all(
      SERVICES.map(async ({ name, hello }) => {
        try {
          const res = await fetch(hello)
          const ok = res.ok && (await res.json()).status === "ok"
          setStatuses((prev) => ({ ...prev, [name]: ok ? "up" : "down" }))
        } catch {
          setStatuses((prev) => ({ ...prev, [name]: "down" }))
        }
      })
    )
  }

  return (
    <main className="mx-auto flex min-h-svh max-w-2xl flex-col gap-6 p-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">
          Personal Finance App
        </h1>
        <p className="text-muted-foreground text-sm">
          Milestone M0 — service skeleton. Every card below is a microservice
          reached through the API gateway.
        </p>
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
              <CardDescription>Spring Boot · PostgreSQL</CardDescription>
            </CardHeader>
          </Card>
        ))}
      </section>
    </main>
  )
}
