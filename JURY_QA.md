# Doc-Debt Tracker: Jury Q&A Defense Guide

This document prepares you for tough questions from software architects, technical leads, and product evaluators. Every answer is grounded directly in the actual implementation of this repository.

---

## Category 1: Architecture & Technical Design

### Q1: "Why use OpenRouter instead of calling OpenAI or Gemini directly?"
**Answer:**
> "Flexibility and cost control. In production, we don't want to be locked into a single provider's rate limits, pricing changes, or proprietary API schema. 
> 
> In our backend, `LlmService` is a clean interface, and `OpenRouterService` allows us to configure fast, low-cost or free-tier models (such as Nemotron or Mistral) for lightweight PR diff summaries, while retaining the ability to switch to stronger reasoning models for the full synthesis step. If an enterprise wants to use a private endpoint, they can change two lines in `application.yml` without altering any business logic."

### Q2: "How do you handle multi-module repositories (monorepos) when a single PR touches several services?"
**Answer:**
> "We implemented `ModuleResolver`. When a git diff comes in, `ModuleResolver` parses every changed file path from the `diff --git` headers. 
> 
> It strips standard framework and build noise (like `src`, `main`, `com`, `controllers`, `dtos`) and maps paths to registered `CodeModule` records in our database. 
> 
> The webhook handler then processes each affected module individually: it generates a module-specific PR summary and increments the volatility counter only for the components that actually changed. If no specific submodule matches, it falls back cleanly to the repository's root module."

### Q3: "What happens if GitHub sends duplicate webhooks or retries delivery?"
**Answer:**
> "We have built-in idempotency. In `GitHubWebhookController`, we read GitHub's unique delivery header (`X-GitHub-Delivery`). We check this against `prSummaryRepository.existsByDeliveryId()`. 
> 
> If GitHub redelivers the same event due to a network timeout or retry policy, our service returns an HTTP 202 Accepted and discards the duplicate immediately, ensuring duplicate PR summaries and incorrect volatility spikes never occur."

### Q4: "Why did you separate Technical and Business documentation instead of having one unified document?"
**Answer:**
> "Because in real software organizations, these documents serve two fundamentally distinct stakeholders with different technical vocabularies:
> - **Engineers & Architects** need High-Level/Low-Level Design (HLD/LLD) documents: API paths, HTTP methods, payload schemas, database tables, and dependency interactions.
> - **Product Managers, Support & Compliance** need feature specifications: what does this feature do for the user, what business rules apply, and what is the customer impact.
> 
> In our Map phase, our prompts ask OpenRouter for two distinct sections. When healing, the technical synthesizer updates architectural sections, while the business synthesizer updates feature and user-impact sections without exposing internal code snippets."

---

## Category 2: LLM Reliability, Hallucination & Cost

### Q5: "What if the LLM hallucinates an API endpoint or a database table that doesn't exist?"
**Answer:**
> "We tackle this with strict anti-hallucination guardrails embedded in our system prompt templates in `LlmService`:
> 
> The prompt explicitly mandates:
> *'STRICT ANTI-HALLUCINATION RULE: Do NOT invent non-existent file names, class names, API endpoints, or data models not explicitly present in the provided codebase context or diff. If specific details are not present, write N/A — Not specified in repository context.'*
> 
> Furthermore, the LLM does not generate documentation in a vacuum—it receives the existing Markdown document and only modifies or adds the sections supported by the accumulated PR diffs."

### Q6: "What if a PR description is completely blank or says something generic like 'bug fix'?"
**Answer:**
> "The system does not depend solely on human-written PR descriptions. The primary source of truth is the **actual git diff** fetched directly from GitHub's API. 
> 
> The diff contains the exact code changes—added methods, modified controller routes, and changed entity fields. Even if the developer leaves the PR title and body blank, the LLM reads the code changes directly to determine the architectural impact."

### Q7: "How do you handle massive PRs—like a 10,000-line diff or dependency upgrades?"
**Answer:**
> "We use a two-tier filtering and budgeting approach:
> 1. **Noise Filtering:** In `ModuleResolver` and `LlmService`, we automatically ignore non-architectural noise—such as lockfiles (`package-lock.json`, `pom.xml` build artifacts), minified JavaScript, and binary files.
> 2. **Diff Budgeting:** In `ImpactAnalysisService` and `LlmService`, diffs are capped at an 8,000-character budget with an explicit truncation indicator (`... [truncated]`). This prevents token overflow and keeps inference costs minimal."

### Q8: "How do you prevent prompt injection if a developer puts malicious text in their PR?"
**Answer:**
> "All raw PR input—including the PR title, body, and git diff—is treated as untrusted user input. In `LlmService.buildSummarizeDiffPrompt()`, we enclose diff and PR content inside XML boundary tags (`<pr_title>`, `<diff>`). 
> 
> The system instructions are isolated outside these tags, and the model is explicitly instructed to treat enclosed content strictly as data to summarize, never as executable commands."

---

## Category 3: Developer Experience & Git Workflow

### Q9: "If multiple PRs are merged each day, won't this create spam commits and clutter git history?"
**Answer:**
> "No, and that is specifically why we introduced the **volatility threshold**. 
> 
> Instead of committing on every 2-line commit, the system queues PR summaries. By default, updates are triggered only when a module accumulates a configurable number of PRs (or during a scheduled nightly batch job). This consolidates multiple changes into a single, clean documentation update."

### Q10: "Where do the healed documents actually live in the repository?"
**Answer:**
> "By default, they follow a standard conventions-based folder structure inside the Git repository:
> - `docs/Technical/<ModuleName>-HLD.md` for technical design documents
> - `docs/Business/<ModuleName>-Business.md` for business feature specifications
> 
> Because they are standard Markdown files stored in Git, they can be code-reviewed, tracked with `git diff`, and rendered natively on GitHub or in documentation site generators like Docusaurus or MkDocs."

### Q11: "What if the auto-generated documentation contains a mistake?"
**Answer:**
> "Because all changes are committed to Git, maintainers have complete safety and visibility:
> 1. Standard Git version control allows reverting or amending any commit with a single command (`git revert`).
> 2. The side-by-side Diff Viewer in our dashboard allows team leads to inspect the exact diff before publishing.
> 3. Teams can configure the storage target to commit to a documentation branch or draft PR rather than directly to `main`."

---

## Category 4: Storage & Future Vision

### Q12: "You mentioned SharePoint and OneDrive as upcoming features. Why aren't they the default today?"
**Answer:**
> "We wanted to ensure our core developer loop—GitHub PR to GitHub Markdown—was completely solid, tested, and zero-setup first. 
> 
> In enterprise environments, SharePoint and OneDrive require Microsoft Entra ID tenant registration, client secrets, and Azure admin consent (`Sites.ReadWrite.All`). We have already implemented the `DocStorageService` interface abstraction and initial connectors in our backend, but we chose to pitch the tested GitHub-native solution today and present enterprise cloud storage as our next milestone."

### Q13: "What is your roadmap for the next 3 to 6 months?"
**Answer:**
> "Our roadmap focuses on three enterprise-readiness pillars:
> 1. **Storage Expansion:** Completing enterprise Microsoft SharePoint and Confluence connectors so non-engineering stakeholders can access specs in their existing knowledge bases.
> 2. **Review Workflows:** Option to open a documentation update as a GitHub Pull Request with assigned reviewers instead of a direct commit.
> 3. **ChatOps Notifications:** Automated Slack and Microsoft Teams alerts when a module crosses its drift threshold or when a documentation draft is generated."
