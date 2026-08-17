import { ArgaliMark } from "@/components/brand"
import { SalaryCalculator } from "@/components/SalaryCalculator"
import { navigate } from "@/lib/route"

/**
 * The calculator, offered without an account.
 *
 * This is the one page a stranger can land on, so it says plainly what Argali is
 * and what it refuses to do. No claim here should promise more than the app does:
 * it records money, it never holds or moves it.
 */
export default function SalaryCalculatorPage() {
  return (
    <main className="bg-background text-foreground min-h-svh">
      <header className="border-border border-b">
        <div className="mx-auto flex max-w-[980px] flex-wrap items-center justify-between gap-3 px-5 py-4 sm:px-8">
          <button
            onClick={() => navigate("/")}
            className="flex cursor-pointer items-center gap-2.5 border-none bg-transparent p-0 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
            aria-label="Argali — go to the app"
          >
            <ArgaliMark className="text-primary size-[26px]" strokeWidth={7} />
            <span className="font-heading text-[19px] font-semibold">Argali</span>
          </button>
          <button
            onClick={() => navigate("/")}
            className="status-tag border-border hover:border-[#4C93A6] hover:text-foreground text-muted-foreground min-h-11 cursor-pointer border bg-transparent px-3.5 normal-case tracking-[0.06em] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
          >
            Sign in
          </button>
        </div>
      </header>

      <div className="mx-auto max-w-[980px] px-5 py-10 sm:px-8 sm:py-14">
        <div className="ledger-label">Romania · {new Date().getFullYear()} rates</div>
        <h1 className="font-heading mt-2 max-w-[620px] text-[30px] leading-[1.15] font-semibold tracking-[-0.01em] sm:text-[38px]">
          What lands in your account, and what never gets there.
        </h1>
        <p className="text-muted-foreground mt-4 max-w-[560px] text-[15px] text-pretty">
          Gross to net, or net to gross, with CAS, CASS and income tax shown separately rather
          than rolled into one number. Nothing is saved, and no account is needed.
        </p>

        <div className="mt-9 grid gap-[22px] lg:grid-cols-[minmax(0,1fr)_320px] lg:items-start">
          <section className="ledger-card p-6">
            <SalaryCalculator />
          </section>

          <aside className="ledger-card p-6">
            <div className="ledger-label">What Argali is</div>
            <p className="text-muted-foreground mt-3.5 text-[14px] text-pretty">
              A ledger for your own money, kept the way a shepherd keeps a tally stick — one
              notch per day, counted rather than scored. No points, no badges, no leaderboards.
            </p>
            <p className="text-muted-foreground mt-3.5 text-[14px] text-pretty">
              It records what you tell it. It never holds your money, never moves it, and never
              connects to your bank to do either.
            </p>
            <button
              onClick={() => navigate("/")}
              className="mt-5 min-h-11 w-full cursor-pointer border border-[#4C93A6] bg-[#123945] px-4 py-2.5 text-[14px] font-medium text-[#C4E7F0] hover:bg-[#174756] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
            >
              Create an account
            </button>
            <p className="text-muted-foreground mt-3 text-[12.5px]">
              The calculator above works without one.
            </p>
          </aside>
        </div>
      </div>
    </main>
  )
}
