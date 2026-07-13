import { useState, type FormEvent } from "react"
import {
  createCategory,
  deleteCategory,
  updateCategory,
  type Category,
} from "@/lib/api"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

export default function CategoriesTab({
  categories,
  onChanged,
}: {
  categories: Category[]
  onChanged: () => void
}) {
  const [error, setError] = useState<string | null>(null)
  const [parentId, setParentId] = useState<string>("none")
  const [mandatory, setMandatory] = useState(false)

  const parents = categories.filter((c) => c.parentId === null)
  const childrenOf = (id: string) => categories.filter((c) => c.parentId === id)

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    const form = e.currentTarget // React nulls currentTarget after awaits
    const data = new FormData(form)
    try {
      await createCategory({
        name: String(data.get("name")),
        parentId: parentId === "none" ? null : parentId,
        isMandatory: mandatory,
      })
      form.reset()
      setMandatory(false)
      setParentId("none")
      onChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Create failed")
    }
  }

  async function toggleMandatory(category: Category) {
    setError(null)
    try {
      await updateCategory(category.id, {
        name: category.name,
        isMandatory: !category.isMandatory,
      })
      onChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Update failed")
    }
  }

  async function remove(id: string) {
    setError(null)
    try {
      await deleteCategory(id)
      onChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed")
    }
  }

  function CategoryRow({ category, indent }: { category: Category; indent: boolean }) {
    return (
      <div
        className={`flex items-center justify-between gap-2 rounded-md px-2 py-1.5 ${indent ? "ml-6" : ""}`}
      >
        <div className="flex items-center gap-2">
          <span className={indent ? "text-sm" : "text-sm font-medium"}>{category.name}</span>
          {category.isMandatory && <Badge variant="secondary">mandatory</Badge>}
        </div>
        <div className="space-x-1">
          <Button variant="ghost" size="sm" onClick={() => toggleMandatory(category)}>
            {category.isMandatory ? "Optional" : "Mandatory"}
          </Button>
          <Button variant="ghost" size="sm" onClick={() => remove(category.id)}>
            Delete
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Add category</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-3" onSubmit={submit}>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="cat-name">Name</Label>
                <Input id="cat-name" name="name" required maxLength={60} />
              </div>
              <div className="space-y-1.5">
                <Label>Parent (optional)</Label>
                <Select value={parentId} onValueChange={setParentId}>
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">None — top level</SelectItem>
                    {parents.map((p) => (
                      <SelectItem key={p.id} value={p.id}>
                        {p.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Checkbox
                id="cat-mandatory"
                checked={mandatory}
                onCheckedChange={(v) => setMandatory(v === true)}
              />
              <Label htmlFor="cat-mandatory">
                Mandatory expense (rent, utilities…) — used to tailor quests
              </Label>
            </div>
            <Button type="submit">Add category</Button>
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
          <CardTitle className="text-base">Your categories</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1">
          {parents.length === 0 && (
            <p className="text-muted-foreground py-4 text-center text-sm">No categories yet.</p>
          )}
          {parents.map((parent) => (
            <div key={parent.id}>
              <CategoryRow category={parent} indent={false} />
              {childrenOf(parent.id).map((child) => (
                <CategoryRow key={child.id} category={child} indent />
              ))}
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  )
}
