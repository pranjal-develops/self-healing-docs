import { useState, type ReactNode } from 'react'

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

interface DocDiffProps {
  result: DocumentDiffResult
}

interface DiffViewProps {
  result: DiffResult
  onClose: () => void
}

function DocDiff({ result }: DocDiffProps): ReactNode {
  const oldContent: string =
    result.oldContent && result.oldContent.length > 0
      ? result.oldContent
      : '(no existing document — this was unmapped)'

  return (
    <div className="diff-columns">
      <div className="diff-column">
        <h4>Before</h4>
        <pre>{oldContent}</pre>
      </div>

      <div className="diff-column diff-column-new">
        <h4>After (draft)</h4>
        <pre>{result.newContent}</pre>
      </div>
    </div>
  )
}

export default function DiffView({
  result,
  onClose,
}: DiffViewProps): ReactNode {
  const {
    moduleName,
    technical,
    business,
    summariesFolded,
  } = result

  const [tab, setTab] = useState<'technical' | 'business'>(
    'technical',
  )

  const active: DocumentDiffResult =
    tab === 'technical' ? technical : business

  return (
    <div
      className="diff-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="diff-title"
    >
      <div className="diff-panel">
        <div className="diff-header">
          <div>
            <h2 id="diff-title">
              {moduleName} — docs healed
            </h2>

            <p className="diff-meta">
              Folded {summariesFolded} PR summar
              {summariesFolded === 1 ? 'y' : 'ies'} into both docs
            </p>
          </div>

          <button
            className="btn btn-ghost"
            type="button"
            onClick={onClose}
          >
            Close ✕
          </button>
        </div>

        <div className="diff-tabs">
          <button
            className={`diff-tab ${
              tab === 'technical' ? 'diff-tab-active' : ''
            }`}
            type="button"
            onClick={() => setTab('technical')}
            aria-selected={tab === 'technical'}
          >
            Technical

            {technical.wasScaffolded && (
              <span className="diff-tab-badge">new</span>
            )}
          </button>

          <button
            className={`diff-tab ${
              tab === 'business' ? 'diff-tab-active' : ''
            }`}
            type="button"
            onClick={() => setTab('business')}
            aria-selected={tab === 'business'}
          >
            Business

            {business.wasScaffolded && (
              <span className="diff-tab-badge">new</span>
            )}
          </button>

          <span className="diff-tab-path">
            <code>{active.draftPath}</code>
          </span>
        </div>

        <DocDiff result={active} />
      </div>
    </div>
  )
}
