import { useEffect, useState } from "react"
import { ArgaliMark } from "@/components/brand"

/**
 * The Ledger v6 boot screen: a ghosted horn sweeps in behind the mark, the
 * wordmark rises, and a progress bar fills before the whole overlay fades out.
 * Shown once per session while the shell settles.
 */
export function BootSplash({ onDone }: { onDone: () => void }) {
  const [gone, setGone] = useState(false)

  useEffect(() => {
    const done = setTimeout(onDone, 2800)
    const hide = setTimeout(() => setGone(true), 2800)
    return () => {
      clearTimeout(done)
      clearTimeout(hide)
    }
  }, [onDone])

  if (gone) return null

  return (
    <div
      className="fixed inset-0 z-[200] flex flex-col items-center justify-center gap-[34px] overflow-hidden"
      style={{
        background: "linear-gradient(160deg,#241A12 0%,#160F0B 100%)",
        animation: "bootOut .5s ease 2.3s forwards",
      }}
      role="status"
      aria-label="Loading Argali"
    >
      <div
        className="text-primary absolute"
        style={{
          left: "-9vw",
          top: "-13vh",
          width: "min(720px,68vw)",
          height: "min(720px,68vw)",
          animation: "bootGhost 1.4s ease forwards",
        }}
      >
        <ArgaliMark withEye={false} className="h-full w-full" />
      </div>

      <div
        className="relative h-24 w-24"
        style={{ color: "#F2EDE1", animation: "bootRise .7s ease both" }}
      >
        <ArgaliMark className="h-full w-full" />
      </div>

      <div
        className="relative text-[34px] font-semibold"
        style={{
          letterSpacing: "0.30em",
          color: "#F2EDE1",
          paddingLeft: "0.30em",
          animation: "bootRise .7s ease .12s both",
        }}
      >
        ARGALI
      </div>

      <div
        className="relative flex flex-col items-center gap-4"
        style={{ animation: "fadeIn .8s ease .5s both" }}
      >
        <div className="h-1 w-[190px] overflow-hidden rounded-full bg-white/10">
          <div
            className="bg-primary h-full rounded-full"
            style={{ animation: "bootFill 2.1s cubic-bezier(.35,0,.2,1) both" }}
          />
        </div>
        <div
          className="font-mono text-[12.5px]"
          style={{ letterSpacing: "0.16em", color: "#a89473" }}
        >
          SECURING YOUR ACCOUNT
        </div>
      </div>
    </div>
  )
}
