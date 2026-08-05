import { useEffect, useState } from "react"
import {
  listNotifications,
  markAllNotificationsRead,
  type AppNotification,
} from "@/lib/api"
import { connectNotifications } from "@/lib/ws"
import { BellIcon } from "@/components/brand"
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
        <button
          className="bg-card text-foreground/85 relative grid size-11 cursor-pointer place-items-center rounded-xl border border-white/[0.08] transition-colors hover:bg-[#241C17] hover:text-foreground"
          aria-label={unread > 0 ? `Notifications — ${unread} unread` : "Notifications"}
          title={unread > 0 ? `Notifications — ${unread} unread` : "Notifications"}
        >
          <BellIcon />
          {unread > 0 && (
            <span className="bg-destructive border-card absolute right-2 top-2 size-2 rounded-full border-[1.5px]" />
          )}
        </button>
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
