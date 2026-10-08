# DashboardController Design Document

## Overview
This document describes the design of the `DashboardController` REST controller introduced in PR #15 by `pranjal-develops`. The controller provides programmatic access to module management operations, including seeding, healing pipeline execution, and volatility score recalculation, without requiring real GitHub API traffic. It serves as a bridge between API requests and the underlying domain entities and services.

## Architecture & Data Flow
The `DashboardController` operates as a stateless REST layer that delegates business logic to injected services and repositories. Upon receiving an HTTP request, the controller routes to the appropriate service method:
- `GET /api/modules` queries `ModuleRepository` to retrieve module records.
- `POST /api/modules` triggers module seeding logic via the `HealingService` or associated pipeline.
- `POST /api/modules/{id}/heal` initiates the healing pipeline for a specific module identified by `id`, leveraging `HealingService` and `PrSummaryRepository`.
- `POST /api/modules/{id}/simulate` generates synthetic `PrSummary` records with canned technical and business summaries, then invokes `volatilityService.recalculate()` to update volatility scores without external GitHub API calls.
The flow ensures that all operations are backed by `CodeModule` and `PrSummary` entities, with volatility recalculation as a central cross-cutting concern.

## APIs & Data Models
**Endpoints:**
- `GET /api/modules`
- `POST /api/modules`
- `POST /api/modules/{id}/heal`
- `POST /api/modules/{id}/simulate`

**DTOs:**
- `ModuleStatusDto`: DTO backing the module status operations.
- `HealResultDto`: DTO representing the result of a healing operation.

**Entities:**
- `CodeModule`: Core entity representing a code module, persisted via `ModuleRepository`.
- `PrSummary`: Entity storing pull request summaries, persisted via `PrSummaryRepository`, capable of being generated with canned technical and business summaries for simulation purposes.

## Dependencies
The controller is constructed with the following dependencies:
- `ModuleRepository`: Provides CRUD and query operations for `CodeModule` entities.
- `PrSummaryRepository`: Provides CRUD and query operations for `PrSummary` entities.
- `VolatilityService`: Exposes `recalculate()` method for volatility score recalculation.
- `HealingService`: Encapsulates the healing pipeline execution logic.

## Key Behaviors
- **Module Seeding & Retrieval:** `GET` and `POST` on `/api/modules` enable programmatic module seeding and status retrieval.
- **Healing Pipeline Execution:** `POST /api/modules/{id}/heal` triggers the healing process for a specific module, coordinating with `HealingService` and `PrSummaryRepository`.
- **Simulation without GitHub Traffic:** `POST /api/modules/{id}/simulate` is designed to generate fake `PrSummary` records with predefined technical and business summaries. It deliberately avoids real GitHub API traffic by using canned data and immediately recalculates module volatility scores via `volatilityService.recalculate()`.
- **Volatility Recalculation:** All pathways that modify module state converge on `volatilityService.recalculate()` to ensure volatility scores are consistently updated.