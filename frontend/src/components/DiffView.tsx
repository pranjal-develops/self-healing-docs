import { useState, type ReactNode, type MouseEvent } from 'react'
import { motion } from 'motion/react'

interface DocumentDiffResult {
  oldContent?: string | null
  newContent: string
  wasScaffolded: boolean
  draftPath: string
}

export interface DiffResult {
  moduleName: string
  technical: DocumentDiffResult
  business: DocumentDiffResult
  summariesFolded: number
}

interface DiffViewProps {
  result: DiffResult
  onClose: () => void
}

function DocPanel({
  label,
  content,
  variant,
}: {
  label: string
  content: string
  variant: 'before' | 'after'
}): ReactNode {
  const isAfter = variant === 'after'
  return (
    <div className="flex min-h-0 flex-col overflow-hidden">
      <div
        className={`flex items-center justify-between border-b px-5 py-3 ${
          isAfter
            ? 'border-[color:var(--color-good)]/30 bg-[color:var(--color-good)]/5'
            : 'border-[color:var(--color-line)] bg-[color:var(--color-canvas-2)]'
        }`}
      >
        <div className="flex items-center gap-2">
          <span
            className="h-1.5 w-1.5 rounded-full"
            style={{
              background: isAfter
                ? 'var(--color-good)'
                : 'var(--color-muted)',
            }}
          />
          <span
            className="font-mono text-[10px] font-semibold uppercase tracking-widest"
            style={{
              color: isAfter
                ? 'var(--color-good)'
                : 'var(--color-muted)',
            }}
          >
            {label}
          </span>
        </div>
        <span className="font-mono text-[10px] uppercase tracking-widest text-[color:var(--color-faint)]">
          {isAfter ? '+ draft' : '− previous'}
        </span>
      </div>
      <pre className="scrollbar-slim m-0 flex-1 overflow-auto whitespace-pre-wrap break-words bg-[color:var(--color-surface)] p-5 font-mono text-[12.5px] leading-relaxed text-[color:var(--color-ink-2)]">
        {content}
      </pre>
    </div>
  )
}

export default function DiffView({
  result,
  onClose,
}: DiffViewProps): ReactNode {
  const { moduleName, technical, business, summariesFolded } = result
  const [tab, setTab] = useState<'technical' | 'business'>('technical')
  const active = tab === 'technical' ? technical : business

  const oldContent =
    active.oldContent && active.oldContent.length > 0
      ? active.oldContent
      : '(no existing document — this was unmapped)'

  return (
    <motion.div
      role="dialog"
      aria-modal="true"
      aria-labelledby="diff-title"
      onClick={onClose}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.18 }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-[color:var(--color-ink)]/50 p-4 backdrop-blur-md sm:p-8"
    >
      <motion.div
        onClick={(e: MouseEvent) => e.stopPropagation()}
        initial={{ opacity: 0, y: 20, scale: 0.98 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: 20, scale: 0.98 }}
        transition={{ type: 'spring', stiffness: 260, damping: 26 }}
        className="corner-brackets relative flex max-h-[92vh] w-full max-w-6xl flex-col overflow-hidden border border-[color:var(--color-line-strong)] bg-[color:var(--color-surface)] shadow-[var(--shadow-pop)]"
      >
        <div className="flex items-start justify-between gap-6 border-b border-[color:var(--color-line-strong)] px-6 py-5">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="h-1.5 w-1.5 rounded-full bg-[color:var(--color-good)]" />
              <span className="font-mono text-[10px] font-semibold uppercase tracking-widest text-[color:var(--color-good)]">
                Healed · Map·Reduce
              </span>
            </div>
            <h2
              id="diff-title"
              className="display mt-1 truncate text-4xl leading-none text-[color:var(--color-ink)] sm:text-5xl"
            >
              {moduleName}
            </h2>
            <p className="mt-2 font-mono text-[11px] uppercase tracking-widest text-[color:var(--color-muted)]">
              Folded{' '}
              <span className="tabular text-[color:var(--color-ink)]">
                {String(summariesFolded).padStart(2, '0')}
              </span>{' '}
              PR summar{summariesFolded === 1 ? 'y' : 'ies'} into both docs
            </p>
          </div>

          <motion.button
            type="button"
            onClick={onClose}
            whileHover={{ rotate: 90 }}
            transition={{ type: 'spring', stiffness: 400, damping: 20 }}
            aria-label="Close"
            className="grid h-9 w-9 shrink-0 place-items-center border border-[color:var(--color-line-strong)] text-[color:var(--color-ink-3)] transition hover:border-[color:var(--color-ink)] hover:text-[color:var(--color-ink)]"
          >
            <svg
              className="h-4 w-4"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </motion.button>
        </div>

        <div className="flex flex-wrap items-center gap-3 border-b border-[color:var(--color-line)] bg-[color:var(--color-canvas-2)] px-6 py-3">
          <span className="eyebrow">Doc</span>
          <div className="flex border border-[color:var(--color-line-strong)] bg-[color:var(--color-surface)] p-0.5">
            {(['technical', 'business'] as const).map((key) => {
              const isActive = tab === key
              const scaffolded =
                (key === 'technical' ? technical : business).wasScaffolded
              return (
                <button
                  key={key}
                  type="button"
                  onClick={() => setTab(key)}
                  aria-selected={isActive}
                  className="relative inline-flex items-center gap-2 px-3.5 py-1.5 font-mono text-[11px] font-semibold uppercase tracking-widest transition"
                >
                  {isActive && (
                    <motion.span
                      layoutId="diff-tab-pill"
                      className="absolute inset-0 bg-[color:var(--color-ink)]"
                      transition={{
                        type: 'spring',
                        stiffness: 500,
                        damping: 32,
                      }}
                    />
                  )}
                  <span
                    className={`relative ${
                      isActive
                        ? 'text-[color:var(--color-canvas)]'
                        : 'text-[color:var(--color-ink-3)]'
                    }`}
                  >
                    {key === 'technical' ? 'Technical' : 'Business'}
                  </span>
                  {scaffolded && (
                    <span
                      className={`relative px-1 py-0.5 font-mono text-[9px] font-bold uppercase tracking-widest ${
                        isActive
                          ? 'bg-[color:var(--color-accent)] text-[color:var(--color-canvas)]'
                          : 'bg-[color:var(--color-warn)]/15 text-[color:var(--color-warn)]'
                      }`}
                    >
                      new
                    </span>
                  )}
                </button>
              )
            })}
          </div>

          <div className="ml-auto flex min-w-0 items-center gap-2 font-mono text-[11px] text-[color:var(--color-muted)]">
            <span className="eyebrow">Path</span>
            <code className="truncate border border-[color:var(--color-line-strong)] bg-[color:var(--color-surface)] px-2 py-1 text-[color:var(--color-ink-2)]">
              {active.draftPath}
            </code>
          </div>
        </div>

        <motion.div
          key={tab}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.22 }}
          className="grid min-h-0 flex-1 grid-cols-1 gap-px bg-[color:var(--color-line)] md:grid-cols-2"
        >
          <DocPanel label="Before" content={oldContent} variant="before" />
          <DocPanel
            label="After (draft)"
            content={active.newContent}
            variant="after"
          />
        </motion.div>
      </motion.div>
    </motion.div>
  )
}
