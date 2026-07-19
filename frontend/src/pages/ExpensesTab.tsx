import { useCallback, useEffect, useRef, useState, type FormEvent } from "react"
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
import { categoryIcon } from "@/lib/icons"
import { lastUsedCategory, recordCategoryUse, topCategories } from "@/lib/categoryUsage"
import { useToast } from "@/components/Toast"
import { CategorySelect } from "@/components/CategorySelect"
import CategoriesTab from "@/pages/CategoriesTab"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
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

const formatDate = (iso: string) => iso.slice(5).split("-").reverse().join(".") + "." + iso.slice(0, 4)

export default function ExpensesTab({
  categories,
  onCategoriesChanged,
}: {
  categories: Category[]
  onCategoriesChanged: () => void
}) {
  const { user } = useAuth()
  const userId = user?.id ?? "anon"
  const toast = useToast()

  const [expenses, setExpenses] = useState<Expense[]>([])
  const [recurring, setRecurring] = useState<RecurringExpense[]>([])
  const [total, setTotal] = useState(0)
  const [monthTotal, setMonthTotal] = useState(0)
  const [monthMax, setMonthMax] = useState(0)
  const [error, setError] = useState<string | null>(null)

  const [editing, setEditing] = useState<Expense | null>(null)
  const [amount, setAmount] = useState("")
  const [note, setNote] = useState("")
  const [date, setDate] = useState(today())
  const [categoryId, setCategoryId] = useState<string | undefined>(
    () => lastUsedCategory(userId) ?? undefined
  )
  const [repeatMonthly, setRepeatMonthly] = useState(false)
  const amountRef = useRef<HTMLInputElement | null>(null)

  const [filterFrom, setFilterFrom] = useState("")
  const [filterTo, setFilterTo] = useState("")
  const [filterCategory, setFilterCategory] = useState<string>()

  const chips = topCategories(userId, 5)
    .map((id) => categories.find((c) => c.id === id))
    .filter((c): c is Category => c !== undefined)

  const reload = useCallback(async () => {
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
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load expenses")
    }
  }, [filterFrom, filterTo, filterCategory])

  useEffect(() => {
    reload()
  }, [reload])

  function startEdit(expense: Expense) {
    setEditing(expense)
    setAmount((expense.amount / 100).toFixed(2))
    setNote(expense.note ?? "")
    setDate(expense.expenseDate)
    setCategoryId(expense.categoryId)
    setRepeatMonthly(false)
    amountRef.current?.focus()
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
        toast("✓", "Expense added — dashboard updated")
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
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed")
    }
  }

  const enterSubmits = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") submit()
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-[22px] font-semibold tracking-tight">Expenses</h1>
        <div className="text-muted-foreground flex gap-4 text-[12.5px]">
          <span>
            This month: <span className="text-foreground font-mono">{formatRon(monthTotal)}</span>
          </span>
          <span>
            Entries: <span className="text-foreground font-mono">{total}</span>
          </span>
          <span>
            Largest: <span className="text-foreground font-mono">{formatRon(monthMax)}</span>
          </span>
        </div>
      </div>

      <Card className={editing ? "border-primary/50" : ""}>
        <CardHeader className="pb-2">
          <CardTitle className="ledger-label font-normal">
            {editing ? "Edit expense" : "Add expense"}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-3" onSubmit={submit}>
            {chips.length > 0 && !editing && (
              <div className="flex flex-wrap gap-1.5" aria-label="Frequent categories">
                {chips.map((c) => (
                  <Button
                    key={c.id}
                    type="button"
                    size="sm"
                    variant={categoryId === c.id ? "default" : "outline"}
                    onClick={() => setCategoryId(c.id)}
                  >
                    {categoryLabel(categories, c.id)}
                  </Button>
                ))}
              </div>
            )}
            <div className="grid items-end gap-3 md:grid-cols-[140px_200px_1fr_150px_auto]">
              <div className="space-y-1.5">
                <Label htmlFor="amount">Amount (RON)</Label>
                <Input
                  id="amount"
                  ref={amountRef}
                  type="number"
                  step="0.01"
                  min="0.01"
                  placeholder="0.00"
                  className="font-mono"
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
                <Input
                  id="note"
                  placeholder="Optional note"
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  onKeyDown={enterSubmits}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="date">Date</Label>
                <Input
                  id="date"
                  type="date"
                  className="font-mono"
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                />
              </div>
              <div className="flex gap-2">
                <Button type="submit">{editing ? "Save" : "Add"}</Button>
                {editing && (
                  <Button type="button" variant="outline" onClick={cancelEdit}>
                    Cancel
                  </Button>
                )}
              </div>
            </div>
            {!editing && (
              <div className="flex items-center gap-2">
                <Checkbox
                  id="repeat"
                  checked={repeatMonthly}
                  onCheckedChange={(v) => setRepeatMonthly(v === true)}
                />
                <Label htmlFor="repeat" className="text-muted-foreground font-normal">
                  Repeat monthly from this date — past months are filled in automatically
                </Label>
              </div>
            )}
          </form>
        </CardContent>
      </Card>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {recurring.length > 0 && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="ledger-label font-normal">Recurring monthly</CardTitle>
            <CardDescription>Posted automatically — no typing required.</CardDescription>
          </CardHeader>
          <CardContent>
            <ul className="space-y-1.5">
              {recurring.map((r) => (
                <li key={r.id} className="flex items-center justify-between gap-2 text-sm">
                  <span className="flex items-center gap-2">
                    <span className="text-base">{categoryIcon(categories, r.categoryId)}</span>
                    {categoryLabel(categories, r.categoryId)}
                    {r.note && <span className="text-muted-foreground">· {r.note}</span>}
                    <Badge variant="secondary">day {r.dayOfMonth}</Badge>
                  </span>
                  <span className="flex items-center gap-2">
                    <span className="font-mono font-medium">{formatRon(r.amount)}</span>
                    <span className="text-muted-foreground font-mono text-xs">next {r.nextRun}</span>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => deleteRecurring(r.id).then(reload)}
                    >
                      ✕
                    </Button>
                  </span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      <Card className="overflow-hidden py-0">
        <div className="text-muted-foreground grid grid-cols-[44px_1.4fr_1.2fr_1fr_110px_96px] items-center gap-3 border-b px-5 py-3 font-mono text-[10.5px] uppercase tracking-[.1em]">
          <span />
          <span>Note</span>
          <span>Category</span>
          <span>Date</span>
          <span className="text-right">Amount</span>
          <span />
        </div>
        <div className="grid gap-2 border-b px-5 py-3 sm:grid-cols-3">
          <Input
            type="date"
            value={filterFrom}
            onChange={(e) => setFilterFrom(e.target.value)}
            aria-label="From date"
          />
          <Input
            type="date"
            value={filterTo}
            onChange={(e) => setFilterTo(e.target.value)}
            aria-label="To date"
          />
          <CategorySelect
            categories={categories}
            value={filterCategory}
            onChange={setFilterCategory}
            allowAll
          />
        </div>
        {expenses.length === 0 ? (
          <p className="text-muted-foreground py-8 text-center text-sm">
            No expenses yet. Add your first one above.
          </p>
        ) : (
          expenses.map((expense) => (
            <div
              key={expense.id}
              className={`group grid grid-cols-[44px_1.4fr_1.2fr_1fr_110px_96px] items-center gap-3 border-b px-5 py-3 last:border-b-0 ${
                editing?.id === expense.id ? "bg-primary/10" : "hover:bg-white/[.025]"
              }`}
            >
              <span className="bg-secondary/50 grid size-8 place-items-center rounded-lg text-sm">
                {categoryIcon(categories, expense.categoryId)}
              </span>
              <div className="min-w-0">
                <p className="truncate text-[13px] font-medium">
                  {expense.note || categoryLabel(categories, expense.categoryId)}
                </p>
              </div>
              <span>
                <Badge variant="outline" className="font-normal">
                  {categoryLabel(categories, expense.categoryId)}
                </Badge>
              </span>
              <span className="text-muted-foreground font-mono text-xs">
                {formatDate(expense.expenseDate)}
              </span>
              <span className="text-right font-mono text-[13px] font-medium">
                −{formatRon(expense.amount)}
              </span>
              <span className="flex justify-end gap-0.5 opacity-40 group-hover:opacity-100">
                <Button
                  variant="ghost"
                  size="sm"
                  title="Add again, dated today"
                  onClick={() => duplicate(expense)}
                >
                  ⟳
                </Button>
                <Button variant="ghost" size="sm" title="Edit" onClick={() => startEdit(expense)}>
                  ✎
                </Button>
                <Button variant="ghost" size="sm" title="Delete" onClick={() => remove(expense.id)}>
                  ✕
                </Button>
              </span>
            </div>
          ))
        )}
      </Card>

      <details className="group">
        <summary className="ledger-label cursor-pointer list-none py-2 select-none">
          <span className="group-open:hidden">▸ Manage categories</span>
          <span className="hidden group-open:inline">▾ Manage categories</span>
        </summary>
        <div className="pt-2">
          <CategoriesTab categories={categories} onChanged={onCategoriesChanged} />
        </div>
      </details>
    </div>
  )
}
