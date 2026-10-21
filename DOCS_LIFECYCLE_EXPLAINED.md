# How Self-Healing Documentation Works: Full Lifecycle Deep Dive

This document explains the technical lifecycle of **Doc-Debt Tracker**, from the exact moment a GitHub webhook is first attached to a repository, to how baseline documentation is created, to how subsequent code changes and Pull Requests are tracked, summarized, and healed.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                               GITHUB REPOSITORY                                 │
│                                                                                 │
│  [1. Webhook Attached] ──> Ping Event                                           │
│  [2. PR Opened/Sync]   ──> Diff Analysis & Impact Comment                       │
│  [3. PR Merged]        ──> Code Diff & Merge Event                              │
└──────────────┬───────────────────────────────┬──────────────────────────────────┘
               │ (HMAC Verified Webhooks)      │
               ▼                               ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           DOC-DEBT TRACKER BACKEND                              │
│                                                                                 │
│   STAGE 1: ONBOARDING            STAGE 2: PR SUMMARIZATION (MAP)                │
│   ────────────────────           ───────────────────────────────                │
│   • GitHub Ping Receiver         • Safe Diff Fetch via GitHub API               │
│   • Fetch README, build files    • Noise Filter (package-lock, build noise)     │
│   • OpenRouter Initial Scaffold  • ModuleResolver (maps files to modules)       │
│   • Auto-commit baseline docs    • OpenRouter Dual Summary (Tech + Biz)         │
│                                  • Volatility Drift Recalculation               │
│                                                                                 │
│   STAGE 3: DOC HEALING (REDUCE)                                                 │
│   ─────────────────────────────                                                 │
│   • Threshold Check (Pending PRs >= Threshold) OR "Heal Now" Click              │
│   • Fetch Existing Markdown from repo (`docs/Technical/`, `docs/Business/`)     │
│   • OpenRouter Synthesizer (Preserve structure + insert diffs + anti-hallucinate)│
│   • Auto-commit updated Markdown back to GitHub repository                      │
│   • Reset Volatility Score to 0                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Part 1: Initial Attachment & Baseline Doc Scaffolding

### 1. What Happens When You First Attach the Webhook?
When a repository maintainer navigates to **GitHub Repo Settings $\rightarrow$ Webhooks $\rightarrow$ Add Webhook**:
* **Payload URL:** `https://your-domain.com/webhook/github`
* **Content type:** `application/json`
* **Secret:** Your configured `GITHUB_WEBHOOK_SECRET`
* **Events:** Pull requests

The exact second you click **"Add webhook"**, GitHub sends a test event called a **`ping` event** (`X-GitHub-Event: ping`).

### 2. How the Backend Catches and Validates the Ping
In [`GitHubWebhookController.java`](file:///c:/Github/self-healing-docs/backend/src/main/java/com/docdebt/controller/GitHubWebhookController.java#L101-L106):
1. **HMAC Signature Check:** The controller calculates the HMAC-SHA256 hash of the incoming byte body against `GITHUB_WEBHOOK_SECRET`. If valid, execution continues; otherwise, it fails closed with `401 Unauthorized`.
2. **Ping Event Detection:** When `eventType.equals("ping")`, the controller immediately returns `HTTP 202 Accepted` to GitHub so there is zero timeout lag.
3. **Async Dispatch:** It launches `processPingAsync(rawBody)` in a background thread.

### 3. How the System Identifies What is in the Repository
Before calling any LLM, the system needs real ground truth about the repository. It extracts metadata from the webhook JSON:
* `repository.full_name` (e.g. `pranjal-develops/self-healing-docs`)
* `repository.name` (e.g. `self-healing-docs`)
* `repository.description`
* `repository.default_branch` (defaults to `main`)

Next, in [`HealingService.scaffoldInitialDocs()`](file:///c:/Github/self-healing-docs/backend/src/main/java/com/docdebt/service/HealingService.java#L115-L166), the service makes authenticated calls to the **GitHub Contents REST API** to inspect the repository:

```java
// 1. Fetch Repository README
GitHubFile readme = gitHubService.getFileContent(repoFullName, "README.md", defaultBranch);

// 2. Detect Project Type & Dependencies (Java vs Node vs Python)
GitHubFile pom = gitHubService.getFileContent(repoFullName, "pom.xml", defaultBranch);
if (pom == null) {
    GitHubFile pkg = gitHubService.getFileContent(repoFullName, "package.json", defaultBranch);
}
```

The system stitches these together into an initial context bundle:
* **Repository Overview & Description**
* **First 1,500 characters of `README.md`** (describes project purpose, setup, and features)
* **First 1,000 characters of `pom.xml` or `package.json`** (lists actual database drivers, web frameworks, security libraries, and dependencies)

### 4. How the LLM Scaffolds Baseline Documents
This gathered context is fed into two specialized scaffolding prompts in [`LlmService.java`](file:///c:/Github/self-healing-docs/backend/src/main/java/com/docdebt/service/LlmService.java#L113-L160):

#### A. Technical Design Document (HLD/LLD)
The prompt instructs OpenRouter:
> *"You are a Principal Software Architect writing a production-grade High-Level/Low-Level Design (HLD/LLD) for module `{name}`.*
> 
> *CRITICAL RULE: Do NOT invent or hallucinate non-existent files, class names, or endpoints. If specific details are not in the context, write 'N/A — Not specified in repository context'.*
> 
> *Include these exact section headings:*
> 1. Executive Summary & Core Responsibilities
> 2. Component Architecture & System Flow
> 3. API Contracts & Endpoint Specifications
> 4. Data Model, Database Schema & Entities
> 5. Integrations & External Dependencies"

#### B. Business Feature Specification
The prompt instructs OpenRouter:
> *"You are a Product Manager writing a comprehensive business specification for module `{name}` for non-technical stakeholders (Product Managers, Executives, Support).*
> 
> *Focus strictly on user capabilities and business value. Do NOT include code snippets.*
> 
> *Include these exact section headings:*
> 1. Executive Summary & Value Proposition
> 2. Core Features & Capabilities
> 3. Target Users & Stakeholders
> 4. Business Value & Problem Solved
> 5. High-Level Workflows"

### 5. Publishing the Baseline
Once generated, the system creates the folders and commits the files directly into the repository branch via `gitHubService.createOrUpdateFile()`:
* `docs/Technical/<moduleName>-HLD.md`
* `docs/Business/<moduleName>-Business.md`

Commit message: `"[Doc-Debt] Initial auto-scaffolded documentation for <moduleName>"`.

---

## Part 2: What Happens When Pull Requests Are Created & Merged

Once baseline documentation exists, the system listens for developer activity on GitHub.

```
Developer opens PR
       │
       ▼
GitHub Webhook: action = "opened" / "synchronize"
       │
       ├─► Fetch Diff from GitHub API (Accept: application/vnd.github.v3.diff)
       ├─► Analyze Impact across registered modules
       └─► (Optional) Post pre-merge comment on PR explaining doc impact
       
Developer merges PR
       │
       ▼
GitHub Webhook: action = "closed" AND merged = true
       │
       ├─► Fetch complete git diff
       ├─► ModuleResolver maps modified files to specific modules
       │
       ▼ (For each affected module)
OpenRouter Diff Summarization (MAP PHASE)
       │
       ├─► Extract TECHNICAL summary (APIs, schemas, dependencies)
       ├─► Extract BUSINESS summary (user impact, new features)
       ├─► Persist PrSummary in Database
       └─► Recalculate Volatility Score: (Pending PRs × 10) + Days
       
Threshold Exceeded OR "Heal Now" clicked (REDUCE PHASE)
       │
       ├─► Fetch EXISTING doc from GitHub (docs/Technical/ & docs/Business/)
       ├─► OpenRouter Synthesizer updates existing markdown with change logs
       ├─► Anti-hallucination constraint ensures only real diffs are inserted
       ├─► Commit updated Markdown directly back to repository
       └─► Reset Volatility Score to 0
```

---

### Step 1: Pre-Merge Impact Analysis (On PR Opened / Synchronize)
When an engineer opens a PR or pushes new commits to an open PR:
1. GitHub fires `pull_request` with `action="opened"` or `action="synchronize"`.
2. The backend fetches the raw PR diff using GitHub's REST API:
   ```http
   GET /repos/{owner}/{repo}/pulls/{number}
   Header: Accept: application/vnd.github.v3.diff
   ```
3. [`ImpactAnalysisService.java`](file:///c:/Github/self-healing-docs/backend/src/main/java/com/docdebt/service/ImpactAnalysisService.java) passes the diff and the list of registered repository modules to OpenRouter.
4. OpenRouter returns a structured JSON map:
   ```json
   {
     "affectedModules": ["AuthService"],
     "docUpdates": {
       "AuthService": [
         "technical doc needs update for OAuth2 token endpoint",
         "business doc needs update for Google/Microsoft login flow"
       ]
     }
   }
   ```
5. If enabled, the bot posts a friendly markdown comment directly on the PR, giving developers and reviewers early visibility before code is merged.

---

### Step 2: Extracting Diff Summaries (On PR Merged — The "Map" Phase)
When the PR is merged (`action="closed"` and `merged=true`):
1. **Fetch Diff:** The backend retrieves the final, approved git diff.
2. **Path Noise Filtering & Module Mapping:**
   In [`ModuleResolver.java`](file:///c:/Github/self-healing-docs/backend/src/main/java/com/docdebt/service/ModuleResolver.java), the system inspects every `diff --git a/path b/path` line.
   * It skips build noise (`package-lock.json`, minified bundles, images).
   * It strips framework noise (`src`, `main`, `java`, `com`, `controllers`).
   * It resolves which module was touched (e.g. `AuthService`).
3. **Prompt Injection Isolation:**
   The diff is untrusted text. In `LlmService.buildSummarizeDiffPrompt()`, the diff is enclosed inside strict XML tags:
   ```xml
   <pr_title>Add OAuth2 support</pr_title>
   <pr_description>Implemented Google and Microsoft SSO</pr_description>
   <diff>
   diff --git a/src/main/java/com/auth/AuthController.java b/...
   + @PostMapping("/oauth2/token")
   + public ResponseEntity<TokenResponse> exchangeToken(...)
   </diff>
   ```
4. **Dual Summary Generation:**
   OpenRouter processes the diff and outputs strictly two labeled sections:
   * **TECHNICAL:** *"Added POST /oauth2/token endpoint with Bearer auth. Updated User model with oauth_provider and oauth_id fields."*
   * **BUSINESS:** *"Users can now log in using Google and Microsoft accounts. No breaking changes for existing password users."*
5. **Database Persistence & Volatility Drift:**
   * The summaries are stored in the `PrSummary` table.
   * Volatility is updated:
     $$\text{Volatility Score} = (\text{Pending PRs} \times 10) + (\text{Days since last doc update})$$

---

### Step 3: Incremental Document Healing (The "Reduce" Phase)
When a module reaches its pending PR threshold (default: 1 PR or configured $N$ PRs), or when a lead clicks **"Heal Now"** in the dashboard:

1. **Retrieve Current Ground Truth:**
   The service reads the *existing* documentation files directly from GitHub:
   * `docs/Technical/AuthService-HLD.md`
   * `docs/Business/AuthService-Business.md`
2. **Aggregate Change Logs:**
   All pending, unprocessed PR summaries for that module are combined:
   ```markdown
   - [PR #142 by dev1] Added POST /oauth2/token endpoint...
   - [PR #145 by dev2] Added refresh token rotation and 15-minute expiry...
   ```
3. **Intelligent Synthesis (Prompt Execution):**
   In [`LlmService.buildSynthesizeTechnicalPrompt()`](file:///c:/Github/self-healing-docs/backend/src/main/java/com/docdebt/service/LlmService.java#L75-L91), OpenRouter receives:
   * The **full existing Markdown document**.
   * The **accumulated engineering changes**.
   * Strict instructions:
     * Preserve all existing headings and accurate technical details.
     * Incorporate new endpoints under Section 3 (API Contracts).
     * Incorporate updated fields under Section 4 (Data Models).
     * **Do NOT invent anything not present in the changes.**
4. **Direct GitHub Commit:**
   The healed content is committed back to the repository:
   * Branch: Repository default branch (or configured target branch).
   * File: `docs/Technical/AuthService-HLD.md`
   * Commit Message: `"[Doc-Debt] Auto-healed technical doc for AuthService"`
5. **State Reset:**
   * All processed PR summaries are marked `processed = true`.
   * The module's `lastDocUpdate` timestamp is updated to the current time.
   * The module's `volatilityScore` is reset to `0` (status becomes **Stable** / Green).

---

## Summary Comparison: Initial Scaffold vs. PR Healing

| Dimension | Initial Onboarding (`ping` event) | Subsequent PR Updates (`pull_request` merged) |
| :--- | :--- | :--- |
| **Trigger** | Webhook added in GitHub repo settings | Developer merges a PR |
| **Source Context** | Repo description, `README.md`, `pom.xml` / `package.json` | The exact git diff + PR title/body |
| **Document State** | Documents do not exist yet | Documents already exist in `docs/` |
| **LLM Operation** | **Scaffolding:** Creates full 5-section design templates from scratch | **Synthesis:** Updates existing sections; preserves intact content |
| **Output Location** | `docs/Technical/<name>-HLD.md`<br>`docs/Business/<name>-Business.md` | Overwrites existing files with clean Git diff commit |
| **Volatility Effect** | Initializes module with score `0` | Increments score on merge; resets to `0` on heal |
