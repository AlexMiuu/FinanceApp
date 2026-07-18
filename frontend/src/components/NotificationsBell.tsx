import { useEffect, useState } from "react"
import {
  listNotifications,
  markAllNotificationsRead,
  type AppNotification,
} from "@/lib/api"
import { connectNotifications } from "@/lib/ws"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"

const TYPE_ICON: Record<string, string> = {
  "quest.completed": "🎉",
  "quest.failed": "✗",
  "quest.suggested": "✦",
}

export function NotificationsBell() {
  const [items, setItems] = useState<AppNotification[]>([])
  const [unread, setUnread] = useState(0)

  useEffect(() => {
    listNotifications()
      .then((list) => {
        setItems(list.items)
        setUnread(list.unread)
      })
      .catch(() => {})

    // Live pushes prepend and bump the badge without any reload.
    return connectNotifications((notification) => {
      setItems((prev) => [notification, ...prev].slice(0, 50))
      setUnread((prev) => prev + 1)
    })
  }, [])

  async function readAll() {
    try {
      await markAllNotificationsRead()
      setUnread(0)
      setItems((prev) => prev.map((n) => ({ ...n, read: true })))
    } catch {
      /* non-fatal */
    }
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" className="relative" aria-label="Notifications">
          🔔
          {unread > 0 && (
            <Badge className="absolute -right-2 -top-2 h-5 min-w-5 justify-center rounded-full px-1">
              {unread}
            </Badge>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80 p-0">
        <div className="flex items-center justify-between border-b p-3">
          <p className="text-sm font-semibold">Notifications</p>
          {unread > 0 && (
            <Button variant="ghost" size="sm" onClick={readAll}>
              Mark all read
            </Button>
          )}
        </div>
        <ul className="max-h-80 overflow-y-auto">
          {items.length === 0 && (
            <li className="text-muted-foreground p-4 text-center text-sm">
              Nothing yet. Quest updates will appear here live.
            </li>
          )}
          {items.map((n) => (
            <li
              key={n.id}
              className={`border-b p-3 text-sm last:border-b-0 ${n.read ? "opacity-60" : ""}`}
            >
              <p className="font-medium">
                {TYPE_ICON[n.type] ?? "•"} {n.title}
              </p>
              <p className="text-muted-foreground">{n.body}</p>
              <p className="text-muted-foreground pt-0.5 text-xs">
                {new Date(n.createdAt).toLocaleString()}
              </p>
            </li>
          ))}
        </ul>
      </PopoverContent>
    </Popover>
  )
}
