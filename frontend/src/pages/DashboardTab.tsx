import { useEffect, useMemo, useState } from "react"
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts"
import { formatRon, getDashboard, getNetWorth, type Dashboard, type NetWorth } from "@/lib/api"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"

// Validated categorical palette (dataviz reference set, light mode) — slots
// assigned to categories in stable alphabetical order, never by rank.
const SERIES = [
  "#2a78d6", // blue
  "#1baf7a", // aqua
  "#eda100", // yellow
  "#008300", // green
  "#4a3aa7", // violet
  "#e34948", // red
  "#e87ba4", // magenta
  "#eb6834", // orange
]
const OTHER_COLOR = "#898781" // muted ink for the folded "Other" slice
const SINGLE_SERIES = "#2a78d6" // sequential default hue for the daily bars
const GRID = "#e1e0d9"
const MUTED = "#898781"

const thisMonth = () => new Date().toISOString().slice(0, 7)

function StatTile({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-muted-foreground text-xs font-medium uppercase tracking-wide">
          {label}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-2xl font-semibold">{value}</p>
        {hint && <p className="text-muted-foreground pt-1 text-xs">{hint}</p>}
      </CardContent>
    </Card>
  )
}

export default function DashboardTab() {
  const [month, setMonth] = useState(thisMonth())
  const [data, setData] = useState<Dashboard | null>(null)
  const [netWorth, setNetWorth] = useState<NetWorth | null>(null)
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
  }, [])

  // ≤ 8 categorical slots: smallest categories fold into "Other".
  const slices = useMemo(() => {
    if (!data) return []
    const sorted = [...data.byCategory] // already alphabetical = stable slot order
    if (sorted.length <= SERIES.length) {
      return sorted.map((s, i) => ({ ...s, color: SERIES[i] }))
    }
    const keep = sorted
      .map((s, i) => ({ ...s, color: SERIES[i % SERIES.length], i }))
      .sort((a, b) => b.amount - a.amount)
      .slice(0, SERIES.length - 1)
      .sort((a, b) => a.i - b.i)
      .map((s, i) => ({ category: s.category, amount: s.amount, color: SERIES[i] }))
    const other = sorted.reduce((sum, s) => sum + s.amount, 0) - keep.reduce((sum, s) => sum + s.amount, 0)
    return [...keep, { category: "Other", amount: other, color: OTHER_COLOR }]
  }, [data])

  const dailyData = useMemo(
    () =>
      data?.byDay.map((d) => ({
        day: Number(d.date.slice(8, 10)),
        amount: d.amount / 100,
      })) ?? [],
    [data]
  )

  if (error) return <p className="text-destructive py-8 text-center text-sm">{error}</p>
  if (!data) return <p className="text-muted-foreground py-8 text-center text-sm">Loading…</p>

  const delta = data.previousMonthTotal > 0 ? data.totalSpent - data.previousMonthTotal : null

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-lg font-semibold">Overview</h2>
        <Input
          type="month"
          value={month}
          onChange={(e) => e.target.value && setMonth(e.target.value)}
          className="w-44"
          aria-label="Month"
        />
      </div>

      {netWorth && (
        <StatTile
          label="Net worth"
          value={formatRon(netWorth.total)}
          hint={`${netWorth.accounts} savings account${netWorth.accounts === 1 ? "" : "s"} · income ${formatRon(netWorth.monthlyIncome)}/month`}
        />
      )}

      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile
          label="Total spent"
          value={formatRon(data.totalSpent)}
          hint={
            delta !== null
              ? `${delta >= 0 ? "+" : ""}${formatRon(delta)} vs last month`
              : undefined
          }
        />
        <StatTile label="Expenses" value={String(data.expenseCount)} />
        <StatTile
          label="Mandatory share"
          value={
            data.totalSpent > 0
              ? `${Math.round((data.mandatorySpent / data.totalSpent) * 100)}%`
              : "—"
          }
          hint={formatRon(data.mandatorySpent)}
        />
        <StatTile
          label="Projected month-end"
          value={data.projectedMonthEnd !== null ? formatRon(data.projectedMonthEnd) : "—"}
          hint={data.projectedMonthEnd !== null ? "linear projection" : "past month"}
        />
      </section>

      <section className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Spending by category</CardTitle>
          </CardHeader>
          <CardContent>
            {slices.length === 0 ? (
              <p className="text-muted-foreground py-10 text-center text-sm">
                No expenses this month.
              </p>
            ) : (
              <div className="flex items-center gap-4">
                <ResponsiveContainer width="55%" height={220}>
                  <PieChart>
                    <Pie
                      data={slices}
                      dataKey="amount"
                      nameKey="category"
                      innerRadius={55}
                      outerRadius={90}
                      paddingAngle={2}
                      strokeWidth={0}
                    >
                      {slices.map((slice) => (
                        <Cell key={slice.category} fill={slice.color} />
                      ))}
                    </Pie>
                    <Tooltip formatter={(value) => formatRon(Number(value))} />
                  </PieChart>
                </ResponsiveContainer>
                <ul className="flex-1 space-y-1.5 text-sm">
                  {slices.map((slice) => (
                    <li key={slice.category} className="flex items-center justify-between gap-2">
                      <span className="flex items-center gap-2">
                        <span
                          className="inline-block size-2.5 rounded-full"
                          style={{ backgroundColor: slice.color }}
                        />
                        {slice.category}
                      </span>
                      <span className="text-muted-foreground">{formatRon(slice.amount)}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Daily spending</CardTitle>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={dailyData} margin={{ top: 4, right: 4, left: 4, bottom: 0 }}>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis
                  dataKey="day"
                  tickLine={false}
                  axisLine={false}
                  tick={{ fill: MUTED, fontSize: 11 }}
                  interval={4}
                />
                <YAxis
                  tickLine={false}
                  axisLine={false}
                  tick={{ fill: MUTED, fontSize: 11 }}
                  width={44}
                />
                <Tooltip
                  formatter={(value) => [formatRon(Math.round(Number(value) * 100)), "spent"]}
                  labelFormatter={(day) => `${data.month}-${String(day).padStart(2, "0")}`}
                />
                <Bar dataKey="amount" fill={SINGLE_SERIES} radius={[4, 4, 0, 0]} maxBarSize={18} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </section>
    </div>
  )
}
