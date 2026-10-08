import { useState, type FormEvent } from 'react'
import { motion, AnimatePresence } from 'motion/react'
import { api } from '../api'

interface NewModuleFormProps {
  onCreated: () => void | Promise<void>
}

export default function NewModuleForm({
  onCreated,
}: NewModuleFormProps) {
  const [open, setOpen] = useState<boolean>(false)
  const [name, setName] = useState<string>('')
  const [technicalDocPath, setTechnicalDocPath] = useState<string>('')
  const [businessDocPath, setBusinessDocPath] = useState<string>('')
  const [submitting, setSubmitting] = useState<boolean>(false)

  async function submit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault()
    const trimmedName = name.trim()
    if (!trimmedName) return

    setSubmitting(true)
    try {
      await api.createModule(
        trimmedName,
        technicalDocPath.trim() || null,
        businessDocPath.trim() || null,
      )
      setName('')
      setTechnicalDocPath('')
      setBusinessDocPath('')
      setOpen(false)
      await onCreated()
    } finally {
      setSubmitting(false)
    }
  }

  const inputCls =
    'w-full border-0 border-b border-[color:var(--color-line-strong)] bg-transparent px-0 py-2 font-mono text-sm text-[color:var(--color-ink)] placeholder-[color:var(--color-faint)] transition focus:border-[color:var(--color-accent)] focus:outline-none focus:ring-0'

  return (
    <div className="w-full">
      <AnimatePresence mode="wait" initial={false}>
        {!open ? (
          <motion.button
            key="cta"
            type="button"
            onClick={() => setOpen(true)}
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            whileHover={{ y: -1 }}
            className="group flex w-full items-center justify-between gap-4 border border-[color:var(--color-ink)] bg-[color:var(--color-ink)] px-5 py-4 text-left transition hover:border-[color:var(--color-accent)] hover:bg-[color:var(--color-accent)]"
          >
            <div>
              <div className="font-mono text-[10px] font-semibold uppercase tracking-widest text-[color:var(--color-canvas)]/70">
                Action / new
              </div>
              <div className="mt-1 display text-2xl text-[color:var(--color-canvas)]">
                Track a module
              </div>
            </div>
            <motion.div
              className="grid h-10 w-10 shrink-0 place-items-center border border-[color:var(--color-canvas)]/30 text-[color:var(--color-canvas)]"
              whileHover={{ rotate: 90 }}
              transition={{ type: 'spring', stiffness: 400, damping: 20 }}
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
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
            </motion.div>
          </motion.button>
        ) : (
          <motion.form
            key="form"
            onSubmit={submit}
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.22 }}
            className="corner-brackets relative border border-[color:var(--color-line-strong)] bg-[color:var(--color-surface)] p-5"
          >
            <div className="mb-4 flex items-center justify-between">
              <div className="font-mono text-[10px] font-semibold uppercase tracking-widest text-[color:var(--color-muted)]">
                New module · form
              </div>
              <button
                type="button"
                onClick={() => setOpen(false)}
                disabled={submitting}
                aria-label="Close"
                className="text-[color:var(--color-muted)] transition hover:text-[color:var(--color-ink)] disabled:opacity-50"
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
              </button>
            </div>

            <div className="space-y-4">
              <Field label="Module name" hint="required">
                <input
                  type="text"
                  placeholder="PaymentService"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  autoFocus
                  required
                  className={inputCls}
                />
              </Field>

              <Field label="Technical doc path" hint="optional">
                <input
                  type="text"
                  placeholder="docs/tech/payment.md"
                  value={technicalDocPath}
                  onChange={(e) => setTechnicalDocPath(e.target.value)}
                  className={inputCls}
                />
              </Field>

              <Field label="Business doc path" hint="optional">
                <input
                  type="text"
                  placeholder="docs/business/payment.md"
                  value={businessDocPath}
                  onChange={(e) => setBusinessDocPath(e.target.value)}
                  className={inputCls}
                />
              </Field>
            </div>

            <div className="mt-6 flex items-center gap-3 border-t border-[color:var(--color-line)] pt-4">
              <button
                type="button"
                onClick={() => setOpen(false)}
                disabled={submitting}
                className="font-mono text-[11px] font-semibold uppercase tracking-widest text-[color:var(--color-muted)] transition hover:text-[color:var(--color-ink)] disabled:opacity-50"
              >
                Cancel
              </button>
              <motion.button
                type="submit"
                disabled={submitting}
                whileTap={{ scale: 0.98 }}
                className="ml-auto inline-flex items-center gap-2 bg-[color:var(--color-ink)] px-4 py-2.5 font-mono text-[11px] font-semibold uppercase tracking-widest text-[color:var(--color-canvas)] transition hover:bg-[color:var(--color-accent)] disabled:cursor-not-allowed disabled:opacity-60"
              >
                {submitting ? (
                  <>
                    <span className="h-3 w-3 animate-spin rounded-full border-2 border-[color:var(--color-canvas)]/30 border-t-[color:var(--color-canvas)]" />
                    Adding
                  </>
                ) : (
                  <>
                    Confirm
                    <svg
                      className="h-3 w-3"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <polyline points="20 6 9 17 4 12" />
                    </svg>
                  </>
                )}
              </motion.button>
            </div>
          </motion.form>
        )}
      </AnimatePresence>
    </div>
  )
}

function Field({
  label,
  hint,
  children,
}: {
  label: string
  hint?: string
  children: React.ReactNode
}) {
  return (
    <label className="block">
      <div className="mb-0 flex items-baseline justify-between">
        <span className="font-mono text-[10px] font-semibold uppercase tracking-widest text-[color:var(--color-muted)]">
          {label}
        </span>
        {hint && (
          <span className="font-mono text-[9px] uppercase tracking-widest text-[color:var(--color-faint)]">
            {hint}
          </span>
        )}
      </div>
      {children}
    </label>
  )
}
