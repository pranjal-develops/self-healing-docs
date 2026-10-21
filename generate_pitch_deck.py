import sys
import os
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
from pptx.enum.shapes import MSO_SHAPE

def create_presentation():
    prs = Presentation()
    prs.slide_width = Inches(13.333)
    prs.slide_height = Inches(7.5)
    blank_layout = prs.slide_layouts[6]  # Blank slide

    # Color Palette (Modern Dark / Technical Slate)
    BG_COLOR = RGBColor(11, 15, 25)         # Deep slate #0B0F19
    CARD_BG = RGBColor(22, 30, 46)          # Dark card fill #161E2E
    CARD_BORDER = RGBColor(45, 55, 72)      # Slate border #2D3748
    CARD_ACCENT_BG = RGBColor(17, 24, 39)   # Darker card #111827
    
    TEXT_WHITE = RGBColor(248, 250, 252)    # Pure bright white #F8FAFC
    TEXT_MUTED = RGBColor(148, 163, 184)    # Slate muted #94A3B8
    TEXT_DIM = RGBColor(100, 116, 139)      # Dim gray #64748B
    
    ACCENT_CYAN = RGBColor(56, 189, 248)    # Cyan #38BDF8
    ACCENT_GREEN = RGBColor(52, 211, 153)   # Emerald #34D399
    ACCENT_AMBER = RGBColor(251, 191, 36)   # Amber #FBBF24
    ACCENT_PURPLE = RGBColor(168, 85, 247)  # Violet #A855F7

    def apply_slide_bg(slide):
        bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
        bg.fill.solid()
        bg.fill.fore_color.rgb = BG_COLOR
        bg.line.fill.background()
        return bg

    def add_header(slide, tag, title, subtitle=None):
        # Tag Badge
        tag_box = slide.shapes.add_textbox(Inches(0.8), Inches(0.5), Inches(10), Inches(0.4))
        tf_tag = tag_box.text_frame
        tf_tag.word_wrap = True
        tf_tag.margin_left = tf_tag.margin_top = tf_tag.margin_right = tf_tag.margin_bottom = 0
        p_tag = tf_tag.paragraphs[0]
        p_tag.text = tag.upper()
        p_tag.font.name = "Arial"
        p_tag.font.size = Pt(11)
        p_tag.font.bold = True
        p_tag.font.color.rgb = ACCENT_CYAN

        # Title
        title_box = slide.shapes.add_textbox(Inches(0.8), Inches(0.85), Inches(11.5), Inches(0.7))
        tf_title = title_box.text_frame
        tf_title.word_wrap = True
        tf_title.margin_left = tf_title.margin_top = tf_title.margin_right = tf_title.margin_bottom = 0
        p_title = tf_title.paragraphs[0]
        p_title.text = title
        p_title.font.name = "Arial"
        p_title.font.size = Pt(24)
        p_title.font.bold = True
        p_title.font.color.rgb = TEXT_WHITE

        if subtitle:
            p_sub = tf_title.add_paragraph()
            p_sub.text = subtitle
            p_sub.font.name = "Arial"
            p_sub.font.size = Pt(13)
            p_sub.font.color.rgb = TEXT_MUTED

    def add_card(slide, left, top, width, height, bg_color=CARD_BG, border_color=CARD_BORDER):
        card = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left, top, width, height)
        card.fill.solid()
        card.fill.fore_color.rgb = bg_color
        card.line.color.rgb = border_color
        card.line.width = Pt(1.5)
        return card

    # =========================================================================
    # SLIDE 1: TITLE SLIDE
    # =========================================================================
    s1 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s1)

    # Main Hero Container
    add_card(s1, Inches(1.5), Inches(1.2), Inches(10.33), Inches(5.1), bg_color=CARD_ACCENT_BG, border_color=CARD_BORDER)

    tb = s1.shapes.add_textbox(Inches(2.0), Inches(1.8), Inches(9.33), Inches(3.8))
    tf = tb.text_frame
    tf.word_wrap = True

    p0 = tf.paragraphs[0]
    p0.text = "EVENT-DRIVEN DEVELOPER AUTOMATION"
    p0.font.name = "Arial"
    p0.font.size = Pt(12)
    p0.font.bold = True
    p0.font.color.rgb = ACCENT_CYAN

    p1 = tf.add_paragraph()
    p1.text = "Doc-Debt Tracker"
    p1.font.name = "Arial"
    p1.font.size = Pt(40)
    p1.font.bold = True
    p1.font.color.rgb = TEXT_WHITE

    p2 = tf.add_paragraph()
    p2.text = "Automated Self-Healing Documentation via GitHub Pull Requests"
    p2.font.name = "Arial"
    p2.font.size = Pt(18)
    p2.font.color.rgb = ACCENT_GREEN

    p3 = tf.add_paragraph()
    p3.text = "\n• Synchronizing Technical HLD/LLD & Business Feature Specs in Real Time"
    p3.font.name = "Arial"
    p3.font.size = Pt(14)
    p3.font.color.rgb = TEXT_MUTED

    p4 = tf.add_paragraph()
    p4.text = "• Powered by Spring Boot 3 & OpenRouter LLM Orchestration"
    p4.font.name = "Arial"
    p4.font.size = Pt(14)
    p4.font.color.rgb = TEXT_MUTED

    p5 = tf.add_paragraph()
    p5.text = "\nPresented by: Engineering Team  |  Hackathon 2026"
    p5.font.name = "Arial"
    p5.font.size = Pt(13)
    p5.font.color.rgb = TEXT_DIM

    notes1 = s1.notes_slide.notes_text_frame
    notes1.text = "TIMING: 0:00 - 0:25\nSAY: Good morning members of the jury. My name is [Your Name], and today we are presenting Doc-Debt Tracker—an automated system that keeps both technical and business documentation in continuous sync with your code, directly triggered by GitHub Pull Requests."

    # =========================================================================
    # SLIDE 2: THE PROBLEM (DUAL-AUDIENCE DOC DEBT)
    # =========================================================================
    s2 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s2)
    add_header(s2, "The Real-World Dilemma", "The Silent Killer of Velocity: Documentation Drift", "Why code ships in minutes, but documentation remains months out of date")

    # Card 1: Technical Docs Drift
    add_card(s2, Inches(0.8), Inches(1.8), Inches(5.6), Inches(4.8))
    tb = s2.shapes.add_textbox(Inches(1.1), Inches(2.1), Inches(5.0), Inches(4.2))
    tf = tb.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.text = "AUDIENCE 1: SOFTWARE ENGINEERS"
    p.font.name = "Arial"
    p.font.size = Pt(12)
    p.font.bold = True
    p.font.color.rgb = ACCENT_CYAN

    p = tf.add_paragraph()
    p.text = "Technical HLD / LLD Out of Sync"
    p.font.name = "Arial"
    p.font.size = Pt(18)
    p.font.bold = True
    p.font.color.rgb = TEXT_WHITE

    bullets1 = [
        "Pull requests add endpoints, modify database schemas, or adjust retry logic.",
        "Updating architecture docs is manual, friction-heavy, and easily forgotten.",
        "New developers onboard onto inaccurate design specs, wasting sprint hours.",
        "Traditional linters only check markdown syntax, not architectural truth."
    ]
    for b in bullets1:
        p = tf.add_paragraph()
        p.text = "• " + b
        p.font.name = "Arial"
        p.font.size = Pt(13)
        p.font.color.rgb = TEXT_MUTED

    # Card 2: Business Docs Drift
    add_card(s2, Inches(6.9), Inches(1.8), Inches(5.6), Inches(4.8))
    tb = s2.shapes.add_textbox(Inches(7.2), Inches(2.1), Inches(5.0), Inches(4.2))
    tf = tb.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.text = "AUDIENCE 2: PRODUCT & STAKEHOLDERS"
    p.font.name = "Arial"
    p.font.size = Pt(12)
    p.font.bold = True
    p.font.color.rgb = ACCENT_AMBER

    p = tf.add_paragraph()
    p.text = "Business Specs Lag Behind"
    p.font.name = "Arial"
    p.font.size = Pt(18)
    p.font.bold = True
    p.font.color.rgb = TEXT_WHITE

    bullets2 = [
        "Product Managers, support, and compliance need plain-English functionality.",
        "Code PRs contain zero non-technical summaries of customer-facing impact.",
        "Release notes become a rushed end-of-quarter scavenger hunt.",
        "Result: Misalignment between what engineers built and what business thinks exists."
    ]
    for b in bullets2:
        p = tf.add_paragraph()
        p.text = "• " + b
        p.font.name = "Arial"
        p.font.size = Pt(13)
        p.font.color.rgb = TEXT_MUTED

    notes2 = s2.notes_slide.notes_text_frame
    notes2.text = "TIMING: 0:25 - 1:10\nSAY: As developers, we have all lived through this scenario: you finish a feature, write tests, open a PR, and get it merged. You tell yourself, 'I'll update the architecture document tomorrow.' But tomorrow never comes. Worse, documentation has two distinct audiences: Engineers (who need schemas and API routes) and Product Managers (who need user features). Linters only catch typos, not architectural drift. That's the gap Doc-Debt Tracker solves."

    # =========================================================================
    # SLIDE 3: OUR SOLUTION (THE EVENT-DRIVEN WORKFLOW)
    # =========================================================================
    s3 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s3)
    add_header(s3, "How It Works", "Continuous Self-Healing Workflow", "Automated 3-stage pipeline connecting GitHub PRs to updated repository documentation")

    # 3 Pipeline Steps
    step_width = Inches(3.64)
    gap = Inches(0.4)
    left_start = Inches(0.8)

    steps = [
        {
            "num": "STEP 01",
            "title": "PR Event & Diff Filter",
            "color": ACCENT_CYAN,
            "items": [
                "Developer merges PR on GitHub.",
                "GitHub webhook delivers payload to Spring Boot.",
                "HMAC-SHA256 signature verified.",
                "Instant HTTP 202 ACK (zero CI delay).",
                "Non-architectural file noise filtered."
            ]
        },
        {
            "num": "STEP 02",
            "title": "OpenRouter Dual Summary",
            "color": ACCENT_AMBER,
            "items": [
                "Diff is isolated inside XML delimiters.",
                "Fast, efficient LLM generates 2 summaries:",
                "  1. Technical Summary (APIs, schemas).",
                "  2. Business Summary (user features).",
                "Volatility drift score recalculated.",
                "PrSummary stored in database."
            ]
        },
        {
            "num": "STEP 03",
            "title": "Heal & Auto-Commit",
            "color": ACCENT_GREEN,
            "items": [
                "Module threshold reached or 'Heal Now' clicked.",
                "LLM merges accumulated changes into existing doc.",
                "Existing Markdown structure preserved.",
                "Strict anti-hallucination guardrails.",
                "Auto-commits updated Markdown to GitHub docs."
            ]
        }
    ]

    for i, st in enumerate(steps):
        cur_left = left_start + i * (step_width + gap)
        add_card(s3, cur_left, Inches(1.8), step_width, Inches(4.8))
        tb = s3.shapes.add_textbox(cur_left + Inches(0.3), Inches(2.1), step_width - Inches(0.6), Inches(4.2))
        tf = tb.text_frame
        tf.word_wrap = True

        p = tf.paragraphs[0]
        p.text = st["num"]
        p.font.name = "Arial"
        p.font.size = Pt(11)
        p.font.bold = True
        p.font.color.rgb = st["color"]

        p = tf.add_paragraph()
        p.text = st["title"]
        p.font.name = "Arial"
        p.font.size = Pt(17)
        p.font.bold = True
        p.font.color.rgb = TEXT_WHITE

        for it in st["items"]:
            p = tf.add_paragraph()
            p.text = "• " + it
            p.font.name = "Arial"
            p.font.size = Pt(12)
            p.font.color.rgb = TEXT_MUTED

    notes3 = s3.notes_slide.notes_text_frame
    notes3.text = "TIMING: 1:10 - 1:55\nSAY: Rather than asking developers to write docs after every sprint, our system listens to GitHub webhooks. 1) When a PR merges, we receive the diff and verify security. 2) OpenRouter creates structured dual summaries—technical and business. 3) When changes reach the threshold, our synthesizer updates the existing markdown and commits directly back to GitHub. Let's inspect the system via screenshots."

    # =========================================================================
    # SLIDE 4: SCREENSHOT 1 — PR INGESTION & IMPACT ANALYSIS
    # =========================================================================
    s4 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s4)
    add_header(s4, "System Walkthrough: Stage 1", "PR Ingestion & Impact Analysis", "Zero-friction developer integration with optional pre-merge PR comments")

    # Left: Mock Screenshot Card
    add_card(s4, Inches(0.8), Inches(1.8), Inches(6.5), Inches(4.8), bg_color=CARD_ACCENT_BG)
    tb = s4.shapes.add_textbox(Inches(1.1), Inches(2.1), Inches(5.9), Inches(4.2))
    tf = tb.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.text = "GITHUB PR IMPACT BOT COMMENT"
    p.font.name = "Courier New"
    p.font.size = Pt(11)
    p.font.color.rgb = ACCENT_CYAN

    code_lines = [
        "## 📋 Doc-Debt Impact Analysis (PR #142)",
        "**Status:** Impact Detected  |  **Confidence:** High",
        "",
        "### Affected Module: `AuthService`",
        "• Technical Doc: New OAuth2 token endpoints (`/oauth/token`)",
        "• Data Model: `User` entity updated with `oauth_provider`",
        "• Business Spec: Users can now log in via Google/Microsoft SSO",
        "",
        "### Affected Module: `UserService`",
        "• Technical Doc: Minor schema adjustment",
        "",
        "---",
        "Generated automatically by Doc-Debt Tracker Webhook"
    ]
    for cl in code_lines:
        p = tf.add_paragraph()
        p.text = cl
        p.font.name = "Courier New"
        p.font.size = Pt(11)
        p.font.color.rgb = RGBColor(226, 232, 240) if cl.startswith("###") else TEXT_MUTED

    # Right: Technical Details Card
    add_card(s4, Inches(7.6), Inches(1.8), Inches(4.9), Inches(4.8))
    tb = s4.shapes.add_textbox(Inches(7.9), Inches(2.1), Inches(4.3), Inches(4.2))
    tf = tb.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.text = "KEY IMPLEMENTATION DETAILS"
    p.font.name = "Arial"
    p.font.size = Pt(12)
    p.font.bold = True
    p.font.color.rgb = ACCENT_AMBER

    points4 = [
        ("Instant HTTP 202 ACK", "Webhook finishes in milliseconds; all diff processing runs asynchronously in Spring Boot threads."),
        ("Delivery Deduplication", "Checks X-GitHub-Delivery header to ensure retry events never create duplicate summaries."),
        ("ModuleResolver Engine", "Parses modified file paths from diff headers, strips src/main/java noise, and matches registered modules."),
        ("Pre-Merge Transparency", "Developers see documentation impact directly in the PR before code is even merged.")
    ]
    for title, desc in points4:
        p = tf.add_paragraph()
        p.text = "• " + title + ":"
        p.font.name = "Arial"
        p.font.size = Pt(13)
        p.font.bold = True
        p.font.color.rgb = TEXT_WHITE
        p2 = tf.add_paragraph()
        p2.text = "   " + desc
        p2.font.name = "Arial"
        p2.font.size = Pt(12)
        p2.font.color.rgb = TEXT_MUTED

    notes4 = s4.notes_slide.notes_text_frame
    notes4.text = "TIMING: 1:55 - 2:45\nSAY: When a PR is opened or merged, our Spring Boot service returns an immediate 202 Accepted so GitHub's 10-second timeout is never hit. Our ModuleResolver parses the git diff paths, strips package noise, and maps changes to modules like AuthService. As shown here, it can post a concise impact comment right on the PR."

    # =========================================================================
    # SLIDE 5: SCREENSHOT 2 — THE DOC-DEBT HEATMAP DASHBOARD
    # =========================================================================
    s5 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s5)
    add_header(s5, "System Walkthrough: Stage 2", "The Doc-Debt Heatmap Dashboard", "Real-time visibility into documentation drift across microservices and components")

    # Dashboard Mock UI (Left)
    add_card(s5, Inches(0.8), Inches(1.8), Inches(6.8), Inches(4.8), bg_color=CARD_ACCENT_BG)
    tb = s5.shapes.add_textbox(Inches(1.1), Inches(2.1), Inches(6.2), Inches(4.2))
    tf = tb.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.text = "DASHBOARD OBSERVABILITY: /api/modules"
    p.font.name = "Arial"
    p.font.size = Pt(11)
    p.font.bold = True
    p.font.color.rgb = ACCENT_CYAN

    modules_mock = [
        ("[CRITICAL] AuthService", "Volatility: 62  |  Pending PRs: 5  |  Target: GitHub", RGBColor(239, 68, 68)),
        ("[DRIFTING] PaymentService", "Volatility: 34  |  Pending PRs: 3  |  Target: GitHub", RGBColor(245, 158, 11)),
        ("[STABLE] UserService", "Volatility: 4   |  Pending PRs: 0  |  Target: GitHub", RGBColor(34, 197, 94)),
        ("[STABLE] NotificationService", "Volatility: 8   |  Pending PRs: 0  |  Target: GitHub", RGBColor(34, 197, 94))
    ]
    for name, stats, color in modules_mock:
        p = tf.add_paragraph()
        p.text = "■ " + name
        p.font.name = "Arial"
        p.font.size = Pt(14)
        p.font.bold = True
        p.font.color.rgb = color
        p_sub = tf.add_paragraph()
        p_sub.text = "   " + stats
        p_sub.font.name = "Arial"
        p_sub.font.size = Pt(11)
        p_sub.font.color.rgb = TEXT_MUTED

    # Right: Volatility Math & Action
    add_card(s5, Inches(7.9), Inches(1.8), Inches(4.6), Inches(4.8))
    tb = s5.shapes.add_textbox(Inches(8.2), Inches(2.1), Inches(4.0), Inches(4.2))
    tf = tb.text_frame
    tf.word_wrap = True

    p = tf.paragraphs[0]
    p.text = "DETERMINISTIC VOLATILITY"
    p.font.name = "Arial"
    p.font.size = Pt(12)
    p.font.bold = True
    p.font.color.rgb = ACCENT_GREEN

    p = tf.add_paragraph()
    p.text = "Volatility Formula:"
    p.font.name = "Arial"
    p.font.size = Pt(14)
    p.font.bold = True
    p.font.color.rgb = TEXT_WHITE

    p = tf.add_paragraph()
    p.text = "Score = (Pending PRs × 10) + (Days since last doc update)"
    p.font.name = "Courier New"
    p.font.size = Pt(11)
    p.font.color.rgb = ACCENT_AMBER

    points5 = [
        "Configurable Thresholds: Each module has a custom threshold (e.g., 3 PRs). Auto-heals when exceeded.",
        "On-Demand 'Heal Now': Leads can click Heal Now to synchronize docs right before a deployment.",
        "Nightly Batch Job: DebtEvaluatorJob runs at 2 AM to evaluate dormant modules that accumulated time drift."
    ]
    for pt in points5:
        p = tf.add_paragraph()
        p.text = "• " + pt
        p.font.name = "Arial"
        p.font.size = Pt(12)
        p.font.color.rgb = TEXT_MUTED

    notes5 = s5.notes_slide.notes_text_frame
    notes5.text = "TIMING: 2:45 - 3:35\nSAY: Here is our React dashboard. The Debt Heatmap gives leads an instant view of documentation freshness across modules. Our volatility formula combines pending PRs and days since the last update. Modules turn yellow and red as PRs merge. Maintainers can let the threshold trigger auto-healing or click 'Heal Now' on demand."

    # =========================================================================
    # SLIDE 6: SCREENSHOT 3 — SIDE-BY-SIDE DIFF & REPO COMMIT
    # =========================================================================
    s6 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s6)
    add_header(s6, "System Walkthrough: Stage 3", "Side-by-Side Diff & GitHub Commit", "Intelligent incremental synthesis preserving structure with zero file wipeouts")

    # Diff View Box (Left)
    add_card(s6, Inches(0.8), Inches(1.8), Inches(6.8), Inches(4.8), bg_color=CARD_ACCENT_BG)
    tb = s6.shapes.add_textbox(Inches(1.1), Inches(2.1), Inches(6.2), Inches(4.2))
    tf = tb.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.text = "UNIFIED DIFF: docs/Technical/AuthService-HLD.md"
    p.font.name = "Courier New"
    p.font.size = Pt(11)
    p.font.color.rgb = ACCENT_CYAN

    diff_lines = [
        "  ## 3. API Contracts & Endpoint Specifications",
        "   POST /api/auth/login",
        "+  POST /api/auth/oauth2/token  [ADDED via PR #142]",
        "+    Headers: Authorization: Bearer <token>",
        "+    Response: { accessToken, refreshToken, expiresIn }",
        "",
        "  ## 4. Data Model & Entities",
        "   Entity: User",
        "   - id: Long (Primary Key)",
        "   - email: String",
        "+  - oauth_provider: String (Nullable) [ADDED via PR #142]",
        "+  - oauth_id: String (Nullable) [ADDED via PR #142]"
    ]
    for dl in diff_lines:
        p = tf.add_paragraph()
        p.text = dl
        p.font.name = "Courier New"
        p.font.size = Pt(11)
        if dl.startswith("+"):
            p.font.color.rgb = RGBColor(74, 222, 128)  # Bright Green
            p.font.bold = True
        elif dl.startswith("  ##"):
            p.font.color.rgb = TEXT_WHITE
            p.font.bold = True
        else:
            p.font.color.rgb = TEXT_MUTED

    # Right: Dual Perspective Highlights
    add_card(s6, Inches(7.9), Inches(1.8), Inches(4.6), Inches(4.8))
    tb = s6.shapes.add_textbox(Inches(8.2), Inches(2.1), Inches(4.0), Inches(4.2))
    tf = tb.text_frame
    tf.word_wrap = True

    p = tf.paragraphs[0]
    p.text = "WHY THIS APPROACH WORKS"
    p.font.name = "Arial"
    p.font.size = Pt(12)
    p.font.bold = True
    p.font.color.rgb = ACCENT_GREEN

    points6 = [
        ("Incremental Synthesis", "Takes the existing doc + accumulated PR summaries. Does NOT rewrite the entire file from scratch."),
        ("Direct GitHub Commits", "Automatically commits healed Markdown directly to the repo branch under docs/Technical and docs/Business."),
        ("Dual Tabs in UI", "Maintainers can toggle between Technical HLD/LLD and Business Feature Specification."),
        ("Automatic Reset", "Once committed, pending summaries are marked processed and volatility resets to 0.")
    ]
    for title, desc in points6:
        p = tf.add_paragraph()
        p.text = "• " + title + ":"
        p.font.name = "Arial"
        p.font.size = Pt(13)
        p.font.bold = True
        p.font.color.rgb = TEXT_WHITE
        p2 = tf.add_paragraph()
        p2.text = "   " + desc
        p2.font.name = "Arial"
        p2.font.size = Pt(12)
        p2.font.color.rgb = TEXT_MUTED

    notes6 = s6.notes_slide.notes_text_frame
    notes6.text = "TIMING: 3:35 - 4:15\nSAY: When healing runs, our Diff View shows what actually changed. The LLM does not wipe out the file. As highlighted in green, new OAuth2 endpoints and database fields are inserted into the existing section structure. With one click, maintainers can toggle between Technical HLD and Business Feature specs. The updated draft is auto-committed to GitHub."

    # =========================================================================
    # SLIDE 7: ENGINEERING GUARDRAILS & PRACTICAL CHOICES
    # =========================================================================
    s7 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s7)
    add_header(s7, "Reliability & Safety", "Engineering Guardrails & Practical Choices", "How we ensure production reliability, cost control, and factual accuracy")

    cards7 = [
        {
            "tag": "GUARDRAIL 1",
            "title": "Anti-Hallucination Prompting",
            "color": ACCENT_CYAN,
            "desc": "System prompts mandate: 'Do NOT invent non-existent file names, API endpoints, or schemas. If details are missing, write N/A - Not specified.' Zero fabricated APIs."
        },
        {
            "tag": "GUARDRAIL 2",
            "title": "Diff Noise & Token Budgeting",
            "color": ACCENT_AMBER,
            "desc": "Lockfiles (package-lock.json), minified JS, and build artifacts are stripped. Diffs are capped at an 8,000-character budget to prevent context overflow and control LLM costs."
        },
        {
            "tag": "GUARDRAIL 3",
            "title": "OpenRouter Model Agility",
            "color": ACCENT_GREEN,
            "desc": "Provider abstraction in LlmService allows swapping models via configuration without code changes. Fast, free models for PR summaries; high-reasoning models for synthesis."
        },
        {
            "tag": "GUARDRAIL 4",
            "title": "Prompt Injection Defense",
            "color": ACCENT_PURPLE,
            "desc": "All untrusted PR diffs and descriptions are quarantined inside strict XML tags (<diff>...</diff>). Models are instructed to treat them strictly as data, never as commands."
        }
    ]

    card_w = Inches(5.6)
    card_h = Inches(2.25)
    coords = [
        (Inches(0.8), Inches(1.8)),
        (Inches(6.9), Inches(1.8)),
        (Inches(0.8), Inches(4.3)),
        (Inches(6.9), Inches(4.3))
    ]

    for i, c7 in enumerate(cards7):
        cx, cy = coords[i]
        add_card(s7, cx, cy, card_w, card_h)
        tb = s7.shapes.add_textbox(cx + Inches(0.3), cy + Inches(0.2), card_w - Inches(0.6), card_h - Inches(0.4))
        tf = tb.text_frame
        tf.word_wrap = True

        p = tf.paragraphs[0]
        p.text = c7["tag"]
        p.font.name = "Arial"
        p.font.size = Pt(11)
        p.font.bold = True
        p.font.color.rgb = c7["color"]

        p = tf.add_paragraph()
        p.text = c7["title"]
        p.font.name = "Arial"
        p.font.size = Pt(15)
        p.font.bold = True
        p.font.color.rgb = TEXT_WHITE

        p = tf.add_paragraph()
        p.text = c7["desc"]
        p.font.name = "Arial"
        p.font.size = Pt(12)
        p.font.color.rgb = TEXT_MUTED

    notes7 = s7.notes_slide.notes_text_frame
    notes7.text = "TIMING: 4:15 - 4:45\nSAY: To make this safe for real teams, we implemented 4 guardrails: 1) Anti-hallucination rules forcing 'Not specified' instead of fake APIs. 2) Diff noise filtering and token budgets. 3) OpenRouter flexibility to keep costs near zero. 4) XML prompt-injection defense treating all PR diffs as untrusted data."

    # =========================================================================
    # SLIDE 8: ROADMAP & CLOSING
    # =========================================================================
    s8 = prs.slides.add_slide(blank_layout)
    apply_slide_bg(s8)
    add_header(s8, "Roadmap & Vision", "What's Working Today & What's Next", "Honest engineering delivery with a clear enterprise roadmap")

    # 3 Phase Cards
    phases = [
        {
            "tag": "CURRENT DELIVERABLE (SHIPPED)",
            "title": "Phase 1: GitHub Native",
            "color": ACCENT_GREEN,
            "items": [
                "GitHub Webhook receiver with HMAC validation",
                "OpenRouter PR dual-summarization pipeline",
                "React Debt Heatmap & Unified Diff Viewer",
                "Automated commit sync to GitHub docs/ folder",
                "Tested & operational in active repository"
            ]
        },
        {
            "tag": "UPCOMING MILESTONE (IN PROTOTYPE)",
            "title": "Phase 2: Cloud Storage Sync",
            "color": ACCENT_AMBER,
            "items": [
                "Microsoft SharePoint REST API connector",
                "Microsoft OneDrive Graph API device code auth",
                "DocStorageService interface already built in backend",
                "Bridges engineering Git docs to corporate intranets",
                "Requires Azure Entra ID enterprise tenant setup"
            ]
        },
        {
            "tag": "FUTURE HORIZON (PLANNED)",
            "title": "Phase 3: Review Workflows",
            "color": ACCENT_CYAN,
            "items": [
                "Open documentation updates as dedicated PRs",
                "Slack & MS Teams alerting on high volatility",
                "Confluence & Notion enterprise document sync",
                "Automated PR merge blockers for critical doc debt"
            ]
        }
    ]

    for i, ph in enumerate(phases):
        cur_left = left_start + i * (step_width + gap)
        add_card(s8, cur_left, Inches(1.8), step_width, Inches(4.8))
        tb = s8.shapes.add_textbox(cur_left + Inches(0.3), Inches(2.1), step_width - Inches(0.6), Inches(4.2))
        tf = tb.text_frame
        tf.word_wrap = True

        p = tf.paragraphs[0]
        p.text = ph["tag"]
        p.font.name = "Arial"
        p.font.size = Pt(10)
        p.font.bold = True
        p.font.color.rgb = ph["color"]

        p = tf.add_paragraph()
        p.text = ph["title"]
        p.font.name = "Arial"
        p.font.size = Pt(16)
        p.font.bold = True
        p.font.color.rgb = TEXT_WHITE

        for it in ph["items"]:
            p = tf.add_paragraph()
            p.text = "• " + it
            p.font.name = "Arial"
            p.font.size = Pt(12)
            p.font.color.rgb = TEXT_MUTED

    notes8 = s8.notes_slide.notes_text_frame
    notes8.text = "TIMING: 4:45 - 5:00\nSAY: Today, our system reliably reads and commits docs directly to GitHub Markdown. On our next milestone, we are finishing cloud storage connectors for SharePoint and OneDrive so non-technical stakeholders can access specs in Microsoft 365. Doc-Debt Tracker turns documentation from a neglected chore into an automatic background process. Thank you, and we welcome your questions."

    output_path = os.path.abspath("Doc_Debt_Tracker_Pitch.pptx")
    prs.save(output_path)
    print(f"Successfully generated presentation at: {output_path}")

if __name__ == "__main__":
    create_presentation()
