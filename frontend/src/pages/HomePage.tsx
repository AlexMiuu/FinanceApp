import { useCallback, useEffect, useState } from "react"
import { getCategories, type Category } from "@/lib/api"
import { TopNav, type PageKey } from "@/components/TopNav"
import DashboardTab from "@/pages/DashboardTab"
import ExpensesTab from "@/pages/ExpensesTab"
import ReportsTab from "@/pages/ReportsTab"
import ProfileTab from "@/pages/ProfileTab"
import QuestsTab from "@/pages/QuestsTab"

export default function HomePage() {
  const [page, setPage] = useState<PageKey>("dashboard")
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
    <div className="min-h-svh">
      <TopNav page={page} onNavigate={setPage} />
      <main className="mx-auto w-full max-w-[1100px] px-5 py-7 sm:px-8 sm:pb-10">
        {page === "dashboard" && (
          <DashboardTab onNavigate={(p) => setPage(p as PageKey)} />
        )}
        {page === "expenses" && (
          <ExpensesTab categories={categories} onCategoriesChanged={reloadCategories} />
        )}
        {page === "reports" && <ReportsTab categories={categories} />}
        {page === "quests" && <QuestsTab categories={categories} />}
        {page === "profile" && <ProfileTab />}
      </main>
    </div>
  )
}
