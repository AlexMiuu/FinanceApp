import { useAuth } from "@/auth/AuthContext"
import { Button } from "@/components/ui/button"
import { NotificationsBell } from "@/components/NotificationsBell"

export type PageKey = "dashboard" | "expenses" | "reports" | "quests" | "profile"

const NAV: { key: PageKey; label: string }[] = [
  { key: "dashboard", label: "Dashboard" },
  { key: "expenses", label: "Expenses" },
  { key: "reports", label: "Reports" },
  { key: "quests", label: "Quests" },
  { key: "profile", label: "Profile" },
]

export function TopNav({
  page,
  onNavigate,
}: {
  page: PageKey
  onNavigate: (page: PageKey) => void
}) {
  const { user } = useAuth()
  const initial = (user?.displayName ?? "?").charAt(0).toUpperCase()

  return (
    <header className="bg-card/60 sticky top-0 z-10 border-b backdrop-blur">
      <div className="mx-auto flex h-[60px] max-w-[1400px] items-center justify-between gap-4 px-6">
        <button
          className="flex cursor-pointer items-center gap-2.5 border-none bg-transparent p-0"
          onClick={() => onNavigate("dashboard")}
        >
          <span className="bg-primary text-primary-foreground grid size-7 place-items-center rounded-lg font-mono text-sm font-bold">
            L
          </span>
          <span className="text-[15px] font-semibold tracking-wide">LEDGER</span>
        </button>

        <nav className="hidden gap-1.5 md:flex">
          {NAV.map((item) => (
            <button
              key={item.key}
              onClick={() => onNavigate(item.key)}
              className={`cursor-pointer rounded-lg border-none px-4 py-1.5 text-[13px] font-medium transition-colors ${
                page === item.key
                  ? "bg-secondary text-foreground"
                  : "text-muted-foreground hover:text-foreground bg-transparent"
              }`}
            >
              {item.label}
            </button>
          ))}
        </nav>

        <div className="flex items-center gap-3">
          <Button size="sm" onClick={() => onNavigate("expenses")}>
            + Add expense
          </Button>
          <NotificationsBell />
          <button
            aria-label="Profile"
            onClick={() => onNavigate("profile")}
            className="bg-secondary grid size-8 cursor-pointer place-items-center rounded-full border-none text-xs font-semibold"
          >
            {initial}
          </button>
        </div>
      </div>
      <nav className="flex gap-1 overflow-x-auto px-4 pb-2 md:hidden">
        {NAV.map((item) => (
          <button
            key={item.key}
            onClick={() => onNavigate(item.key)}
            className={`cursor-pointer whitespace-nowrap rounded-lg border-none px-3 py-1 text-xs font-medium ${
              page === item.key ? "bg-secondary text-foreground" : "text-muted-foreground bg-transparent"
            }`}
          >
            {item.label}
          </button>
        ))}
      </nav>
    </header>
  )
}
