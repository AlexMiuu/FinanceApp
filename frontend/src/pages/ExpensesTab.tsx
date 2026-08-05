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
import CategoriesTab from "@/pages/CategoriesTab"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Checkbox } from "@/components/ui/checkbox"
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

const CATEGORY_BAR = ["#c79a5b", "#e8d3b4", "#b07e52", "#8a6440", "#9cb37a"]

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
  const [open, setOpen] = useState<string | null>(null)
  const [chip, setChip] = useState<string>("All")

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
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load expenses")
    } finally {
      setLoading(false)
    }
  }, [filterFrom, filterTo, filterCategory, categories])

  useEffect(() => {
    reload()
  }, [reload])

  // Filter chips: "All" plus the top-level categories present this month.
  const filterChips = useMemo(() => {
    const names = Array.from(new Set(expenses.map((e) => topCategoryName(categories, e.categoryId))))
    return ["All", ...names.slice(0, 5)]
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
        toast("✓", "Expense updated")
        cancelEdit()
      } else if (repeatMonthly) {
        await createRecurring({ ...body, startDate: date })
        setRepeatMonthly(false)
        toast("🔁", `Recurring expense created — posts on day ${Number(date.slice(8, 10))} monthly`)
        setAmount("")
        setNote("")
      } else {
        await createExpense(body)
        toast("✓", "Expense added")
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
      toast("✓", "Added again, dated today")
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Duplicate failed")
    }
  }

  async function remove(id: string) {
    try {
      await deleteExpense(id)
      toast("🗑", "Expense deleted")
      setOpen(null)
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed")
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
    "text-foreground w-full rounded-xl border border-white/10 bg-[#241C17] px-3.5 py-2.5 text-sm outline-none focus-visible:border-primary"

  return (
    <div className="flex flex-wrap items-start gap-[22px]">
      {/* Main column */}
      <div className="flex min-w-[min(100%,440px)] flex-1 basis-[62%] flex-col gap-4">
        {/* Search */}
        <div className="bg-card flex items-center gap-3 rounded-2xl border border-white/[0.09] px-4">
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
              className="text-muted-foreground cursor-pointer rounded-full border border-white/15 bg-white/[0.07] px-2.5 py-1 text-xs hover:border-white/30"
            >
              Clear
            </button>
          )}
        </div>

        {/* Filter chips */}
        <div className="flex flex-wrap gap-2.5">
          {filterChips.map((label) => {
            const active = chip === label
            return (
              <button
                key={label}
                onClick={() => setChip(label)}
                className={`cursor-pointer rounded-full border px-4 py-2 text-[13.5px] transition-all ${
                  active
                    ? "bg-primary text-primary-foreground border-primary"
                    : "text-foreground/85 border-white/12 bg-white/[0.04] hover:border-white/30"
                }`}
              >
                {label}
              </button>
            )
          })}
        </div>

        <div className="ledger-label">{resultLabel}</div>

        {/* Ledger list */}
        <section className="bg-card rounded-2xl border border-white/[0.07] px-6 py-3">
          {loading ? (
            <div className="flex flex-col items-center justify-center gap-4 py-[70px]">
              <div className="text-foreground size-16">
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
                  <path
                    d="M86 78H38L10 60V50L48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39"
                    stroke="#C79A5B"
                    style={{ strokeDasharray: 370, animation: "coilUnroll 2.1s cubic-bezier(.5,0,.5,1) infinite" }}
                  />
                  <circle cx="32" cy="52" r="4" fill="currentColor" stroke="none" />
                </svg>
              </div>
              <div className="ledger-label" style={{ color: "#8C7D6C" }}>
                Loading transactions
              </div>
            </div>
          ) : shown.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-6 py-14 text-center">
              <div className="mb-2.5 size-20" style={{ color: "#3D3229" }}>
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
              <div className="text-[17px] font-semibold">No transactions match</div>
              <p className="text-muted-foreground max-w-[320px] text-sm text-pretty">
                {total === 0
                  ? "No expenses yet — add your first with the + button."
                  : "Try a different merchant, a broader category, or clear the filters."}
              </p>
              {(localQuery || chip !== "All") && (
                <button
                  onClick={() => {
                    onQueryChange?.("")
                    setChip("All")
                  }}
                  className="bg-primary text-primary-foreground mt-3.5 cursor-pointer rounded-xl border-none px-5 py-2.5 text-sm font-semibold hover:bg-[#D8B27A]"
                >
                  Reset filters
                </button>
              )}
            </div>
          ) : (
            <>
            <div className="ledger-label -mx-3.5 flex items-center gap-4 border-b border-white/10 px-3.5 pb-2">
              <span className="w-9 text-right">No.</span>
              <span className="flex-1">Entry</span>
              <span className="w-[132px] text-right">Amount · date</span>
              <span className="w-[13px]" aria-hidden />
            </div>
            {shown.map((expense, i) => {
              const isOpen = open === expense.id
              const label = expense.note || categoryLabel(categories, expense.categoryId)
              return (
                <div key={expense.id} className="border-b border-white/[0.06] last:border-b-0">
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
                    className="-mx-3.5 flex cursor-pointer items-center gap-4 rounded-lg px-3.5 py-3.5 transition-colors hover:bg-[#c79a5b]/[0.07]"
                  >
                    <div className="text-muted-foreground/70 tnum w-9 flex-none text-right font-mono text-[12px]">
                      {String(i + 1).padStart(3, "0")}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[15px] font-medium">{label}</div>
                      <div className="text-muted-foreground text-[13px]">
                        {categoryLabel(categories, expense.categoryId)}
                      </div>
                    </div>
                    <div className="flex items-center gap-3.5">
                      <div className="w-[132px] text-right">
                        <div className="tnum text-foreground font-mono text-[15px]">
                          −{formatRon(expense.amount)}
                        </div>
                        <div className="tnum text-muted-foreground mt-0.5 font-mono text-[12px]">
                          {formatDate(expense.expenseDate)}
                        </div>
                      </div>
                      <span
                        className="text-[19px] transition-transform"
                        style={{ color: "#B0A18F", transform: `rotate(${isOpen ? 90 : 0}deg)` }}
                      >
                        ›
                      </span>
                    </div>
                  </div>
                  {isOpen && (
                    <div
                      className="-mx-3.5 mb-3.5 flex flex-wrap gap-6 rounded-2xl border border-white/[0.07] bg-[#241C17] p-4.5"
                      style={{ animation: "riseIn .16s ease" }}
                    >
                      {[
                        ["CATEGORY", categoryLabel(categories, expense.categoryId)],
                        ["POSTED", formatDate(expense.expenseDate) + " " + expense.expenseDate.slice(0, 4)],
                        ["NOTE", expense.note || "—"],
                        ["AMOUNT", formatRon(expense.amount)],
                      ].map(([label, value]) => (
                        <div key={label} className="min-w-[120px]">
                          <div className="ledger-label">{label}</div>
                          <div className="tnum mt-1.5 text-[14.5px]">{value}</div>
                        </div>
                      ))}
                      <div className="flex flex-1 items-end justify-end gap-2.5">
                        <button
                          onClick={() => duplicate(expense)}
                          className="text-foreground/85 cursor-pointer rounded-[11px] border border-white/15 bg-white/[0.05] px-3.5 py-2 text-[13px] hover:border-white/30"
                        >
                          Add again
                        </button>
                        <button
                          onClick={() => startEdit(expense)}
                          className="text-foreground/85 cursor-pointer rounded-[11px] border border-white/15 bg-white/[0.05] px-3.5 py-2 text-[13px] hover:border-white/30"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => remove(expense.id)}
                          className="text-destructive cursor-pointer rounded-[11px] border border-[#c96a4e]/40 bg-[#c96a4e]/10 px-3.5 py-2 text-[13px] hover:bg-[#c96a4e]/20"
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )
            })}
            <div className="-mx-3.5 mt-1 flex items-center gap-4 border-t border-white/10 px-3.5 pt-3 text-[13.5px] font-semibold">
              <span className="w-9" aria-hidden />
              <span className="flex-1">
                Total shown · {shown.length} entr{shown.length === 1 ? "y" : "ies"}
              </span>
              <span className="tnum w-[132px] text-right font-mono">
                −{formatRon(shownTotal).replace(/\s?RON$/, "")}
              </span>
              <span className="w-[13px]" aria-hidden />
            </div>
            </>
          )}
        </section>

        {/* Date-range / category server filters */}
        <details className="group">
          <summary className="ledger-label cursor-pointer list-none py-1 select-none">
            <span className="group-open:hidden">▸ Filter by date &amp; category</span>
            <span className="hidden group-open:inline">▾ Filter by date &amp; category</span>
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
        <section className="bg-card rounded-2xl border border-white/[0.07] p-6">
          <h2 className="text-[17px] font-semibold">This month</h2>
          <div className="mt-5 flex gap-5">
            <div className="flex-1">
              <div className="text-muted-foreground text-[13.5px]">Spent</div>
              <div className="font-heading tnum mt-1.5 text-[28px] font-semibold tracking-tight">
                {formatRon(monthTotal).replace(/\s?RON$/, "")}
              </div>
            </div>
            <div className="flex-1 text-right">
              <div className="text-muted-foreground text-[13.5px]">Largest</div>
              <div className="font-heading tnum mt-1.5 text-[28px] font-semibold tracking-tight">
                {formatRon(monthMax).replace(/\s?RON$/, "")}
              </div>
            </div>
          </div>
          <div className="text-muted-foreground mt-5 border-t border-white/[0.07] pt-4.5 text-[13.5px]">
            {total} transactions total
          </div>
        </section>

        {monthByCat.length > 0 && (
          <section className="bg-card rounded-2xl border border-white/[0.07] p-6">
            <h2 className="mb-5 text-[17px] font-semibold">Top categories</h2>
            <div className="flex flex-col gap-4.5">
              {monthByCat.map((cat, i) => (
                <div key={cat.name}>
                  <div className="flex justify-between text-[14.5px]">
                    <span className="font-medium">{cat.name}</span>
                    <span className="tnum font-mono">{formatRon(cat.amount)}</span>
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
          className={`bg-card rounded-2xl border p-6 ${editing ? "border-primary/50" : "border-white/[0.07]"}`}
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
                      className={`cursor-pointer rounded-full border px-3 py-1.5 text-[12.5px] transition-colors ${
                        active
                          ? "border-primary bg-primary/15 text-foreground"
                          : "border-white/12 bg-white/[0.05] hover:border-primary"
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
                className={`${rowField} tnum font-mono`}
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
                className="bg-primary text-primary-foreground flex-1 cursor-pointer rounded-xl border-none py-3 text-[14px] font-semibold hover:bg-[#D8B27A]"
              >
                {editing ? "Save changes" : "Add expense"}
              </button>
              {editing && (
                <button
                  type="button"
                  onClick={cancelEdit}
                  className="text-foreground cursor-pointer rounded-xl border border-white/15 bg-transparent px-4 py-3 text-[14px] hover:bg-white/[0.06]"
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
          <section className="bg-card rounded-2xl border border-white/[0.07] p-6">
            <h2 className="text-[17px] font-semibold">Recurring monthly</h2>
            <p className="text-muted-foreground mt-1 text-[13px]">
              Posted automatically — no typing required.
            </p>
            <ul className="mt-4 space-y-2.5">
              {recurring.map((r) => (
                <li key={r.id} className="flex items-center gap-3">
                  <div className="grid size-9 flex-none place-items-center rounded-lg bg-[#241C17] font-mono text-[12px] text-[#CFC1AE]">
                    {monogram(r.note || topCategoryName(categories, r.categoryId))}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[13.5px] font-medium">
                      {r.note || categoryLabel(categories, r.categoryId)}
                    </p>
                    <p className="text-muted-foreground text-[11.5px]">day {r.dayOfMonth} · next {r.nextRun}</p>
                  </div>
                  <span className="tnum font-mono text-[13.5px] font-medium">{formatRon(r.amount)}</span>
                  <button
                    onClick={() => deleteRecurring(r.id).then(reload)}
                    aria-label="Remove recurring"
                    className="text-muted-foreground hover:text-foreground cursor-pointer px-1"
                  >
                    ✕
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )}

        <details className="group">
          <summary className="ledger-label cursor-pointer list-none py-1 select-none">
            <span className="group-open:hidden">▸ Manage categories</span>
            <span className="hidden group-open:inline">▾ Manage categories</span>
          </summary>
          <div className="pt-2">
            <CategoriesTab categories={categories} onChanged={onCategoriesChanged} />
          </div>
        </details>
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
