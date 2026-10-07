import type { ReactNode } from 'react'

type HeatLevel = 'LOW' | 'MEDIUM' | 'HIGH'

const HEAT_LABEL: Record<HeatLevel, string> = {
  LOW: 'Stable',
  MEDIUM: 'Drifting',
  HIGH: 'Critical',
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

interface DriftGaugeProps {
  score: number
  threshold: number
  heat: HeatLevel
}

interface DebtHeatmapProps {
  modules: HeatmapModule[]
  busyId: string | null
  onSimulate: (id: string) => void | Promise<void>
  onHeal: (id: string) => void | Promise<void>
}

function DriftGauge({
  score,
  threshold,
  heat,
}: DriftGaugeProps): ReactNode {
  // Semi-circular gauge; cap the score at 2x the threshold.
  const capped: number = Math.min(score, threshold * 2)
  const pct: number = capped / (threshold * 2)
  const angle: number = -90 + pct * 180

  return (
    <div
      className={`gauge gauge-${heat.toLowerCase()}`}
      aria-label={`Volatility score ${score}`}
    >
      <svg
        viewBox="0 0 100 55"
        width="96"
        height="54"
        aria-hidden="true"
      >
        <path
          d="M 6 50 A 44 44 0 0 1 94 50"
          className="gauge-track"
        />

        <path
          d="M 6 50 A 44 44 0 0 1 94 50"
          className="gauge-fill"
          style={{
            strokeDasharray: `${pct * 138}, 138`,
          }}
        />

        <line
          x1="50"
          y1="50"
          x2="50"
          y2="14"
          className="gauge-needle"
          style={{
            transform: `rotate(${angle}deg)`,
            transformOrigin: '50px 50px',
          }}
        />

        <circle
          cx="50"
          cy="50"
          r="3"
          className="gauge-hub"
        />
      </svg>

      <div className="gauge-score">{score}</div>
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
    <div className="heatmap-grid">
      {modules.map((module: HeatmapModule) => {
        const heatClass: string = module.heatLevel.toLowerCase()

        return (
          <article
            key={module.id}
            className={`module-card heat-${heatClass}`}
          >
            <div className="module-card-top">
              <h3>{module.name}</h3>

              <span
                className={`heat-pill heat-pill-${heatClass}`}
              >
                {HEAT_LABEL[module.heatLevel]}
              </span>
            </div>

            <DriftGauge
              score={module.volatilityScore}
              threshold={50}
              heat={module.heatLevel}
            />

            <dl className="module-stats">
              <div>
                <dt>Unprocessed PRs</dt>
                <dd>{module.unprocessedSummaries}</dd>
              </div>

              <div>
                <dt>Days since doc update</dt>
                <dd>{module.daysSinceLastUpdate}</dd>
              </div>
            </dl>

            {(module.technicalScaffolded ||
              module.businessScaffolded) && (
              <div className="badge-row">
                {module.technicalScaffolded && (
                  <span className="badge-scaffolded">
                    New technical doc
                  </span>
                )}

                {module.businessScaffolded && (
                  <span className="badge-scaffolded">
                    New business doc
                  </span>
                )}
              </div>
            )}

            <div className="module-card-actions">
              <button
                className="btn btn-ghost"
                type="button"
                disabled={busyId === module.id}
                onClick={() => onSimulate(module.id)}
                title="Simulate 5 rapid PR merges"
              >
                ⚡ Time Travel
              </button>

              <button
                className="btn btn-primary"
                type="button"
                disabled={busyId === module.id}
                onClick={() => onHeal(module.id)}
                title="Run the Map-Reduce healing pipeline now"
              >
                {busyId === module.id
                  ? 'Healing…'
                  : '✚ Heal Now'}
              </button>
            </div>
          </article>
        )
      })}
    </div>
  )
}
