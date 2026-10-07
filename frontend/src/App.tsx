import {
  useCallback,
  useEffect,
  useState,
  type JSX,
} from 'react'

import { api } from './api'
import NewModuleForm from './components/NewModuleForm'
import DebtHeatmap, {
  type HeatmapModule,
} from './components/DebtHeatmap'
import DiffView, {
  type DiffResult,
} from './components/DiffView'

export default function App(): JSX.Element {
  const [modules, setModules] = useState<HeatmapModule[]>([])
  const [loading, setLoading] = useState<boolean>(true)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [healResult, setHealResult] = useState<DiffResult | null>(
    null,
  )

  const refresh = useCallback(async (): Promise<void> => {
    try {
      setError(null)

      const data: HeatmapModule[] = await api.listModules()
      setModules(data)
    } catch (error: unknown) {
      setError(
        error instanceof Error
          ? error.message
          : 'An unknown error occurred',
      )
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
  const initialRefreshId: number = window.setTimeout(() => {
    void refresh()
  }, 0)

  const intervalId: number = window.setInterval(() => {
    void refresh()
  }, 8000)

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
    } catch (error: unknown) {
      setError(
        error instanceof Error
          ? error.message
          : 'Unable to simulate changes',
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
    } catch (error: unknown) {
      setError(
        error instanceof Error
          ? error.message
          : 'Unable to heal documentation',
      )
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="app-title">
          <span className="app-title-mark">§</span>

          <div>
            <h1>Doc&#8209;Debt Tracker</h1>
            <p className="app-subtitle">
              Drift monitor — code vs. HLD/LLD design docs
            </p>
          </div>
        </div>

        <NewModuleForm onCreated={refresh} />
      </header>

      {error && (
        <div className="banner-error">
          Couldn&apos;t reach the backend: {error}
        </div>
      )}

      {loading ? (
        <div className="empty-state">
          Reading the fault lines…
        </div>
      ) : modules.length === 0 ? (
        <div className="empty-state">
          No modules tracked yet. Add one above, or wait for the first
          PR merge webhook.
        </div>
      ) : (
        <DebtHeatmap
          modules={modules}
          busyId={busyId}
          onSimulate={handleSimulate}
          onHeal={handleHeal}
        />
      )}

      {healResult && (
        <DiffView
          result={healResult}
          onClose={() => setHealResult(null)}
        />
      )}
    </div>
  )
}
