import { useCallback, useEffect, useState, type FormEvent } from "react"
import {
  createExpense,
  deleteExpense,
  formatRon,
  listExpenses,
  updateExpense,
  type Category,
  type Expense,
} from "@/lib/api"
import { CategorySelect } from "@/components/CategorySelect"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
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
}: {
  categories: Category[]
  categoryId: string | undefined
  setCategoryId: (id: string | undefined) => void
  defaults?: Expense
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
  const [expenses, setExpenses] = useState<Expense[]>([])
  const [total, setTotal] = useState(0)
  const [error, setError] = useState<string | null>(null)

  const [newCategoryId, setNewCategoryId] = useState<string>()
  const [editing, setEditing] = useState<Expense | null>(null)
  const [editCategoryId, setEditCategoryId] = useState<string>()

  const [filterFrom, setFilterFrom] = useState("")
  const [filterTo, setFilterTo] = useState("")
  const [filterCategory, setFilterCategory] = useState<string>()

  const reload = useCallback(async () => {
    try {
      const page = await listExpenses({
        from: filterFrom || undefined,
        to: filterTo || undefined,
        categoryId: filterCategory,
      })
      setExpenses(page.items)
      setTotal(page.totalElements)
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
      } else {
        await createExpense(body)
        form.reset()
      }
      await reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed")
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

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Add expense</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-3" onSubmit={submit}>
            <ExpenseFields
              categories={categories}
              categoryId={newCategoryId}
              setCategoryId={setNewCategoryId}
            />
            <Button type="submit">Add expense</Button>
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
