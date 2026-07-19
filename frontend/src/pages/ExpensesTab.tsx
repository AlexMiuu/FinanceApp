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
import { lastUsedCategory, recordCategoryUse, topCategories } from "@/lib/categoryUsage"
import { CategorySelect } from "@/components/CategorySelect"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"

const today = () => new Date().toISOString().slice(0, 10)

function categoryLabel(categories: Category[], id: string): string {
  const category = categories.find((c) => c.id === id)
  if (!category) return "—"
  if (!category.parentId) return category.name
  const parent = categories.find((c) => c.id === category.parentId)
  return parent ? `${parent.name} › ${category.name}` : category.name
}

function ExpenseFields({
  categories,
  categoryId,
  setCategoryId,
  defaults,
  amountRef,
}: {
  categories: Category[]
  categoryId: string | undefined
  setCategoryId: (id: string | undefined) => void
  defaults?: Expense
  amountRef?: React.RefObject<HTMLInputElement | null>
}) {
  return (
    <>
      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-1.5">
          <Label htmlFor="amount">Amount (RON)</Label>
          <Input
            id="amount"
            name="amount"
            type="number"
            step="0.01"
            min="0.01"
            required
            ref={amountRef}
            defaultValue={defaults ? (defaults.amount / 100).toFixed(2) : ""}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="expenseDate">Date</Label>
          <Input
            id="expenseDate"
            name="expenseDate"
            type="date"
            required
            defaultValue={defaults?.expenseDate ?? today()}
          />
        </div>
      </div>
      <div className="space-y-1.5">
        <Label>Category</Label>
        <CategorySelect categories={categories} value={categoryId} onChange={setCategoryId} />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="note">Note (optional)</Label>
        <Input id="note" name="note" defaultValue={defaults?.note ?? ""} />
      </div>
    </>
  )
}

export default function ExpensesTab({ categories }: { categories: Category[] }) {
  const { user } = useAuth()
  const userId = user?.id ?? "anon"

  const [expenses, setExpenses] = useState<Expense[]>([])
  const [recurring, setRecurring] = useState<RecurringExpense[]>([])
  const [total, setTotal] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)

  const [newCategoryId, setNewCategoryId] = useState<string | undefined>(
    () => lastUsedCategory(userId) ?? undefined
  )
  const [repeatMonthly, setRepeatMonthly] = useState(false)
  const [editing, setEditing] = useState<Expense | null>(null)
  const [editCategoryId, setEditCategoryId] = useState<string>()
  const amountRef = useRef<HTMLInputElement | null>(null)

  const [filterFrom, setFilterFrom] = useState("")
  const [filterTo, setFilterTo] = useState("")
  const [filterCategory, setFilterCategory] = useState<string>()

  // One-tap chips: most-used categories that still exist.
  const chips = topCategories(userId, 5)
    .map((id) => categories.find((c) => c.id === id))
    .filter((c): c is Category => c !== undefined)

  const reload = useCallback(async () => {
    try {
      const [page, recurringList] = await Promise.all([
        listExpenses({
          from: filterFrom || undefined,
          to: filterTo || undefined,
          categoryId: filterCategory,
        }),
        listRecurring(),
      ])
      setExpenses(page.items)
      setTotal(page.totalElements)
      setRecurring(recurringList)
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load expenses")
    }
  }, [filterFrom, filterTo, filterCategory])

  useEffect(() => {
    reload()
  }, [reload])

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setInfo(null)
    const form = e.currentTarget // React nulls currentTarget after awaits
    const data = new FormData(form)
    const categoryId = editing ? editCategoryId : newCategoryId
    if (!categoryId) {
      setError("Pick a category")
      return
    }
    const body = {
      amount: Math.round(parseFloat(String(data.get("amount"))) * 100),
      categoryId,
      note: String(data.get("note")) || null,
      expenseDate: String(data.get("expenseDate")),
    }
    try {
      if (editing) {
        await updateExpense(editing.id, body)
        setEditing(null)
      } else if (repeatMonthly) {
        await createRecurring({ ...body, startDate: body.expenseDate })
        setRepeatMonthly(false)
        setInfo(`Recurring expense created — it posts automatically on day ${Number(body.expenseDate.slice(8, 10))} of every month.`)
        form.reset()
      } else {
        await createExpense(body)
        form.reset()
      }
      recordCategoryUse(userId, categoryId)
      amountRef.current?.focus()
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed")
    }
  }

  async function duplicate(expense: Expense) {
    setError(null)
    try {
      await createExpense({
        amount: expense.amount,
        categoryId: expense.categoryId,
        note: expense.note,
        expenseDate: today(),
      })
      recordCategoryUse(userId, expense.categoryId)
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Duplicate failed")
    }
  }

  async function remove(id: string) {
    setError(null)
    try {
      await deleteExpense(id)
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed")
    }
  }

  async function removeRecurring(id: string) {
    setError(null)
    try {
      await deleteRecurring(id)
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed")
    }
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Add expense</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-3" onSubmit={submit}>
            {chips.length > 0 && (
              <div className="flex flex-wrap gap-1.5" aria-label="Frequent categories">
                {chips.map((c) => (
                  <Button
                    key={c.id}
                    type="button"
                    size="sm"
                    variant={newCategoryId === c.id ? "default" : "outline"}
                    onClick={() => setNewCategoryId(c.id)}
                  >
                    {categoryLabel(categories, c.id)}
                  </Button>
                ))}
              </div>
            )}
            <ExpenseFields
              categories={categories}
              categoryId={newCategoryId}
              setCategoryId={setNewCategoryId}
              amountRef={amountRef}
            />
            <div className="flex items-center gap-2">
              <Checkbox
                id="repeat"
                checked={repeatMonthly}
                onCheckedChange={(v) => setRepeatMonthly(v === true)}
              />
              <Label htmlFor="repeat" className="font-normal">
                Repeat monthly from this date (rent, subscriptions…) — past months are
                filled in automatically
              </Label>
            </div>
            <Button type="submit">{repeatMonthly ? "Add recurring expense" : "Add expense"}</Button>
          </form>
        </CardContent>
      </Card>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      {info && (
        <Alert>
          <AlertDescription>{info}</AlertDescription>
        </Alert>
      )}

      {recurring.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Recurring monthly</CardTitle>
            <CardDescription>Posted automatically — no typing required.</CardDescription>
          </CardHeader>
          <CardContent>
            <ul className="space-y-1.5">
              {recurring.map((r) => (
                <li key={r.id} className="flex items-center justify-between gap-2 text-sm">
                  <span className="flex items-center gap-2">
                    {categoryLabel(categories, r.categoryId)}
                    {r.note && <span className="text-muted-foreground">· {r.note}</span>}
                    <Badge variant="secondary">day {r.dayOfMonth}</Badge>
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="font-medium">{formatRon(r.amount)}</span>
                    <span className="text-muted-foreground text-xs">next {r.nextRun}</span>
                    <Button variant="ghost" size="sm" onClick={() => removeRecurring(r.id)}>
                      Delete
                    </Button>
                  </span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">
            Expenses <span className="text-muted-foreground font-normal">({total})</span>
          </CardTitle>
          <div className="grid gap-2 pt-2 sm:grid-cols-3">
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
        </CardHeader>
        <CardContent>
          {expenses.length === 0 ? (
            <p className="text-muted-foreground py-4 text-center text-sm">
              No expenses yet. Add your first one above.
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Note</TableHead>
                  <TableHead className="text-right">Amount</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {expenses.map((expense) => (
                  <TableRow key={expense.id}>
                    <TableCell>{expense.expenseDate}</TableCell>
                    <TableCell>{categoryLabel(categories, expense.categoryId)}</TableCell>
                    <TableCell className="text-muted-foreground max-w-40 truncate">
                      {expense.note}
                    </TableCell>
                    <TableCell className="text-right font-medium">
                      {formatRon(expense.amount)}
                    </TableCell>
                    <TableCell className="space-x-1 text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        title="Add the same expense again, dated today"
                        onClick={() => duplicate(expense)}
                      >
                        Again
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => {
                          setEditing(expense)
                          setEditCategoryId(expense.categoryId)
                        }}
                      >
                        Edit
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => remove(expense.id)}>
                        Delete
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Edit expense</DialogTitle>
          </DialogHeader>
          {editing && (
            <form className="space-y-3" onSubmit={submit}>
              <ExpenseFields
                categories={categories}
                categoryId={editCategoryId}
                setCategoryId={setEditCategoryId}
                defaults={editing}
              />
              <Button type="submit" className="w-full">
                Save changes
              </Button>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}
