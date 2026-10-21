# Doc-Debt Tracker: Presentation & Pitch Script

This document contains the exact presentation script and slide timing for the **Doc-Debt Tracker** hackathon presentation.

---

## Pitch Overview & Timing Budget

| Section | Slides | Core Time (5 Min) | Extended (+2 Min Buffer) |
| :--- | :--- | :--- | :--- |
| **1. Hook & The Stale Docs Dilemma** | Slides 1 & 2 | 0:00 – 1:10 | 0:00 – 1:10 |
| **2. How It Works (Event Loop)** | Slide 3 | 1:10 – 1:55 | 1:10 – 1:55 |
| **3. Visual Walkthrough (Screenshots)** | Slides 4, 5, 6 | 1:55 – 3:45 | 1:55 – 4:30 |
| **4. Guardrails & Anti-Hallucination** | Slide 7 | 3:45 – 4:30 | 4:30 – 5:30 |
| **5. Roadmap & Future Storage Sync** | Slide 8 | 4:30 – 5:00 | 5:30 – 6:15 |
| **6. Closing Transition to Q&A** | Slide 8 | 5:00 (Done) | 6:15 – 7:00 (Buffer / Deep Dive) |

---

## Slide-by-Slide Speaking Script

### Slide 1: Title Slide
* **Visual:** Slide with title: **Doc-Debt Tracker: Automated Documentation via GitHub Pull Requests**
* **Time:** 0:00 – 0:25

**Spoken Script:**
> "Good morning, members of the jury. 
> 
> My name is [Your Name], and today we are presenting **Doc-Debt Tracker**—an automated system that keeps both technical and business documentation in continuous sync with your code, directly triggered by GitHub Pull Requests."

---

### Slide 2: The Core Problem: The Silent Drift
* **Visual:** The dual-audience problem diagram (Technical Specs vs. Business Specs vs. Production Code).
* **Time:** 0:25 – 1:10

**Spoken Script:**
> "As developers, we have all lived through this scenario: you finish a feature, write tests, open a PR, and get it merged. You tell yourself, *'I'll update the architecture document tomorrow.'* 
> 
> But tomorrow never comes. A few months later, new engineers join the team, read the docs, and realize the API routes, database schemas, and configuration keys don't match the code at all.
> 
> Worse, documentation has two distinct audiences:
> 1. **Engineers**, who need accurate endpoint contracts, schemas, and class interactions.
> 2. **Product Managers and stakeholders**, who just want to know what customer-facing features actually shipped.
> 
> Standard linters and CI checks can find broken markdown links or formatting errors, but they have zero understanding of architectural drift. 
> 
> That is the gap we built Doc-Debt Tracker to solve."

---

### Slide 3: Our Solution: The Event-Driven Workflow
* **Visual:** 3-step diagram: GitHub PR Merged $\rightarrow$ Spring Boot + OpenRouter $\rightarrow$ Auto-committed GitHub Markdown.
* **Time:** 1:10 – 1:55

**Spoken Script:**
> "Rather than asking developers to write documentation updates by hand after every release, our system integrates directly into the developer workflow via GitHub webhooks.
> 
> Here is how it works:
> 1. Whenever a developer merges a PR, GitHub fires a webhook event to our Spring Boot backend.
> 2. Our service extracts the code diff, filters out build noise, and routes it to an LLM via **OpenRouter**.
> 3. OpenRouter generates a structured **Dual Summary**: an engineering-focused technical summary, and a customer-focused business summary.
> 4. As changes accumulate, the system evaluates the module's drift and automatically heals the documentation, committing updated Markdown files directly back to the GitHub repository under the `docs` folder.
> 
> Let's walk through what this looks like in practice with actual screenshots from our working system."

---

### Slide 4: Screenshot 1 — PR Ingestion & Impact Analysis
* **Visual:** Screenshot of GitHub PR event and backend impact analysis detection.
* **Time:** 1:55 – 2:45

**Spoken Script:**
> "Looking at our first walkthrough screenshot:
> 
> When a PR is opened or merged, our backend receives the payload, verifies the HMAC-SHA256 signature, and immediately returns an HTTP 202 Accepted. This guarantees zero blocking latency on developer CI pipelines.
> 
> Our backend inspects the modified file paths, filters out framework noise like `src/main/java`, and maps the changed files to the corresponding module registered in our database—such as `AuthService` or `UserService`.
> 
> The system can also post an impact analysis comment directly onto open PRs, giving the team early visibility into which documentation sections will be affected when the PR lands."

---

### Slide 5: Screenshot 2 — The Doc-Debt Heatmap Dashboard
* **Visual:** Screenshot of the React dashboard showing module cards, color-coded status badges (Stable / Drifting / Critical), and pending PR counters.
* **Time:** 2:45 – 3:35

**Spoken Script:**
> "Here on our React dashboard, you can see our **Debt Heatmap**. It gives engineering leads instant observability into documentation freshness across all modules.
> 
> We compute a simple, deterministic volatility score:
> `Score = (Pending Unprocessed PRs × 10) + (Days since last doc update)`
> 
> - If a module has zero pending changes, it stays green—**Stable**.
> - As pull requests merge, the score rises, transitioning to yellow for **Drifting**, and red for **Critical**.
> 
> Each module has a configurable threshold. When enough PRs have landed, the system auto-heals the docs. But if a lead wants to refresh docs immediately before a release, they can simply click this **'Heal Now'** button."

---

### Slide 6: Screenshot 3 — The Healed Result (Side-by-Side Diff)
* **Visual:** Screenshot of the Side-by-Side Diff Viewer showing before vs. after documentation, green additions, and Technical/Business tab toggles.
* **Time:** 3:35 – 4:15

**Spoken Script:**
> "This screenshot shows what happens when healing runs: our **Side-by-Side Diff View**.
> 
> Notice that the LLM does not wipe out the file and write a generic replacement. Instead, it takes the existing document and incorporates the accumulated changes into the existing sections.
> 
> Highlighted in green on the right:
> - Under API Contracts, new endpoints are inserted with exact request and response schemas.
> - Under Data Models, new entity fields are documented.
> - Existing accurate descriptions and headings are preserved intact.
> 
> With a single click, maintainers can toggle between the **Technical Design Document (HLD/LLD)** and the **Business Feature Specification**. 
> Once generated, the system automatically commits the updated Markdown draft directly back into the repository."

---

### Slide 7: Engineering Guardrails & Practical Choices
* **Visual:** Guardrails breakdown: Anti-Hallucination Prompts, Diff Budgeting, OpenRouter flexibility.
* **Time:** 4:15 – 4:45

**Spoken Script:**
> "To ensure this is reliable for real production teams, we implemented key engineering safeguards:
> 
> 1. **Strict Anti-Hallucination Prompts:** Our system prompts explicitly instruct the model: *If an endpoint or schema is not explicitly in the diff, write 'Not specified'—do not invent fake classes or APIs.*
> 2. **Token Budgeting:** We filter out lockfiles and build artifacts, and truncate raw diffs to stay within cost-effective token budgets.
> 3. **OpenRouter Flexibility:** By integrating OpenRouter, we avoid vendor lock-in and can use fast, cost-effective models for quick PR summaries, and capable reasoning models for document synthesis."

---

### Slide 8: Roadmap & Closing
* **Visual:** Next milestones timeline (Current: GitHub Markdown $\rightarrow$ Next: Microsoft SharePoint & OneDrive sync $\rightarrow$ Slack notifications).
* **Time:** 4:45 – 5:00

**Spoken Script:**
> "Today, our system reliably reads and commits documentation directly to GitHub Markdown repositories. 
> 
> As our next milestone, we have designed the storage abstraction to support enterprise cloud drives, including **Microsoft SharePoint and OneDrive**, allowing business stakeholders to view updated specs where they already work.
> 
> Doc-Debt Tracker turns documentation from a neglected post-release chore into an automatic, continuous background process.
> 
> Thank you, and we look forward to your questions."

---

## The +2 Minute Deep-Dive (If Extended to 7 Minutes)

If granted the extra 2 minutes before Q&A, deliver this section smoothly after Slide 7:

> "To give a little more depth on our backend architecture:
> 
> First, regarding **webhook reliability**:
> GitHub webhooks can arrive in bursts during high-volume merge windows. We use Spring Boot asynchronous processing combined with `X-GitHub-Delivery` header deduplication. Even if GitHub redelivers a webhook due to a network blip, our database enforces delivery idempotency so duplicate summaries are never stored.
> 
> Second, regarding **repo onboarding**:
> When a team adds our webhook to their repository, GitHub sends an initial `ping` event. Doc-Debt Tracker detects this ping, automatically creates the module registry entry, reads the repository's `README` and `pom.xml` or `package.json`, and scaffolds initial baseline Technical and Business documentation templates from day one.
> 
> Third, regarding **developer trust**:
> Because the documentation is updated as standard Markdown files inside Git, teams maintain full version history. Developers can review the auto-generated documentation commits, cherry-pick changes, or adapt them during PR code reviews.
> 
> This approach keeps the tool lightweight, practical, and dependable."
