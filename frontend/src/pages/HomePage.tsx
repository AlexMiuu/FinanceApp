import { useCallback, useEffect, useState } from "react"
import { useAuth } from "@/auth/AuthContext"
import { getCategories, type Category } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import DashboardTab from "@/pages/DashboardTab"
import ExpensesTab from "@/pages/ExpensesTab"
import CategoriesTab from "@/pages/CategoriesTab"
import ReportsTab from "@/pages/ReportsTab"

export default function HomePage() {
  const { user, logout } = useAuth()
  const [categories, setCategories] = useState<Category[]>([])

  const reloadCategories = useCallback(() => {
    getCategories()
      .then(setCategories)
      .catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    reloadCategories()
  }, [reloadCategories])

  return (
    <main className="mx-auto flex min-h-svh max-w-3xl flex-col gap-6 p-4 sm:p-8">
      <header className="flex items-start justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">Personal Finance App</h1>
          <p className="text-muted-foreground text-sm">
            Signed in as {user?.displayName} ({user?.email})
          </p>
        </div>
        <Button variant="outline" onClick={logout}>
          Sign out
        </Button>
      </header>

      <Tabs defaultValue="dashboard">
        <TabsList>
          <TabsTrigger value="dashboard">Dashboard</TabsTrigger>
          <TabsTrigger value="expenses">Expenses</TabsTrigger>
          <TabsTrigger value="categories">Categories</TabsTrigger>
          <TabsTrigger value="reports">Reports</TabsTrigger>
        </TabsList>
        <TabsContent value="dashboard" className="pt-4">
          <DashboardTab />
        </TabsContent>
        <TabsContent value="expenses" className="pt-4">
          <ExpensesTab categories={categories} />
        </TabsContent>
        <TabsContent value="categories" className="pt-4">
          <CategoriesTab categories={categories} onChanged={reloadCategories} />
        </TabsContent>
        <TabsContent value="reports" className="pt-4">
          <ReportsTab categories={categories} />
        </TabsContent>
      </Tabs>
    </main>
  )
}
