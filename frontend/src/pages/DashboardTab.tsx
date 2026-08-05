import { useEffect, useMemo, useState } from "react"
import {
  formatRon,
  getCalendar,
  getDashboard,
  getNetWorth,
  listExpenses,
  listQuests,
  type Category,
  type Dashboard,
  type Expense,
  type GoalCalendar,
  type NetWorth,
  type Quest,
} from "@/lib/api"
import { HornGlyph } from "@/components/brand"
import { RabojStreak } from "@/components/raboj"

const DONUT = ["#c79a5b", "#e8d3b4", "#b07e52", "#8a6440", "#9cb37a", "#c98a3c"]

const thisMonth = () => new Date().toISOString().slice(0, 7)
const monthLabel = (m: string) =>
  new Date(m + "-01T00:00:00").toLocaleDateString("en-GB", { month: "long", year: "numeric" })

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
function monogram(label: string): string {
  const w = label.trim().split(/\s+/).filter(Boolean)
  if (!w.length) return "··"
  return (w.length === 1 ? w[0].slice(0, 2) : w[0][0] + w[1][0]).toUpperCase()
}
const shortDate = (iso: string) =>
  new Date(iso + "T00:00:00").toLocaleDateString("en-GB", { day: "numeric", month: "short" })

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
  const [slice, setSlice] = useState<string | null>(null)
  const [hover, setHover] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getDashboard(month)
      .then((d) => {
        setData(d)
        setError(null)
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load"))
  }, [month])

  useEffect(() => {
    getNetWorth().then(setNetWorth).catch(() => setNetWorth(null))
    listQuests().then(setQuests).catch(() => setQuests([]))
    getCalendar().then(setCalendar).catch(() => setCalendar(null))
    listExpenses({}).then((p) => setRecent(p.items.slice(0, 4))).catch(() => setRecent([]))
  }, [])

  // Spend-by-category, largest first, capped to the donut palette.
  const slices = useMemo(() => {
    if (!data) return []
    const sorted = [...data.byCategory].sort((a, b) => b.amount - a.amount)
    if (sorted.length <= DONUT.length) return sorted.map((s, i) => ({ ...s, color: DONUT[i] }))
    const keep = sorted.slice(0, DONUT.length - 1)
    const other = sorted.slice(DONUT.length - 1).reduce((s, x) => s + x.amount, 0)
    return [...keep.map((s, i) => ({ ...s, color: DONUT[i] })), { category: "Other", amount: other, color: DONUT[DONUT.length - 1] }]
  }, [data])

  // Daily spend, last 7 days of the month, for the hero trend bars.
  const bars = useMemo(() => {
    if (!data) return []
    const days = data.byDay.slice(-7)
    const max = Math.max(1, ...days.map((d) => d.amount))
    return days.map((d, i) => ({
      label: new Date(d.date + "T00:00:00").toLocaleDateString("en-GB", { weekday: "short" }).slice(0, 3),
      h: Math.max(6, Math.round((d.amount / max) * 120)),
      on: i === days.length - 1,
    }))
  }, [data])

  const greenDays = calendar?.days.filter((d) => d.status === "MET").length ?? 0
  const activeQuests = quests.filter((q) => q.status === "ACTIVE").slice(0, 3)

  if (error) return <p className="text-destructive py-8 text-center text-sm">{error}</p>
  if (!data) return <DashSkeletonNote />

  const totalSpent = data.totalSpent
  const delta = data.totalSpent - data.previousMonthTotal
  const active = hover ?? slice
  const activeSlice = slices.find((s) => s.category === active)
  const donutTotal = activeSlice ? activeSlice.amount : totalSpent

  // SVG donut geometry.
  const R = 62
  const C = 2 * Math.PI * R
  let offset = 0

  return (
    <div className="flex flex-wrap items-start gap-[22px]">
      {/* Left */}
      <div className="flex min-w-[min(100%,440px)] flex-1 basis-[62%] flex-col gap-[22px]">
        <div className="ledger-label flex h-6 items-center">OVERVIEW · {monthLabel(month)}</div>

        {/* Balance hero */}
        <section
          className="relative overflow-hidden rounded-3xl border border-white/[0.09] p-6"
          style={{ background: "linear-gradient(155deg,#3A2A1E 0%,#2A1E16 45%,#191210 100%)" }}
        >
          <div className="pointer-events-none absolute -right-12 -bottom-16 size-[270px] opacity-10">
            <HornGlyphFull />
          </div>
          <div className="relative flex flex-wrap items-start justify-between gap-3.5">
            <div>
              <div className="ledger-label">Net worth</div>
              <div className="mt-3 flex items-baseline gap-2">
                <span className="font-heading tnum text-[48px] leading-none font-semibold tracking-[-0.03em] sm:text-[56px]">
                  {netWorth ? Math.floor(netWorth.total / 100).toLocaleString("en-US") : "—"}
                </span>
                <span className="font-heading tnum text-[22px] font-medium text-[#D2C5B4]">
                  {netWorth ? "." + String(netWorth.total % 100).padStart(2, "0") : ""}
                </span>
                <span className="text-muted-foreground font-mono text-[14px]">RON</span>
              </div>
              <div
                className="mt-4 inline-flex items-center gap-2 rounded-full border px-3.5 py-2 font-mono text-[13px]"
                style={
                  delta <= 0
                    ? { borderColor: "rgba(156,179,122,0.35)", background: "rgba(156,179,122,0.1)", color: "#B2C58F" }
                    : { borderColor: "rgba(201,106,78,0.35)", background: "rgba(201,106,78,0.1)", color: "#E0A08C" }
                }
              >
                {delta <= 0 ? "↓" : "↑"} {formatRon(Math.abs(delta)).replace(/\s?RON$/, "")}{" "}
                {delta <= 0 ? "less" : "more"} spent than last month
              </div>
            </div>
            <div className="text-right">
              <div className="ledger-label">Spent · {monthLabel(month).split(" ")[0]}</div>
              <div className="tnum mt-2 font-mono text-[18px] font-semibold">−{formatRon(totalSpent)}</div>
              {data.projectedMonthEnd !== null && (
                <div className="text-muted-foreground mt-1 text-[11.5px]">
                  projected {formatRon(data.projectedMonthEnd)}
                </div>
              )}
            </div>
          </div>
          {bars.length > 0 && (
            <div
              className="relative mt-6.5 grid items-end gap-3.5"
              style={{ gridTemplateColumns: `repeat(${bars.length},1fr)`, height: 130 }}
            >
              {bars.map((b, i) => (
                <div key={i} className="flex flex-col items-center gap-2.5">
                  <div
                    className="w-full max-w-[46px] rounded-full transition-[filter]"
                    style={{
                      height: b.h,
                      background: b.on ? "#C79A5B" : "rgba(255,255,255,0.3)",
                    }}
                  />
                  <span className="text-muted-foreground font-mono text-[11.5px]">{b.label}</span>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Where your money goes */}
        <section className="bg-card rounded-3xl border border-white/[0.07] p-6">
          <div className="mb-5.5 flex items-center gap-2.5">
            <HornGlyph className="text-primary size-5.5" />
            <h2 className="text-[18px] font-semibold">Where your money goes</h2>
          </div>
          <div className="grid gap-14 [grid-template-columns:repeat(auto-fit,minmax(300px,1fr))]">
            {/* Donut + legend */}
            <div className="min-w-0">
              <div className="ledger-label mb-4.5">Spend by category</div>
              {slices.length === 0 ? (
                <p className="text-muted-foreground py-8 text-sm">No spending recorded this month.</p>
              ) : (
                <div className="flex items-center gap-5.5">
                  <div className="relative size-[158px] flex-none">
                    <svg viewBox="0 0 158 158" className="size-full -rotate-90">
                      {slices.map((s) => {
                        const frac = totalSpent > 0 ? s.amount / totalSpent : 0
                        const len = C * frac
                        const on = active === s.category
                        const dim = active && !on
                        const el = (
                          <circle
                            key={s.category}
                            cx={79}
                            cy={79}
                            r={R}
                            fill="none"
                            stroke={s.color}
                            strokeWidth={on ? 30 : 24}
                            strokeDasharray={`${Math.max(0, len - 3)} ${C - len + 3}`}
                            strokeDashoffset={-offset}
                            opacity={dim ? 0.32 : 1}
                            style={{ cursor: "pointer", transition: "stroke-width .15s ease, opacity .15s ease" }}
                            onMouseEnter={() => setHover(s.category)}
                            onMouseLeave={() => setHover(null)}
                            onClick={() => setSlice(slice === s.category ? null : s.category)}
                          />
                        )
                        offset += len
                        return el
                      })}
                    </svg>
                    <div className="pointer-events-none absolute inset-0 grid place-items-center text-center">
                      <div>
                        <div className="font-heading tnum text-[23px] font-semibold tracking-tight">
                          {formatRon(donutTotal).replace(/\s?RON$/, "")}
                        </div>
                        <div className="ledger-label !text-[9.5px]">
                          {activeSlice ? activeSlice.category : "RON spent"}
                        </div>
                      </div>
                    </div>
                  </div>
                  <div className="flex min-w-0 flex-1 flex-col gap-3">
                    {slices.map((s) => {
                      const pct = totalSpent > 0 ? Math.round((s.amount / totalSpent) * 100) : 0
                      const sel = slice === s.category
                      return (
                        <div
                          key={s.category}
                          role="button"
                          tabIndex={0}
                          onClick={() => setSlice(sel ? null : s.category)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter" || e.key === " ") {
                              e.preventDefault()
                              setSlice(sel ? null : s.category)
                            }
                          }}
                          onMouseEnter={() => setHover(s.category)}
                          onMouseLeave={() => setHover(null)}
                          className={`-mx-2 flex cursor-pointer items-center gap-2.5 rounded-lg px-2 py-1.5 text-[14.5px] transition-colors ${
                            sel ? "bg-white/[0.07]" : "hover:bg-white/[0.06]"
                          }`}
                        >
                          <span className="size-2.5 flex-none rounded-[3px]" style={{ background: s.color }} />
                          <span className="flex-1 truncate font-medium">{s.category}</span>
                          <span className="tnum font-mono">{formatRon(s.amount).replace(/\s?RON$/, "")}</span>
                          <span className="tnum text-muted-foreground w-9 text-right font-mono">{pct}%</span>
                        </div>
                      )
                    })}
                    <p className="text-muted-foreground mt-0.5 text-[12.5px]">
                      Click a category to open it in Expenses
                    </p>
                  </div>
                </div>
              )}
              {slice && activeSlice && (
                <div
                  className="mt-4 rounded-2xl border border-white/[0.09] bg-[#241C17] p-5"
                  style={{ animation: "riseIn .16s ease" }}
                >
                  <div className="flex items-center justify-between">
                    <div className="text-[15.5px] font-semibold">{activeSlice.category}</div>
                    <button onClick={() => setSlice(null)} className="text-muted-foreground cursor-pointer text-lg leading-none">×</button>
                  </div>
                  <div className="text-muted-foreground mt-1 font-mono text-[12px]">
                    {formatRon(activeSlice.amount)} ·{" "}
                    {totalSpent > 0 ? Math.round((activeSlice.amount / totalSpent) * 100) : 0}% of spend
                  </div>
                  <button
                    onClick={() => onNavigate?.("expenses", activeSlice.category)}
                    className="text-primary mt-4 w-full cursor-pointer rounded-xl border border-primary/50 bg-primary/15 py-2.5 text-[13.5px] font-semibold hover:bg-primary/25"
                  >
                    See all {activeSlice.category} transactions →
                  </button>
                </div>
              )}
            </div>

            {/* Recent activity */}
            <div className="min-w-0">
              <div className="mb-4.5 flex items-baseline justify-between">
                <span className="ledger-label">Recent activity</span>
                <button onClick={() => onNavigate?.("expenses")} className="text-primary cursor-pointer text-[13px]">
                  See all
                </button>
              </div>
              <div className="flex flex-col">
                {recent.length === 0 ? (
                  <p className="text-muted-foreground py-4 text-sm">Nothing yet — add your first expense.</p>
                ) : (
                  recent.map((t) => {
                    const label = t.note || topName(categories, t.categoryId)
                    return (
                      <button
                        key={t.id}
                        onClick={() => onNavigate?.("expenses")}
                        className="-mx-3 flex items-center gap-3.5 rounded-xl px-3 py-2.75 text-left transition-colors hover:bg-white/[0.055]"
                      >
                        <div className="grid size-[38px] flex-none place-items-center rounded-xl bg-[#241C17] font-mono text-[14px] text-[#CFC1AE]">
                          {monogram(label)}
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="truncate text-[14.5px] font-medium">{label}</div>
                          <div className="text-muted-foreground font-mono text-[12px]">
                            {categoryLabel(categories, t.categoryId)} · {shortDate(t.expenseDate)}
                          </div>
                        </div>
                        <span className="tnum font-mono text-[14px] text-[#EFE9DC]">−{formatRon(t.amount).replace(/\s?RON$/, "")}</span>
                      </button>
                    )
                  })
                )}
              </div>
            </div>
          </div>
        </section>
      </div>

      {/* Right widgets */}
      <div className="flex min-w-[min(100%,320px)] flex-1 basis-[30%] flex-col gap-[22px]">
        {/* Savings */}
        <section className="bg-card rounded-3xl border border-white/[0.07] p-6">
          <div className="flex items-center gap-2.5">
            <HornGlyph className="text-primary size-5" />
            <h2 className="text-[17px] font-semibold">Savings</h2>
          </div>
          <div className="mt-4 flex items-baseline gap-2.5">
            <span className="font-heading tnum text-[36px] font-semibold tracking-tight">
              {netWorth ? formatRon(netWorth.total).replace(/\s?RON$/, "") : "—"}
            </span>
            <span className="text-muted-foreground text-[14.5px]">RON saved</span>
          </div>
          <p className="text-muted-foreground mt-4 text-[13.5px]">
            {netWorth?.accounts ?? 0} savings account{(netWorth?.accounts ?? 0) === 1 ? "" : "s"} ·{" "}
            {netWorth ? formatRon(netWorth.monthlyIncome).replace(/\s?RON$/, "") : "—"} income / month
          </p>
        </section>

        {/* Streak — a răboj tally: one notch per on-budget day */}
        <section className="bg-card rounded-3xl border border-white/[0.07] p-6">
          <h2 className="text-[17px] font-semibold">Streak</h2>
          <div className="mt-3.5 flex items-baseline gap-2.5">
            <span className="figure text-[36px] font-semibold">{greenDays}</span>
            <span className="text-muted-foreground text-[14.5px]">on-budget days this month</span>
          </div>
          <div className="mt-5">
            <RabojStreak count={greenDays} />
          </div>
        </section>

        {/* Active quests */}
        <section className="bg-card rounded-3xl border border-white/[0.07] p-6">
          <div className="mb-4.5 flex items-center justify-between">
            <h2 className="text-[17px] font-semibold">Active quests</h2>
            <button onClick={() => onNavigate?.("quests")} className="text-primary cursor-pointer text-[13.5px]">All</button>
          </div>
          <div className="flex flex-col gap-4.5">
            {activeQuests.length === 0 ? (
              <p className="text-muted-foreground text-sm">No active quests — accept one on the Quests page.</p>
            ) : (
              activeQuests.map((q) => {
                const pct = q.target > 0 ? Math.min(100, (q.progress / q.target) * 100) : 0
                const hot = q.kind === "CAP" && pct > 85
                const color = hot ? "#C98A3C" : "#C79A5B"
                return (
                  <div key={q.id}>
                    <div className="flex items-baseline justify-between gap-2.5">
                      <span className="text-[14.5px] font-medium" style={{ color: hot ? "#C98A3C" : undefined }}>
                        {q.title}
                      </span>
                      <span className="tnum font-mono text-[13px]" style={{ color: hot ? "#C98A3C" : undefined }}>
                        {q.kind === "DAYS"
                          ? `${q.progress}/${q.target}`
                          : `${Math.round(q.progress / 100)} / ${Math.round(q.target / 100)}`}
                      </span>
                    </div>
                    <div className="mt-2.5 h-2 overflow-hidden rounded-full bg-white/[0.09]">
                      <div className="h-full rounded-full" style={{ width: `${pct}%`, background: color }} />
                    </div>
                  </div>
                )
              })
            )}
          </div>
        </section>
      </div>
    </div>
  )
}

/* Full ram mark used as the watermark inside the hero. */
function HornGlyphFull() {
  return (
    <svg
      viewBox="-8 -13 116 116"
      fill="none"
      stroke="#C79A5B"
      strokeWidth={7}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-full"
      aria-hidden="true"
    >
      <path d="M86 78H38L10 60V50L48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39" />
      <circle cx="32" cy="52" r="4" fill="#C79A5B" stroke="none" />
    </svg>
  )
}

function DashSkeletonNote() {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-24">
      <div className="text-foreground size-14">
        <svg viewBox="-8 -13 116 116" fill="none" stroke="currentColor" strokeWidth={7} strokeLinecap="round" strokeLinejoin="round" className="size-full" aria-hidden="true">
          <path d="M86 78H38L10 60V50L48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39" stroke="#C79A5B" style={{ strokeDasharray: 370, animation: "coilUnroll 2.1s cubic-bezier(.5,0,.5,1) infinite" }} />
          <circle cx="32" cy="52" r="4" fill="currentColor" stroke="none" />
        </svg>
      </div>
      <div className="ledger-label" style={{ color: "#8C7D6C" }}>Loading your money</div>
    </div>
  )
}
