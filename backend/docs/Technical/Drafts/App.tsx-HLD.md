# App.tsx Design Document

## Overview
- Module: `App.tsx`
- Pull Request #9 (by `pranjal-develops`) introduces client-side keyboard shortcut handling.
- Shortcuts implemented: 
  - `R`: Refresh
  - `D`: Toggle light/dark mode
  - `1`–`4`: Filter modules by heat level
  - `Escape`: Close modals
- Functionality is achieved via a new `useEffect` keydown listener that routes to existing state functions and API-proxied actions.
- All other changes in the PR are strictly code formatting (vertical spacing, prop rearrangement, comment tweaks) and do not modify runtime behavior, data models, dependencies, or system architecture.

## Architecture & Data Flow
- Keyboard events are captured at the application level using a `useEffect` hook with a `keydown` listener.
- Key presses are mapped to discrete actions that delegate to pre-existing state management functions.
- Actions requiring data modification are proxied through existing API endpoints; no new API contracts are defined.
- The architecture remains unchanged; the listener acts as a thin routing layer over the current state/API layer.
- Formatting-only diffs are structurally neutral and do not impact the component hierarchy or data flow.

## APIs & Data Models
- All shortcut-triggered actions route to existing API-proxied functions (e.g., refresh, mode toggle, heat-level filtering, modal dismissal).
- No new data models, types, or API endpoints are introduced.
- The PR explicitly states that data models, dependencies, and system architecture are unmodified.
- Formatting changes are cosmetic and do not affect serialization, state shapes, or API contracts.

## Dependencies
- No new runtime or build dependencies are added.
- The `useEffect` hook is part of the standard React API; no additional packages are required.
- Existing dependency stack (React, UI libraries, etc.) is unaffected.
- Code formatting adjustments do not introduce or update dependency versions.

## Key Behaviors
- **R**: Triggers a refresh action by routing to the existing state function and API proxy.
- **D**: Toggles light/dark mode via the existing state management function.
- **1–4**: Filters the module list by the corresponding heat level, delegating to existing state and API logic.
- **Escape**: Closes any open modals by routing to the appropriate state function.
- All behaviors are implemented through routing to pre-existing functions; no inline logic or new state slices are created.
- The PR’s formatting changes (vertical spacing, prop rearrangement, comment tweaks) are visually apparent but produce zero functional difference.