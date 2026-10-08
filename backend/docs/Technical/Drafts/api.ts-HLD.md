# Overview
The PR adds a new `api.ts` module that introduces TypeScript interfaces and API methods to establish a typed data contract between UI components and the backend API surface, without altering existing component behavior.

# Architecture & Data Flow
The module defines four API methods (`listModules`, `healNow`, `createModule`, `simulate`) that map to the endpoints `/modules` and `/modules/{id}/heal/simulate`. All API calls utilize a unified error handling mechanism via the `handle<T>` function, ensuring consistent error propagation and response processing across the module's interface.

# APIs & Data Models
- **TypeScript Interfaces**: `Module`, `CreateModuleRequest`
- **API Methods & Endpoints**:
  - `listModules` → `/modules`
  - `createModule` → `/modules`
  - `healNow` → `/modules/{id}/heal/simulate`
  - `simulate` → `/modules/{id}/heal/simulate`
- Error handling is centralized through the generic `handle<T>` function, providing a consistent contract for success and error responses.

# Dependencies
The module depends on the `handle<T>` utility function for unified error handling. It is designed to integrate with the existing TypeScript type system and backend API surface, maintaining compatibility without requiring changes to current component implementations.

# Key Behaviors
- Establishes a typed data contract between UI components and the backend API.
- Provides four distinct methods for module listing, creation, immediate healing, and simulation.
- Ensures error consistency and reliability via the `handle<T>` function.
- Maintains backward compatibility by preserving existing component behavior.