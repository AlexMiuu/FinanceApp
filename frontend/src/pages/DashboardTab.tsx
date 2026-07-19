import { useEffect, useMemo, useState } from "react"
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts"
import { useAuth } from "@/auth/AuthContext"
import {
  formatRon,
  getCalendar,
  getDashboard,
  getNetWorth,
  listQuests,
  type Dashboard,
  type GoalCalendar,
  type NetWorth,
  type Quest,
} from "@/lib/api"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Progress } from "@/components/ui/progress"

// Dataviz-validated categorical set for the espresso surface (worst ΔE 23.6).
const SERIES = ["#d95926", "#199e70", "#c98500", "#9085e9", "#008300", "#e66767"]
const OTHER_COLOR = "#8a8074"
const PRIMARY = "#f97c0e"
const GOOD = "#0ca30c"
const DANGER = "#ff4500"
const GRID = "rgba(255,255,255,.06)"
const MUTED = "#a89e90"

const thisMonth = () => new Date().toISOString().slice(0, 7)

function greeting(): string {
  const h = new Date().getHours()
  return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening"
}

const WEEK_STATUS_STYLE: Record<string, string> = {
  MET: "bg-[#0ca30c]/30",
  MISSED: "bg-[#ff4500]/35",
  IN_PROGRESS: "border border-[#f97c0e] bg-transparent",
  FUTURE: "bg-secondary/40",
  NO_GOAL: "bg-secondary/25",
}

export default function DashboardTab({ onNavigate }: { onNavigate?: (page: string) => void }) {
  const { user } = useAuth()
  const [month, setMonth] = useState(thisMonth())
  const [data, setData] = useState<Dashboard | null>(null)
  const [netWorth, setNetWorth] = useState<NetWorth | null>(null)
  const [quests, setQuests] = useState<Quest[]>([])
  const [calendar, setCalendar] = useState<GoalCalendar | null>(null)
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
  }, [])

  const slices = useMemo(() => {
    if (!data) return []
    const sorted = [...data.byCategory]
    if (sorted.length <= SERIES.length) {
      return sorted.map((s, i) => ({ ...s, color: SERIES[i] }))
    }
    const keep = sorted
      .map((s, i) => ({ ...s, i }))
      .sort((a, b) => b.amount - a.amount)
      .slice(0, SERIES.length - 1)
      .sort((a, b) => a.i - b.i)
      .map((s, i) => ({ category: s.category, amount: s.amount, color: SERIES[i] }))
    const other =
      sorted.reduce((sum, s) => sum + s.amount, 0) - keep.reduce((sum, s) => sum + s.amount, 0)
    return [...keep, { category: "Other", amount: other, color: OTHER_COLOR }]
  }, [data])

  const cumulative = useMemo(() => {
    let acc = 0
    return (
      data?.byDay.map((d) => {
        acc += d.amount
        return { day: Number(d.date.slice(8, 10)), total: acc / 100 }
      }) ?? []
    )
  }, [data])

  // Current-week strip (Mon..Sun) from the goal calendar.
  const week = useMemo(() => {
    if (!calendar) return []
    const today = new Date()
    const monday = new Date(today)
    monday.setDate(today.getDate() - ((today.getDay() + 6) % 7))
    const dates = Array.from({ length: 7 }, (_, i) => {
      const d = new Date(monday)
      d.setDate(monday.getDate() + i)
      return d.toISOString().slice(0, 10)
    })
    return dates.map((date) => calendar.days.find((d) => d.date === date) ?? null)
  }, [calendar])

  const greenDays = calendar?.days.filter((d) => d.status === "MET").length ?? 0
  const activeQuests = quests.filter((q) => q.status === "ACTIVE").slice(0, 3)

  const isCurrentMonth = month === thisMonth()
  const now = new Date()
  const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate()
  const monthElapsedPct = Math.round((now.getDate() / daysInMonth) * 100)
  const daysLeft = daysInMonth - now.getDate()

  if (error) return <p className="text-destructive py-8 text-center text-sm">{error}</p>
  if (!data) return <p className="text-muted-foreground py-8 text-center text-sm">Loading…</p>

  const goalSummaries = [...(calendar?.monthlyGoals ?? []), ...(calendar?.yearlyGoals ?? [])]

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">
            {greeting()}, {user?.displayName}
          </h1>
          <p className="text-muted-foreground mt-0.5 text-[12.5px]">
            {now.toLocaleDateString("en-GB", { weekday: "long", month: "long", day: "numeric" })}
            {isCurrentMonth && ` · ${daysLeft} days left in the month`}
          </p>
        </div>
        <Input
          type="month"
          value={month}
          onChange={(e) => e.target.value && setMonth(e.target.value)}
          className="w-42"
          aria-label="Month"
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        {/* main column */}
        <div className="min-w-0 space-y-6">
          <div>
            <div className="flex items-baseline justify-between">
              <div>
                <p className="ledger-label">Net worth</p>
                <p className="mt-1.5 flex items-baseline gap-2.5">
                  <span className="text-[40px] font-semibold tracking-tight">
                    {netWorth ? (netWorth.total / 100).toLocaleString("ro-RO", { minimumFractionDigits: 2 }) : "—"}
                  </span>
                  <span className="text-muted-foreground font-mono text-base font-medium">RON</span>
                </p>
              </div>
              <div className="text-right">
                <p className="ledger-label">Spent · {data.month}</p>
                <p className="mt-1.5 font-mono text-base font-semibold" style={{ color: DANGER }}>
                  −{formatRon(data.totalSpent)}
                </p>
                {data.projectedMonthEnd !== null && (
                  <p className="text-muted-foreground mt-0.5 text-[11px]">
                    projected {formatRon(data.projectedMonthEnd)}
                  </p>
                )}
              </div>
            </div>
            <ResponsiveContainer width="100%" height={200} className="mt-2">
              <AreaChart data={cumulative} margin={{ top: 8, right: 4, left: 4, bottom: 0 }}>
                <defs>
                  <linearGradient id="spendFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={PRIMARY} stopOpacity={0.25} />
                    <stop offset="100%" stopColor={PRIMARY} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis
                  dataKey="day"
                  tickLine={false}
                  axisLine={false}
                  tick={{ fill: MUTED, fontSize: 11, fontFamily: "Geist Mono Variable" }}
                  interval={4}
                />
                <YAxis
                  tickLine={false}
                  axisLine={false}
                  tick={{ fill: MUTED, fontSize: 11, fontFamily: "Geist Mono Variable" }}
                  width={48}
                />
                <Tooltip
                  contentStyle={{ background: "#332a1d", border: "1px solid rgba(255,255,255,.12)", borderRadius: 10 }}
                  formatter={(value) => [formatRon(Math.round(Number(value) * 100)), "cumulative"]}
                  labelFormatter={(day) => `${data.month}-${String(day).padStart(2, "0")}`}
                />
                <Area
                  type="monotone"
                  dataKey="total"
                  stroke={PRIMARY}
                  strokeWidth={2.5}
                  fill="url(#spendFill)"
                />
              </AreaChart>
            </ResponsiveContainer>
            <p className="text-muted-foreground mt-1 text-[11px]">
              Cumulative spending through the month
            </p>
          </div>

          <div className="grid gap-6 xl:grid-cols-2">
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="ledger-label flex items-baseline justify-between font-normal">
                  Spending by category
                  <span className="text-muted-foreground font-sans text-[11.5px] normal-case tracking-normal">
                    {data.month}
                  </span>
                </CardTitle>
              </CardHeader>
              <CardContent>
                {slices.length === 0 ? (
                  <p className="text-muted-foreground py-8 text-center text-sm">
                    No expenses this month.
                  </p>
                ) : (
                  <div className="flex items-center gap-5">
                    <div className="relative h-[120px] w-[120px] flex-none">
                      <ResponsiveContainer width="100%" height="100%">
                        <PieChart>
                          <Pie
                            data={slices}
                            dataKey="amount"
                            nameKey="category"
                            innerRadius={40}
                            outerRadius={57}
                            paddingAngle={2}
                            strokeWidth={0}
                          >
                            {slices.map((slice) => (
                              <Cell key={slice.category} fill={slice.color} />
                            ))}
                          </Pie>
                          <Tooltip
                            contentStyle={{ background: "#332a1d", border: "1px solid rgba(255,255,255,.12)", borderRadius: 10 }}
                            formatter={(value) => formatRon(Number(value))}
                          />
                        </PieChart>
                      </ResponsiveContainer>
                      <div className="pointer-events-none absolute inset-0 grid place-items-center text-center">
                        <div>
                          <p className="text-base font-semibold leading-none">
                            {(data.totalSpent / 100000).toFixed(1)}K
                          </p>
                          <p className="text-muted-foreground mt-1 font-mono text-[9px]">RON</p>
                        </div>
                      </div>
                    </div>
                    <ul className="flex-1 space-y-2 text-[12.5px]">
                      {slices.map((slice) => (
                        <li key={slice.category} className="flex items-center gap-2">
                          <span
                            className="inline-block size-2 rounded-full"
                            style={{ backgroundColor: slice.color }}
                          />
                          {slice.category}
                          <span className="ml-auto font-mono">{formatRon(slice.amount)}</span>
                          <span className="text-muted-foreground w-9 text-right font-mono">
                            {data.totalSpent > 0 ? Math.round((slice.amount / data.totalSpent) * 100) : 0}%
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="ledger-label flex items-baseline justify-between font-normal">
                  Quest progress
                  <button
                    className="text-primary cursor-pointer border-none bg-transparent p-0 font-sans text-[11.5px] normal-case tracking-normal"
                    onClick={() => onNavigate?.("quests")}
                  >
                    View all
                  </button>
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {activeQuests.length === 0 ? (
                  <p className="text-muted-foreground py-6 text-center text-sm">
                    No active quests — accept one from the Quests page.
                  </p>
                ) : (
                  activeQuests.map((quest) => {
                    const pct = quest.target > 0 ? Math.min(100, (quest.progress / quest.target) * 100) : 0
                    return (
                      <div key={quest.id}>
                        <div className="flex justify-between text-[13px] font-medium">
                          <span className="truncate pr-2">{quest.title}</span>
                          <span className="text-muted-foreground font-mono text-xs">
                            {quest.kind === "DAYS"
                              ? `${quest.progress}/${quest.target} days`
                              : `${Math.round(quest.progress / 100)} / ${Math.round(quest.target / 100)}`}
                          </span>
                        </div>
                        <Progress value={pct} className="mt-2 h-[5px]" />
                      </div>
                    )
                  })
                )}
              </CardContent>
            </Card>
          </div>
        </div>

        {/* sidebar */}
        <div className="space-y-6">
          <Card className="bg-gradient-to-br from-[#48403a] to-[#2b2318]">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-semibold">Savings</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex justify-between">
                <div>
                  <p className="ledger-label text-[10px]">Total saved</p>
                  <p className="mt-1 text-xl font-semibold">
                    {netWorth ? formatRon(netWorth.total) : "—"}
                  </p>
                </div>
                <div className="text-right">
                  <p className="ledger-label text-[10px]">Income / month</p>
                  <p className="mt-1 text-xl font-semibold">
                    {netWorth ? formatRon(netWorth.monthlyIncome) : "—"}
                  </p>
                </div>
              </div>
              <p className="text-muted-foreground mt-3 text-[11px]">
                {netWorth?.accounts ?? 0} savings account{(netWorth?.accounts ?? 0) === 1 ? "" : "s"} —
                manage them in Profile
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="ledger-label flex items-baseline justify-between font-normal">
                Daily goals
                <button
                  className="text-primary cursor-pointer border-none bg-transparent p-0 font-sans text-[11.5px] normal-case tracking-normal"
                  onClick={() => onNavigate?.("quests")}
                >
                  Calendar
                </button>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="flex items-baseline gap-2">
                <span className="text-3xl font-semibold">{greenDays}</span>
                <span className="text-muted-foreground text-[12.5px]">green days this month</span>
              </p>
              <div className="mt-3 grid grid-cols-7 gap-1.5">
                {["M", "T", "W", "T", "F", "S", "S"].map((d, i) => (
                  <div key={`h${i}`} className="text-muted-foreground text-center font-mono text-[10px]">
                    {d}
                  </div>
                ))}
                {week.map((day, i) => (
                  <div
                    key={i}
                    title={day ? `${day.date}: ${day.status.toLowerCase().replace("_", " ")}` : ""}
                    className={`grid aspect-square place-items-center rounded-md ${day ? WEEK_STATUS_STYLE[day.status] : "bg-secondary/25"}`}
                  >
                    {day?.status === "MET" && (
                      <span className="size-1.5 rounded-full" style={{ backgroundColor: GOOD }} />
                    )}
                    {day?.status === "MISSED" && (
                      <span className="size-1.5 rounded-full" style={{ backgroundColor: DANGER }} />
                    )}
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="ledger-label flex items-baseline justify-between font-normal">
                Budgets
                <button
                  className="text-primary cursor-pointer border-none bg-transparent p-0 font-sans text-[11.5px] normal-case tracking-normal"
                  onClick={() => onNavigate?.("quests")}
                >
                  + Add
                </button>
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3.5">
              {goalSummaries.length === 0 ? (
                <p className="text-muted-foreground py-3 text-center text-sm">
                  No monthly goals yet — set one on the Quests page.
                </p>
              ) : (
                goalSummaries.slice(0, 4).map((goal) => {
                  const pct = goal.target > 0 ? Math.round((goal.actual / goal.target) * 100) : 0
                  const hot = pct > 85
                  const aheadOfPace = isCurrentMonth && pct > monthElapsedPct
                  return (
                    <div key={goal.goalId}>
                      <div className="flex justify-between text-[12.5px]">
                        <span>{goal.name}</span>
                        <span
                          className="font-mono text-[11.5px]"
                          style={{ color: hot ? DANGER : MUTED }}
                        >
                          {Math.round(goal.actual / 100)} / {Math.round(goal.target / 100)}
                        </span>
                      </div>
                      <div className="bg-secondary/60 mt-1.5 h-[5px] rounded-full">
                        <div
                          className="h-[5px] rounded-full"
                          style={{
                            width: `${Math.min(100, pct)}%`,
                            backgroundColor: hot ? DANGER : PRIMARY,
                          }}
                        />
                      </div>
                      <p className="mt-1 text-[10.5px]" style={{ color: hot ? DANGER : MUTED }}>
                        {hot
                          ? `Over pace — ${formatRon(Math.max(0, goal.target - goal.actual))} left`
                          : aheadOfPace
                            ? "Slightly ahead of pace"
                            : "On pace"}
                      </p>
                    </div>
                  )
                })
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
