import { useState, type ReactNode } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { api } from '../api'

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

const STORAGE_ICON: Record<string, string> = {
  github: '⌥',
  onedrive: '◈',
  sharepoint: '◆',
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
  docStorageTarget?: string
  prHealThreshold?: number
  repositoryFullName?: string | null
}

interface DebtHeatmapProps {
  modules: HeatmapModule[]
  busyId: string | null
  onSimulate: (id: string) => void | Promise<void>
  onHeal: (id: string) => void | Promise<void>
  onSettingsUpdated?: () => void | Promise<void>
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
          <span className="display tabular text-[28px] leading-none" style={{ color }}>
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
  onSettingsUpdated,
}: DebtHeatmapProps): ReactNode {
  const [editingModule, setEditingModule] = useState<HeatmapModule | null>(null)
  const [savingSettings, setSavingSettings] = useState<boolean>(false)
  const [editStorageTarget, setEditStorageTarget] = useState<string>('github')
  const [editPrThreshold, setEditPrThreshold] = useState<number>(1)

  function openEditModal(module: HeatmapModule) {
    setEditingModule(module)
    setEditStorageTarget(module.docStorageTarget ?? 'github')
    setEditPrThreshold(module.prHealThreshold ?? 1)
  }

  async function handleSaveSettings() {
    if (!editingModule) return
    setSavingSettings(true)
    try {
      await api.updateSettings(editingModule.id, {
        docStorageTarget: editStorageTarget,
        prHealThreshold: editPrThreshold,
      })
      setEditingModule(null)
      if (onSettingsUpdated) {
        await onSettingsUpdated()
      }
    } catch (e) {
      console.error('Failed to update settings:', e)
    } finally {
      setSavingSettings(false)
    }
  }

  return (
    <>
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

          const storageTarget = module.docStorageTarget ?? 'github'
          const threshold = module.prHealThreshold ?? 1

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
              {/* Heat bar */}
              <div
                aria-hidden
                className="absolute inset-x-0 top-0 h-[3px]"
                style={{ background: `linear-gradient(90deg, ${color}, transparent)` }}
              />

              {/* Header */}
              <div className="flex items-start justify-between gap-3 px-5 pt-5">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="h-1.5 w-1.5 rounded-full" style={{ background: color }} />
                    <span className="font-mono text-[10px] font-semibold uppercase tracking-widest text-[color:var(--color-muted)]">
                      {HEAT_LABEL[module.heatLevel]}
                    </span>
                  </div>
                  <h3 className="display mt-1 truncate text-3xl leading-none text-[color:var(--color-ink)]">
                    {module.name}
                  </h3>
                  {module.repositoryFullName && (
                    <div className="mt-1 truncate font-mono text-[9px] text-[color:var(--color-faint)]">
                      {module.repositoryFullName}
                    </div>
                  )}
                </div>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => openEditModal(module)}
                    title="Configure storage target & PR threshold"
                    className="flex h-7 w-7 items-center justify-center border border-[color:var(--color-line)] bg-[color:var(--color-canvas)] text-[color:var(--color-muted)] transition hover:border-[color:var(--color-ink)] hover:text-[color:var(--color-ink)]"
                  >
                    <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <circle cx="12" cy="12" r="3" />
                      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
                    </svg>
                  </button>
                  <span className="shrink-0 font-mono text-[10px] tabular uppercase tracking-widest text-[color:var(--color-faint)]">
                    {ref}
                  </span>
                </div>
              </div>

              {/* Drift meter */}
              <div className="px-5">
                <DriftMeter score={module.volatilityScore} threshold={50} heat={module.heatLevel} />
              </div>

              {/* Stats row */}
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

              {/* Storage + threshold badges */}
              <div className="flex flex-wrap items-center gap-1.5 border-t border-[color:var(--color-line)] px-5 py-3">
                <StorageBadge target={storageTarget} />
                <button
                  type="button"
                  onClick={() => openEditModal(module)}
                  className="transition hover:opacity-80"
                >
                  <ThresholdBadge threshold={threshold} />
                </button>
                {(module.technicalScaffolded || module.businessScaffolded) && (
                  <>
                    {module.technicalScaffolded && <ScaffoldBadge label="tech·new" />}
                    {module.businessScaffolded && <ScaffoldBadge label="biz·new" />}
                  </>
                )}
              </div>

              {/* Actions */}
              <div className="mt-auto flex items-stretch border-t border-[color:var(--color-line-strong)]">
                <motion.button
                  type="button"
                  disabled={busy}
                  onClick={() => onSimulate(module.id)}
                  title="Simulate 5 rapid PR merges (demo mode)"
                  whileTap={{ scale: 0.98 }}
                  className="flex flex-1 items-center justify-center gap-2 border-r border-[color:var(--color-line-strong)] px-4 py-3 font-mono text-[11px] font-semibold uppercase tracking-widest text-[color:var(--color-ink-3)] transition hover:bg-[color:var(--color-canvas-2)] hover:text-[color:var(--color-ink)] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
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
                      <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
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

      {/* Settings Modal */}
      <AnimatePresence>
        {editingModule && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
          >
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="w-full max-w-md border border-[color:var(--color-line-strong)] bg-[color:var(--color-surface)] p-6 shadow-2xl"
            >
              <div className="flex items-center justify-between border-b border-[color:var(--color-line)] pb-4">
                <div>
                  <div className="eyebrow">Module Settings</div>
                  <h2 className="display text-2xl text-[color:var(--color-ink)]">
                    {editingModule.name}
                  </h2>
                </div>
                <button
                  type="button"
                  onClick={() => setEditingModule(null)}
                  className="font-mono text-sm text-[color:var(--color-muted)] hover:text-[color:var(--color-ink)]"
                >
                  ✕
                </button>
              </div>

              <div className="mt-5 space-y-5">
                {/* Storage Target */}
                <div>
                  <label className="eyebrow mb-2 block">Document Storage Target</label>
                  <div className="grid grid-cols-3 gap-2">
                    {[
                      { id: 'github', label: 'GitHub Docs', icon: '⌥' },
                      { id: 'onedrive', label: 'OneDrive', icon: '◈' },
                      { id: 'sharepoint', label: 'SharePoint', icon: '◆' },
                    ].map((t) => (
                      <button
                        key={t.id}
                        type="button"
                        onClick={() => setEditStorageTarget(t.id)}
                        className={`flex flex-col items-center gap-1 border p-3 font-mono text-xs font-semibold uppercase tracking-wider transition ${
                          editStorageTarget === t.id
                            ? 'border-[color:var(--color-ink)] bg-[color:var(--color-canvas-2)] text-[color:var(--color-ink)]'
                            : 'border-[color:var(--color-line)] text-[color:var(--color-muted)] hover:border-[color:var(--color-line-strong)]'
                        }`}
                      >
                        <span className="text-base">{t.icon}</span>
                        {t.label}
                      </button>
                    ))}
                  </div>
                </div>

                {/* PR Heal Threshold Slider */}
                <div>
                  <div className="flex items-center justify-between">
                    <label htmlFor="edit-pr-threshold" className="eyebrow">
                      PR Heal Threshold
                    </label>
                    <span className="font-mono text-xs font-bold text-[color:var(--color-ink)]">
                      {editPrThreshold} {editPrThreshold === 1 ? 'PR' : 'PRs'}
                    </span>
                  </div>
                  <p className="mt-1 font-mono text-[11px] text-[color:var(--color-muted)]">
                    Auto-heals documentation after every{' '}
                    <strong className="text-[color:var(--color-ink)]">{editPrThreshold}</strong> merged PR(s).
                  </p>
                  <input
                    id="edit-pr-threshold"
                    type="range"
                    min="1"
                    max="20"
                    value={editPrThreshold}
                    onChange={(e) => setEditPrThreshold(Number(e.target.value))}
                    className="mt-3 w-full cursor-pointer accent-[color:var(--color-ink)]"
                  />
                  <div className="flex justify-between font-mono text-[10px] text-[color:var(--color-faint)]">
                    <span>1 (Heal every PR)</span>
                    <span>10</span>
                    <span>20</span>
                  </div>
                </div>
              </div>

              {/* Modal Actions */}
              <div className="mt-6 flex justify-end gap-3 border-t border-[color:var(--color-line)] pt-4">
                <button
                  type="button"
                  onClick={() => setEditingModule(null)}
                  className="px-4 py-2 font-mono text-xs uppercase tracking-wider text-[color:var(--color-muted)] hover:text-[color:var(--color-ink)]"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={savingSettings}
                  onClick={handleSaveSettings}
                  className="bg-[color:var(--color-ink)] px-5 py-2 font-mono text-xs font-semibold uppercase tracking-wider text-[color:var(--color-canvas)] transition hover:bg-[color:var(--color-accent)] disabled:opacity-50"
                >
                  {savingSettings ? 'Saving...' : 'Save Settings'}
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
}

function StorageBadge({ target }: { target: string }): ReactNode {
  const label = target === 'github' ? 'GitHub' : target === 'onedrive' ? 'OneDrive' : 'SharePoint'
  const icon = STORAGE_ICON[target] ?? '◇'
  return (
    <span className="inline-flex items-center gap-1 border border-[color:var(--color-line-strong)] bg-[color:var(--color-surface)] px-1.5 py-0.5 font-mono text-[9px] font-semibold uppercase tracking-widest text-[color:var(--color-ink-3)]">
      <span>{icon}</span>
      {label}
    </span>
  )
}

function ThresholdBadge({ threshold }: { threshold: number }): ReactNode {
  return (
    <span className="inline-flex items-center gap-1 border border-[color:var(--color-line-strong)] bg-[color:var(--color-surface)] px-1.5 py-0.5 font-mono text-[9px] font-semibold uppercase tracking-widest text-[color:var(--color-muted)]"
      title={`Auto-heals after ${threshold} PR${threshold === 1 ? '' : 's'}`}
    >
      heal/{threshold}PR
    </span>
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
