import { Suspense, lazy, useCallback, useEffect, useRef, useState } from "react"
import { useAuth } from "@/auth/AuthContext"
import { getCategories, getWeather, type Category } from "@/lib/api"
import { Sidebar, MobileNav, type PageKey } from "@/components/Sidebar"
import { AddSheet } from "@/components/AddSheet"
import { BootSplash } from "@/components/BootSplash"
import { NotificationsBell } from "@/components/NotificationsBell"
import { CloseIcon, SearchIcon } from "@/components/brand"
import { STORAGE_REGISTRY, readStored, writeStored } from "@/lib/storage"
// Each tab is its own chunk: opening the app pays for the dashboard only, and the
// other four arrive when they are first navigated to.
const DashboardTab = lazy(() => import("@/pages/DashboardTab"))
const ExpensesTab = lazy(() => import("@/pages/ExpensesTab"))
const ReportsTab = lazy(() => import("@/pages/ReportsTab"))
const ProfileTab = lazy(() => import("@/pages/ProfileTab"))
const QuestsTab = lazy(() => import("@/pages/QuestsTab"))

/** Shown for the moment a tab's chunk is in flight; matches the ledger-label voice. */
function TabLoading() {
  return (
    <div className="ledger-label py-16 text-center">
      Turning the page
    </div>
  )
}

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
  // Once per session, not once per render of the shell: a reload or a tab switch
  // mid-session should land straight on the dashboard.
  const [booting, setBooting] = useState(
    () => !readStored(STORAGE_REGISTRY.splashSeen, false)
  )
  const [query, setQuery] = useState("")
  const [searchOpen, setSearchOpen] = useState(false)
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

  // Ambient F4 surface: a root-level class, never a prop drilled through the
  // tree. Defaults to clear (never gates functionality) until the catch-up
  // read resolves or a live transition arrives. The STOMP client is a
  // dynamic import, matching NotificationsBell's pattern (M10 code-split) —
  // a static import here would pull it back into the main chunk.
  useEffect(() => {
    const BAND_CLASSES = ["weather-clear", "weather-gathering", "weather-storm"]
    const applyBand = (band: string) => {
      document.documentElement.classList.remove(...BAND_CLASSES)
      document.documentElement.classList.add(`weather-${band}`)
    }
    applyBand("clear")
    getWeather().then((w) => applyBand(w.band)).catch(() => applyBand("clear"))

    let disconnect: (() => void) | null = null
    let cancelled = false
    import("@/lib/ws").then(({ connectWeather }) => {
      if (cancelled) return
      disconnect = connectWeather(applyBand)
    })

    return () => {
      cancelled = true
      disconnect?.()
      document.documentElement.classList.remove(...BAND_CLASSES)
    }
  }, [])

  // ⌘K / Ctrl-K focuses the header search.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && (e.key === "k" || e.key === "K")) {
        e.preventDefault()
        // Also reveals the field on narrow viewports, where it starts collapsed.
        setSearchOpen(true)
        requestAnimationFrame(() => searchRef.current?.focus())
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

  function onBootDone() {
    writeStored(STORAGE_REGISTRY.splashSeen, true)
    setBooting(false)
  }

  function openMobileSearch() {
    setSearchOpen(true)
    // The field is only revealed by the state change above, so focus waits a frame.
    requestAnimationFrame(() => searchRef.current?.focus())
  }

  function closeMobileSearch() {
    setSearchOpen(false)
    setQuery("")
  }

  function afterAdd() {
    setReloadKey((k) => k + 1)
  }

  return (
    <div className="bg-background text-foreground flex min-h-svh">
      {booting && <BootSplash onDone={onBootDone} />}

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
            <h1 className="font-heading text-[26px] font-semibold tracking-tight sm:text-[31px]">
              {head.title(firstName)}
            </h1>
            <p className="text-muted-foreground mt-1.5 text-[14px] sm:text-[15px]">{head.subtitle}</p>
          </div>
          <div className="flex flex-wrap items-center justify-end gap-3">
            {/* Under 640px the search field is collapsed behind this control, so the
                header still fits a phone without dropping search entirely. */}
            <button
              onClick={openMobileSearch}
              aria-label="Search transactions"
              aria-expanded={searchOpen}
              className={`text-muted-foreground hover:text-foreground bg-card border-border grid size-11 flex-none cursor-pointer place-items-center border transition-colors hover:bg-white/[0.06] sm:hidden ${
                searchOpen ? "hidden" : ""
              }`}
            >
              <SearchIcon />
            </button>

            <div
              className={`bg-card border-border w-full min-w-0 items-center gap-2.5 border px-4 py-3 sm:flex sm:w-auto sm:min-w-[230px] ${
                searchOpen ? "flex" : "hidden"
              }`}
            >
              <SearchIcon className="text-muted-foreground" />
              <input
                ref={searchRef}
                value={query}
                onChange={(e) => onSearch(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Escape") closeMobileSearch()
                }}
                placeholder="Search transactions"
                aria-label="Search transactions"
                className="text-foreground min-w-0 flex-1 border-none bg-transparent text-sm outline-none"
              />
              <span className="text-muted-foreground border-border hidden border px-1.5 py-0.5 font-mono text-[12px] sm:inline">
                ⌘K
              </span>
              <button
                onClick={closeMobileSearch}
                aria-label="Close search"
                className="text-muted-foreground hover:text-foreground -mr-2 grid size-11 flex-none cursor-pointer place-items-center rounded-lg sm:hidden"
              >
                <CloseIcon />
              </button>
            </div>

            <NotificationsBell />
          </div>
        </header>

        {/* Screens */}
        <Suspense fallback={<TabLoading />}>
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
        </Suspense>
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
