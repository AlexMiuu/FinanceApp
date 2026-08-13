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
  type PeriodSummary,
  type Quest,
} from "@/lib/api"
import { useToast } from "@/components/Toast"
import { CategorySelect } from "@/components/CategorySelect"
import { PledgeSheet } from "@/components/PledgeSheet"
import { CloseIcon } from "@/components/brand"
import { Stamp } from "@/components/raboj"
import { Alert, AlertAction, AlertDescription } from "@/components/ui/alert"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

const OUT = "#E09880"
const GOOD = "#8FC7A6"
const ICE = "#9AD4E3"

const MONTH_LABELS = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]

const today = () => new Date().toISOString().slice(0, 10)
const thisMonth = () => new Date().toISOString().slice(0, 7)

function daysLeftLabel(periodEnd: string): string {
  const diff = Math.ceil((new Date(periodEnd + "T23:59:59").getTime() - Date.now()) / 86400000)
  return diff <= 0 ? "ends today" : diff === 1 ? "1 day left" : `${diff} days left`
}

// Outcomes are deliberately different tones — a quest actually pursued and lost
// (missed) reads differently from one that simply expired unclaimed (let go).
const CLOSED_OUTCOME: Record<string, { label: string; color: string }> = {
  COMPLETED: { label: "kept", color: GOOD },
  FAILED: { label: "missed", color: OUT },
  DECLINED: { label: "let go — no penalty", color: "#9AA3A8" },
}

/** An offered or in-progress quest: title, a hairline bar with a tick at the target, meta. */
function QuestCard({
  quest,
  actions,
}: {
  quest: Quest
  actions?: { onAccept: () => void; onDecline: () => void }
}) {
  const target = Math.max(quest.target, 0)
  const scale = Math.max(target, 1) * 1.25
  const barPct = Math.min(100, (quest.progress / scale) * 100)
  const tickPct = Math.min(100, (target / scale) * 100)
  const over = quest.kind === "CAP" && quest.progress > target
  const color = over ? OUT : ICE
  const fmt = (v: number) => (quest.kind === "DAYS" ? `${v} day${v === 1 ? "" : "s"}` : formatRon(v).replace(/\s?RON$/, ""))
  const macroSource = quest.templateCode === "SEASONAL_RESERVE" ? quest.params.macroSource : undefined
  const macroAsOfDate = quest.templateCode === "SEASONAL_RESERVE" ? quest.params.macroAsOfDate : undefined

  return (
    <div className="edge-mark-accent border-b border-[#2A3033] py-5 pl-3 last:border-b-0">
      <div className="flex items-baseline justify-between gap-4">
        <span className="text-[15.5px]">{quest.title}</span>
        <span className="figure flex-none text-[15px]" style={{ color }}>
          {fmt(quest.progress)} / {fmt(target)}
        </span>
      </div>
      <div className="bg-border relative mt-3.5 h-px">
        <div className="bg-primary absolute inset-y-0 left-0 h-px" style={{ width: `${barPct}%` }} />
        <div className="absolute -top-[5px] h-[11px] w-px" style={{ left: `${tickPct}%`, background: OUT }} />
      </div>
      <div className="mt-2.5 flex flex-wrap items-baseline justify-between gap-4">
        <span className="text-muted-foreground text-[13px]">
          {quest.periodStart} → {quest.periodEnd}
        </span>
        <span className="status-tag text-muted-foreground whitespace-nowrap">{daysLeftLabel(quest.periodEnd)}</span>
      </div>
      {typeof macroSource === "string" && typeof macroAsOfDate === "string" && (
        <div className="text-muted-foreground mt-1.5 text-[11px]">
          via {macroSource}, as of {macroAsOfDate}
        </div>
      )}
      {actions && (
        <div className="mt-3.5 flex gap-2.5">
          <button
            onClick={actions.onAccept}
            className="cursor-pointer border border-[#4C93A6] bg-[#123945] px-4 py-2 text-[13px] font-semibold text-[#C4E7F0] hover:bg-[#174756]"
          >
            Accept
          </button>
          <button
            onClick={actions.onDecline}
            className="cursor-pointer border border-border bg-transparent px-4 py-2 text-[13px] text-foreground/85 hover:border-[#4C93A6] hover:text-foreground"
          >
            Skip
          </button>
        </div>
      )}
    </div>
  )
}

/** A self-set pledge: same-day oaths get an urgent minute countdown, multi-day ones a day count. */
function OathRow({
  oath,
  confirming,
  onRequestCancel,
  onDismissCancel,
  onConfirmCancel,
}: {
  oath: Oath
  confirming: boolean
  onRequestCancel: () => void
  onDismissCancel: () => void
  onConfirmCancel: () => void
}) {
  const now = Date.now()
  const expires = new Date(oath.expiresAt).getTime()
  const created = new Date(oath.createdAt).getTime()
  const sameDay = new Date(oath.expiresAt).toDateString() === new Date().toDateString()
  const msLeft = Math.max(0, expires - now)

  let countdown: string
  let urgent = false
  let barColor = ICE
  let barPct: number

  if (sameDay) {
    const minsLeft = Math.floor(msLeft / 60000)
    const h = Math.floor(minsLeft / 60)
    const m = minsLeft % 60
    countdown = `${h}h ${m}m left`
    urgent = minsLeft <= 120
    barColor = minsLeft <= 360 ? OUT : ICE
    barPct = Math.max(6, Math.round((minsLeft / (24 * 60)) * 100))
  } else {
    const daysLeft = Math.max(0, Math.ceil(msLeft / 86400000))
    countdown = `${daysLeft} day${daysLeft === 1 ? "" : "s"} left`
    const totalMs = Math.max(1, expires - created)
    barPct = Math.max(6, Math.round((msLeft / totalMs) * 100))
  }

  return (
    <div className="edge-mark-accent border-b border-[#2A3033] py-4.5 pl-3 last:border-b-0">
      <div className="flex items-baseline justify-between gap-3.5">
        <span className="text-[15px]">{oath.categoryName}</span>
        <span className="flex flex-none items-center gap-2">
          <span
            className={`status-tag whitespace-nowrap ${urgent ? "animate-pulse" : ""}`}
            style={{ color: barColor }}
          >
            {countdown}
          </span>
          <button
            onClick={onRequestCancel}
            className="status-tag text-muted-foreground hover:text-destructive cursor-pointer"
          >
            abandon
          </button>
        </span>
      </div>
      <div className="text-muted-foreground mt-1.5 text-[13px]">
        Pledged {formatRon(oath.pledgedAmount)} · until {oath.expiresAt.slice(0, 10)}
      </div>
      <div className="relative mt-2.5 h-px bg-[#2A3033]">
        <div className="h-px" style={{ width: `${barPct}%`, background: barColor }} />
      </div>
      {confirming && (
        <div className="mt-3.5 border border-[#2A3033] bg-[#14181B] p-4">
          <p className="text-[13.5px] font-medium">Abandon this oath?</p>
          <p className="text-muted-foreground mt-1.5 text-[12.5px]">
            Abandoning is recorded as a fact, not a failure — nothing is deducted, because nothing is scored.
          </p>
          <div className="mt-3.5 flex justify-end gap-2.5">
            <button
              onClick={onDismissCancel}
              className="cursor-pointer border border-border bg-transparent px-3.5 py-2 text-[13px] text-foreground/85 hover:border-[#4C93A6]"
            >
              Keep holding
            </button>
            <button
              onClick={onConfirmCancel}
              className="text-destructive border-destructive/40 hover:bg-destructive/10 hover:border-destructive cursor-pointer border bg-transparent px-3.5 py-2 text-[13px]"
            >
              Abandon
            </button>
          </div>
        </div>
      )}
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
  const [confirmCancelId, setConfirmCancelId] = useState<string | null>(null)
  const [streakView, setStreakView] = useState<"month" | "year">("month")
  const [yearCache, setYearCache] = useState<{ year: string; months: Record<string, GoalCalendar | null> } | null>(null)

  const reload = useCallback(() => {
    Promise.all([listQuests(), listGoals(), getCalendar(month), listOaths()])
      .then(([q, g, c, o]) => {
        setQuests(q)
        setGoals(g)
        setCalendar(c)
        setOaths(o)
        setError(null)
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Could not read your quests and oaths"))
  }, [month])

  useEffect(() => {
    reload()
  }, [reload])

  // Lazy, cached: only fetched once the Year view is opened, and only for months
  // up to today (months ahead of now stay blank — see copy below).
  useEffect(() => {
    if (streakView !== "year") return
    const year = month.slice(0, 4)
    if (yearCache?.year === year) return
    const now = new Date()
    const lastMonthNum = year === String(now.getFullYear()) ? now.getMonth() + 1 : 12
    const keys = Array.from({ length: lastMonthNum }, (_, i) => `${year}-${String(i + 1).padStart(2, "0")}`)
    let cancelled = false
    Promise.all(keys.map((k) => getCalendar(k).catch(() => null))).then((results) => {
      if (cancelled) return
      const months: Record<string, GoalCalendar | null> = {}
      results.forEach((c, i) => {
        months[keys[i]] = c
      })
      setYearCache({ year, months })
    })
    return () => {
      cancelled = true
    }
  }, [streakView, month, yearCache])

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

  const offered = quests.filter((q) => q.status === "SUGGESTED")
  const inProgress = quests.filter((q) => q.status === "ACTIVE")
  const closed = quests
    .filter((q) => q.status === "COMPLETED" || q.status === "FAILED" || q.status === "DECLINED")
    .slice(0, 8)
  const openOaths = oaths.filter((o) => o.status === "OPEN")
  const keptCount = oaths.filter((o) => o.status === "KEPT").length
  const slippedCount = oaths.filter((o) => o.status === "SLIPPED").length

  // "Entered" is deliberately distinct from "on budget" — any day with a logged
  // expense counts, matching the copy below ("at least one entry").
  const daysEntered = useMemo(() => calendar?.days.filter((d) => d.totalSpent > 0).length ?? 0, [calendar])
  const entryStreak = useMemo(() => {
    if (!calendar) return 0
    let n = 0
    for (const d of [...calendar.days].reverse()) {
      if (d.totalSpent > 0) n++
      else if (d.status !== "FUTURE") break
    }
    return n
  }, [calendar])

  const counts = [
    { k: "days entered", v: String(daysEntered), mark: "edge-mark-accent" },
    { k: "day streak", v: String(entryStreak), mark: "edge-mark-good" },
    { k: "kept · missed", v: `${keptCount} · ${slippedCount}`, mark: "edge-mark-bad" },
  ]

  const yearHeatmap = useMemo(() => {
    const year = month.slice(0, 4)
    if (!yearCache || yearCache.year !== year) return null
    const now = new Date()
    return MONTH_LABELS.map((label, i) => {
      const key = `${year}-${String(i + 1).padStart(2, "0")}`
      const cal = yearCache.months[key]
      if (!cal) return { label, share: null as number | null }
      const elapsedDays = key === thisMonth() ? now.getDate() : cal.days.length
      const entered = cal.days.filter((d) => d.totalSpent > 0).length
      return { label, share: elapsedDays > 0 ? entered / elapsedDays : 0 }
    })
  }, [yearCache, month])

  const firstDayOffset = calendar ? (new Date(`${calendar.month}-01T00:00:00`).getDay() + 6) % 7 : 0
  const periodSummaries: PeriodSummary[] = [
    ...(calendar?.monthlyGoals ?? []),
    ...(calendar?.yearlyGoals ?? []),
  ]

  const card = "ledger-card p-6"
  const field =
    "text-foreground w-full border border-border bg-transparent px-3.5 py-2.5 text-sm outline-none focus-visible:border-[#4C93A6]"

  return (
    <div className="flex flex-col gap-[22px]">
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
          <AlertAction>
            <button onClick={reload} className="status-tag text-destructive hover:text-foreground cursor-pointer">
              Retry
            </button>
          </AlertAction>
        </Alert>
      )}

      <div className="flex flex-col gap-11 lg:flex-row lg:items-start">
        {/* Main */}
        <div className="min-w-0 flex-1">
          <div className="ledger-label">Offered to you</div>
          {offered.length === 0 ? (
            <p className="text-muted-foreground py-4 text-[13.5px]">
              No quests offered right now — new ones arrive as your spending history grows.
            </p>
          ) : (
            offered.map((q) => (
              <QuestCard
                key={q.id}
                quest={q}
                actions={{
                  onAccept: () => act(acceptQuest, q.id, "Quest accepted — good luck!"),
                  onDecline: () => act(declineQuest, q.id),
                }}
              />
            ))
          )}
          <p className="text-muted-foreground mt-3 text-[12.5px]">
            Offered quests that go unclaimed expire quietly at the window&rsquo;s end — no missed mark,
            matching the counted-not-scored philosophy on the right.
          </p>

          {inProgress.length > 0 && (
            <>
              <div className="ledger-label mt-7">In progress</div>
              {inProgress.map((q) => (
                <QuestCard key={q.id} quest={q} />
              ))}
            </>
          )}

          <div className="mt-7 flex items-baseline justify-between gap-3">
            <div className="ledger-label">Set by you</div>
            <button
              onClick={() => setPledgeOpen(true)}
              className="status-tag text-primary hover:text-foreground cursor-pointer"
            >
              + take an oath
            </button>
          </div>
          {openOaths.length === 0 ? (
            <p className="text-muted-foreground py-4 text-[13.5px]">
              No oaths sworn yet — pledge a category limit for today, the week, or the month.
            </p>
          ) : (
            openOaths.map((oath) => (
              <OathRow
                key={oath.id}
                oath={oath}
                confirming={confirmCancelId === oath.id}
                onRequestCancel={() => setConfirmCancelId(oath.id)}
                onDismissCancel={() => setConfirmCancelId(null)}
                onConfirmCancel={() => {
                  act(cancelOath, oath.id, "Oath abandoned")
                  setConfirmCancelId(null)
                }}
              />
            ))
          )}

          {closed.length > 0 && (
            <>
              <div className="ledger-label mt-7">Closed</div>
              {closed.map((q, i) => {
                const outcome = CLOSED_OUTCOME[q.status] ?? { label: q.status.toLowerCase(), color: "#9AA3A8" }
                return (
                  <div
                    key={q.id}
                    className="flex items-center justify-between gap-3.5 border-b border-[#2A3033] py-3 last:border-b-0"
                  >
                    <span className="flex min-w-0 items-center gap-3">
                      <span className="text-muted-foreground flex-none font-mono text-[11px]">
                        Q-{String(closed.length - i).padStart(3, "0")}
                      </span>
                      <span className="truncate text-[14px] text-[#C7CDD0]">{q.title}</span>
                    </span>
                    <span className="status-tag flex-none whitespace-nowrap" style={{ color: outcome.color }}>
                      {outcome.label}
                    </span>
                  </div>
                )
              })}
            </>
          )}

          {/* Budget goals — a separate real feature the mockup doesn't cover, kept below the quest ledger */}
          <div className="ledger-label mt-9">Budget goals</div>

          <section className={`${card} mt-3`}>
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
                      ? { background: "color-mix(in srgb, #8FC7A6 18%, transparent)", border: "1px solid color-mix(in srgb, #8FC7A6 45%, transparent)", color: "#8FC7A6" }
                      : over
                        ? { background: "color-mix(in srgb, #E09880 18%, transparent)", border: "1px solid color-mix(in srgb, #E09880 45%, transparent)", color: "#E09880" }
                        : { background: "transparent", border: "1px solid #2A3033", color: "#9AA3A8" }
                    return (
                      <div
                        key={day.date}
                        title={
                          day.goals.length
                            ? day.goals.map((g) => `${g.name}: ${formatRon(g.actual)} / ${formatRon(g.target)}`).join("\n")
                            : "No daily goals"
                        }
                        className="relative grid aspect-square place-items-center text-[14.5px] font-semibold"
                        style={{ ...style, outline: inProg ? "1px solid #9AD4E3" : undefined, outlineOffset: 1 }}
                      >
                        {/* Shape marks so status is never colour-only: a notch cut for on-budget, a cross for over. */}
                        {met && (
                          <span className="absolute right-1.5 top-1.5 h-2.5 w-[2px]" style={{ background: GOOD }} />
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
                    <span className="size-3" style={{ background: "color-mix(in srgb, #8FC7A6 22%, transparent)", border: "1px solid color-mix(in srgb, #8FC7A6 45%, transparent)" }} />
                    On budget
                  </span>
                  <span className="flex items-center gap-2">
                    <span className="size-3" style={{ background: "color-mix(in srgb, #E09880 22%, transparent)", border: "1px solid color-mix(in srgb, #E09880 45%, transparent)" }} />
                    Over
                  </span>
                  <span className="flex items-center gap-2">
                    <span className="size-3" style={{ background: "transparent", border: "1px solid #2A3033" }} />
                    Upcoming
                  </span>
                </div>
                {periodSummaries.length > 0 && (
                  <div className="border-border mt-5 space-y-2 border-t pt-4">
                    {periodSummaries.map((s) => (
                      <div key={s.goalId} className="flex items-center justify-between text-sm">
                        <span className="flex items-center gap-2.5">
                          {s.name}
                          <Stamp tone={s.inProgress ? "muted" : s.met ? "good" : "over"}>
                            {s.inProgress ? "open" : s.met ? "met" : "over"}
                          </Stamp>
                        </span>
                        <span className="figure text-xs" style={{ color: s.actual > s.target ? OUT : "#9AA3A8" }}>
                          {formatRon(s.actual)} / {formatRon(s.target)}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </>
            )}
          </section>

          <section className={`${card} mt-[22px]`}>
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
                className="cursor-pointer border border-[#4C93A6] bg-[#123945] px-4 py-2.5 text-[13.5px] font-semibold text-[#C4E7F0] hover:bg-[#174756]"
              >
                Add goal
              </button>
            </form>
            {goals.length > 0 && (
              <ul className="border-border mt-4 space-y-2 border-t pt-4">
                {goals.map((goal) => (
                  <li key={goal.id} className="flex items-center justify-between gap-2 text-sm">
                    <span className="flex items-center gap-2">
                      {goal.name}
                      <span className="status-tag text-muted-foreground">{goal.period.toLowerCase()}</span>
                    </span>
                    <span className="flex items-center gap-2">
                      <span className="figure text-xs" style={{ color: goal.currentActual > goal.targetAmount ? OUT : "#9AA3A8" }}>
                        {formatRon(goal.currentActual)} / {formatRon(goal.targetAmount)}
                      </span>
                      <button
                        onClick={() => deleteGoal(goal.id).then(reload)}
                        aria-label="Delete goal"
                        className="text-muted-foreground hover:text-foreground grid size-11 flex-none cursor-pointer place-items-center hover:bg-white/[0.06]"
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

        {/* Sidebar */}
        <div className="flex w-full flex-none flex-col lg:w-[320px]">
          <div className="ledger-label">This season</div>
          <div className="mt-4 flex gap-8">
            {counts.map((c) => (
              <div key={c.k} className={`${c.mark} pl-3`}>
                <div className="figure text-[27px] font-light">{c.v}</div>
                <div className="text-muted-foreground mt-1.5 whitespace-nowrap font-mono text-[9.5px] tracking-[0.14em] uppercase">
                  {c.k}
                </div>
              </div>
            ))}
          </div>
          <p className="text-muted-foreground mt-3.5 text-[13px]">
            Counted, not scored. No points, no badges, no levels.
          </p>

          <div className="border-border mt-6 border-t pt-[18px]">
            <div className="flex items-center justify-between">
              <span className="ledger-label">Entry streak</span>
              <div className="flex gap-3">
                <button
                  onClick={() => setStreakView("month")}
                  className={`status-tag cursor-pointer pb-0.5 ${
                    streakView === "month" ? "text-primary shadow-[inset_0_-1px_0_0_var(--primary)]" : "text-muted-foreground"
                  }`}
                >
                  Month
                </button>
                <button
                  onClick={() => setStreakView("year")}
                  className={`status-tag cursor-pointer pb-0.5 ${
                    streakView === "year" ? "text-primary shadow-[inset_0_-1px_0_0_var(--primary)]" : "text-muted-foreground"
                  }`}
                >
                  Year
                </button>
              </div>
            </div>

            {streakView === "month" ? (
              <>
                <div className="mt-4 flex flex-wrap gap-2">
                  {calendar?.days.map((d) => {
                    const entered = d.totalSpent > 0
                    return (
                      <div
                        key={d.date}
                        className="grid size-8 flex-none place-items-center border font-mono text-[11px]"
                        style={{
                          background: entered ? "#123945" : "transparent",
                          borderColor: entered ? "#4C93A6" : "var(--border)",
                          color: entered ? "#C4E7F0" : "#9AA3A8",
                        }}
                      >
                        {Number(d.date.slice(8, 10))}
                      </div>
                    )
                  })}
                </div>
                <p className="text-muted-foreground mt-3.5 text-[13px]">
                  Filled squares are days with at least one entry. The streak counts backwards from today, so
                  a gap yesterday costs you the run — not the record.
                </p>
              </>
            ) : yearHeatmap ? (
              <>
                <div className="mt-3.5 grid grid-cols-6 gap-1.5">
                  {yearHeatmap.map((m) => {
                    const bg =
                      m.share == null
                        ? "#2A3033"
                        : m.share >= 0.65
                          ? "#123945"
                          : m.share >= 0.4
                            ? "rgba(76,147,166,.35)"
                            : "rgba(224,152,128,.25)"
                    const fg = m.share == null ? "#9AA3A8" : m.share >= 0.65 ? "#C4E7F0" : "#C7CDD0"
                    return (
                      <div
                        key={m.label}
                        className="grid aspect-square place-items-center font-mono text-[9.5px]"
                        style={{ background: bg, color: fg }}
                      >
                        {m.label}
                      </div>
                    )
                  })}
                </div>
                <p className="text-muted-foreground mt-3.5 text-[13px]">
                  Shaded by share of days entered that month. Months ahead of today are blank, not zero.
                </p>
              </>
            ) : (
              <p className="text-muted-foreground mt-3.5 text-[13px]">Loading…</p>
            )}
          </div>
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
