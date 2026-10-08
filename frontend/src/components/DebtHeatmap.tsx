import { motion } from 'motion/react'
import type { ReactNode } from 'react'

type HeatLevel = 'LOW' | 'MEDIUM' | 'HIGH'

const HEAT_LABEL: Record<HeatLevel, string> = {
  LOW: 'Stable',
  MEDIUM: 'Drifting',
  HIGH: 'Critical',
}

const HEAT_COLOR: Record<HeatLevel, string> = {
  LOW: 'var(--color-good)',
  MEDIUM: 'var(--color-warn)',
  HIGH: 'var(--color-bad)',
}

export interface HeatmapModule {
  id: string
  name: string
  heatLevel: HeatLevel
  volatilityScore: number
  unprocessedSummaries: number
  daysSinceLastUpdate: number
  technicalScaffolded: boolean
  businessScaffolded: boolean
}

interface DebtHeatmapProps {
  modules: HeatmapModule[]
  busyId: string | null
  onSimulate: (id: string) => void | Promise<void>
  onHeal: (id: string) => void | Promise<void>
}

interface DriftMeterProps {
  score: number
  threshold: number
  heat: HeatLevel
}

function DriftMeter({ score, threshold, heat }: DriftMeterProps): ReactNode {
  const max = threshold * 2
  const capped = Math.min(score, max)
  const pct = (capped / max) * 100
  const thresholdPct = (threshold / max) * 100
  const color = HEAT_COLOR[heat]

  return (
    <div className="mt-5">
      <div className="mb-1.5 flex items-baseline justify-between">
        <span className="eyebrow">Volatility</span>
        <div className="flex items-baseline gap-1">
          <span
            className="display tabular text-[28px] leading-none"
            style={{ color }}
          >
            {score}
          </span>
          <span className="font-mono text-[10px] tabular text-[color:var(--color-faint)]">
            / {max}
          </span>
        </div>
      </div>
      <div className="relative h-1.5 w-full overflow-hidden bg-[color:var(--color-canvas-2)]">
        <motion.div
          initial={{ width: 0 }}
          animate={{ width: `${pct}%` }}
          transition={{ duration: 0.9, ease: 'easeOut' }}
          className="h-full"
          style={{ background: color }}
        />
        <div
          className="absolute top-0 h-full w-px bg-[color:var(--color-ink)]/40"
          style={{ left: `${thresholdPct}%` }}
          aria-hidden
        />
      </div>
      <div className="mt-1.5 flex items-center justify-between font-mono text-[9px] tabular uppercase tracking-widest text-[color:var(--color-faint)]">
        <span>0</span>
        <span
          style={{ marginLeft: `calc(${thresholdPct}% - 8px)` }}
          className="text-[color:var(--color-muted)]"
        >
          ↑ {threshold}
        </span>
        <span>{max}</span>
      </div>
    </div>
  )
}

export default function DebtHeatmap({
  modules,
  busyId,
  onSimulate,
  onHeal,
}: DebtHeatmapProps): ReactNode {
  return (
    <motion.div
      className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3"
      variants={{
        hidden: { opacity: 0 },
        show: {
          opacity: 1,
          transition: { staggerChildren: 0.05, delayChildren: 0.05 },
        },
      }}
      initial="hidden"
      animate="show"
    >
      {modules.map((module, index) => {
        const color = HEAT_COLOR[module.heatLevel]
        const busy = busyId === module.id
        const ref = `${String(index + 1).padStart(3, '0')}·${module.name
          .replace(/[^A-Za-z]/g, '')
          .slice(0, 3)
          .toUpperCase()}`

        return (
          <motion.article
            key={module.id}
            layout
            variants={{
              hidden: { opacity: 0, y: 16 },
              show: {
                opacity: 1,
                y: 0,
                transition: { duration: 0.32, ease: 'easeOut' },
              },
            }}
            whileHover={{ y: -3 }}
            className="corner-brackets group relative flex flex-col border border-[color:var(--color-line)] bg-[color:var(--color-surface)] transition-colors hover:border-[color:var(--color-line-strong)]"
          >
            <div
              aria-hidden
              className="absolute inset-x-0 top-0 h-[3px]"
              style={{
                background: `linear-gradient(90deg, ${color}, transparent)`,
              }}
            />

            <div className="flex items-start justify-between gap-3 px-5 pt-5">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span
                    className="h-1.5 w-1.5 rounded-full"
                    style={{ background: color }}
                  />
                  <span className="font-mono text-[10px] font-semibold uppercase tracking-widest text-[color:var(--color-muted)]">
                    {HEAT_LABEL[module.heatLevel]}
                  </span>
                </div>
                <h3 className="display mt-1 truncate text-3xl leading-none text-[color:var(--color-ink)]">
                  {module.name}
                </h3>
              </div>

              <span className="shrink-0 font-mono text-[10px] tabular uppercase tracking-widest text-[color:var(--color-faint)]">
                {ref}
              </span>
            </div>

            <div className="px-5">
              <DriftMeter
                score={module.volatilityScore}
                threshold={50}
                heat={module.heatLevel}
              />
            </div>

            <dl className="mt-5 grid grid-cols-2 border-t border-[color:var(--color-line)]">
              <div className="border-r border-[color:var(--color-line)] px-5 py-4">
                <dt className="eyebrow">Pending PRs</dt>
                <dd className="mt-1 display tabular text-3xl leading-none">
                  {String(module.unprocessedSummaries).padStart(2, '0')}
                </dd>
              </div>
              <div className="px-5 py-4">
                <dt className="eyebrow">Days idle</dt>
                <dd className="mt-1 display tabular text-3xl leading-none">
                  {String(module.daysSinceLastUpdate).padStart(2, '0')}
                </dd>
              </div>
            </dl>

            {(module.technicalScaffolded || module.businessScaffolded) && (
              <div className="flex flex-wrap gap-1.5 border-t border-dashed border-[color:var(--color-line)] px-5 py-3">
                {module.technicalScaffolded && (
                  <ScaffoldBadge label="tech·new" />
                )}
                {module.businessScaffolded && (
                  <ScaffoldBadge label="biz·new" />
                )}
              </div>
            )}

            <div className="mt-auto flex items-stretch border-t border-[color:var(--color-line-strong)]">
              <motion.button
                type="button"
                disabled={busy}
                onClick={() => onSimulate(module.id)}
                title="Simulate 5 rapid PR merges"
                whileTap={{ scale: 0.98 }}
                className="flex flex-1 items-center justify-center gap-2 border-r border-[color:var(--color-line-strong)] px-4 py-3 font-mono text-[11px] font-semibold uppercase tracking-widest text-[color:var(--color-ink-3)] transition hover:bg-[color:var(--color-canvas-2)] hover:text-[color:var(--color-ink)] disabled:cursor-not-allowed disabled:opacity-50"
              >
                <svg
                  className="h-3.5 w-3.5"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
                </svg>
                Time Travel
              </motion.button>

              <motion.button
                type="button"
                disabled={busy}
                onClick={() => onHeal(module.id)}
                title="Run the Map-Reduce healing pipeline now"
                whileTap={{ scale: 0.98 }}
                className="flex flex-1 items-center justify-center gap-2 bg-[color:var(--color-ink)] px-4 py-3 font-mono text-[11px] font-semibold uppercase tracking-widest text-[color:var(--color-canvas)] transition hover:bg-[color:var(--color-accent)] disabled:cursor-not-allowed disabled:opacity-60"
              >
                {busy ? (
                  <>
                    <span className="h-3 w-3 animate-spin rounded-full border-2 border-[color:var(--color-canvas)]/30 border-t-[color:var(--color-canvas)]" />
                    Healing
                  </>
                ) : (
                  <>
                    Heal Now
                    <svg
                      className="h-3.5 w-3.5"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <line x1="5" y1="12" x2="19" y2="12" />
                      <polyline points="12 5 19 12 12 19" />
                    </svg>
                  </>
                )}
              </motion.button>
            </div>
          </motion.article>
        )
      })}
    </motion.div>
  )
}

function ScaffoldBadge({ label }: { label: string }): ReactNode {
  return (
    <span className="inline-flex items-center gap-1 border border-[color:var(--color-warn)]/40 bg-[color:var(--color-warn)]/10 px-1.5 py-0.5 font-mono text-[9px] font-semibold uppercase tracking-widest text-[color:var(--color-warn)]">
      <span className="h-1 w-1 rounded-full bg-[color:var(--color-warn)]" />
      {label}
    </span>
  )
}
