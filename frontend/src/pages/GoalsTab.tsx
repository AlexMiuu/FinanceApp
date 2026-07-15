import { useCallback, useEffect, useState, type FormEvent } from "react"
import {
  createGoal,
  deleteGoal,
  formatRon,
  getCalendar,
  listGoals,
  type Category,
  type CalendarDay,
  type Goal,
  type GoalCalendar,
  type PeriodSummary,
} from "@/lib/api"
import { CategorySelect } from "@/components/CategorySelect"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

// Status colors from the dataviz status palette — never reused as series colors.
const STATUS_STYLE: Record<CalendarDay["status"], string> = {
  MET: "bg-[#0ca30c] text-white",
  MISSED: "bg-[#d03b3b] text-white",
  IN_PROGRESS: "border-2 border-[#2a78d6] text-foreground",
  FUTURE: "bg-muted text-muted-foreground",
  NO_GOAL: "text-muted-foreground",
}

const today = () => new Date().toISOString().slice(0, 10)
const thisMonth = () => new Date().toISOString().slice(0, 7)

function PeriodRow({ summary }: { summary: PeriodSummary }) {
  const over = summary.actual > summary.target
  return (
    <li className="flex items-center justify-between gap-2 text-sm">
      <span className="flex items-center gap-2">
        {summary.name}
        {summary.inProgress ? (
          <Badge variant="outline">in progress</Badge>
        ) : summary.met ? (
          <Badge className="bg-[#0ca30c]">✓ met</Badge>
        ) : (
          <Badge variant="destructive">✗ missed</Badge>
        )}
      </span>
      <span className={over ? "text-destructive font-medium" : "text-muted-foreground"}>
        {formatRon(summary.actual)} / {formatRon(summary.target)}
      </span>
    </li>
  )
}

export default function GoalsTab({ categories }: { categories: Category[] }) {
  const [goals, setGoals] = useState<Goal[]>([])
  const [calendar, setCalendar] = useState<GoalCalendar | null>(null)
  const [month, setMonth] = useState(thisMonth())
  const [error, setError] = useState<string | null>(null)
  const [period, setPeriod] = useState<Goal["period"]>("MONTHLY")
  const [goalCategory, setGoalCategory] = useState<string>()

  const reload = useCallback(() => {
    Promise.all([listGoals(), getCalendar(month)])
      .then(([g, c]) => {
        setGoals(g)
        setCalendar(c)
        setError(null)
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load"))
  }, [month])

  useEffect(() => {
    reload()
  }, [reload])

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    const form = e.currentTarget
    const data = new FormData(form)
    try {
      await createGoal({
        name: String(data.get("name")),
        categoryId: goalCategory ?? null,
        targetAmount: Math.round(parseFloat(String(data.get("target"))) * 100),
        period,
        startDate: String(data.get("startDate")),
        endDate: null,
      })
      form.reset()
      setGoalCategory(undefined)
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed")
    }
  }

  // Monday-first offset for the calendar grid.
  const firstDayOffset = calendar
    ? (new Date(`${calendar.month}-01T00:00:00`).getDay() + 6) % 7
    : 0

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">New spending goal</CardTitle>
          <CardDescription>
            Stay under a target per day, month, or year — overall or for one category.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-3" onSubmit={submit}>
            <div className="grid gap-2 sm:grid-cols-2">
              <Input name="name" required maxLength={100} placeholder="Groceries under 800 RON" aria-label="Goal name" />
              <Input
                name="target"
                type="number"
                step="0.01"
                min="0.01"
                required
                placeholder="Target (RON)"
                aria-label="Target amount"
              />
            </div>
            <div className="grid gap-2 sm:grid-cols-3">
              <Select value={period} onValueChange={(v) => setPeriod(v as Goal["period"])}>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="DAILY">Daily</SelectItem>
                  <SelectItem value="MONTHLY">Monthly</SelectItem>
                  <SelectItem value="YEARLY">Yearly</SelectItem>
                </SelectContent>
              </Select>
              <CategorySelect
                categories={categories}
                value={goalCategory}
                onChange={setGoalCategory}
                allowAll
              />
              <Input name="startDate" type="date" required defaultValue={today()} aria-label="Start date" />
            </div>
            <Button type="submit">Add goal</Button>
          </form>
        </CardContent>
      </Card>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Your goals</CardTitle>
        </CardHeader>
        <CardContent>
          {goals.length === 0 ? (
            <p className="text-muted-foreground py-2 text-center text-sm">No goals yet.</p>
          ) : (
            <ul className="space-y-2">
              {goals.map((goal) => (
                <li key={goal.id} className="flex items-center justify-between gap-2 text-sm">
                  <span className="flex items-center gap-2">
                    {goal.name}
                    <Badge variant="secondary">{goal.period.toLowerCase()}</Badge>
                    {!goal.active && <Badge variant="outline">inactive</Badge>}
                  </span>
                  <span className="flex items-center gap-2">
                    <span
                      className={
                        goal.currentActual > goal.targetAmount
                          ? "text-destructive font-medium"
                          : "text-muted-foreground"
                      }
                    >
                      {formatRon(goal.currentActual)} / {formatRon(goal.targetAmount)} this period
                    </span>
                    <Button variant="ghost" size="sm" onClick={() => deleteGoal(goal.id).then(reload)}>
                      Delete
                    </Button>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between text-base">
            Goal calendar
            <Input
              type="month"
              value={month}
              onChange={(e) => e.target.value && setMonth(e.target.value)}
              className="w-44"
              aria-label="Calendar month"
            />
          </CardTitle>
          <CardDescription>
            Daily goals per day — <span className="font-medium text-[#0ca30c]">met</span> ·{" "}
            <span className="text-destructive font-medium">missed</span> · outlined = today ·
            gray = future / no goal
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {calendar && (
            <>
              <div className="grid grid-cols-7 gap-1 text-center">
                {["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"].map((d) => (
                  <div key={d} className="text-muted-foreground pb-1 text-xs">
                    {d}
                  </div>
                ))}
                {Array.from({ length: firstDayOffset }).map((_, i) => (
                  <div key={`pad-${i}`} />
                ))}
                {calendar.days.map((day) => (
                  <div
                    key={day.date}
                    title={
                      day.goals.length
                        ? day.goals
                            .map((g) => `${g.name}: ${formatRon(g.actual)} / ${formatRon(g.target)}`)
                            .join("\n")
                        : "No daily goals"
                    }
                    aria-label={`${day.date}: ${day.status.toLowerCase().replace("_", " ")}`}
                    className={`flex h-9 items-center justify-center rounded-md text-xs font-medium ${STATUS_STYLE[day.status]}`}
                  >
                    {Number(day.date.slice(8, 10))}
                  </div>
                ))}
              </div>

              {(calendar.monthlyGoals.length > 0 || calendar.yearlyGoals.length > 0) && (
                <div className="space-y-2">
                  {calendar.monthlyGoals.length > 0 && (
                    <>
                      <p className="text-muted-foreground text-xs font-medium uppercase tracking-wide">
                        This month
                      </p>
                      <ul className="space-y-1">
                        {calendar.monthlyGoals.map((s) => (
                          <PeriodRow key={s.goalId} summary={s} />
                        ))}
                      </ul>
                    </>
                  )}
                  {calendar.yearlyGoals.length > 0 && (
                    <>
                      <p className="text-muted-foreground text-xs font-medium uppercase tracking-wide">
                        This year
                      </p>
                      <ul className="space-y-1">
                        {calendar.yearlyGoals.map((s) => (
                          <PeriodRow key={s.goalId} summary={s} />
                        ))}
                      </ul>
                    </>
                  )}
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
