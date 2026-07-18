import { useCallback, useEffect, useState } from "react"
import { useAuth } from "@/auth/AuthContext"
import { getCategories, type Category } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import DashboardTab from "@/pages/DashboardTab"
import ExpensesTab from "@/pages/ExpensesTab"
import CategoriesTab from "@/pages/CategoriesTab"
import ReportsTab from "@/pages/ReportsTab"
import ProfileTab from "@/pages/ProfileTab"
import GoalsTab from "@/pages/GoalsTab"
import QuestsTab from "@/pages/QuestsTab"
import { NotificationsBell } from "@/components/NotificationsBell"

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
        <div className="flex items-center gap-2">
          <NotificationsBell />
          <Button variant="outline" onClick={logout}>
            Sign out
          </Button>
        </div>
      </header>

      <Tabs defaultValue="dashboard">
        <TabsList>
          <TabsTrigger value="dashboard">Dashboard</TabsTrigger>
          <TabsTrigger value="expenses">Expenses</TabsTrigger>
          <TabsTrigger value="categories">Categories</TabsTrigger>
          <TabsTrigger value="goals">Goals</TabsTrigger>
          <TabsTrigger value="quests">Quests</TabsTrigger>
          <TabsTrigger value="reports">Reports</TabsTrigger>
          <TabsTrigger value="profile">Profile</TabsTrigger>
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
        <TabsContent value="goals" className="pt-4">
          <GoalsTab categories={categories} />
        </TabsContent>
        <TabsContent value="quests" className="pt-4">
          <QuestsTab />
        </TabsContent>
        <TabsContent value="reports" className="pt-4">
          <ReportsTab categories={categories} />
        </TabsContent>
        <TabsContent value="profile" className="pt-4">
          <ProfileTab />
        </TabsContent>
      </Tabs>
    </main>
  )
}
