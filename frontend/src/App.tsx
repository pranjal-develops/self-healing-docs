import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type JSX,
} from 'react'
import { motion, AnimatePresence } from 'motion/react'

import { api } from './api'
import { useTheme } from './hooks/useTheme'
import NewModuleForm from './components/NewModuleForm'
import DebtHeatmap, {
  type HeatmapModule,
} from './components/DebtHeatmap'
import DiffView, {
  type DiffResult,
} from './components/DiffView'

type HeatFilter = 'ALL' | 'HIGH' | 'MEDIUM' | 'LOW'

const FILTERS: { key: HeatFilter; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'HIGH', label: 'Critical' },
  { key: 'MEDIUM', label: 'Drifting' },
  { key: 'LOW', label: 'Stable' },
]

export default function App(): JSX.Element {
  const [modules, setModules] = useState<HeatmapModule[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [healResult, setHealResult] = useState<DiffResult | null>(null)
  const [filter, setFilter] = useState<HeatFilter>('ALL')
  const [lastSync, setLastSync] = useState<Date | null>(null)

  const { theme, toggle: toggleTheme } = useTheme()

  const refresh = useCallback(async (): Promise<void> => {
    try {
      setError(null)
      const data: HeatmapModule[] = await api.listModules()
      setModules(data)
      setLastSync(new Date())
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : 'An unknown error occurred',
      )
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const initialRefreshId = window.setTimeout(() => void refresh(), 0)
    const intervalId = window.setInterval(() => void refresh(), 8000)
    return () => {
      window.clearTimeout(initialRefreshId)
      window.clearInterval(intervalId)
    }
  }, [refresh])

  async function handleSimulate(id: string): Promise<void> {
    setBusyId(id)
    try {
      await api.simulate(id, 5)
      await refresh()
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : 'Unable to simulate changes',
      )
    } finally {
      setBusyId(null)
    }
  }

  async function handleHeal(id: string): Promise<void> {
    setBusyId(id)
    try {
      const result: DiffResult = await api.healNow(id)
      setHealResult(result)
      await refresh()
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : 'Unable to heal documentation',
      )
    } finally {
      setBusyId(null)
    }
  }

  const stats = useMemo(() => {
    const total = modules.length
    const critical = modules.filter((m) => m.heatLevel === 'HIGH').length
    const drifting = modules.filter((m) => m.heatLevel === 'MEDIUM').length
    const stable = modules.filter((m) => m.heatLevel === 'LOW').length
    const pending = modules.reduce(
      (sum, m) => sum + m.unprocessedSummaries,
      0,
    )
    return { total, critical, drifting, stable, pending }
  }, [modules])

  const filtered = useMemo(() => {
    if (filter === 'ALL') return modules
    return modules.filter((m) => m.heatLevel === filter)
  }, [modules, filter])

  return (
    <div className="relative min-h-screen bg-canvas-noise text-ink">
      <BlueprintBackdrop />

      <div className="relative z-10 mx-auto w-full max-w-[1400px] px-5 sm:px-8">
        <TopBar theme={theme} onToggleTheme={toggleTheme} />

        <Hero
          lastSync={lastSync}
          onCreated={refresh}
          totalModules={stats.total}
        />

        <StatsStrip
          total={stats.total}
          critical={stats.critical}
          drifting={stats.drifting}
          stable={stats.stable}
          pending={stats.pending}
        />

        <AnimatePresence>
          {error && (
            <motion.div
              key="error"
              role="alert"
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              className="mb-6 border border-[color:var(--color-bad)]/30 bg-[color:var(--color-bad)]/5 px-4 py-3 text-sm"
            >
              <div className="flex items-start gap-3 font-mono">
                <span className="mt-0.5 text-[color:var(--color-bad)]">✕</span>
                <div>
                  <div className="text-xs font-semibold uppercase tracking-widest text-[color:var(--color-bad)]">
                    Backend unreachable
                  </div>
                  <div className="mt-1 text-[color:var(--color-ink-3)]">
                    {error}
                  </div>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        <Toolbar
          filter={filter}
          onFilterChange={setFilter}
          visibleCount={filtered.length}
          totalCount={stats.total}
        />

        <div className="pb-24">
          <AnimatePresence mode="wait">
            {loading ? (
              <LoadingState key="loading" />
            ) : modules.length === 0 ? (
              <EmptyState key="empty" />
            ) : filtered.length === 0 ? (
              <NoResultsState key="no-results" onReset={() => setFilter('ALL')} />
            ) : (
              <motion.div
                key={`heatmap-${filter}`}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -12 }}
                transition={{ duration: 0.28 }}
              >
                <DebtHeatmap
                  modules={filtered}
                  busyId={busyId}
                  onSimulate={handleSimulate}
                  onHeal={handleHeal}
                />
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        <Footer />
      </div>

      <AnimatePresence>
        {healResult && (
          <DiffView
            result={healResult}
            onClose={() => setHealResult(null)}
          />
        )}
      </AnimatePresence>
    </div>
  )
}

/* ---------- Sub-components (kept inline for cohesion) ---------- */

function BlueprintBackdrop(): JSX.Element {
  return (
    <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
      <div className="absolute inset-0 bg-blueprint opacity-[0.35] dark:opacity-[0.25]" />
      <div
        className="absolute inset-0"
        style={{
          background:
            'radial-gradient(1000px 500px at 15% -5%, rgba(255,92,26,0.08), transparent 60%), radial-gradient(800px 400px at 100% 0%, rgba(15,15,15,0.04), transparent 55%)',
        }}
      />
    </div>
  )
}

function TopBar({
  theme,
  onToggleTheme,
}: {
  theme: 'light' | 'dark'
  onToggleTheme: () => void
}): JSX.Element {
  return (
    <div className="flex items-center justify-between border-b border-[color:var(--color-line)] py-4">
      <div className="flex items-center gap-3">
        <div className="grid h-8 w-8 place-items-center border border-[color:var(--color-line-strong)] bg-[color:var(--color-surface)] font-mono text-sm font-bold">
          §
        </div>
        <div className="flex flex-col leading-tight">
          <span className="font-mono text-[11px] font-semibold tracking-widest text-[color:var(--color-ink-2)]">
            DOC·DEBT
          </span>
          <span className="font-mono text-[10px] uppercase tracking-[0.2em] text-[color:var(--color-muted)]">
            tracker / v0.1.0
          </span>
        </div>
      </div>

      <nav className="flex items-center gap-1 sm:gap-2">
        <a
          href="https://github.com"
          target="_blank"
          rel="noreferrer"
          className="hidden items-center gap-2 border border-transparent px-3 py-1.5 font-mono text-xs uppercase tracking-widest text-[color:var(--color-ink-3)] transition hover:border-[color:var(--color-line-strong)] hover:text-[color:var(--color-ink)] sm:inline-flex"
        >
          <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 .5C5.6.5.5 5.6.5 12c0 5.1 3.3 9.4 7.9 10.9.6.1.8-.2.8-.6v-2.2c-3.2.7-3.9-1.4-3.9-1.4-.5-1.3-1.2-1.7-1.2-1.7-1.1-.7.1-.7.1-.7 1.2.1 1.8 1.2 1.8 1.2 1 1.8 2.7 1.3 3.4 1 .1-.8.4-1.3.7-1.6-2.6-.3-5.3-1.3-5.3-5.7 0-1.3.5-2.3 1.2-3.1-.1-.3-.5-1.5.1-3.2 0 0 1-.3 3.3 1.2 1-.3 2-.4 3-.4s2 .1 3 .4c2.3-1.5 3.3-1.2 3.3-1.2.7 1.7.2 2.9.1 3.2.8.8 1.2 1.9 1.2 3.1 0 4.4-2.7 5.4-5.3 5.7.4.4.8 1.1.8 2.2v3.2c0 .3.2.7.8.6C20.2 21.4 23.5 17.1 23.5 12 23.5 5.6 18.4.5 12 .5z" />
          </svg>
          Source
        </a>
        <ThemeToggle theme={theme} onToggle={onToggleTheme} />
      </nav>
    </div>
  )
}

function ThemeToggle({
  theme,
  onToggle,
}: {
  theme: 'light' | 'dark'
  onToggle: () => void
}): JSX.Element {
  const isDark = theme === 'dark'
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-label={`Switch to ${isDark ? 'light' : 'dark'} mode`}
      className="relative flex h-8 w-16 items-center border border-[color:var(--color-line-strong)] bg-[color:var(--color-surface)] p-0.5 transition hover:border-[color:var(--color-ink-3)]"
    >
      <motion.span
        layout
        transition={{ type: 'spring', stiffness: 500, damping: 30 }}
        className={`absolute top-0.5 h-6 w-7 bg-[color:var(--color-ink)] ${isDark ? 'right-0.5' : 'left-0.5'}`}
      />
      <span className="relative z-10 flex w-full items-center justify-between px-1.5 font-mono text-[10px] font-bold tracking-widest">
        <span
          className={isDark ? 'text-[color:var(--color-muted)]' : 'text-[color:var(--color-canvas)]'}
        >
          LT
        </span>
        <span
          className={isDark ? 'text-[color:var(--color-canvas)]' : 'text-[color:var(--color-muted)]'}
        >
          DK
        </span>
      </span>
    </button>
  )
}

function Hero({
  lastSync,
  onCreated,
  totalModules,
}: {
  lastSync: Date | null
  onCreated: () => void | Promise<void>
  totalModules: number
}): JSX.Element {
  return (
    <section className="grid grid-cols-1 gap-8 py-10 md:py-14 lg:grid-cols-12 lg:gap-10">
      <div className="lg:col-span-8">
        <div className="mb-4 flex items-center gap-3">
          <span className="eyebrow">Issue №{String(totalModules).padStart(3, '0')}</span>
          <span className="h-px flex-1 bg-[color:var(--color-line)]" />
          <span className="eyebrow">
            {new Date().toLocaleDateString('en-US', {
              year: 'numeric',
              month: 'short',
              day: '2-digit',
            })}
          </span>
        </div>

        <motion.h1
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className="display text-[52px] leading-[0.95] sm:text-[72px] md:text-[92px] lg:text-[108px]"
        >
          Docs that heal <br />
          <span className="display-italic text-[color:var(--color-accent)]">
            themselves.
          </span>
        </motion.h1>

        <motion.p
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.15 }}
          className="mt-6 max-w-2xl text-base leading-relaxed text-[color:var(--color-ink-3)] sm:text-lg"
        >
          A drift monitor for HLD &amp; LLD design docs. Watches every merged PR,
          measures how far your documentation has strayed from the code, and
          rewrites the spec on demand — via a Map&#8209;Reduce healing pipeline.
        </motion.p>
      </div>

      <div className="lg:col-span-4 lg:pt-8">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-3 border-t border-[color:var(--color-line)] pt-4">
            <span
              className="relative flex h-2 w-2 shrink-0"
              aria-hidden
            >
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[color:var(--color-good)] opacity-60" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-[color:var(--color-good)]" />
            </span>
            <div className="font-mono text-[11px] uppercase tracking-widest text-[color:var(--color-muted)]">
              Live · syncing every 8s
              {lastSync && (
                <span className="ml-2 text-[color:var(--color-ink-3)]">
                  {lastSync.toLocaleTimeString([], {
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit',
                  })}
                </span>
              )}
            </div>
          </div>
          <NewModuleForm onCreated={onCreated} />
        </div>
      </div>
    </section>
  )
}

function StatsStrip({
  total,
  critical,
  drifting,
  stable,
  pending,
}: {
  total: number
  critical: number
  drifting: number
  stable: number
  pending: number
}): JSX.Element {
  const cells: {
    label: string
    value: number
    accent?: string
  }[] = [
    { label: 'Modules', value: total },
    { label: 'Critical', value: critical, accent: 'var(--color-bad)' },
    { label: 'Drifting', value: drifting, accent: 'var(--color-warn)' },
    { label: 'Stable', value: stable, accent: 'var(--color-good)' },
    { label: 'Pending PRs', value: pending },
  ]

  return (
    <div className="relative mb-8 border border-[color:var(--color-line-strong)] bg-[color:var(--color-surface)]">
      <div className="absolute -top-2 left-4 bg-[color:var(--color-canvas)] px-2">
        <span className="eyebrow">Overview</span>
      </div>
      <div className="grid grid-cols-2 divide-x divide-y divide-[color:var(--color-line)] sm:grid-cols-3 sm:divide-y-0 lg:grid-cols-5">
        {cells.map((cell, i) => (
          <motion.div
            key={cell.label}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.05 * i, duration: 0.35 }}
            className="relative px-5 py-5"
          >
            <div className="flex items-baseline justify-between">
              <span className="eyebrow">{cell.label}</span>
              <span
                className="font-mono text-[10px] tabular text-[color:var(--color-faint)]"
              >
                {String(i + 1).padStart(2, '0')}
              </span>
            </div>
            <div className="mt-3 flex items-baseline gap-2">
              <span
                className="display tabular text-5xl"
                style={cell.accent ? { color: cell.accent } : undefined}
              >
                {String(cell.value).padStart(2, '0')}
              </span>
              {cell.accent && cell.value > 0 && (
                <span
                  className="h-1.5 w-1.5 rounded-full"
                  style={{ background: cell.accent }}
                />
              )}
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  )
}

function Toolbar({
  filter,
  onFilterChange,
  visibleCount,
  totalCount,
}: {
  filter: HeatFilter
  onFilterChange: (f: HeatFilter) => void
  visibleCount: number
  totalCount: number
}): JSX.Element {
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-[color:var(--color-line)] pb-4">
      <div className="flex items-center gap-2">
        <span className="eyebrow">Filter</span>
        <div className="flex flex-wrap gap-1 border border-[color:var(--color-line-strong)] bg-[color:var(--color-surface)] p-0.5">
          {FILTERS.map((f) => {
            const active = filter === f.key
            return (
              <button
                key={f.key}
                type="button"
                onClick={() => onFilterChange(f.key)}
                className={`relative px-3 py-1.5 font-mono text-[11px] font-semibold uppercase tracking-widest transition ${
                  active
                    ? 'text-[color:var(--color-canvas)]'
                    : 'text-[color:var(--color-ink-3)] hover:text-[color:var(--color-ink)]'
                }`}
              >
                {active && (
                  <motion.span
                    layoutId="filter-pill"
                    className="absolute inset-0 bg-[color:var(--color-ink)]"
                    transition={{ type: 'spring', stiffness: 500, damping: 32 }}
                  />
                )}
                <span className="relative">{f.label}</span>
              </button>
            )
          })}
        </div>
      </div>

      <div className="flex items-center gap-2 font-mono text-[11px] uppercase tracking-widest text-[color:var(--color-muted)]">
        <span className="tabular text-[color:var(--color-ink)]">
          {String(visibleCount).padStart(2, '0')}
        </span>
        <span>/</span>
        <span className="tabular">{String(totalCount).padStart(2, '0')}</span>
        <span>shown</span>
      </div>
    </div>
  )
}

function LoadingState(): JSX.Element {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3"
    >
      {[0, 1, 2, 3, 4, 5].map((i) => (
        <div
          key={i}
          className="relative h-64 border border-[color:var(--color-line)] bg-[color:var(--color-surface)]"
        >
          <div className="absolute inset-0 bg-stripes opacity-40" />
          <motion.div
            className="absolute inset-x-0 top-1/2 h-px bg-[color:var(--color-line-strong)]"
            animate={{ scaleX: [0, 1, 0] }}
            transition={{
              duration: 1.6,
              delay: i * 0.1,
              repeat: Infinity,
              ease: 'easeInOut',
            }}
            style={{ transformOrigin: 'left' }}
          />
        </div>
      ))}
    </motion.div>
  )
}

function EmptyState(): JSX.Element {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      className="corner-brackets relative mx-auto max-w-2xl border border-dashed border-[color:var(--color-line-strong)] bg-[color:var(--color-surface)] px-8 py-16 text-center"
    >
      <div className="mx-auto mb-6 grid h-14 w-14 place-items-center border border-[color:var(--color-line-strong)] font-mono text-xl font-bold text-[color:var(--color-ink-3)]">
        ∅
      </div>
      <h3 className="display text-4xl">No modules yet.</h3>
      <p className="mt-3 text-sm text-[color:var(--color-ink-3)]">
        Track your first module above — or wait for the first PR merge webhook
        to land.
      </p>
    </motion.div>
  )
}

function NoResultsState({ onReset }: { onReset: () => void }): JSX.Element {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="mx-auto max-w-lg border border-[color:var(--color-line)] bg-[color:var(--color-surface)] px-6 py-12 text-center"
    >
      <div className="eyebrow">No matches</div>
      <p className="mt-3 text-base text-[color:var(--color-ink-2)]">
        No modules match the current filter.
      </p>
      <button
        type="button"
        onClick={onReset}
        className="mt-5 font-mono text-xs uppercase tracking-widest text-[color:var(--color-accent)] link-tick"
      >
        Reset filter →
      </button>
    </motion.div>
  )
}

function Footer(): JSX.Element {
  return (
    <footer className="border-t border-[color:var(--color-line)] py-6">
      <div className="flex flex-col items-start justify-between gap-3 font-mono text-[11px] uppercase tracking-widest text-[color:var(--color-muted)] sm:flex-row sm:items-center">
        <div>Doc·Debt Tracker — Map·Reduce healing pipeline</div>
        <div className="flex items-center gap-4">
          <span>API · localhost:8080</span>
          <span className="hidden sm:inline">·</span>
          <span>Made with rigor</span>
        </div>
      </div>
    </footer>
  )
}
