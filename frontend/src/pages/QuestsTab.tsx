import { useCallback, useEffect, useState, type FormEvent } from "react"
import {
  acceptQuest,
  createGoal,
  declineQuest,
  deleteGoal,
  formatRon,
  getCalendar,
  listGoals,
  listQuests,
  type Category,
  type Goal,
  type GoalCalendar,
  type PeriodSummary,
  type Quest,
} from "@/lib/api"
import { useToast } from "@/components/Toast"
import { CategorySelect } from "@/components/CategorySelect"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

const PRIMARY = "#f97c0e"
const GOOD = "#0ca30c"
const DANGER = "#ff4500"

const QUEST_ICON: Record<string, string> = {
  CATEGORY_CAP: "🍽",
  WEEKLY_CAP: "💳",
  NO_SPEND_DAYS: "🚫",
  BEAT_LAST_MONTH: "📉",
}

const DAY_STYLE: Record<string, string> = {
  MET: "bg-white/[.045]",
  MISSED: "bg-white/[.045]",
  IN_PROGRESS: "bg-white/[.045] border border-[#f97c0e]",
  FUTURE: "bg-white/[.02]",
  NO_GOAL: "bg-white/[.02]",
}

const today = () => new Date().toISOString().slice(0, 10)
const thisMonth = () => new Date().toISOString().slice(0, 7)

function daysLeftLabel(periodEnd: string): string {
  const diff = Math.ceil(
    (new Date(periodEnd + "T23:59:59").getTime() - Date.now()) / 86400000
  )
  return diff <= 0 ? "ends today" : diff === 1 ? "1 day left" : `${diff} days left`
}

function QuestCard({ quest }: { quest: Quest }) {
  const pct = quest.target > 0 ? Math.min(100, (quest.progress / quest.target) * 100) : 0
  const done = quest.status === "COMPLETED"
  const failed = quest.status === "FAILED"
  const hot = quest.kind === "CAP" && pct > 85 && !done
  const accent = done ? GOOD : failed || hot ? DANGER : PRIMARY

  const fmt = (v: number) => (quest.kind === "DAYS" ? `${v} days` : formatRon(v))

  return (
    <Card style={{ borderLeft: `3px solid ${accent}` }}>
      <CardContent className="pt-4">
        <div className="flex items-center gap-2">
          <span
            className="rounded-md px-2 py-0.5 font-mono text-[10.5px] tracking-wider"
            style={{ color: accent, backgroundColor: "rgba(255,255,255,.07)" }}
          >
            {quest.periodEnd.slice(0, 7) === thisMonth() &&
            quest.periodStart.slice(0, 7) === thisMonth() &&
            new Date(quest.periodEnd).getTime() - new Date(quest.periodStart).getTime() > 8 * 86400000
              ? "MONTHLY"
              : "WEEKLY"}
          </span>
          {quest.status === "ACTIVE" && (
            <span className="text-muted-foreground text-[11.5px]">⏱ {daysLeftLabel(quest.periodEnd)}</span>
          )}
          {done && <Badge className="bg-[#0ca30c]">✓ completed</Badge>}
          {failed && <Badge variant="destructive">✗ failed</Badge>}
          <span className="ml-auto text-[15px]">{QUEST_ICON[quest.templateCode] ?? "🎯"}</span>
        </div>
        <p className="mt-2.5 text-[14.5px] font-semibold">{quest.title}</p>
        <div className="text-muted-foreground mt-3 flex justify-between font-mono text-xs">
          <span>{fmt(quest.progress)}</span>
          <span>{fmt(quest.target)}</span>
        </div>
        <div className="bg-secondary/60 mt-1.5 h-1.5 rounded-full">
          <div
            className="h-1.5 rounded-full transition-all"
            style={{ width: `${pct}%`, backgroundColor: accent }}
          />
        </div>
      </CardContent>
    </Card>
  )
}

export default function QuestsTab({ categories }: { categories: Category[] }) {
  const toast = useToast()
  const [quests, setQuests] = useState<Quest[]>([])
  const [goals, setGoals] = useState<Goal[]>([])
  const [calendar, setCalendar] = useState<GoalCalendar | null>(null)
  const [month, setMonth] = useState(thisMonth())
  const [error, setError] = useState<string | null>(null)
  const [period, setPeriod] = useState<Goal["period"]>("MONTHLY")
  const [goalCategory, setGoalCategory] = useState<string>()

  const reload = useCallback(() => {
    Promise.all([listQuests(), listGoals(), getCalendar(month)])
      .then(([q, g, c]) => {
        setQuests(q)
        setGoals(g)
        setCalendar(c)
        setError(null)
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load"))
  }, [month])

  useEffect(() => {
    reload()
  }, [reload])

  async function act(action: (id: string) => Promise<unknown>, id: string, msg?: string) {
    try {
      await action(id)
      if (msg) toast("🎯", msg)
      reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : "Action failed")
    }
  }

  async function submitGoal(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
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
      toast("✓", "Goal added")
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed")
    }
  }

  const suggested = quests.filter((q) => q.status === "SUGGESTED")
  const active = quests.filter((q) => q.status === "ACTIVE")
  const finished = quests.filter((q) => q.status === "COMPLETED" || q.status === "FAILED").slice(0, 4)

  const firstDayOffset = calendar
    ? (new Date(`${calendar.month}-01T00:00:00`).getDay() + 6) % 7
    : 0

  const periodSummaries: PeriodSummary[] = [
    ...(calendar?.monthlyGoals ?? []),
    ...(calendar?.yearlyGoals ?? []),
  ]

  return (
    <div className="space-y-5">
      <h1 className="text-[22px] font-semibold tracking-tight">Goals &amp; Quests</h1>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="grid items-start gap-6 lg:grid-cols-[400px_1fr]">
        {/* left column */}
        <div className="space-y-5">
          <div>
            <p className="mb-3 text-[15px] font-semibold">Active quests</p>
            <div className="space-y-3">
              {active.length === 0 ? (
                <p className="text-muted-foreground text-sm">
                  Nothing active — accept a suggestion below.
                </p>
              ) : (
                active.map((quest) => <QuestCard key={quest.id} quest={quest} />)
              )}
            </div>
          </div>

          <div>
            <p className="mb-3 text-[15px] font-semibold">Suggested for you</p>
            <div className="space-y-2.5">
              {suggested.length === 0 ? (
                <p className="text-muted-foreground text-[12.5px]">
                  No more suggestions — new quests arrive as your spending history grows.
                </p>
              ) : (
                suggested.map((quest) => (
                  <div
                    key={quest.id}
                    className="flex items-center gap-3.5 rounded-xl border border-dashed border-white/15 bg-card px-4 py-3.5"
                  >
                    <span className="text-base">{QUEST_ICON[quest.templateCode] ?? "🎯"}</span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[13.5px] font-medium">{quest.title}</p>
                      <p className="text-muted-foreground text-[11.5px]">
                        {quest.periodStart} → {quest.periodEnd}
                      </p>
                    </div>
                    <Button
                      size="sm"
                      onClick={() => act(acceptQuest, quest.id, "Quest accepted — good luck!")}
                    >
                      Accept
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => act(declineQuest, quest.id)}
                    >
                      Skip
                    </Button>
                  </div>
                ))
              )}
            </div>
          </div>

          {finished.length > 0 && (
            <div>
              <p className="mb-3 text-[15px] font-semibold">Recent history</p>
              <div className="space-y-3">
                {finished.map((quest) => (
                  <QuestCard key={quest.id} quest={quest} />
                ))}
              </div>
            </div>
          )}
        </div>

        {/* right column */}
        <div className="space-y-5">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center justify-between text-base">
                <span>
                  Goal calendar
                  <span className="text-muted-foreground ml-2 text-xs font-normal">
                    daily spending goals
                  </span>
                </span>
                <Input
                  type="month"
                  value={month}
                  onChange={(e) => e.target.value && setMonth(e.target.value)}
                  className="w-42"
                  aria-label="Calendar month"
                />
              </CardTitle>
            </CardHeader>
            <CardContent>
              {calendar && (
                <>
                  <div className="grid grid-cols-7 gap-2">
                    {["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"].map((d) => (
                      <div key={d} className="text-muted-foreground pb-1 text-center font-mono text-[10px]">
                        {d}
                      </div>
                    ))}
                    {Array.from({ length: firstDayOffset }).map((_, i) => (
                      <div key={`pad-${i}`} />
                    ))}
                    {calendar.days.map((day) => {
                      const past = day.status === "MET" || day.status === "MISSED"
                      return (
                        <div
                          key={day.date}
                          title={
                            day.goals.length
                              ? day.goals
                                  .map((g) => `${g.name}: ${formatRon(g.actual)} / ${formatRon(g.target)}`)
                                  .join("\n")
                              : "No daily goals"
                          }
                          className={`flex aspect-[1.15] flex-col items-center justify-center gap-1 rounded-lg ${DAY_STYLE[day.status]}`}
                        >
                          <span
                            className={`text-[12.5px] font-medium ${
                              day.status === "IN_PROGRESS"
                                ? "text-primary"
                                : past
                                  ? "text-foreground"
                                  : "text-muted-foreground/60"
                            }`}
                          >
                            {Number(day.date.slice(8, 10))}
                          </span>
                          {past && (
                            <span
                              className="size-[7px] rounded-full"
                              style={{ backgroundColor: day.status === "MET" ? GOOD : DANGER }}
                            />
                          )}
                          {day.status === "IN_PROGRESS" && (
                            <span className="size-[7px] rounded-full bg-white/30" />
                          )}
                        </div>
                      )
                    })}
                  </div>
                  <div className="mt-4 flex justify-center gap-5 text-xs">
                    <span className="text-muted-foreground flex items-center gap-1.5">
                      <span className="size-2 rounded-full" style={{ backgroundColor: GOOD }} />
                      Goal met
                    </span>
                    <span className="text-muted-foreground flex items-center gap-1.5">
                      <span className="size-2 rounded-full" style={{ backgroundColor: DANGER }} />
                      Missed
                    </span>
                    <span className="text-muted-foreground flex items-center gap-1.5">
                      <span className="size-2 rounded-full bg-white/30" />
                      Today / pending
                    </span>
                  </div>
                  {periodSummaries.length > 0 && (
                    <div className="mt-4 space-y-1.5 border-t pt-3">
                      {periodSummaries.map((s) => (
                        <div key={s.goalId} className="flex items-center justify-between text-sm">
                          <span className="flex items-center gap-2">
                            {s.name}
                            {s.inProgress ? (
                              <Badge variant="outline">in progress</Badge>
                            ) : s.met ? (
                              <Badge className="bg-[#0ca30c]">✓ met</Badge>
                            ) : (
                              <Badge variant="destructive">✗ missed</Badge>
                            )}
                          </span>
                          <span
                            className="font-mono text-xs"
                            style={{ color: s.actual > s.target ? DANGER : "#a89e90" }}
                          >
                            {formatRon(s.actual)} / {formatRon(s.target)}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-base">Your goals</CardTitle>
              <CardDescription>
                Spending limits per day, month, or year — overall or per category.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <form className="space-y-2.5" onSubmit={submitGoal}>
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
                    className="font-mono"
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
                  <Input name="startDate" type="date" required defaultValue={today()} aria-label="Start date" className="font-mono" />
                </div>
                <Button type="submit" size="sm">
                  Add goal
                </Button>
              </form>
              {goals.length > 0 && (
                <ul className="space-y-2 border-t pt-3">
                  {goals.map((goal) => (
                    <li key={goal.id} className="flex items-center justify-between gap-2 text-sm">
                      <span className="flex items-center gap-2">
                        {goal.name}
                        <Badge variant="secondary">{goal.period.toLowerCase()}</Badge>
                      </span>
                      <span className="flex items-center gap-1.5">
                        <span
                          className="font-mono text-xs"
                          style={{ color: goal.currentActual > goal.targetAmount ? DANGER : "#a89e90" }}
                        >
                          {formatRon(goal.currentActual)} / {formatRon(goal.targetAmount)}
                        </span>
                        <Button variant="ghost" size="sm" onClick={() => deleteGoal(goal.id).then(reload)}>
                          ✕
                        </Button>
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
