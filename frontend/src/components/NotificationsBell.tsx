import { useEffect, useState } from "react"
import {
  listNotifications,
  markAllNotificationsRead,
  type AppNotification,
} from "@/lib/api"
import { BellIcon } from "@/components/brand"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"

// A small status dot stands in for each notification type — one hand, no emoji.
const TYPE_DOT: Record<string, string> = {
  "quest.completed": "var(--good)",
  "quest.failed": "var(--destructive)",
  "quest.suggested": "var(--primary)",
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

    // Live pushes prepend and bump the badge without any reload. The STOMP client
    // is pulled in dynamically so its ~40kB stays off the first paint — nothing is
    // rendered from it, and a few hundred ms before the socket opens costs nothing.
    let disconnect: (() => void) | null = null
    let cancelled = false

    import("@/lib/ws").then(({ connectNotifications }) => {
      if (cancelled) return
      disconnect = connectNotifications((notification) => {
        setItems((prev) => [notification, ...prev].slice(0, 50))
        setUnread((prev) => prev + 1)
      })
    })

    return () => {
      cancelled = true
      disconnect?.()
    }
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
          className="text-muted-foreground border-border hover:border-[#4C93A6] relative grid size-11 cursor-pointer place-items-center border bg-transparent transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
          aria-label={unread > 0 ? `Notifications — ${unread} unread` : "Notifications"}
          title={unread > 0 ? `Notifications — ${unread} unread` : "Notifications"}
        >
          <BellIcon />
          {unread > 0 && (
            <span className="bg-primary text-primary-foreground border-sidebar absolute -right-1.5 -top-1.5 flex h-[16px] min-w-[16px] items-center justify-center border px-1 font-mono text-[10px] leading-none">
              {unread > 9 ? "9+" : unread}
            </span>
          )}
        </button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        className="bg-popover border-border w-[360px] rounded-none p-0 shadow-[0_12px_24px_rgba(0,0,0,0.4)]"
      >
        <div className="flex items-center justify-between border-b p-3">
          <p className="ledger-label text-[10.5px]">Notifications</p>
          {unread > 0 && (
            <button
              onClick={readAll}
              className="text-primary min-h-9 cursor-pointer px-2 text-[12.5px] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
            >
              Mark all read
            </button>
          )}
        </div>
        <ul className="max-h-80 overflow-y-auto">
          {items.length === 0 && (
            <li className="flex flex-col items-start gap-1.5 p-5 text-left">
              <p className="font-heading text-[15px] font-semibold">Nothing to tell you</p>
              <p className="text-muted-foreground text-[13px] text-pretty">
                Argali speaks up when a pace changes or an oath comes due. Quiet here means quiet
                in the ledger.
              </p>
            </li>
          )}
          {items.map((n) => (
            <li
              key={n.id}
              className={`border-b p-3 text-sm last:border-b-0 ${n.read ? "opacity-60" : ""}`}
            >
              <p className="flex items-center gap-2 font-medium">
                <span
                  className="size-1.5 flex-none rounded-full"
                  style={{ background: TYPE_DOT[n.type] ?? "var(--muted-foreground)" }}
                />
                {n.title}
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
