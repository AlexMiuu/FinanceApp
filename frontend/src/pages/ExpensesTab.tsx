import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react"
import { useAuth } from "@/auth/AuthContext"
import {
  createExpense,
  createRecurring,
  deleteExpense,
  deleteRecurring,
  formatRon,
  listExpenses,
  listRecurring,
  updateExpense,
  type Category,
  type Expense,
  type RecurringExpense,
} from "@/lib/api"
import { lastUsedCategory, recordCategoryUse, topCategories } from "@/lib/categoryUsage"
import { useToast } from "@/components/Toast"
import { CategorySelect } from "@/components/CategorySelect"
import { ChevronIcon, CloseIcon } from "@/components/brand"
import CategoriesTab from "@/pages/CategoriesTab"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

const today = () => new Date().toISOString().slice(0, 10)

function categoryLabel(categories: Category[], id: string): string {
  const category = categories.find((c) => c.id === id)
  if (!category) return "—"
  if (!category.parentId) return category.name
  const parent = categories.find((c) => c.id === category.parentId)
  return parent ? `${parent.name} › ${category.name}` : category.name
}

/** Top-level category name for an expense — powers filter chips and grouping. */
function topCategoryName(categories: Category[], id: string): string {
  const category = categories.find((c) => c.id === id)
  if (!category) return "Other"
  return category.parentId
    ? categories.find((c) => c.id === category.parentId)?.name ?? "Other"
    : category.name
}

/** Two-letter monogram for a row avatar — one hand, no emoji. */
function monogram(label: string): string {
  const words = label.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return "··"
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase()
  return (words[0][0] + words[1][0]).toUpperCase()
}

const formatDate = (iso: string) => {
  const d = new Date(iso + "T00:00:00")
  return d.toLocaleDateString("en-GB", { day: "numeric", month: "short" })
}

const formatTime = (d: Date) => d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })

const CATEGORY_BAR = ["#9AD4E3", "#4C93A6", "#8FC7A6", "#E09880", "#8A9399"]

export default function ExpensesTab({
  categories,
  onCategoriesChanged,
  query = "",
  onQueryChange,
}: {
  categories: Category[]
  onCategoriesChanged: () => void
  query?: string
  onQueryChange?: (q: string) => void
}) {
  const { user } = useAuth()
  const userId = user?.id ?? "anon"
  const toast = useToast()

  const [expenses, setExpenses] = useState<Expense[]>([])
  const [recurring, setRecurring] = useState<RecurringExpense[]>([])
  const [total, setTotal] = useState(0)
  const [monthTotal, setMonthTotal] = useState(0)
  const [monthMax, setMonthMax] = useState(0)
  const [monthByCat, setMonthByCat] = useState<{ name: string; amount: number }[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [lastReadAt, setLastReadAt] = useState<Date | null>(null)
  const [open, setOpen] = useState<string | null>(null)
  const [chip, setChip] = useState<string>("All")

  const [voidTarget, setVoidTarget] = useState<Expense | null>(null)
  const [voiding, setVoiding] = useState(false)
  const [noteTarget, setNoteTarget] = useState<Expense | null>(null)
  const [noteDraft, setNoteDraft] = useState("")
  const [savingNote, setSavingNote] = useState(false)

  const [editing, setEditing] = useState<Expense | null>(null)
  const [amount, setAmount] = useState("")
  const [note, setNote] = useState("")
  const [date, setDate] = useState(today())
  const [categoryId, setCategoryId] = useState<string | undefined>(
    () => lastUsedCategory(userId) ?? undefined
  )
  const [repeatMonthly, setRepeatMonthly] = useState(false)
  const amountRef = useRef<HTMLInputElement | null>(null)
  const editRef = useRef<HTMLDivElement | null>(null)

  const [filterFrom, setFilterFrom] = useState("")
  const [filterTo, setFilterTo] = useState("")
  const [filterCategory, setFilterCategory] = useState<string>()

  const localQuery = query.trim().toLowerCase()

  const chips = topCategories(userId, 5)
    .map((id) => categories.find((c) => c.id === id))
    .filter((c): c is Category => c !== undefined)

  const reload = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    try {
      const month = today().slice(0, 7)
      const [page, recurringList, monthPage] = await Promise.all([
        listExpenses({
          from: filterFrom || undefined,
          to: filterTo || undefined,
          categoryId: filterCategory,
        }),
        listRecurring(),
        listExpenses({ from: `${month}-01`, to: `${month}-31` }),
      ])
      setExpenses(page.items)
      setTotal(page.totalElements)
      setRecurring(recurringList)
      setMonthTotal(monthPage.items.reduce((a, e) => a + e.amount, 0))
      setMonthMax(monthPage.items.reduce((a, e) => Math.max(a, e.amount), 0))
      const byCat = new Map<string, number>()
      for (const e of monthPage.items) {
        const name = topCategoryName(categories, e.categoryId)
        byCat.set(name, (byCat.get(name) ?? 0) + e.amount)
      }
      setMonthByCat(
        [...byCat.entries()]
          .map(([name, amount]) => ({ name, amount }))
          .sort((a, b) => b.amount - a.amount)
          .slice(0, 5)
      )
      setLastReadAt(new Date())
    } catch (e) {
      setLoadError(
        e instanceof Error ? `We could not read your ledger — ${e.message}` : "We could not read your ledger"
      )
    } finally {
      setLoading(false)
    }
  }, [filterFrom, filterTo, filterCategory, categories])

  useEffect(() => {
    reload()
  }, [reload])

  // Filter chips: "All" plus the top-level categories present this month, each carrying its count.
  const filterChips = useMemo(() => {
    const counts = new Map<string, number>()
    for (const e of expenses) {
      const name = topCategoryName(categories, e.categoryId)
      counts.set(name, (counts.get(name) ?? 0) + 1)
    }
    const names = [...counts.keys()].slice(0, 5)
    return [
      { label: "All", count: expenses.length },
      ...names.map((name) => ({ label: name, count: counts.get(name) ?? 0 })),
    ]
  }, [expenses, categories])

  const shown = useMemo(
    () =>
      expenses.filter((e) => {
        if (chip !== "All" && topCategoryName(categories, e.categoryId) !== chip) return false
        if (!localQuery) return true
        const hay = `${e.note ?? ""} ${categoryLabel(categories, e.categoryId)} ${(e.amount / 100).toFixed(2)}`
        return hay.toLowerCase().includes(localQuery)
      }),
    [expenses, chip, localQuery, categories]
  )

  // Running balance of the shown page — cumulative spend as you read down the ledger.
  const shownWithBalance = useMemo(() => {
    let acc = 0
    return shown.map((expense) => {
      acc += expense.amount
      return { expense, balance: acc }
    })
  }, [shown])

  const monthMaxCat = monthByCat[0]?.amount ?? 1

  function startEdit(expense: Expense) {
    setEditing(expense)
    setAmount((expense.amount / 100).toFixed(2))
    setNote(expense.note ?? "")
    setDate(expense.expenseDate)
    setCategoryId(expense.categoryId)
    setRepeatMonthly(false)
    editRef.current?.scrollIntoView({ behavior: "smooth", block: "start" })
    setTimeout(() => amountRef.current?.focus(), 120)
  }

  function cancelEdit() {
    setEditing(null)
    setAmount("")
    setNote("")
    setDate(today())
  }

  async function submit(e?: FormEvent<HTMLFormElement>) {
    e?.preventDefault()
    setError(null)
    if (!categoryId) {
      setError("Pick a category")
      return
    }
    const bani = Math.round(parseFloat(amount) * 100)
    if (!bani || bani <= 0) {
      setError("Enter a valid amount")
      return
    }
    const body = { amount: bani, categoryId, note: note || null, expenseDate: date }
    try {
      if (editing) {
        await updateExpense(editing.id, body)
        toast("Expense updated")
        cancelEdit()
      } else if (repeatMonthly) {
        await createRecurring({ ...body, startDate: date })
        setRepeatMonthly(false)
        toast(`Recurring expense created — posts on day ${Number(date.slice(8, 10))} monthly`)
        setAmount("")
        setNote("")
      } else {
        await createExpense(body)
        toast("Expense added")
        setAmount("")
        setNote("")
      }
      recordCategoryUse(userId, categoryId)
      amountRef.current?.focus()
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed")
    }
  }

  async function duplicate(expense: Expense) {
    try {
      await createExpense({
        amount: expense.amount,
        categoryId: expense.categoryId,
        note: expense.note,
        expenseDate: today(),
      })
      recordCategoryUse(userId, expense.categoryId)
      toast("Added again, dated today")
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Duplicate failed")
    }
  }

  async function remove(id: string) {
    try {
      await deleteExpense(id)
      toast("Expense deleted")
      setOpen(null)
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed")
    }
  }

  async function confirmVoid() {
    if (!voidTarget) return
    setVoiding(true)
    try {
      await remove(voidTarget.id)
      setVoidTarget(null)
    } finally {
      setVoiding(false)
    }
  }

  function startNote(expense: Expense) {
    setNoteTarget(expense)
    setNoteDraft(expense.note ?? "")
  }

  async function submitNote() {
    if (!noteTarget) return
    setSavingNote(true)
    try {
      await updateExpense(noteTarget.id, {
        amount: noteTarget.amount,
        categoryId: noteTarget.categoryId,
        note: noteDraft || null,
        expenseDate: noteTarget.expenseDate,
      })
      toast("Note saved")
      setNoteTarget(null)
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Note save failed")
    } finally {
      setSavingNote(false)
    }
  }

  const enterSubmits = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") submit()
  }

  const shownTotal = shown.reduce((a, e) => a + e.amount, 0)

  const resultLabel = loading
    ? "LOADING…"
    : `${shown.length} OF ${total} SHOWN${chip === "All" ? "" : " · " + chip.toUpperCase()}${
        localQuery ? ` · “${query.trim()}”` : ""
      }`

  const rowField =
    "text-foreground border-border w-full border bg-transparent px-3.5 py-2.5 text-sm outline-none focus-visible:border-primary"

  return (
    <div className="flex flex-wrap items-start gap-[22px]">
      {/* Main column */}
      <div className="flex min-w-[min(100%,440px)] flex-1 basis-[62%] flex-col gap-4">
        {/* Search */}
        <div className="bg-card border-border flex items-center gap-3 border px-4">
          <SearchGlyph />
          <input
            value={query}
            onChange={(e) => onQueryChange?.(e.target.value)}
            placeholder="Search merchant, category or amount"
            className="text-foreground flex-1 border-none bg-transparent py-3.5 text-[14.5px] outline-none"
          />
          {localQuery && (
            <button
              onClick={() => onQueryChange?.("")}
              className="ledger-label border-border text-muted-foreground hover:border-primary cursor-pointer border px-2.5 py-1"
            >
              Clear
            </button>
          )}
        </div>

        {/* Filter chips */}
        <div className="flex flex-wrap gap-2">
          {filterChips.map(({ label, count }) => {
            const active = chip === label
            return (
              <button
                key={label}
                onClick={() => setChip(label)}
                className={`status-tag tnum cursor-pointer border px-3 py-1.5 transition-colors ${
                  active
                    ? "border-primary bg-primary/10 text-primary"
                    : "border-border text-muted-foreground hover:border-[#4C93A6]"
                }`}
              >
                {label} · {count}
              </button>
            )
          })}
        </div>

        <div className="ledger-label">{resultLabel}</div>

        {/* Ledger list — column headers stay put across loading, empty and error too:
            a first-run user learns the table's shape before it has rows, and a returning
            user with a failed read gets the same chrome instead of something that looks broken. */}
        <section className="ledger-card px-6 py-3" aria-busy={loading}>
          <div className="ledger-label -mx-3.5 flex items-center gap-4 border-b border-white/10 px-3.5 pb-2">
            <span className="w-9 text-right">No.</span>
            <span className="flex-1">Entry</span>
            <span className="w-[92px] text-right">Amount</span>
            <span className="w-[100px] text-right">Balance</span>
            <span className="w-[128px] text-right">Actions</span>
            <span className="w-[18px]" aria-hidden />
          </div>

          {loading && expenses.length === 0 ? (
            <div role="status" aria-live="polite" className="flex flex-col">
              <span className="sr-only">Reading your ledger</span>
              <div className="ruled">
                {[62, 44, 74, 38, 54].map((w, i) => (
                  <div
                    key={i}
                    className="flex items-center gap-4 py-4"
                    style={{ animation: `shimmer 1.6s ease-in-out ${(i * 0.22).toFixed(2)}s infinite` }}
                  >
                    <span className="h-px w-9 bg-white/15" aria-hidden />
                    <span className="h-px flex-1 bg-white/15" style={{ maxWidth: `${w}%` }} aria-hidden />
                    <span className="ml-auto h-px w-[92px] bg-white/15" aria-hidden />
                    <span className="h-px w-[100px] bg-white/10" aria-hidden />
                    <span className="h-px w-[128px] bg-white/10" aria-hidden />
                  </div>
                ))}
              </div>
              <div className="ledger-label pt-3">Ruling the page…</div>
            </div>
          ) : loadError && shown.length === 0 ? (
            <div
              role="alert"
              className="my-3 flex flex-col gap-3 border border-destructive/50 bg-white/[0.02] px-5 py-6 shadow-[inset_2px_0_0_0_var(--destructive)]"
            >
              <div className="text-[18px] font-semibold">We could not read your ledger</div>
              <p className="text-muted-foreground max-w-[440px] text-sm text-pretty">
                Nothing was lost and nothing changed — this is a reading problem, not a money problem.
                {lastReadAt
                  ? ` Your entries are on the server exactly as you left them at ${formatTime(lastReadAt)}.`
                  : ""}
              </p>
              <div>
                <button
                  onClick={() => reload()}
                  className="cursor-pointer border border-[#4C93A6] bg-[#123945] px-5 py-2.5 text-sm font-medium text-[#C4E7F0] hover:bg-[#174756]"
                >
                  Try again
                </button>
              </div>
            </div>
          ) : shown.length === 0 ? (
            <div className="flex flex-col items-center gap-2.5 px-6 py-16 text-center">
              {/* Ram brand-iron on a blank page — an intentional empty ledger. */}
              <div className="border-border grid size-[72px] place-items-center border bg-white/[0.03]">
                <div className="text-muted-foreground size-10">
                  <svg
                    viewBox="-8 -13 116 116"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth={7}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="size-full"
                    aria-hidden="true"
                  >
                    <path d="M86 78H38L10 60V50L48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39" />
                    <circle cx="32" cy="52" r="4" fill="currentColor" stroke="none" />
                  </svg>
                </div>
              </div>
              <div className="mt-1.5 text-[18px] font-semibold">
                {total === 0 ? "An unnotched stick" : "No entries match"}
              </div>
              <p className="text-muted-foreground max-w-[330px] text-sm text-pretty">
                {total === 0
                  ? "Nothing recorded yet, and no numbers to show you until there is. The first cut is the hardest one — today's coffee will do."
                  : "Try a different merchant, a broader category, or clear the filters to see every entry."}
              </p>
              {total === 0 ? (
                <button
                  onClick={() => {
                    editRef.current?.scrollIntoView({ behavior: "smooth", block: "start" })
                    setTimeout(() => amountRef.current?.focus(), 120)
                  }}
                  className="mt-3.5 cursor-pointer border border-[#4C93A6] bg-[#123945] px-5 py-2.5 text-sm font-medium text-[#C4E7F0] hover:bg-[#174756]"
                >
                  Record the first entry
                </button>
              ) : (
                (localQuery || chip !== "All") && (
                  <button
                    onClick={() => {
                      onQueryChange?.("")
                      setChip("All")
                    }}
                    className="mt-3.5 cursor-pointer border border-[#4C93A6] bg-[#123945] px-5 py-2.5 text-sm font-medium text-[#C4E7F0] hover:bg-[#174756]"
                  >
                    Clear filters
                  </button>
                )
              )}
            </div>
          ) : (
            <>
            {loadError && (
              <div
                role="alert"
                className="ledger-label my-2 flex flex-wrap items-center justify-between gap-3 border border-destructive/40 bg-white/[0.02] px-3.5 py-2.5 normal-case text-destructive shadow-[inset_2px_0_0_0_var(--destructive)]"
              >
                <span>
                  We could not read the latest — showing what we last had
                  {lastReadAt ? ` at ${formatTime(lastReadAt)}` : ""}.
                </span>
                <button onClick={() => reload()} className="text-primary cursor-pointer normal-case">
                  Try again
                </button>
              </div>
            )}
            <div className="ruled">
            {shownWithBalance.map(({ expense, balance }, i) => {
              const isOpen = open === expense.id
              const label = expense.note || categoryLabel(categories, expense.categoryId)
              const otherThatDay =
                expenses.filter((e) => e.expenseDate === expense.expenseDate).length - 1
              return (
                <div key={expense.id} className="group">
                  <div
                    role="button"
                    tabIndex={0}
                    aria-expanded={isOpen}
                    onClick={() => setOpen(isOpen ? null : expense.id)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault()
                        setOpen(isOpen ? null : expense.id)
                      }
                    }}
                    className="-mx-3.5 flex cursor-pointer items-center gap-4 px-3.5 py-3.5 transition-colors hover:bg-white/[0.05]"
                  >
                    <div className="text-muted-foreground/70 tnum w-9 flex-none text-right font-mono text-[12px]">
                      {String(i + 1).padStart(3, "0")}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[15px] font-medium">{label}</div>
                      <div className="mt-1 flex items-center gap-2">
                        <span className="status-tag bg-primary/10 text-primary px-1.5 py-0.5">
                          {topCategoryName(categories, expense.categoryId)}
                        </span>
                        <span className="tnum text-muted-foreground text-[12px]">
                          {formatDate(expense.expenseDate)}
                        </span>
                      </div>
                    </div>
                    <div className="figure text-foreground w-[92px] flex-none text-right text-[15px]">
                      −{formatRon(expense.amount).replace(/\s?RON$/, "")}
                    </div>
                    <div className="tnum text-muted-foreground w-[100px] flex-none text-right text-[13.5px]">
                      {formatRon(balance).replace(/\s?RON$/, "")}
                    </div>
                    <div className="status-tag flex w-[128px] flex-none items-center justify-end gap-2.5">
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          duplicate(expense)
                        }}
                        aria-label="Add again"
                        className="text-muted-foreground hover:text-foreground min-h-[28px] min-w-[28px] cursor-pointer px-0.5 py-2.5"
                      >
                        copy
                      </button>
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          startEdit(expense)
                        }}
                        aria-label="Edit entry"
                        className="min-h-[28px] min-w-[28px] cursor-pointer px-0.5 py-2.5 text-[#4C93A6] hover:text-[#9AD4E3]"
                      >
                        edit
                      </button>
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          startNote(expense)
                        }}
                        aria-label="Add margin note"
                        className="text-muted-foreground hover:text-foreground min-h-[28px] min-w-[28px] cursor-pointer px-0.5 py-2.5"
                      >
                        note
                      </button>
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          setVoidTarget(expense)
                        }}
                        aria-label="Void entry"
                        className="text-destructive hover:text-destructive/80 min-h-[28px] min-w-[28px] cursor-pointer px-0.5 py-2.5"
                      >
                        void
                      </button>
                    </div>
                    <span
                      className="text-muted-foreground grid size-[18px] flex-none place-items-center transition-transform"
                      style={{ transform: `rotate(${isOpen ? 90 : 0}deg)` }}
                    >
                      <ChevronIcon size={18} />
                    </span>
                  </div>
                  {isOpen && (
                    <div
                      className="ledger-card -mx-3.5 mb-3.5 flex flex-wrap gap-6 p-4.5"
                      style={{ animation: "riseIn .16s ease" }}
                    >
                      {[
                        ["CATEGORY", categoryLabel(categories, expense.categoryId)],
                        ["RECORDED", formatDate(expense.expenseDate) + " " + expense.expenseDate.slice(0, 4)],
                        ["OTHER ENTRIES THAT DAY", String(otherThatDay)],
                      ].map(([label, value]) => (
                        <div key={label} className="min-w-[120px]">
                          <div className="ledger-label">{label}</div>
                          <div className="tnum mt-1.5 text-[14.5px]">{value}</div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )
            })}
            </div>
            <div className="-mx-3.5 mt-1 flex items-center gap-4 border-t border-white/10 px-3.5 pt-3 text-[13.5px] font-semibold">
              <span className="w-9" aria-hidden />
              <span className="flex-1">
                Page subtotal · {shown.length} entr{shown.length === 1 ? "y" : "ies"}
              </span>
              <span className="figure w-[92px] text-right">
                −{formatRon(shownTotal).replace(/\s?RON$/, "")}
              </span>
              <span className="tnum w-[100px] text-right">
                {formatRon(shownTotal).replace(/\s?RON$/, "")}
              </span>
              <span className="w-[128px]" aria-hidden />
              <span className="w-[18px]" aria-hidden />
            </div>
            </>
          )}
        </section>

        {/* Date-range / category server filters */}
        <details className="group">
          <summary className="ledger-label flex cursor-pointer list-none items-center gap-1.5 py-1 select-none">
            <ChevronIcon size={13} className="transition-transform group-open:rotate-90" />
            Filter by date &amp; category
          </summary>
          <div className="bg-card mt-2 grid gap-2 rounded-2xl border border-white/[0.07] p-4 sm:grid-cols-3">
            <Input
              type="date"
              value={filterFrom}
              onChange={(e) => setFilterFrom(e.target.value)}
              aria-label="From date"
              className="font-mono"
            />
            <Input
              type="date"
              value={filterTo}
              onChange={(e) => setFilterTo(e.target.value)}
              aria-label="To date"
              className="font-mono"
            />
            <CategorySelect
              categories={categories}
              value={filterCategory}
              onChange={setFilterCategory}
              allowAll
            />
          </div>
        </details>
      </div>

      {/* Right column */}
      <div className="flex min-w-[min(100%,320px)] flex-1 basis-[30%] flex-col gap-[22px]">
        <section className="ledger-card p-6">
          <h2 className="text-[17px] font-semibold">This month</h2>
          <div className="mt-5 flex gap-5">
            <div className="flex-1">
              <div className="text-muted-foreground text-[13.5px]">Spent</div>
              <div className="figure mt-1.5 text-[28px]">
                {formatRon(monthTotal).replace(/\s?RON$/, "")}
              </div>
            </div>
            <div className="flex-1 text-right">
              <div className="text-muted-foreground text-[13.5px]">Largest</div>
              <div className="figure mt-1.5 text-[28px]">
                {formatRon(monthMax).replace(/\s?RON$/, "")}
              </div>
            </div>
          </div>
          <div className="text-muted-foreground mt-5 border-t border-white/[0.07] pt-4.5 text-[13.5px]">
            {total} transactions total
          </div>
        </section>

        {monthByCat.length > 0 && (
          <section className="ledger-card p-6">
            <h2 className="mb-5 text-[17px] font-semibold">Top categories</h2>
            <div className="flex flex-col gap-4.5">
              {monthByCat.map((cat, i) => (
                <div key={cat.name}>
                  <div className="flex justify-between text-[14.5px]">
                    <span className="font-medium">{cat.name}</span>
                    <span className="figure">{formatRon(cat.amount)}</span>
                  </div>
                  <div className="mt-2.5 h-2 overflow-hidden rounded-full bg-white/[0.09]">
                    <div
                      className="h-full rounded-full"
                      style={{
                        width: `${Math.max(6, Math.round((cat.amount / monthMaxCat) * 100))}%`,
                        background: CATEGORY_BAR[i % CATEGORY_BAR.length],
                      }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Add / edit form */}
        <section
          ref={editRef}
          className={`ledger-card p-6 ${editing ? "!border-primary/50" : ""}`}
        >
          <h2 className="mb-4 text-[17px] font-semibold">
            {editing ? "Edit expense" : "Add expense"}
          </h2>
          <form className="space-y-3" onSubmit={submit}>
            {chips.length > 0 && !editing && (
              <div className="flex flex-wrap gap-2" aria-label="Frequent categories">
                {chips.map((c) => {
                  const active = categoryId === c.id
                  return (
                    <button
                      key={c.id}
                      type="button"
                      onClick={() => setCategoryId(c.id)}
                      className={`status-tag cursor-pointer border px-3 py-1.5 transition-colors ${
                        active
                          ? "border-primary text-primary"
                          : "border-border text-muted-foreground hover:border-[#4C93A6]"
                      }`}
                    >
                      {categoryLabel(categories, c.id)}
                    </button>
                  )
                })}
              </div>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="amount">Amount (RON)</Label>
              <input
                id="amount"
                ref={amountRef}
                type="number"
                step="0.01"
                min="0.01"
                inputMode="decimal"
                placeholder="0.00"
                className={`${rowField} figure`}
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                onKeyDown={enterSubmits}
              />
            </div>
            <div className="space-y-1.5">
              <Label>Category</Label>
              <CategorySelect categories={categories} value={categoryId} onChange={setCategoryId} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="note">Note</Label>
              <input
                id="note"
                placeholder="Optional note"
                className={rowField}
                value={note}
                onChange={(e) => setNote(e.target.value)}
                onKeyDown={enterSubmits}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="date">Date</Label>
              <input
                id="date"
                type="date"
                className={`${rowField} font-mono`}
                value={date}
                onChange={(e) => setDate(e.target.value)}
              />
            </div>
            {!editing && (
              <label className="text-muted-foreground flex items-center gap-2 text-[13px]">
                <Checkbox
                  checked={repeatMonthly}
                  onCheckedChange={(v) => setRepeatMonthly(v === true)}
                />
                Repeat monthly from this date
              </label>
            )}
            <div className="flex gap-2 pt-1">
              <button
                type="submit"
                className="flex-1 cursor-pointer border border-[#4C93A6] bg-[#123945] py-3 text-[14px] font-medium text-[#C4E7F0] hover:bg-[#174756]"
              >
                {editing ? "Save changes" : "Add expense"}
              </button>
              {editing && (
                <button
                  type="button"
                  onClick={cancelEdit}
                  className="text-foreground border-border cursor-pointer border bg-transparent px-4 py-3 text-[14px] hover:bg-white/[0.06]"
                >
                  Cancel
                </button>
              )}
            </div>
          </form>
        </section>

        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {recurring.length > 0 && (
          <section className="ledger-card p-6">
            <h2 className="text-[17px] font-semibold">Recurring monthly</h2>
            <p className="text-muted-foreground mt-1 text-[13px]">
              Posted automatically — no typing required.
            </p>
            <ul className="mt-4 space-y-2.5">
              {recurring.map((r) => (
                <li key={r.id} className="flex items-center gap-3">
                  <div className="text-muted-foreground grid size-9 flex-none place-items-center rounded-lg bg-white/[0.06] font-mono text-[12px]">
                    {monogram(r.note || topCategoryName(categories, r.categoryId))}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[13.5px] font-medium">
                      {r.note || categoryLabel(categories, r.categoryId)}
                    </p>
                    <p className="text-muted-foreground text-[12.5px]">day {r.dayOfMonth} · next {r.nextRun}</p>
                  </div>
                  <span className="figure text-[13.5px]">{formatRon(r.amount)}</span>
                  <button
                    onClick={() => deleteRecurring(r.id).then(reload)}
                    aria-label="Remove recurring"
                    className="text-muted-foreground hover:text-foreground grid size-9 flex-none cursor-pointer place-items-center rounded-lg hover:bg-white/[0.06]"
                  >
                    <CloseIcon size={15} />
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )}

        <details className="group">
          <summary className="ledger-label flex cursor-pointer list-none items-center gap-1.5 py-1 select-none">
            <ChevronIcon size={13} className="transition-transform group-open:rotate-90" />
            Manage categories
          </summary>
          <div className="pt-2">
            <CategoriesTab categories={categories} onChanged={onCategoriesChanged} />
          </div>
        </details>

        {/* Void confirmation — the ledger deletes for real here (no correction-line model
            in this API), so the dialog says exactly that instead of promising an append. */}
        <Dialog open={voidTarget !== null} onOpenChange={(v) => !v && setVoidTarget(null)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Void this entry?</DialogTitle>
              <DialogDescription>
                This removes it from your ledger for good — no serial stays behind it. This cannot be undone.
              </DialogDescription>
            </DialogHeader>
            {voidTarget && (
              <div className="border-border bg-background flex items-center gap-3 border px-3.5 py-3 font-mono text-[12.5px]">
                <span className="text-muted-foreground">
                  {String(shown.findIndex((e) => e.id === voidTarget.id) + 1).padStart(3, "0")}
                </span>
                <span className="text-foreground flex-1 truncate">
                  {voidTarget.note || categoryLabel(categories, voidTarget.categoryId)}
                </span>
                <span className="figure">−{formatRon(voidTarget.amount).replace(/\s?RON$/, "")}</span>
              </div>
            )}
            <DialogFooter>
              <button
                onClick={() => setVoidTarget(null)}
                className="text-foreground border-border cursor-pointer border bg-transparent px-5 py-2.5 text-[14px] hover:bg-white/[0.06]"
              >
                Keep it
              </button>
              <button
                onClick={confirmVoid}
                disabled={voiding}
                className="cursor-pointer border border-destructive bg-transparent px-5 py-2.5 text-[14px] font-semibold text-destructive hover:bg-destructive/10 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {voiding ? "Voiding…" : "Void entry"}
              </button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Margin note — a quick note-only edit, separate from the full edit form. */}
        <Dialog open={noteTarget !== null} onOpenChange={(v) => !v && setNoteTarget(null)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Note in the margin</DialogTitle>
              <DialogDescription>Notes never change a figure.</DialogDescription>
            </DialogHeader>
            {noteTarget && (
              <div className="border-border bg-background flex items-center gap-3 border px-3.5 py-3 font-mono text-[12.5px]">
                <span className="text-muted-foreground">{formatDate(noteTarget.expenseDate)}</span>
                <span className="text-foreground flex-1 truncate">
                  {categoryLabel(categories, noteTarget.categoryId)}
                </span>
                <span className="figure">−{formatRon(noteTarget.amount).replace(/\s?RON$/, "")}</span>
              </div>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="margin-note">Note</Label>
              <textarea
                id="margin-note"
                rows={3}
                value={noteDraft}
                onChange={(e) => setNoteDraft(e.target.value)}
                className="text-foreground border-border w-full resize-y border bg-transparent px-3.5 py-2.5 text-sm outline-none focus-visible:border-primary"
              />
            </div>
            <DialogFooter>
              <button
                onClick={() => setNoteTarget(null)}
                className="text-foreground border-border cursor-pointer border bg-transparent px-5 py-2.5 text-[14px] hover:bg-white/[0.06]"
              >
                Cancel
              </button>
              <button
                onClick={submitNote}
                disabled={savingNote}
                className="cursor-pointer border border-[#4C93A6] bg-[#123945] px-5 py-2.5 text-[14px] font-medium text-[#C4E7F0] hover:bg-[#174756] disabled:cursor-not-allowed disabled:opacity-50"
              >
                {savingNote ? "Saving…" : "Save note"}
              </button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </div>
  )
}

function SearchGlyph() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="text-muted-foreground"
      aria-hidden="true"
    >
      <circle cx="9" cy="9" r="5.4" />
      <path d="m13.2 13.2 3.4 3.4" />
    </svg>
  )
}
