// api.ts

import type { HeatmapModule } from "./components/DebtHeatmap"
import type { DiffResult } from "./components/DiffView"

const BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

export interface Module {
  id: string
  name: string
  technicalDocPath: string
  businessDocPath: string
  createdAt?: string
  updatedAt?: string
}

export interface CreateModuleRequest {
  name: string
  technicalDocPath: string | null
  businessDocPath: string | null
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

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message: string = res.statusText

    try {
      const contentType: string =
        res.headers.get('content-type') ?? ''

      if (contentType.includes('application/json')) {
        const body: ErrorResponse = await res.json()
        message =
          body.message ??
          JSON.stringify(body)
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
  const res: Response = await fetch(`${BASE_URL}/modules`)
  return handle<HeatmapModule[]>(res)
},

healNow: async (id: string): Promise<DiffResult> => {
  const res: Response = await fetch(
    `${BASE_URL}/modules/${encodeURIComponent(id)}/heal`,
    {
      method: 'POST',
    },
  )

  return handle<DiffResult>(res)
},

  createModule: async (
    name: string,
    technicalDocPath: string | null,
    businessDocPath: string | null,
  ): Promise<Module> => {
    const payload: CreateModuleRequest = {
      name,
      technicalDocPath,
      businessDocPath,
    }

    const res: Response = await fetch(`${BASE_URL}/modules`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    })

    return handle<Module>(res)
  },

//   healNow: async (id: string): Promise<HealResponse> => {
//     const res: Response = await fetch(
//       `${BASE_URL}/modules/${encodeURIComponent(id)}/heal`,
//       {
//         method: 'POST',
//       },
//     )

//     return handle<HealResponse>(res)
//   },

  simulate: async (
    id: string,
    count: number = 5,
  ): Promise<SimulateResponse> => {
    const params: URLSearchParams = new URLSearchParams({
      count: String(count),
    })

    const res: Response = await fetch(
      `${BASE_URL}/modules/${encodeURIComponent(id)}/simulate?${params}`,
      {
        method: 'POST',
      },
    )

    return handle<SimulateResponse>(res)
  },
}
