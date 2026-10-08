## ImpactAnalysisService Design Document

### Overview
The `ImpactAnalysisService` is a new class that enables AI-driven Pull Request impact analysis. It integrates with `LlmService` to analyze PR metadata and diffs, queries `ModuleRepository` for available code modules, and produces a structured `ImpactResult` detailing affected modules and required documentation updates. The service includes robust JSON parsing with markdown stripping and a fallback singleton inference strategy to handle parse failures gracefully.

### Architecture & Data Flow
- **Input**: PR metadata and associated diffs.
- **Module Query**: Retrieves available code modules from `ModuleRepository`.
- **Prompt Construction**: Formats a structured prompt combining PR metadata, diffs, and the list of available modules.
- **LLM Invocation**: Sends the prompt to `LlmService` and receives a JSON-formatted response.
- **Post-Processing**: Strips any markdown formatting from the raw JSON output.
- **Parsing & Validation**: Parses the cleaned JSON into an `ImpactResult` object.
- **Fallback**: On parse failure, triggers a singleton inference strategy to derive impact estimates without LLM dependency.
- **Output**: Returns an `ImpactResult` instance containing `affectedModules` and `docUpdatesNeeded`.

### APIs & Data Models
- **`ImpactAnalysisService`**
  - Core service class responsible for orchestrating the impact analysis workflow.
- **`ImpactResult`** (Model)
  - `affectedModules`: List of code modules impacted by the PR.
  - `docUpdatesNeeded`: List of documentation updates required based on the PR changes.
- **`LlmService`** (Dependency)
  - Provides the AI-driven analysis capability via structured prompt generation and response handling.
- **`ModuleRepository`** (Dependency)
  - Supplies the list of available code modules used in prompt context construction.

### Dependencies
- `LlmService`
- `ModuleRepository`
- JSON parsing utility (implied, with markdown stripping support)
- Singleton inference strategy (fallback mechanism)

### Key Behaviors
- Queries `ModuleRepository` to fetch available code modules for prompt enrichment.
- Constructs formatted prompts using PR metadata, diffs, and module context.
- Invokes `LlmService` for AI-driven impact assessment.
- Performs JSON parsing with automatic markdown stripping from the LLM response.
- Implements a fallback singleton inference strategy when JSON parsing fails.
- Returns a structured `ImpactResult` with `affectedModules` and `docUpdatesNeeded` fields.