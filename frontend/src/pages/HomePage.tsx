import { useCallback, useEffect, useRef, useState } from "react"
import { useAuth } from "@/auth/AuthContext"
import { getCategories, type Category } from "@/lib/api"
import { Sidebar, MobileNav, type PageKey } from "@/components/Sidebar"
import { AddSheet } from "@/components/AddSheet"
import { BootSplash } from "@/components/BootSplash"
import { NotificationsBell } from "@/components/NotificationsBell"
import { SearchIcon } from "@/components/brand"
import DashboardTab from "@/pages/DashboardTab"
import ExpensesTab from "@/pages/ExpensesTab"
import ReportsTab from "@/pages/ReportsTab"
import ProfileTab from "@/pages/ProfileTab"
import QuestsTab from "@/pages/QuestsTab"

const HEADER: Record<PageKey, { title: (name: string) => string; subtitle: string }> = {
  dashboard: { title: (n) => `Hello, ${n}`, subtitle: "Here's your money at a glance" },
  expenses: { title: () => "Expenses", subtitle: "Your spending log" },
  reports: { title: () => "Reports", subtitle: "Trends across your months" },
  quests: { title: () => "Quests", subtitle: "Goals, streaks & badges" },
  profile: { title: () => "Account", subtitle: "Profile, income and pay tools" },
}

export default function HomePage() {
  const { user } = useAuth()
  const [page, setPage] = useState<PageKey>("dashboard")
  const [categories, setCategories] = useState<Category[]>([])
  const [expanded, setExpanded] = useState(true)
  const [sheetOpen, setSheetOpen] = useState(false)
  const [booting, setBooting] = useState(true)
  const [query, setQuery] = useState("")
  const [reloadKey, setReloadKey] = useState(0)
  const searchRef = useRef<HTMLInputElement | null>(null)

  const reloadCategories = useCallback(() => {
    getCategories()
      .then(setCategories)
      .catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    reloadCategories()
  }, [reloadCategories])

  // ⌘K / Ctrl-K focuses the header search.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && (e.key === "k" || e.key === "K")) {
        e.preventDefault()
        searchRef.current?.focus()
      }
    }
    document.addEventListener("keydown", onKey)
    return () => document.removeEventListener("keydown", onKey)
  }, [])

  const head = HEADER[page]
  const firstName = (user?.displayName ?? "there").split(" ")[0]

  function onSearch(value: string) {
    setQuery(value)
    if (value && page !== "expenses") setPage("expenses")
  }

  function afterAdd() {
    setReloadKey((k) => k + 1)
  }

  return (
    <div className="bg-background text-foreground flex min-h-svh">
      {booting && <BootSplash onDone={() => setBooting(false)} />}

      <Sidebar
        page={page}
        onNavigate={setPage}
        onAdd={() => setSheetOpen(true)}
        expanded={expanded}
        onToggle={() => setExpanded((v) => !v)}
      />

      <main className="flex min-w-0 flex-1 flex-col gap-6 px-5 pt-6 pb-24 sm:px-8 md:pb-14 lg:px-8.5">
        {/* Header */}
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-[26px] font-semibold tracking-tight sm:text-[31px]">
              {head.title(firstName)}
            </h1>
            <p className="text-muted-foreground mt-1.5 text-[14px] sm:text-[15px]">{head.subtitle}</p>
          </div>
          <div className="flex items-center gap-3">
            <div className="bg-card hidden min-w-[230px] items-center gap-2.5 rounded-xl border border-white/[0.08] px-4 py-3 sm:flex">
              <SearchIcon className="text-muted-foreground" />
              <input
                ref={searchRef}
                value={query}
                onChange={(e) => onSearch(e.target.value)}
                placeholder="Search transactions"
                aria-label="Search transactions"
                className="text-foreground min-w-0 flex-1 border-none bg-transparent text-sm outline-none"
              />
              <span className="text-muted-foreground rounded-[5px] border border-white/12 px-1.5 py-0.5 font-mono text-[11px]">
                ⌘K
              </span>
            </div>
            <NotificationsBell />
          </div>
        </header>

        {/* Screens */}
        {page === "dashboard" && (
          <DashboardTab
            key={reloadKey}
            categories={categories}
            onNavigate={(p, categoryName) => {
              if (categoryName) setQuery(categoryName)
              setPage(p as PageKey)
            }}
          />
        )}
        {page === "expenses" && (
          <ExpensesTab
            key={reloadKey}
            categories={categories}
            onCategoriesChanged={reloadCategories}
            query={query}
            onQueryChange={setQuery}
          />
        )}
        {page === "reports" && <ReportsTab categories={categories} />}
        {page === "quests" && <QuestsTab categories={categories} />}
        {page === "profile" && <ProfileTab />}
      </main>

      <MobileNav page={page} onNavigate={setPage} onAdd={() => setSheetOpen(true)} />

      <AddSheet
        open={sheetOpen}
        onClose={() => setSheetOpen(false)}
        categories={categories}
        onSaved={afterAdd}
      />
    </div>
  )
}
