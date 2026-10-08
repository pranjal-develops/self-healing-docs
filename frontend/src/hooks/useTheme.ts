import { useCallback, useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'

function readInitialTheme(): Theme {
  if (typeof window === 'undefined') return 'light'
  try {
    const stored = window.localStorage.getItem('theme')
    if (stored === 'light' || stored === 'dark') return stored
  } catch {
    // ignore storage failures
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light'
}

export function useTheme(): {
  theme: Theme
  toggle: () => void
  setTheme: (t: Theme) => void
} {
  const [theme, setThemeState] = useState<Theme>(readInitialTheme)

  useEffect(() => {
    const root = document.documentElement
    root.classList.toggle('dark', theme === 'dark')
    try {
      window.localStorage.setItem('theme', theme)
    } catch {
      // ignore
    }
  }, [theme])

  const setTheme = useCallback((t: Theme) => setThemeState(t), [])
  const toggle = useCallback(
    () => setThemeState((t) => (t === 'dark' ? 'light' : 'dark')),
    [],
  )

  return { theme, toggle, setTheme }
}
