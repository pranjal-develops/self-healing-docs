# Doc-Debt Tracker - Capabilities & Features Guide

## What This System Does

The Doc-Debt Tracker is an AI-powered documentation management system that automatically keeps your technical and business documentation in sync with your codebase. It integrates with GitHub for change detection, cloud storage for document management, and LLMs for intelligent analysis and synthesis.

### Core Capabilities

1. **PR-Driven Impact Analysis** - Automatically analyzes code changes to determine documentation needs
2. **Cloud Document Storage** - Reads/writes documentation from local, OneDrive, or SharePoint
3. **AI-Powered Documentation Updates** - Uses LLMs to synthesize documentation updates
4. **Cross-Module Dependency Tracking** - Identifies how changes in one module affect others
5. **Automated Summary Generation** - Creates technical and business summaries from PR diffs
6. **Volatility Scoring** - Tracks which modules need documentation attention most urgently

---

## Feature 1: PR Impact Analysis (On PR Opened)

### What It Does

When a developer opens a Pull Request in GitHub, the system automatically:
- Fetches the PR diff
- Analyzes which code modules are affected
- Identifies which documentation files need updating
- Determines if technical docs, business docs, or both need changes
- Logs the impact for visibility

### Why It's Useful

- **Early Visibility**: Team knows immediately which docs need updating before PR merge
- **No Manual Review**: AI analyzes code changes automatically
- **Cross-Module Awareness**: Catches dependencies between modules that humans might miss
- **Prioritization**: Helps focus documentation effort on high-impact changes

### How to Use

**Setup:**
1. Configure GitHub webhook in your repository
2. Set environment variables:
   ```bash
   export GITHUB_TOKEN=ghp_your-token
   export GEMINI_API_KEY=AIza...
   ```

**Automatic Operation:**
- No manual action required
- System triggers on every `pull_request` event with action `opened`
- Results logged to application logs

**Viewing Results:**
```bash
# Check logs for impact analysis
tail -f logs/application.log

# Look for messages like:
# Impact analysis complete for PR #123: affectedModules=[UserService, AuthService], docUpdates={UserService=[technical doc needs update for API changes, business doc needs update for new login flow], AuthService=[technical doc needs review]}
```

### Example Output

```
PR opened - triggering impact analysis: PR #142 - Add OAuth2 support
Impact analysis complete for PR #142: affectedModules=[AuthService, UserService]
Module 'AuthService' needs updates: technical doc needs update for OAuth2 integration, business doc needs update for new authentication method
Module 'UserService' needs updates: technical doc needs review
```

---

## Feature 2: Automated PR Summarization (On PR Merged)

### What It Does

When a PR is merged, the system:
- Fetches the complete diff
- Generates a **technical summary** (engineering-focused: endpoints, data models, dependencies)
- Generates a **business summary** (stakeholder-focused: features, use cases, user impact)
- Stores both summaries in the database
- Updates the module's volatility score

### Why It's Useful

- **Dual Perspectives**: Separate summaries for engineers and business stakeholders
- **Accumulated Context**: Builds history of changes over time
- **No Manual Documentation**: PR descriptions are often incomplete; AI fills gaps
- **Volatility Tracking**: Identifies modules changing frequently

### How to Use

**Setup:**
1. Same webhook configuration as Feature 1
2. System automatically processes merged PRs

**Automatic Operation:**
- Triggers on `pull_request` event with action `closed` and `merged=true`
- Stores summaries in `PrSummary` table
- Updates `CodeModule` volatility score

**Viewing Summaries:**
- Access via dashboard (if frontend exists)
- Query database directly:
  ```sql
  SELECT * FROM pr_summary WHERE module_id = 'UserService' ORDER BY created_at DESC;
  ```

### Example Summaries

**Technical Summary:**
```
Added OAuth2 authentication flow with token refresh endpoints. Updated User model to include oauth_provider and oauth_id fields. Integrated with Spring Security OAuth2 client.
```

**Business Summary:**
```
Users can now log in using Google and Microsoft accounts. No user-facing business impact - this is an internal authentication improvement.
```

---

## Feature 3: Documentation Healing (Manual or Scheduled)

### What It Does

The "healing" process:
- Reads existing documentation from cloud storage
- Aggregates all unprocessed PR summaries for a module
- Uses LLM to synthesize updated documentation
- Pushes updated drafts to cloud storage (Drafts folder)
- Marks PR summaries as processed
- Resets module volatility score

### Why It's Useful

- **Keeps Docs Current**: Documentation evolves with code automatically
- **Draft Review**: Updates go to Drafts folder for human review before publishing
- **Semantic Discovery**: Can match new modules to existing docs by semantic similarity
- **Auto-Scaffolding**: Creates new documentation from scratch if none exists

### How to Use

**Option A: Manual Healing via API**

```bash
# Trigger healing for a specific module
curl -X POST http://localhost:8080/api/modules/UserService/heal

# Response:
{
  "moduleName": "UserService",
  "technicalResult": {
    "oldContent": "...",
    "newContent": "...",
    "draftPath": "ArchitectureDocs/Technical/Drafts/UserService-HLD.md",
    "wasScaffolded": false
  },
  "businessResult": {
    "oldContent": "...",
    "newContent": "...",
    "draftPath": "ArchitectureDocs/Business/Drafts/UserService-Business.md",
    "wasScaffolded": false
  },
  "prsProcessed": 5
}
```

**Option B: Scheduled Healing**

The system includes a scheduled job (configured in `application.yml`):

```yaml
docdebt:
  volatility:
    schedule-cron: "0 0 2 * * *"   # Runs nightly at 2am
```

To enable scheduled healing:
1. Implement `DebtEvaluatorJob` to call `healingService.heal()` for high-volatility modules
2. Configure cron schedule as needed

**Option C: Healing from Dashboard**

If using the frontend:
1. Navigate to module in dashboard
2. Click "Heal Documentation" button
3. Review generated drafts in cloud storage
4. Publish from Drafts to main folder

### What Gets Updated

**Technical Documentation (HLD/LLD):**
- System Architecture
- Endpoints
- Data Model
- Dependencies
- Internal behavior changes

**Business Documentation:**
- Key Features
- Use Cases
- Stakeholders
- Business Value
- User-facing behavior changes

### Example Healing Flow

```
1. System reads existing doc: ArchitectureDocs/Technical/UserService-HLD.md
2. Aggregates 5 unprocessed PR summaries
3. LLM synthesizes update:
   - Preserves accurate sections
   - Incorporates new OAuth2 integration
   - Updates endpoint documentation
   - Refreshes data model section
4. Pushes to: ArchitectureDocs/Technical/Drafts/UserService-HLD.md
5. Human reviews draft in SharePoint/OneDrive
6. Human publishes from Drafts to main folder
7. System marks 5 PRs as processed
8. Module volatility score resets to 0
```

---

## Feature 4: Cloud Storage Integration

### What It Does

The system supports three storage backends for documentation:

1. **Local Storage** (default) - Files on disk
2. **OneDrive** - Personal Microsoft OneDrive
3. **SharePoint** - Enterprise SharePoint/OneDrive for Business

### Why It's Useful

- **Flexibility**: Choose storage based on your environment
- **Zero Setup**: Local storage requires no configuration
- **Enterprise Ready**: SharePoint integrates with corporate document management
- **Personal Use**: OneDrive for individual developers

### How to Use Each Storage Type

#### Local Storage (Default)

**Configuration:**
```yaml
docdebt:
  storage:
    mode: local
    local:
      technical-root: ./docs/Technical
      business-root: ./docs/Business
```

**Setup:**
```bash
# Create folders
mkdir -p docs/Technical docs/Technical/Drafts
mkdir -p docs/Business docs/Business/Drafts
```

**No environment variables needed.**

#### OneDrive Storage

**Configuration:**
```yaml
docdebt:
  storage:
    mode: onedrive
  onedrive:
    client-id: ${ONEDRIVE_CLIENT_ID}
    token-store-path: ./data/onedrive-token.json
    docs-root: ArchitectureDocs
```

**Setup:**
```bash
# 1. Register app in Azure Portal
# 2. Get Client ID
# 3. Set environment variable
export ONEDRIVE_CLIENT_ID=your-client-id
export DOC_STORAGE_MODE=onedrive

# 4. On first run, authenticate via device code flow
#    System will prompt you to visit microsoft.com/devicelogin
```

**Authentication Flow:**
1. System detects no token file
2. Generates device code
3. You visit `https://microsoft.com/devicelogin`
4. Enter code and authorize
5. Token saved locally
6. Automatically refreshed thereafter

#### SharePoint Storage

**Configuration:**
```yaml
docdebt:
  storage:
    mode: sharepoint
  sharepoint:
    tenant-id: ${SHAREPOINT_TENANT_ID}
    client-id: ${SHAREPOINT_CLIENT_ID}
    client-secret: ${SHAREPOINT_CLIENT_SECRET}
    site-id: ${SHAREPOINT_SITE_ID}
    drive-id: ${SHAREPOINT_DRIVE_ID}
    docs-root: ArchitectureDocs
```

**Setup:**
```bash
# 1. Register app in Azure Entra ID
# 2. Configure API permissions: Sites.ReadWrite.All
# 3. Grant admin consent
# 4. Get identifiers (see README for PowerShell commands)
# 5. Set environment variables
export DOC_STORAGE_MODE=sharepoint
export SHAREPOINT_TENANT_ID=xxx
export SHAREPOINT_CLIENT_ID=xxx
export SHAREPOINT_CLIENT_SECRET=xxx
export SHAREPOINT_SITE_ID=xxx
export SHAREPOINT_DRIVE_ID=xxx
```

**Folder Structure Required:**
```
ArchitectureDocs/
├── Technical/
│   ├── UserService-HLD.md
│   └── Drafts/
│       └── UserService-HLD.md
└── Business/
    ├── UserService-Business.md
    └── Drafts/
        └── UserService-Business.md
```

### Switching Storage Backends

```bash
# Switch from local to SharePoint
export DOC_STORAGE_MODE=sharepoint
# Set SharePoint env vars...
./mvnw spring-boot:run

# Switch back to local
export DOC_STORAGE_MODE=local
./mvnw spring-boot:run
```

---

## Feature 5: Volatility Scoring

### What It Does

Each module has a "volatility score" that indicates how urgently documentation needs updating:
- Score increases with each merged PR affecting the module
- Higher score = more changes since last documentation update
- Score resets to 0 after healing

### Why It's Useful

- **Prioritization**: Focus documentation effort on high-volatility modules
- **Visibility**: Dashboard can show which modules are drifting from docs
- **Automated Healing**: Scheduled jobs can heal modules above threshold

### How to Use

**View Volatility Scores:**
```sql
SELECT name, volatility_score, last_doc_update FROM code_module ORDER BY volatility_score DESC;
```

**Configure Threshold:**
```yaml
docdebt:
  volatility:
    threshold: 50  # Modules above this need attention
    pr-weight: 10  # Each PR adds 10 points
    day-weight: 1  # Each day since last doc update adds 1 point
```

**Interpret Scores:**
- **0-20**: Low volatility - docs likely current
- **21-50**: Medium volatility - review recommended
- **51+**: High volatility - healing needed urgently

---

## Feature 6: Semantic Discovery

### What It Does

When a new module is created (no existing documentation):
- System uses LLM embeddings to find semantically similar existing docs
- Matches new module to most similar existing doc
- Allows reusing or adapting existing documentation structure

### Why It's Useful

- **Consistency**: New modules follow established documentation patterns
- **Time Saving**: Don't start documentation from scratch
- **Knowledge Transfer**: Learn from similar modules' documentation

### How to Use

**Automatic Operation:**
- Triggers during healing when module has no doc path
- Uses Gemini embeddings for similarity search
- Configurable threshold in `application.yml`:
  ```yaml
  docdebt:
    semantic:
      similarity-threshold: 0.85
  ```

**Manual Override:**
If semantic matching is incorrect, manually set doc paths:
```sql
UPDATE code_module
SET technical_doc_path = 'ArchitectureDocs/Technical/NewModule-HLD.md'
WHERE name = 'NewModule';
```

---

## Complete Workflow Example

### Scenario: Adding OAuth2 to AuthService

**1. Developer opens PR**
```
PR #142 opened: "Add OAuth2 support"
```

**2. System performs impact analysis (automatic)**
```
Impact analysis:
- Affected modules: AuthService, UserService
- Doc updates needed:
  - AuthService: technical doc (OAuth2 integration), business doc (new login method)
  - UserService: technical doc (oauth fields in User model)
```

**3. Team reviews impact in logs**
```bash
tail -f logs/application.log
# See impact analysis results
```

**4. Developer merges PR**
```
PR #142 merged
```

**5. System generates summaries (automatic)**
```
Technical: "Added OAuth2 authentication flow with token refresh endpoints..."
Business: "Users can now log in using Google and Microsoft accounts..."
```

**6. Volatility score increases**
```
AuthService: 0 → 10
UserService: 0 → 10
```

**7. Team triggers healing (manual or scheduled)**
```bash
curl -X POST http://localhost:8080/api/modules/AuthService/heal
```

**8. System synthesizes documentation updates**
```
- Reads existing AuthService-HLD.md from SharePoint
- Incorporates OAuth2 changes
- Pushes draft to ArchitectureDocs/Technical/Drafts/AuthService-HLD.md
- Reads existing AuthService-Business.md
- Updates login method section
- Pushes draft to ArchitectureDocs/Business/Drafts/AuthService-Business.md
```

**9. Team reviews drafts in SharePoint**
- Review technical doc draft
- Review business doc draft
- Make manual adjustments if needed

**10. Team publishes drafts**
- Move from Drafts to main folder
- Documentation is now current

**11. System marks PRs as processed, resets volatility**
```
AuthService volatility: 10 → 0
PR #142 marked as processed
```

---

## Feature Comparison Table

| Feature | Trigger | Storage | LLM | Manual? |
|---------|---------|---------|-----|---------|
| PR Impact Analysis | PR opened | Database | Yes | No |
| PR Summarization | PR merged | Database | Yes | No |
| Documentation Healing | Manual/API/Scheduled | Cloud | Yes | Yes (review) |
| Volatility Scoring | PR merged | Database | No | No |
| Semantic Discovery | Healing | Database | Yes | No |

---

## API Endpoints Reference

### Webhook Endpoint

**POST /webhook/github**
- Purpose: Receive GitHub webhook events
- Headers: `X-Hub-Signature-256` (optional), `X-GitHub-Event`
- Body: Raw webhook payload
- Events handled: `pull_request` (opened, closed)
- Returns: 200 on success, 401 on invalid signature

### Module Endpoints (if frontend exists)

**GET /api/modules**
- Purpose: List all modules
- Returns: Array of module objects with volatility scores

**GET /api/modules/{name}**
- Purpose: Get module details
- Returns: Module object with doc paths, PR summaries, volatility

**POST /api/modules/{name}/heal**
- Purpose: Trigger documentation healing
- Returns: HealResultDto with old/new content and draft paths

---

## Configuration Reference

### All Environment Variables

```bash
# GitHub Integration
GITHUB_TOKEN=ghp_your-token
GITHUB_WEBHOOK_SECRET=your-secret

# LLM
GEMINI_API_KEY=AIza...

# Storage Mode
DOC_STORAGE_MODE=local|onedrive|sharepoint

# OneDrive (if mode=onedrive)
ONEDRIVE_CLIENT_ID=your-client-id
ONEDRIVE_DOCS_ROOT=ArchitectureDocs

# SharePoint (if mode=sharepoint)
SHAREPOINT_TENANT_ID=your-tenant-id
SHAREPOINT_CLIENT_ID=your-client-id
SHAREPOINT_CLIENT_SECRET=your-client-secret
SHAREPOINT_SITE_ID=your-site-id
SHAREPOINT_DRIVE_ID=your-drive-id
SHAREPOINT_DOCS_ROOT=ArchitectureDocs

# Database (PostgreSQL, optional)
DB_URL=jdbc:postgresql://localhost:5432/docdebt
DB_USERNAME=docdebt
DB_PASSWORD=your-password

# CORS (if using frontend)
CORS_ORIGIN=http://localhost:5173
```

### All Application Properties

```yaml
docdebt:
  volatility:
    threshold: 50
    pr-weight: 10
    day-weight: 1
    schedule-cron: "0 0 2 * * *"

  github:
    api-base-url: https://api.github.com

  storage:
    mode: local
    local:
      technical-root: ./docs/Technical
      business-root: ./docs/Business

  gemini:
    fast-model: gemini-3.5-flash-lite
    power-model: gemini-3.8-flash
    embedding-model: gemini-embedding-001
    base-url: https://generativelanguage.googleapis.com/v1beta

  semantic:
    similarity-threshold: 0.85
```

---

## Troubleshooting Features

### Impact Analysis Not Running

**Symptoms:** No impact analysis logs when PR opened

**Check:**
1. Webhook configured for `pull_request` events?
2. System running and reachable?
3. Logs show webhook received?
4. GEMINI_API_KEY set and valid?

**Solution:**
```bash
# Test webhook manually
curl -X POST http://localhost:8080/webhook/github \
  -H "X-GitHub-Event: pull_request" \
  -d '{"action":"opened","pull_request":{"number":1,"title":"Test"}}'

# Check logs
tail -f logs/application.log
```

### Healing Produces Empty Docs

**Symptoms:** Drafts contain little or no content

**Check:**
1. Are there unprocessed PR summaries?
2. Is LLM returning empty responses?
3. Check network connectivity to Gemini API

**Solution:**
```sql
-- Check for unprocessed PRs
SELECT * FROM pr_summary WHERE processed = false;

-- Manually trigger healing with verbose logging
# Set logging level in application.yml:
logging:
  level:
    com.docdebt.service.GeminiService: DEBUG
```

### SharePoint Upload Fails

**Symptoms:** Drafts not appearing in SharePoint

**Check:**
1. Credentials correct?
2. Site ID and Drive ID valid?
3. Folder structure exists?
4. API permissions granted?

**Solution:**
```bash
# Test credentials manually
curl -H "Authorization: Bearer YOUR_TOKEN" \
  "https://graph.microsoft.com/v1.0/sites/YOUR_SITE_ID/drives/YOUR_DRIVE_ID/root/children"

# Ensure folders exist in SharePoint
```

### Volatility Score Not Updating

**Symptoms:** Score stays at 0 after PR merge

**Check:**
1. Webhook receiving merged events?
2. Module found in database?
3. PR summary saved successfully?

**Solution:**
```sql
-- Check webhook processed PR
SELECT * FROM pr_summary ORDER BY created_at DESC LIMIT 5;

-- Check module exists
SELECT * FROM code_module WHERE name = 'YourModule';

-- Manually recalculate volatility
# This should happen automatically, but you can check via API
```

---

## Best Practices

### 1. Regular Healing

- Schedule healing for high-volatility modules weekly
- Review drafts promptly after healing
- Publish approved drafts quickly

### 2. Webhook Security

- Always use webhook secret in production
- Rotate secrets periodically
- Monitor for invalid signature attempts

### 3. LLM Cost Management

- Use fast-model for quick summaries
- Use power-model only for complex synthesis
- Monitor usage at Google Cloud Console
- Consider caching repeated analyses

### 4. Documentation Structure

- Follow consistent naming conventions
- Use semantic discovery for new modules
- Maintain folder structure in cloud storage
- Review drafts before publishing

### 5. Module Naming

- Use clear, descriptive module names
- Align with code structure
- Avoid abbreviations
- Consider business domain alignment

---

## Future Enhancements (Not Yet Implemented)

- [ ] Direct PR comments with impact analysis
- [ ] Automatic draft publishing after approval
- [ ] Multi-language documentation support
- [ ] Integration with Confluence/Notion
- [ ] Version history tracking for docs
- [ ] Custom LLM prompt templates
- [ ] Webhook notifications for draft availability
- [ ] Bulk healing for multiple modules
- [ ] Documentation quality scoring
- [ ] Integration with documentation review tools

---

## Summary

The Doc-Debt Tracker provides a complete automated documentation management system:

- **Automated**: PRs trigger analysis without manual intervention
- **Intelligent**: LLMs understand code changes and documentation needs
- **Flexible**: Works with local, OneDrive, or SharePoint storage
- **Scalable**: Handles individual repos or enterprise environments
- **Safe**: Drafts require human review before publishing

Start with local storage and H2 database for immediate use, then migrate to SharePoint and PostgreSQL for production deployment.
