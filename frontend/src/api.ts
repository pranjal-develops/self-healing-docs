// api.ts

import type { HeatmapModule } from "./components/DebtHeatmap"
import type { DiffResult } from "./components/DiffView"

const BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

// Optional API key — set VITE_API_KEY in .env for protected backends.
// When allow-anonymous=true on the backend this can be left empty.
const API_KEY: string = import.meta.env.VITE_API_KEY ?? ''

export interface Module {
  id: string
  name: string
  technicalDocPath: string | null
  businessDocPath: string | null
  docStorageTarget: string
  prHealThreshold: number
  repositoryFullName: string | null
}

export interface CreateModuleRequest {
  name: string
  technicalDocPath: string | null
  businessDocPath: string | null
  /** "github" | "onedrive" | "sharepoint" */
  docStorageTarget: string
  /** Auto-heal after this many pending PRs; default 1 */
  prHealThreshold: number
}

export interface HealResponse {
  success: boolean
  message?: string
  module?: Module
}

export interface SimulateResponse {
  success: boolean
  count: number
  message?: string
}

interface ErrorResponse {
  message?: string
  [key: string]: unknown
}

function authHeaders(): HeadersInit {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (API_KEY) {
    headers['X-API-Key'] = API_KEY
  }
  return headers
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message: string = res.statusText

    try {
      const contentType: string = res.headers.get('content-type') ?? ''
      if (contentType.includes('application/json')) {
        const body: ErrorResponse = await res.json()
        message = body.message ?? JSON.stringify(body)
      } else {
        message = (await res.text()) || message
      }
    } catch {
      // Fall back to statusText
    }

    throw new Error(message)
  }

  return res.json() as Promise<T>
}

export const api = {
  listModules: async (): Promise<HeatmapModule[]> => {
    const res: Response = await fetch(`${BASE_URL}/modules`, {
      headers: authHeaders(),
    })
    return handle<HeatmapModule[]>(res)
  },

  healNow: async (id: string): Promise<DiffResult> => {
    const res: Response = await fetch(
      `${BASE_URL}/modules/${encodeURIComponent(id)}/heal`,
      {
        method: 'POST',
        headers: authHeaders(),
      },
    )
    return handle<DiffResult>(res)
  },

  createModule: async (request: CreateModuleRequest): Promise<Module> => {
    const res: Response = await fetch(`${BASE_URL}/modules`, {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(request),
    })
    return handle<Module>(res)
  },

  simulate: async (id: string, count: number = 5): Promise<SimulateResponse> => {
    const params: URLSearchParams = new URLSearchParams({ count: String(count) })
    const res: Response = await fetch(
      `${BASE_URL}/modules/${encodeURIComponent(id)}/simulate?${params}`,
      {
        method: 'POST',
        headers: authHeaders(),
      },
    )
    return handle<SimulateResponse>(res)
  },

  updateSettings: async (
    id: string,
    settings: { docStorageTarget?: string; prHealThreshold?: number },
  ): Promise<HeatmapModule> => {
    const res: Response = await fetch(
      `${BASE_URL}/modules/${encodeURIComponent(id)}`,
      {
        method: 'PATCH',
        headers: authHeaders(),
        body: JSON.stringify(settings),
      },
    )
    return handle<HeatmapModule>(res)
  },
}
