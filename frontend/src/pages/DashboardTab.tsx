import { useCallback, useEffect, useState } from "react"
import {
  formatRon,
  getCalendar,
  getDashboard,
  getDashboardLayout,
  getNetWorth,
  listExpenses,
  listOaths,
  listQuests,
  listSavings,
  saveDashboardLayout,
  type Category,
  type Dashboard,
  type Expense,
  type GoalCalendar,
  type NetWorth,
  type Oath,
  type Quest,
  type SavingsAccount,
  type WidgetId,
} from "@/lib/api"
import { ChevronIcon, HornGlyph } from "@/components/brand"
import {
  COLUMN_NAMES,
  DEFAULT_LAYOUT,
  OTHER,
  WIDGET_NAMES,
  dropAt,
  moveToOtherColumn,
  reorder,
  type Column,
  type Layout,
} from "@/lib/dashboardLayout"

/**
 * The hero and the tally are full-bleed bands: they cancel the main column's own
 * horizontal padding so their hairline rules run the whole width of the page, the
 * way a ruled account book's lines do. Keep in step with HomePage's <main> padding.
 */
const BLEED = "-mx-5 sm:-mx-8 lg:-mx-8.5"
const BAND_PAD = "px-5 sm:px-8 lg:px-10"

const DAY_OK = "#9AD4E3"
const DAY_OVER = "#E09880"
const DAY_EMPTY = "#2A3033"

const thisMonth = () => new Date().toISOString().slice(0, 7)
const monthLabel = (m: string) =>
  new Date(m + "-01T00:00:00").toLocaleDateString("en-GB", { month: "long", year: "numeric" })

/** Whole RON, no bani — the flow row and the tally read as round figures. */
const intRon = (bani: number) => Math.round(bani / 100).toLocaleString("ro-RO")
/** "1.240,00" — a money figure with no currency suffix, for columns that carry their own. */
const bareRon = (bani: number) => formatRon(bani).replace(/\s?RON$/, "")

function categoryLabel(categories: Category[], id: string): string {
  const category = categories.find((c) => c.id === id)
  if (!category) return "—"
  if (!category.parentId) return category.name
  const parent = categories.find((c) => c.id === category.parentId)
  return parent ? `${parent.name} › ${category.name}` : category.name
}
function topName(categories: Category[], id: string): string {
  const c = categories.find((x) => x.id === id)
  if (!c) return "Other"
  return c.parentId ? categories.find((x) => x.id === c.parentId)?.name ?? "Other" : c.name
}
const shortDate = (iso: string) =>
  new Date(iso + "T00:00:00").toLocaleDateString("en-GB", { day: "numeric", month: "short" })
const dayAndMonth = (iso: string) =>
  new Date(iso + "T00:00:00").toLocaleDateString("en-GB", { day: "numeric", month: "long" })

export default function DashboardTab({
  categories = [],
  onNavigate,
}: {
  categories?: Category[]
  onNavigate?: (page: string, categoryName?: string) => void
}) {
  const [month] = useState(thisMonth())
  const [data, setData] = useState<Dashboard | null>(null)
  const [netWorth, setNetWorth] = useState<NetWorth | null>(null)
  const [quests, setQuests] = useState<Quest[]>([])
  const [calendar, setCalendar] = useState<GoalCalendar | null>(null)
  const [recent, setRecent] = useState<Expense[]>([])
  const [savings, setSavings] = useState<SavingsAccount[]>([])
  const [oaths, setOaths] = useState<Oath[]>([])
  const [error, setError] = useState<string | null>(null)
  const [slowLoad, setSlowLoad] = useState(false)
  /** Lifted out of the tally so picking a notch also narrows the ledger below it. */
  const [selectedDay, setSelectedDay] = useState<string | null>(null)

  const [layout, setLayout] = useState<Layout>(DEFAULT_LAYOUT)
  const [arranging, setArranging] = useState(false)
  const [layoutError, setLayoutError] = useState<string | null>(null)
  const [dragging, setDragging] = useState<{ column: Column; index: number } | null>(null)

  const loadDashboard = useCallback(() => {
    setError(null)
    getDashboard(month)
      .then((d) => {
        setData(d)
        setError(null)
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load"))
  }, [month])

  useEffect(() => {
    loadDashboard()
  }, [loadDashboard])

  // "Still fetching?" affordance — a plain retry, not a spinner that runs forever.
  useEffect(() => {
    if (data) return
    setSlowLoad(false)
    const t = setTimeout(() => setSlowLoad(true), 6000)
    return () => clearTimeout(t)
  }, [data, month])

  useEffect(() => {
    getNetWorth().then(setNetWorth).catch(() => setNetWorth(null))
    listQuests().then(setQuests).catch(() => setQuests([]))
    getCalendar().then(setCalendar).catch(() => setCalendar(null))
    // Deeper than the seven rows shown, so picking a day in the tally can narrow to
    // it without a second request. The ledger still only lists what it has loaded.
    listExpenses({}).then((p) => setRecent(p.items.slice(0, 60))).catch(() => setRecent([]))
    listSavings().then(setSavings).catch(() => setSavings([]))
    listOaths().then(setOaths).catch(() => setOaths([]))
    // The arrangement is per-user server state (D9), so it survives this browser.
    getDashboardLayout()
      .then((saved) => setLayout({ main: saved.main, side: saved.side }))
      .catch(() => setLayout(DEFAULT_LAYOUT))
  }, [])

  // Optimistic: the move lands immediately and only a failed save is surfaced.
  const applyLayout = useCallback((next: Layout) => {
    setLayout(next)
    setLayoutError(null)
    saveDashboardLayout(next.main, next.side).catch(() =>
      setLayoutError("Couldn't save your arrangement — it will reset on reload.")
    )
  }, [])

  const activeQuests = quests.filter((q) => q.status === "ACTIVE").slice(0, 3)

  if (error)
    return (
      <div className="flex flex-col items-center gap-3 py-8 text-center">
        <p className="text-destructive text-sm">We could not read your ledger — {error}</p>
        <button
          onClick={loadDashboard}
          className="text-primary min-h-11 cursor-pointer px-3 text-[13.5px] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
        >
          Try again
        </button>
      </div>
    )
  if (!data) return <DashSkeletonNote slow={slowLoad} onRetry={loadDashboard} />

  const widgets: Record<WidgetId, React.ReactNode> = {
    ledger: (
      <LedgerWidget
        entries={recent}
        categories={categories}
        selectedDay={selectedDay}
        onClearDay={() => setSelectedDay(null)}
        onNavigate={onNavigate}
      />
    ),
    savings: <SavingsWidget netWorth={netWorth} savings={savings} />,
    quests: <QuestsWidget activeQuests={activeQuests} oaths={oaths} onNavigate={onNavigate} />,
  }

  function renderColumn(column: Column, className: string) {
    return (
      <div className={className}>
        {layout[column].map((widget, index) => (
          <ArrangeableWidget
            key={widget}
            widget={widget}
            column={column}
            index={index}
            total={layout[column].length}
            arranging={arranging}
            isDragging={dragging?.column === column && dragging.index === index}
            onMove={(delta) => applyLayout(reorder(layout, column, index, delta))}
            onSwapColumn={() => applyLayout(moveToOtherColumn(layout, column, index))}
            onDragStart={() => setDragging({ column, index })}
            onDragEnd={() => setDragging(null)}
            onDrop={() => {
              if (dragging) applyLayout(dropAt(layout, dragging, column, index))
              setDragging(null)
            }}
          >
            {widgets[widget]}
          </ArrangeableWidget>
        ))}
        {arranging && (
          <ColumnEndDropZone
            column={column}
            active={dragging !== null}
            onDrop={() => {
              if (dragging) applyLayout(dropAt(layout, dragging, column, layout[column].length))
              setDragging(null)
            }}
          />
        )}
      </div>
    )
  }

  return (
    <div className="flex flex-col">
      <BalanceHero month={month} data={data} netWorth={netWorth} />
      <TallyBand
        data={data}
        month={month}
        calendar={calendar}
        selectedDay={selectedDay}
        onSelectDay={setSelectedDay}
      />

      <div className="mt-[26px] flex flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <button
            onClick={() => setArranging((v) => !v)}
            aria-pressed={arranging}
            className={`inline-flex min-h-11 cursor-pointer items-center gap-2 border px-4 text-[13.5px] font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3] ${
              arranging
                ? "border-primary/60 bg-primary/15 text-primary"
                : "text-muted-foreground border-border hover:bg-popover"
            }`}
          >
            {arranging ? "Done arranging" : "Arrange widgets"}
          </button>
          {arranging && (
            <p className="text-muted-foreground text-[13px]">
              Drag a widget, or use its arrows to move it.
            </p>
          )}
        </div>

        {layoutError && <p className="text-destructive text-[13px]">{layoutError}</p>}

        <div className="flex flex-wrap items-start gap-[22px]">
          {renderColumn("main", "flex min-w-[min(100%,440px)] flex-1 basis-[62%] flex-col gap-[22px]")}
          {renderColumn("side", "flex min-w-[min(100%,320px)] flex-1 basis-[30%] flex-col gap-[22px]")}
        </div>
      </div>
    </div>
  )
}

/* ── Hero ──────────────────────────────────────────────────────────────── */

/** "munte" -> "Munte". Pastoral calendar terms arrive lower-case. */
function seasonLabel(season: string): string {
  return season.charAt(0).toUpperCase() + season.slice(1).toLowerCase()
}

function BalanceHero({
  month,
  data,
  netWorth,
}: {
  month: string
  data: Dashboard
  netWorth: NetWorth | null
}) {
  // Flow row — came in / went out / left to spend — mirrors the ledger's own
  // three-line summary rather than restating totalSpent a second way.
  const income = netWorth?.monthlyIncome ?? null
  const leftToSpend = income !== null ? income - data.totalSpent : null

  // Ghost balance — a hypothetical: what this month would look like if spend had
  // continued at last month's daily rate. Real numbers (data.previousMonthTotal),
  // prorated to today's day-of-month; not fabricated.
  const dayOfMonth = new Date().getDate()
  const daysInPrevMonth = new Date(new Date().getFullYear(), new Date().getMonth(), 0).getDate()
  const ghostSpendToDate =
    data.previousMonthTotal > 0 ? Math.round((data.previousMonthTotal / daysInPrevMonth) * dayOfMonth) : null
  const paceDelta = ghostSpendToDate === null ? null : data.totalSpent - ghostSpendToDate
  const ghostBalance = netWorth && ghostSpendToDate !== null ? netWorth.total - paceDelta! : null

  // The seasonal figure is only explainable relative to the flat projection it sits beside.
  const seasonalDiffPct =
    data.projectedMonthEndSeasonal !== null && data.projectedMonthEnd
      ? Math.round(((data.projectedMonthEndSeasonal - data.projectedMonthEnd) / data.projectedMonthEnd) * 100)
      : null

  const whole = netWorth ? Math.floor(netWorth.total / 100).toLocaleString("ro-RO") : "—"
  const cents = netWorth ? String(netWorth.total % 100).padStart(2, "0") : null

  return (
    <section className={`ledger-paper relative overflow-hidden ${BLEED} ${BAND_PAD} pt-8 pb-7`}>
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-[104px] -right-24 size-[560px] opacity-[0.13]"
      >
        <HornGlyphFull strokeWidth={3.2} />
      </div>

      <div className="relative flex flex-wrap items-start gap-x-14 gap-y-8">
        <div className="min-w-0 flex-none">
          <div className="ledger-label">Balance carried forward · {monthLabel(month)}</div>
          <div className="mt-3 flex items-baseline">
            <span className="figure text-[46px] leading-none sm:text-[56px]">{whole}</span>
            {cents !== null && <span className="figure text-[24px] text-[#C7CDD0]">,{cents}</span>}
            <span className="text-muted-foreground ml-3 font-mono text-[13px] tracking-[0.12em]">RON</span>
          </div>
          <div className="mt-3.5 h-px w-[320px] max-w-full bg-[#4C93A6]" />

          {income !== null && (
            <div className="mt-5 flex flex-wrap gap-x-9 gap-y-4">
              <Flow label="came in" value={intRon(income)} tone="text-good" />
              <Flow label="went out" value={intRon(data.totalSpent)} />
              {leftToSpend !== null && (
                <Flow label="left to spend" value={intRon(leftToSpend)} tone="text-[#C7CDD0]" />
              )}
            </div>
          )}
        </div>

        <div className="hidden w-px self-stretch bg-border sm:block" />

        <div className="min-w-[240px] flex-1 basis-[360px] pt-0.5">
          {ghostBalance !== null && paceDelta !== null ? (
            <>
              <div className="flex items-center gap-2.5">
                <span className="bg-[#4C93A6] size-1.5 flex-none" />
                <span className="ledger-label !text-primary">Ghost balance</span>
              </div>
              <div className="figure mt-3 text-[32px] text-[#C7CDD0]">{bareRon(ghostBalance)}</div>
              <p className="text-muted-foreground mt-3 max-w-[340px] text-[13.5px] text-pretty">
                Where you would stand if this month had held last month's pace.
              </p>
              <div className="mt-3.5 flex flex-wrap items-center gap-2.5">
                <span className={`font-mono text-[14px] ${paceDelta <= 0 ? "text-good" : "text-destructive"}`}>
                  {bareRon(Math.abs(paceDelta))} RON
                </span>
                <span className="text-muted-foreground text-[13.5px]">
                  {paceDelta <= 0 ? "under that pace" : "above that pace"}
                </span>
              </div>
            </>
          ) : (
            <>
              <div className="ledger-label">Ghost balance</div>
              <p className="text-muted-foreground mt-3 max-w-[340px] text-[13.5px] text-pretty">
                Where you would stand if this month had held last month's pace — it appears once
                there is a previous month to hold to.
              </p>
            </>
          )}

          <div className="ruled mt-6 max-w-[360px] text-[13.5px]">
            {data.projectedMonthEnd !== null && (
              <div className="flex items-baseline justify-between gap-4 py-2">
                <span className="text-muted-foreground">Projected month-end</span>
                <span className="tnum font-mono">{bareRon(data.projectedMonthEnd)}</span>
              </div>
            )}
            {data.projectedMonthEndSeasonal !== null && (
              <div className="flex items-baseline justify-between gap-4 py-2">
                <span className="text-muted-foreground flex items-center gap-2">
                  Seasonally adjusted
                  {data.macroStale && (
                    <span className="status-tag edge-mark-accent text-primary py-0.5 pl-2">stale</span>
                  )}
                </span>
                <span className="tnum font-mono">{bareRon(data.projectedMonthEndSeasonal)}</span>
              </div>
            )}
            <div className="flex items-baseline justify-between gap-4 py-2">
              <span className="text-muted-foreground">Entries logged</span>
              <span className="tnum font-mono">{data.expenseCount}</span>
            </div>
          </div>
        </div>
      </div>

      {data.projectedMonthEndSeasonal !== null && data.macroSeason && (
        <p className="text-muted-foreground relative mt-5 text-[12.5px] leading-relaxed">
          Seasonally adjusted{" "}
          {seasonalDiffPct !== null && (seasonalDiffPct >= 0 ? `+${seasonalDiffPct}` : seasonalDiffPct)}
          {seasonalDiffPct !== null && "% "}for {seasonLabel(data.macroSeason)}
          {data.macroSource && ` — via ${data.macroSource}`}
          {data.macroAsOfDate && `, as of ${data.macroAsOfDate}`}
          {data.macroStale && " (last known good — not refreshed recently)"}
        </p>
      )}
    </section>
  )
}

function Flow({ label, value, tone = "" }: { label: string; value: string; tone?: string }) {
  return (
    <div>
      <div className="ledger-label">{label}</div>
      <div className={`figure mt-1.5 text-[21px] ${tone}`}>{value}</div>
    </div>
  )
}

/* ── Tally ─────────────────────────────────────────────────────────────── */

/**
 * Tally — one notch per day of the month, colored by the calendar's own MET/MISSED
 * read of that day, with the F3 ghost flock's daily figures ruled in beneath as a
 * faint comparison row. Picking a notch narrows the ledger below to that day.
 */
function TallyBand({
  data,
  month,
  calendar,
  selectedDay,
  onSelectDay,
}: {
  data: Dashboard
  month: string
  calendar: GoalCalendar | null
  selectedDay: string | null
  onSelectDay: (date: string | null) => void
}) {
  const days = data.byDay
  if (days.length === 0) return null

  const statusByDate = new Map((calendar?.days ?? []).map((d) => [d.date, d.status]))
  const ghostByDate = new Map((data.ghostByDay ?? []).map((d) => [d.date, d.amount]))

  const peak = Math.max(...days.map((d) => d.amount), ...(data.ghostByDay?.map((d) => d.amount) ?? [0]), 1)
  const daysEntered = days.filter((d) => d.amount > 0).length
  const ghostDays = data.ghostByDay?.filter((d) => d.amount > 0).length ?? 0
  const hasGhost = data.ghostByDay !== null
  const today = new Date().getDate()

  const spent = bareRon(data.totalSpent)
  const baseline = bareRon(data.ghostMonthTotal ?? 0)

  return (
    <section className={`border-border ${BLEED} ${BAND_PAD} border-b pt-6 pb-5`}>
      <div className="mb-4 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-2">
        <div className="flex flex-wrap items-baseline gap-3.5">
          <span className="ledger-label">Tally · {monthLabel(month).split(" ")[0]}</span>
          <span className="text-muted-foreground text-[13px]">
            {selectedDay
              ? `${dayAndMonth(selectedDay)} · click the notch again for the whole month`
              : "click a notch to narrow the ledger to one day"}
          </span>
        </div>
        <div className="flex gap-4">
          <TallyLegendItem color={DAY_OK} label="on pace" />
          <TallyLegendItem color={DAY_OVER} label="over" />
          <TallyLegendItem color={DAY_EMPTY} label="not yet" />
        </div>
      </div>

      <p className="sr-only">
        {hasGhost
          ? `Recorded spend of ${spent} RON so far this month, against a ghost flock baseline of ${baseline} RON by month end`
          : `Recorded spend of ${spent} RON so far this month, across ${daysEntered} recorded days`}
      </p>

      <div className="flex items-center gap-3.5">
        <div className="w-[82px] flex-none">
          <div className="text-[12.5px]">this month</div>
          <div className="text-muted-foreground mt-0.5 font-mono text-[10.5px]">{daysEntered} days</div>
        </div>
        <div className="border-border flex h-16 flex-1 items-end border-b">
          {days.map((d) => {
            const dayNum = Number(d.date.slice(-2))
            const status = statusByDate.get(d.date)
            const isSelected = selectedDay === d.date
            const color = isSelected
              ? "#F2F5F6"
              : d.amount === 0
                ? DAY_EMPTY
                : status === "MISSED"
                  ? DAY_OVER
                  : DAY_OK
            const heightPct = d.amount === 0 ? 25 : Math.round(34 + (d.amount / peak) * 62)
            return (
              <button
                key={d.date}
                type="button"
                aria-pressed={isSelected}
                aria-label={`${shortDate(d.date)} · ${formatRon(d.amount)}${
                  status === "MISSED" ? " · over" : status === "MET" ? " · on pace" : ""
                }`}
                title={`${shortDate(d.date)} · ${formatRon(d.amount)}`}
                onClick={() => onSelectDay(isSelected ? null : d.date)}
                className="flex h-full flex-1 cursor-pointer items-end justify-center border-none p-0 transition-colors hover:bg-[#14181B] focus-visible:outline-2 focus-visible:outline-offset-[-3px] focus-visible:outline-[#9AD4E3]"
                style={{ background: isSelected ? "#123945" : "transparent" }}
              >
                <span
                  className="block"
                  style={{
                    width: dayNum % 5 === 0 ? 2 : 1,
                    height: `${heightPct}%`,
                    background: color,
                  }}
                />
              </button>
            )
          })}
        </div>
      </div>

      {hasGhost && (
        <div className="mt-1.5 flex items-center gap-3.5">
          <div className="w-[82px] flex-none">
            <div className="text-[12.5px] text-[#C7CDD0]">ghost flock</div>
            <div className="text-muted-foreground mt-0.5 font-mono text-[10.5px]">{ghostDays} days</div>
          </div>
          <div className="border-border flex h-8 flex-1 items-end border-b" aria-hidden="true">
            {days.map((d) => {
              const dayNum = Number(d.date.slice(-2))
              const g = ghostByDate.get(d.date) ?? 0
              const heightPct = g === 0 ? 31 : Math.round(44 + (g / peak) * 50)
              return (
                <div key={d.date} className="flex h-full flex-1 items-end justify-center">
                  <span
                    className="block"
                    style={{
                      width: dayNum % 5 === 0 ? 2 : 1,
                      height: `${heightPct}%`,
                      background: g === 0 ? "#3A4145" : "#8A9399",
                    }}
                  />
                </div>
              )
            })}
          </div>
        </div>
      )}

      <div className="mt-2 flex pl-[96px]">
        {days.map((d) => {
          const dayNum = Number(d.date.slice(-2))
          const show = dayNum === 1 || dayNum % 5 === 0 || dayNum === today
          return (
            <div
              key={d.date}
              className="flex-1 text-center font-mono text-[9px]"
              style={{ color: selectedDay === d.date ? DAY_OK : "var(--muted-foreground)" }}
            >
              {show ? dayNum : ""}
            </div>
          )
        })}
      </div>

      <p className="text-muted-foreground mt-3 text-[12.5px] leading-relaxed">
        {hasGhost
          ? `The ghost flock is this same month with your non-mandatory spending held at its trailing 3-month median — ${baseline} RON by month end.`
          : "The ghost flock — this month with your non-mandatory spending held at its 3-month median — unlocks once you have three months of history."}
      </p>
    </section>
  )
}

function TallyLegendItem({ color, label }: { color: string; label: string }) {
  return (
    <span className="text-muted-foreground flex items-center gap-1.5 font-mono text-[10.5px] tracking-[0.1em] uppercase">
      <span className="h-[11px] w-px flex-none" style={{ background: color }} />
      {label}
    </span>
  )
}

/* ── Widgets ───────────────────────────────────────────────────────────── */

/**
 * The ledger's latest entries, or one day of them when a tally notch is picked.
 * Numbering is positional within what is shown — the API carries no entry serial,
 * so a stable never-renumbered serial cannot be shown honestly here.
 */
function LedgerWidget({
  entries,
  categories,
  selectedDay,
  onClearDay,
  onNavigate,
}: {
  entries: Expense[]
  categories: Category[]
  selectedDay: string | null
  onClearDay: () => void
  onNavigate?: (page: string, categoryName?: string) => void
}) {
  const shown = selectedDay
    ? entries.filter((e) => e.expenseDate === selectedDay)
    : entries.slice(0, 7)
  const subtotal = shown.reduce((sum, e) => sum + e.amount, 0)

  return (
    <section className="ledger-card p-6">
      <div className="mb-1.5 flex items-baseline justify-between gap-3">
        <span className="ledger-label">
          {selectedDay ? `Ledger · ${dayAndMonth(selectedDay)}` : "Ledger · latest entries"}
        </span>
        <span className="ledger-label !text-[10px]">Amount</span>
      </div>

      {shown.length === 0 ? (
        <div className="py-10 text-center">
          <p className="text-[14px] text-[#C7CDD0]">
            {selectedDay
              ? "Nothing recorded on that day."
              : "Nothing yet — add your first expense."}
          </p>
          {selectedDay && (
            <button
              onClick={onClearDay}
              className="ledger-label !text-primary mt-3 min-h-11 cursor-pointer px-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
            >
              Show the whole month
            </button>
          )}
        </div>
      ) : (
        <>
          {shown.map((e, i) => {
            const label = e.note || topName(categories, e.categoryId)
            return (
              <button
                key={e.id}
                onClick={() => onNavigate?.("expenses")}
                className="border-border flex h-11 w-full cursor-pointer items-baseline gap-4 border-b px-3 text-left transition-colors hover:bg-popover focus-visible:outline-2 focus-visible:outline-offset-[-3px] focus-visible:outline-[#9AD4E3]"
              >
                <span className="text-muted-foreground w-8 flex-none font-mono text-[11px]">
                  {String(shown.length - i).padStart(2, "0")}
                </span>
                <span className="min-w-0 flex-1 truncate text-[14.5px]">{label}</span>
                <span className="text-muted-foreground hidden flex-none font-mono text-[11px] tracking-[0.08em] uppercase sm:inline">
                  {categoryLabel(categories, e.categoryId)}
                </span>
                <span className="text-muted-foreground w-16 flex-none text-right font-mono text-[11px]">
                  {shortDate(e.expenseDate)}
                </span>
                <span className="figure w-24 flex-none text-right text-[14.5px]">
                  −{bareRon(e.amount)}
                </span>
              </button>
            )
          })}
          <div className="border-border flex h-[46px] items-baseline gap-4 border-b px-3">
            <span className="w-8 flex-none" />
            <span className="ledger-label flex-1">
              {selectedDay ? "Total for that day" : "Latest total"}
            </span>
            <span className="figure w-24 flex-none text-right text-[14.5px]">−{bareRon(subtotal)}</span>
          </div>
        </>
      )}

      <div className="mt-4 flex flex-wrap items-baseline justify-between gap-3">
        <p className="text-muted-foreground text-[13px]">
          Newest first. The full ledger, with search and filters, is on the Expenses tab.
        </p>
        <button
          onClick={() => onNavigate?.("expenses")}
          className="text-primary min-h-11 cursor-pointer px-2 text-[13px] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
        >
          See all
        </button>
      </div>
    </section>
  )
}

function SavingsWidget({ netWorth, savings }: { netWorth: NetWorth | null; savings: SavingsAccount[] }) {
  return (
    <section className="ledger-card p-6">
      <div className="flex items-center gap-2.5">
        <HornGlyph className="text-primary size-5" />
        <h2 className="ledger-label">Net worth · recorded</h2>
      </div>
      <div className="figure mt-3 text-[34px]">{netWorth ? bareRon(netWorth.total) : "—"}</div>
      {savings.length > 0 ? (
        <div className="mt-4">
          {savings.map((s) => (
            <div
              key={s.id}
              className="flex items-center justify-between gap-3 border-b border-[#2A3033] py-2.5 last:border-b-0"
            >
              <span className="min-w-0 truncate text-[14px] text-[#C7CDD0]">{s.name}</span>
              <span className="figure flex-none text-[14px]">{bareRon(s.balance)}</span>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-muted-foreground mt-4 text-[13.5px]">No savings pots recorded yet.</p>
      )}
      <p className="text-muted-foreground mt-4 text-[13px] text-pretty">
        Figures you keep yourself. Argali never touches the money.
      </p>
    </section>
  )
}

/** A row of notches filled to `pct` — the ledger's own texture for "how far along," in place of a smooth bar. */
function Notches({ pct, hot }: { pct: number; hot: boolean }) {
  const total = 18
  const filled = Math.round((pct / 100) * total)
  return (
    <div className="mt-2.5 flex h-6 items-end gap-[2px]" aria-hidden="true">
      {Array.from({ length: total }, (_, i) => (
        <span
          key={i}
          className="flex-1"
          style={{
            height: `${28 + (i % 3) * 10}%`,
            background: i < filled ? (hot ? "var(--destructive)" : "var(--primary)") : "var(--border)",
          }}
        />
      ))}
    </div>
  )
}

function QuestsWidget({
  activeQuests,
  oaths,
  onNavigate,
}: {
  activeQuests: Quest[]
  oaths: Oath[]
  onNavigate?: (page: string, categoryName?: string) => void
}) {
  const openOaths = oaths.filter((o) => o.status === "OPEN").slice(0, 3)

  return (
    <section className="ledger-card p-6">
      <div className="mb-4 flex items-baseline justify-between gap-3">
        <h2 className="ledger-label !text-primary">Quests · active</h2>
        <button
          onClick={() => onNavigate?.("quests")}
          className="text-primary min-h-11 cursor-pointer px-2 text-[13px] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
        >
          All
        </button>
      </div>
      <div className="flex flex-col gap-5">
        {activeQuests.length === 0 ? (
          <p className="text-muted-foreground text-sm">No active quests — accept one on the Quests page.</p>
        ) : (
          activeQuests.map((q) => {
            const pct = q.target > 0 ? Math.min(100, (q.progress / q.target) * 100) : 0
            const hot = q.kind === "CAP" && pct > 85
            return (
              <div key={q.id}>
                <div className="flex items-baseline justify-between gap-2.5">
                  <span className={`text-[14.5px] ${hot ? "text-destructive" : ""}`}>{q.title}</span>
                  <span
                    className={`tnum flex-none font-mono text-[13px] ${hot ? "text-destructive" : ""}`}
                  >
                    {q.kind === "DAYS"
                      ? `${q.progress}/${q.target}`
                      : `${Math.round(q.progress / 100)} / ${Math.round(q.target / 100)}`}
                  </span>
                </div>
                <Notches pct={pct} hot={hot} />
              </div>
            )
          })
        )}
      </div>

      {openOaths.length > 0 && (
        <>
          <div className="bg-border my-5 h-px" />
          <div className="mb-3.5 flex items-baseline justify-between">
            <span className="ledger-label">Open commitments</span>
            <span className="text-muted-foreground font-mono text-[11px]">
              {openOaths.length} · pending
            </span>
          </div>
          <div className="flex flex-col gap-3.5">
            {openOaths.map((o) => {
              const msLeft = new Date(o.expiresAt).getTime() - Date.now()
              const totalMs = new Date(o.expiresAt).getTime() - new Date(o.createdAt).getTime()
              const urgent = msLeft <= 6 * 3_600_000
              const hoursLeft = Math.max(0, Math.floor(msLeft / 3_600_000))
              const daysLeft = Math.floor(hoursLeft / 24)
              const countdown = msLeft <= 0 ? "closing" : daysLeft > 0 ? `${daysLeft}d left` : `${hoursLeft}h left`
              const pctLeft = totalMs > 0 ? Math.max(4, Math.min(100, Math.round((msLeft / totalMs) * 100))) : 4
              const barColor = urgent ? "var(--destructive)" : "var(--primary)"
              return (
                <div
                  key={o.id}
                  className="pb-3.5 pl-3 last:pb-0"
                  style={{ boxShadow: `inset 2px 0 0 0 ${barColor}` }}
                >
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="text-[14px]">
                      Under {bareRon(o.pledgedAmount)} RON on {o.categoryName}
                    </span>
                    <span
                      className="flex-none font-mono text-[10.5px] tracking-[0.1em] uppercase"
                      style={{ color: barColor, animation: urgent ? "shimmer 1.6s ease-in-out infinite" : "none" }}
                    >
                      {countdown}
                    </span>
                  </div>
                  <div
                    className="mt-2.5 h-px"
                    style={{ background: "color-mix(in srgb, var(--foreground) 8%, transparent)" }}
                  >
                    <div className="h-px" style={{ width: `${pctLeft}%`, background: barColor }} />
                  </div>
                </div>
              )
            })}
          </div>
        </>
      )}
    </section>
  )
}

/* ── Arrange affordances ───────────────────────────────────────────────── */

/**
 * Wraps one widget with its arrange affordances. The arrow buttons are the primary
 * mechanism — HTML5 drag-and-drop does not fire on touch and is not reachable by
 * keyboard, so dragging is the enhancement, not the interface.
 */
function ArrangeableWidget({
  widget,
  column,
  index,
  total,
  arranging,
  isDragging,
  onMove,
  onSwapColumn,
  onDragStart,
  onDragEnd,
  onDrop,
  children,
}: {
  widget: WidgetId
  column: Column
  index: number
  total: number
  arranging: boolean
  isDragging: boolean
  onMove: (delta: number) => void
  onSwapColumn: () => void
  onDragStart: () => void
  onDragEnd: () => void
  onDrop: () => void
  children: React.ReactNode
}) {
  if (!arranging) return <>{children}</>

  const name = WIDGET_NAMES[widget]

  return (
    <div
      draggable
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      onDragOver={(e) => e.preventDefault()}
      onDrop={(e) => {
        e.preventDefault()
        onDrop()
      }}
      className="border-primary/40 border border-dashed p-2 transition-opacity"
      style={{ opacity: isDragging ? 0.4 : 1, cursor: "grab" }}
    >
      <div className="mb-2 flex items-center justify-between gap-2 px-1">
        <span className="ledger-label truncate">{name}</span>
        <div className="flex items-center gap-1">
          <ArrangeButton
            label={`Move ${name} up`}
            disabled={index === 0}
            onClick={() => onMove(-1)}
            rotation="-rotate-90"
          />
          <ArrangeButton
            label={`Move ${name} down`}
            disabled={index === total - 1}
            onClick={() => onMove(1)}
            rotation="rotate-90"
          />
          <ArrangeButton
            label={`Move ${name} to ${COLUMN_NAMES[OTHER[column]]}`}
            onClick={onSwapColumn}
            rotation={column === "main" ? "" : "rotate-180"}
          />
        </div>
      </div>
      {children}
    </div>
  )
}

/** 44×44 minimum, per the touch-target follow-up in docs/design.md. */
function ArrangeButton({
  label,
  onClick,
  rotation,
  disabled = false,
}: {
  label: string
  onClick: () => void
  /** Tailwind rotation utility turning the right-pointing chevron into the direction meant. */
  rotation: string
  disabled?: boolean
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      title={label}
      className="text-muted-foreground hover:text-foreground grid size-11 flex-none cursor-pointer place-items-center border border-border transition-colors hover:bg-popover disabled:cursor-not-allowed disabled:opacity-30 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
    >
      <ChevronIcon size={16} className={rotation} />
    </button>
  )
}

/** Lets a widget be dropped past the last one in a column, including an empty column. */
function ColumnEndDropZone({
  column,
  active,
  onDrop,
}: {
  column: Column
  active: boolean
  onDrop: () => void
}) {
  return (
    <div
      onDragOver={(e) => e.preventDefault()}
      onDrop={(e) => {
        e.preventDefault()
        onDrop()
      }}
      aria-hidden="true"
      className="text-muted-foreground border-border grid min-h-16 place-items-center border border-dashed text-[12.5px] transition-colors"
      style={{ opacity: active ? 1 : 0.45 }}
    >
      Drop here to put a widget at the end of {COLUMN_NAMES[column]}
    </div>
  )
}

/* ── Chrome ────────────────────────────────────────────────────────────── */

/* Full ram mark used as the watermark inside the hero. */
function HornGlyphFull({ strokeWidth = 7 }: { strokeWidth?: number }) {
  return (
    <svg
      viewBox="-8 -13 116 116"
      fill="none"
      stroke="#9AD4E3"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-full"
      aria-hidden="true"
    >
      <path d="M86 78H38L10 60V50L48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39" />
      <circle cx="32" cy="52" r="4" fill="#9AD4E3" stroke="none" />
    </svg>
  )
}

/** Notch heights standing in for a count that never climbs from zero — "a figure that climbs is a figure that lies." */
const BOOT_NOTCHES = [
  { h: "100%", color: "#9AD4E3" },
  { h: "72%", color: "#9AD4E3" },
  { h: "88%", color: "#9AD4E3" },
  { h: "60%", color: "#4C93A6" },
  { h: "80%", color: "#4C93A6" },
  { h: "40%", color: "#62696D" },
  { h: "40%", color: "#62696D" },
]

function DashSkeletonNote({ slow, onRetry }: { slow: boolean; onRetry: () => void }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex flex-col items-center justify-center gap-[18px] py-24"
    >
      <div className="text-primary size-14">
        <svg viewBox="-8 -13 116 116" fill="none" stroke="currentColor" strokeWidth={7} strokeLinecap="round" strokeLinejoin="round" className="size-full" aria-hidden="true">
          <path d="M86 78H38L10 60V50L48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39" />
          <circle cx="32" cy="52" r="4" fill="currentColor" stroke="none" />
        </svg>
      </div>
      <div className="flex h-[22px] items-end gap-[3px]" aria-hidden="true">
        {BOOT_NOTCHES.map((n, i) => (
          <span key={i} className="w-[2px]" style={{ height: n.h, background: n.color }} />
        ))}
      </div>
      <div className="ledger-label" style={{ animation: "shimmer 2.4s ease-in-out infinite" }}>
        Counting your notches
      </div>
      {slow && (
        <div className="mt-1 flex flex-col items-center gap-2">
          <p className="text-muted-foreground text-[13px]">Taking longer than usual.</p>
          <button
            onClick={onRetry}
            className="text-primary min-h-11 cursor-pointer px-3 text-[13.5px] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
          >
            Try again
          </button>
        </div>
      )}
    </div>
  )
}
