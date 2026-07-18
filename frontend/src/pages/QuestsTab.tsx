import { useCallback, useEffect, useState } from "react"
import {
  acceptQuest,
  declineQuest,
  formatRon,
  listQuests,
  type Quest,
} from "@/lib/api"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"

function progressLabel(quest: Quest): string {
  if (quest.kind === "DAYS") {
    return `${quest.progress} of ${quest.target} no-spend days`
  }
  return `${formatRon(quest.progress)} of ${formatRon(quest.target)}`
}

function QuestCard({
  quest,
  onAccept,
  onDecline,
}: {
  quest: Quest
  onAccept?: (id: string) => void
  onDecline?: (id: string) => void
}) {
  const pct = quest.target > 0 ? Math.min(100, (quest.progress / quest.target) * 100) : 0
  const overCap = quest.kind === "CAP" && quest.progress > quest.target

  return (
    <div className="space-y-2 rounded-lg border p-3">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-sm font-medium">{quest.title}</p>
          <p className="text-muted-foreground text-xs">
            {quest.periodStart} → {quest.periodEnd}
          </p>
        </div>
        {quest.status === "COMPLETED" && <Badge className="bg-[#0ca30c]">✓ completed</Badge>}
        {quest.status === "FAILED" && <Badge variant="destructive">✗ failed</Badge>}
        {quest.status === "ACTIVE" && <Badge variant="secondary">active</Badge>}
        {quest.status === "SUGGESTED" && <Badge variant="outline">suggested</Badge>}
      </div>

      {(quest.status === "ACTIVE" || quest.status === "COMPLETED" || quest.status === "FAILED") && (
        <div className="space-y-1">
          <Progress value={pct} aria-label="Quest progress" />
          <p className={`text-xs ${overCap ? "text-destructive font-medium" : "text-muted-foreground"}`}>
            {progressLabel(quest)}
          </p>
        </div>
      )}

      {quest.status === "SUGGESTED" && onAccept && onDecline && (
        <div className="flex gap-2">
          <Button size="sm" onClick={() => onAccept(quest.id)}>
            Accept
          </Button>
          <Button size="sm" variant="outline" onClick={() => onDecline(quest.id)}>
            Decline
          </Button>
        </div>
      )}
    </div>
  )
}

export default function QuestsTab() {
  const [quests, setQuests] = useState<Quest[]>([])
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(() => {
    listQuests()
      .then((list) => {
        setQuests(list)
        setError(null)
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load quests"))
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  async function act(action: (id: string) => Promise<unknown>, id: string) {
    try {
      await action(id)
      reload()
    } catch (e) {
      setError(e instanceof Error ? e.message : "Action failed")
    }
  }

  const suggested = quests.filter((q) => q.status === "SUGGESTED")
  const active = quests.filter((q) => q.status === "ACTIVE")
  const finished = quests.filter((q) => q.status === "COMPLETED" || q.status === "FAILED")

  return (
    <div className="space-y-6">
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Suggested for you</CardTitle>
          <CardDescription>
            Tailored from your spending history, income, and mandatory expenses. New
            suggestions appear each week and month.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-2">
          {suggested.length === 0 ? (
            <p className="text-muted-foreground py-2 text-center text-sm">
              No new suggestions right now — add a few expenses and check back.
            </p>
          ) : (
            suggested.map((quest) => (
              <QuestCard
                key={quest.id}
                quest={quest}
                onAccept={(id) => act(acceptQuest, id)}
                onDecline={(id) => act(declineQuest, id)}
              />
            ))
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Active quests</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {active.length === 0 ? (
            <p className="text-muted-foreground py-2 text-center text-sm">
              Accept a suggestion to start tracking.
            </p>
          ) : (
            active.map((quest) => <QuestCard key={quest.id} quest={quest} />)
          )}
        </CardContent>
      </Card>

      {finished.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">History</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {finished.map((quest) => (
              <QuestCard key={quest.id} quest={quest} />
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  )
}
