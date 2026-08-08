import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react"
import {
  acceptQuest,
  cancelOath,
  createGoal,
  declineQuest,
  deleteGoal,
  formatRon,
  getCalendar,
  listGoals,
  listOaths,
  listQuests,
  type Category,
  type Goal,
  type GoalCalendar,
  type Oath,
  type OathStatus,
  type PeriodSummary,
  type Quest,
} from "@/lib/api"
import { useToast } from "@/components/Toast"
import { CategorySelect } from "@/components/CategorySelect"
import { PledgeSheet } from "@/components/PledgeSheet"
import { CloseIcon, HornGlyph } from "@/components/brand"
import { RabojStreak, Stamp } from "@/components/raboj"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

const OUT = "#c96a4e"
const GOOD = "#9cb37a"
const WARN = "#c98a3c"

const today = () => new Date().toISOString().slice(0, 10)
const thisMonth = () => new Date().toISOString().slice(0, 7)

function daysLeftLabel(periodEnd: string): string {
  const diff = Math.ceil((new Date(periodEnd + "T23:59:59").getTime() - Date.now()) / 86400000)
  return diff <= 0 ? "ends today" : diff === 1 ? "1 day left" : `${diff} days left`
}

// KEPT and FORGONE are deliberately different tones — they are different acts
// (a match within tolerance vs. a window closing with no match at all).
const OATH_STAMP_TONE: Record<OathStatus, "brass" | "good" | "over" | "muted"> = {
  OPEN: "brass",
  KEPT: "good",
  SLIPPED: "over",
  FORGONE: "muted",
}

function QuestRow({ quest }: { quest: Quest }) {
  const pct = quest.target > 0 ? Math.min(100, (quest.progress / quest.target) * 100) : 0
  const done = quest.status === "COMPLETED"
  const failed = quest.status === "FAILED"
  const hot = quest.kind === "CAP" && pct > 85 && !done
  const color = done ? GOOD : failed ? OUT : hot ? WARN : "#c79a5b"
  const fmt = (v: number) => (quest.kind === "DAYS" ? `${v} days` : formatRon(v).replace(/\s?RON$/, ""))
  const status = done ? "Done" : failed ? "Over" : quest.status === "ACTIVE" ? daysLeftLabel(quest.periodEnd) : `${fmt(quest.progress)} / ${fmt(quest.target)}`

  return (
    <div>
      <div className="flex items-baseline justify-between gap-2.5">
        <span className="text-[15px] font-medium" style={{ color: hot || failed ? color : undefined }}>
          {quest.title}
        </span>
        <span className="tnum font-mono text-[13px]" style={{ color: hot || failed || done ? color : "#C3B4A2" }}>
          {status}
        </span>
      </div>
      <div className="mt-2.5 h-2 overflow-hidden rounded-full bg-white/[0.09]">
        <div className="h-full rounded-full" style={{ width: `${pct}%`, background: color }} />
      </div>
      <div className="mt-2.5 text-[13px]" style={{ color: hot || failed ? color : "#C3B4A2" }}>
        {fmt(quest.progress)} of {fmt(quest.target)}
      </div>
    </div>
  )
}

export default function QuestsTab({ categories }: { categories: Category[] }) {
  const toast = useToast()
  const [quests, setQuests] = useState<Quest[]>([])
  const [goals, setGoals] = useState<Goal[]>([])
  const [oaths, setOaths] = useState<Oath[]>([])
  const [calendar, setCalendar] = useState<GoalCalendar | null>(null)
  const [month, setMonth] = useState(thisMonth())
  const [error, setError] = useState<string | null>(null)
  const [period, setPeriod] = useState<Goal["period"]>("MONTHLY")
  const [goalCategory, setGoalCategory] = useState<string>()
  const [pledgeOpen, setPledgeOpen] = useState(false)

  const reload = useCallback(() => {
    Promise.all([listQuests(), listGoals(), getCalendar(month), listOaths()])
      .then(([q, g, c, o]) => {
        setQuests(q)
        setGoals(g)
        setCalendar(c)
        setOaths(o)
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
      if (msg) toast(msg)
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
      toast("Goal added")
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed")
    }
  }

  const suggested = quests.filter((q) => q.status === "SUGGESTED")
  const active = quests.filter((q) => q.status === "ACTIVE")
  const finished = quests.filter((q) => q.status === "COMPLETED" || q.status === "FAILED").slice(0, 4)

  // Trailing streak of on-budget days up to today.
  const streak = useMemo(() => {
    if (!calendar) return 0
    let n = 0
    for (const d of [...calendar.days].reverse()) {
      if (d.status === "MET") n++
      else if (d.status === "MISSED") break
    }
    return n
  }, [calendar])
  const greenDays = calendar?.days.filter((d) => d.status === "MET").length ?? 0

  const firstDayOffset = calendar ? (new Date(`${calendar.month}-01T00:00:00`).getDay() + 6) % 7 : 0
  const periodSummaries: PeriodSummary[] = [
    ...(calendar?.monthlyGoals ?? []),
    ...(calendar?.yearlyGoals ?? []),
  ]

  const card = "ledger-card p-6"
  const field =
    "text-foreground w-full rounded-xl border border-white/10 bg-[#241C17] px-3.5 py-2.5 text-sm outline-none focus-visible:border-primary"

  return (
    <div className="flex flex-col gap-[22px]">
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="flex flex-wrap items-start gap-[22px]">
        {/* Left */}
        <div className="flex min-w-[min(100%,300px)] flex-1 basis-[30%] flex-col gap-[22px]">
          <section
            className="rounded-2xl border border-white/[0.09] p-6"
            style={{ background: "linear-gradient(160deg,#33261b 0%,#171009 100%)" }}
          >
            <div className="ledger-label">Days on budget · this month</div>
            <div className="mt-3 flex items-baseline gap-2.5">
              <div className="figure text-[54px] leading-none font-semibold">{greenDays}</div>
              <div className="text-muted-foreground text-[15px]">day{greenDays === 1 ? "" : "s"} notched</div>
            </div>
            <div className="mt-5">
              <RabojStreak count={greenDays} />
            </div>
            <p className="text-muted-foreground mt-4 text-[14px]">
              {streak > 0
                ? `${streak}-day run going — keep cutting notches`
                : "No run yet — a notch is cut each on-budget day"}
            </p>
          </section>

          <section className={card}>
            <h2 className="mb-3.5 text-[17px] font-semibold">Suggested for you</h2>
            <div className="flex flex-col gap-2.5">
              {suggested.length === 0 ? (
                <p className="text-muted-foreground text-[13px]">
                  No more suggestions — new quests arrive as your spending history grows.
                </p>
              ) : (
                suggested.map((q) => (
                  <div
                    key={q.id}
                    className="flex items-center gap-3.5 rounded-2xl border border-dashed border-white/15 bg-[#241C17] px-4 py-3.5"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[13.5px] font-medium">{q.title}</p>
                      <p className="text-muted-foreground font-mono text-[12.5px]">
                        {q.periodStart} → {q.periodEnd}
                      </p>
                    </div>
                    <button
                      onClick={() => act(acceptQuest, q.id, "Quest accepted — good luck!")}
                      className="bg-primary text-primary-foreground cursor-pointer rounded-lg border-none px-3 py-1.5 text-[13px] font-semibold hover:bg-[#D8B27A]"
                    >
                      Accept
                    </button>
                    <button
                      onClick={() => act(declineQuest, q.id)}
                      className="text-muted-foreground hover:text-foreground cursor-pointer text-[13px]"
                    >
                      Skip
                    </button>
                  </div>
                ))
              )}
            </div>
          </section>
        </div>

        {/* Right */}
        <div className="flex min-w-[min(100%,440px)] flex-1 basis-[62%] flex-col gap-[22px]">
          {/* Calendar */}
          <section className={card}>
            <div className="mb-5 flex flex-wrap items-start justify-between gap-3.5">
              <div>
                <h2 className="text-[18px] font-semibold">Goal calendar</h2>
                <p className="text-muted-foreground mt-1.5 text-[13.5px]">Days you stayed on budget</p>
              </div>
              <Input
                type="month"
                value={month}
                onChange={(e) => e.target.value && setMonth(e.target.value)}
                aria-label="Calendar month"
                className="w-40 font-mono"
              />
            </div>
            {calendar && (
              <>
                <div className="grid grid-cols-7 gap-2">
                  {["M", "T", "W", "T", "F", "S", "S"].map((d, i) => (
                    <div key={i} className="text-muted-foreground pb-1 text-center font-mono text-[12.5px]">{d}</div>
                  ))}
                  {Array.from({ length: firstDayOffset }).map((_, i) => (
                    <div key={`pad-${i}`} className="aspect-square" />
                  ))}
                  {calendar.days.map((day) => {
                    const met = day.status === "MET"
                    const over = day.status === "MISSED"
                    const inProg = day.status === "IN_PROGRESS"
                    const style = met
                      ? { background: "rgba(156,179,122,0.2)", border: "1px solid rgba(156,179,122,0.42)", color: "#D8E2C0" }
                      : over
                        ? { background: "rgba(201,106,78,0.2)", border: "1px solid rgba(201,106,78,0.42)", color: "#E8C0B0" }
                        : { background: "rgba(255,255,255,0.035)", border: "1px solid rgba(255,255,255,0.08)", color: "#B8A997" }
                    return (
                      <div
                        key={day.date}
                        title={
                          day.goals.length
                            ? day.goals.map((g) => `${g.name}: ${formatRon(g.actual)} / ${formatRon(g.target)}`).join("\n")
                            : "No daily goals"
                        }
                        className="relative grid aspect-square place-items-center rounded-[11px] text-[14.5px] font-semibold"
                        style={{ ...style, outline: inProg ? "2px solid #C79A5B" : undefined, outlineOffset: 1 }}
                      >
                        {/* Shape marks so status is never colour-only: a notch cut for on-budget, a cross for over. */}
                        {met && (
                          <span className="absolute right-1.5 top-1.5 h-2.5 w-[2px] rounded-full" style={{ background: GOOD }} />
                        )}
                        {over && (
                          <span className="absolute right-1 top-1" style={{ color: OUT }}>
                            <CloseIcon size={9} />
                          </span>
                        )}
                        {Number(day.date.slice(8, 10))}
                      </div>
                    )
                  })}
                </div>
                <div className="text-muted-foreground mt-5 flex flex-wrap justify-center gap-5 text-[13px]">
                  <span className="flex items-center gap-2">
                    <span className="size-4 rounded-[5px]" style={{ background: "rgba(156,179,122,0.22)", border: "1px solid rgba(156,179,122,0.45)" }} />
                    On budget
                  </span>
                  <span className="flex items-center gap-2">
                    <span className="size-4 rounded-[5px]" style={{ background: "rgba(201,106,78,0.22)", border: "1px solid rgba(201,106,78,0.45)" }} />
                    Over
                  </span>
                  <span className="flex items-center gap-2">
                    <span className="size-4 rounded-[5px]" style={{ background: "rgba(255,255,255,0.04)", border: "1px solid rgba(255,255,255,0.09)" }} />
                    Upcoming
                  </span>
                </div>
                {periodSummaries.length > 0 && (
                  <div className="mt-5 space-y-2 border-t border-white/[0.07] pt-4">
                    {periodSummaries.map((s) => (
                      <div key={s.goalId} className="flex items-center justify-between text-sm">
                        <span className="flex items-center gap-2.5">
                          {s.name}
                          <Stamp tone={s.inProgress ? "muted" : s.met ? "good" : "over"}>
                            {s.inProgress ? "open" : s.met ? "met" : "over"}
                          </Stamp>
                        </span>
                        <span className="tnum font-mono text-xs" style={{ color: s.actual > s.target ? OUT : "#C3B4A2" }}>
                          {formatRon(s.actual)} / {formatRon(s.target)}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </>
            )}
          </section>

          {/* Active quests */}
          <section className={card}>
            <div className="mb-5.5 flex items-center gap-2.5">
              <HornGlyph className="text-primary size-5.5" />
              <h2 className="text-[18px] font-semibold">Active quests</h2>
            </div>
            {active.length === 0 && finished.length === 0 ? (
              <p className="text-muted-foreground text-sm">Nothing active — accept a suggestion on the left.</p>
            ) : (
              <div className="grid gap-6 [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
                {active.map((q) => <QuestRow key={q.id} quest={q} />)}
                {finished.map((q) => <QuestRow key={q.id} quest={q} />)}
              </div>
            )}
          </section>

          {/* Oaths */}
          <section className={card}>
            <div className="mb-5 flex flex-wrap items-start justify-between gap-3.5">
              <div>
                <h2 className="text-[18px] font-semibold">Oaths</h2>
                <p className="text-muted-foreground mt-1.5 text-[13.5px]">
                  Pledges sworn before you spend, not records after
                </p>
              </div>
              <button
                onClick={() => setPledgeOpen(true)}
                className="bg-primary text-primary-foreground cursor-pointer rounded-xl border-none px-4 py-2.5 text-[13.5px] font-semibold hover:bg-[#D8B27A]"
              >
                Take an oath
              </button>
            </div>
            {oaths.length === 0 ? (
              <p className="text-muted-foreground text-sm">
                No oaths sworn yet — pledge a category limit for the week or month.
              </p>
            ) : (
              <ul className="space-y-2">
                {oaths.map((oath) => (
                  <li
                    key={oath.id}
                    className="flex items-center justify-between gap-2.5 rounded-xl border border-white/[0.07] bg-[#241C17] px-4 py-3"
                  >
                    <span className="flex min-w-0 items-center gap-2.5">
                      <span className="truncate text-[13.5px] font-medium">{oath.categoryName}</span>
                      <Stamp tone={OATH_STAMP_TONE[oath.status]}>{oath.status.toLowerCase()}</Stamp>
                      {oath.status === "OPEN" && (
                        <span className="text-muted-foreground font-mono text-[11.5px]">
                          until {oath.expiresAt.slice(0, 10)}
                        </span>
                      )}
                    </span>
                    <span className="flex flex-none items-center gap-2">
                      <span className="tnum text-muted-foreground font-mono text-xs">
                        {formatRon(oath.pledgedAmount)}
                      </span>
                      {oath.status === "OPEN" && (
                        <button
                          onClick={() => act(cancelOath, oath.id, "Oath cancelled")}
                          aria-label="Cancel oath"
                          className="text-muted-foreground hover:text-foreground grid size-9 flex-none cursor-pointer place-items-center rounded-lg hover:bg-white/[0.06]"
                        >
                          <CloseIcon size={15} />
                        </button>
                      )}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* Your goals */}
          <section className={card}>
            <h2 className="text-[18px] font-semibold">Your goals</h2>
            <p className="text-muted-foreground mt-1 mb-4 text-[13.5px]">
              Spending limits per day, month, or year — overall or per category.
            </p>
            <form className="space-y-2.5" onSubmit={submitGoal}>
              <div className="grid gap-2.5 sm:grid-cols-2">
                <input name="name" required maxLength={100} placeholder="Groceries under 800 RON" aria-label="Goal name" className={field} />
                <input name="target" type="number" step="0.01" min="0.01" required placeholder="Target (RON)" aria-label="Target amount" className={`${field} font-mono`} />
              </div>
              <div className="grid gap-2.5 sm:grid-cols-3">
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
                <CategorySelect categories={categories} value={goalCategory} onChange={setGoalCategory} allowAll />
                <input name="startDate" type="date" required defaultValue={today()} aria-label="Start date" className={`${field} font-mono`} />
              </div>
              <button
                type="submit"
                className="bg-primary text-primary-foreground cursor-pointer rounded-xl border-none px-4 py-2.5 text-[13.5px] font-semibold hover:bg-[#D8B27A]"
              >
                Add goal
              </button>
            </form>
            {goals.length > 0 && (
              <ul className="mt-4 space-y-2 border-t border-white/[0.07] pt-4">
                {goals.map((goal) => (
                  <li key={goal.id} className="flex items-center justify-between gap-2 text-sm">
                    <span className="flex items-center gap-2">
                      {goal.name}
                      <span className="text-muted-foreground rounded-md bg-white/[0.06] px-2 py-0.5 font-mono text-[11.5px]">
                        {goal.period.toLowerCase()}
                      </span>
                    </span>
                    <span className="flex items-center gap-2">
                      <span className="tnum font-mono text-xs" style={{ color: goal.currentActual > goal.targetAmount ? OUT : "#C3B4A2" }}>
                        {formatRon(goal.currentActual)} / {formatRon(goal.targetAmount)}
                      </span>
                      <button
                        onClick={() => deleteGoal(goal.id).then(reload)}
                        aria-label="Delete goal"
                        className="text-muted-foreground hover:text-foreground grid size-9 flex-none cursor-pointer place-items-center rounded-lg hover:bg-white/[0.06]"
                      >
                        <CloseIcon size={15} />
                      </button>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>

      <PledgeSheet
        open={pledgeOpen}
        onClose={() => setPledgeOpen(false)}
        categories={categories}
        onSaved={reload}
      />
    </div>
  )
}
