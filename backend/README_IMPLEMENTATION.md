# Doc-Debt Tracker - PR-Driven Documentation Update System

## Overview

This system automatically analyzes GitHub Pull Requests, determines which documentation needs updating based on code changes, and reads/writes documentation from cloud storage (OneDrive or SharePoint). It uses LLM-powered impact analysis to identify cross-module dependencies and documentation requirements.

## Architecture

### Core Components

1. **GitHubWebhookController** - Receives GitHub webhook events
   - Handles `pull_request` events (opened, closed/merged)
   - On PR opened: Triggers impact analysis to identify affected modules
   - On PR merged: Generates summaries and updates volatility scores

2. **ImpactAnalysisService** - LLM-powered dependency analysis
   - Analyzes PR diff to determine affected modules
   - Identifies which documentation (technical/business) needs updates
   - Returns structured impact results with specific update requirements

3. **HealingService** - Documentation update orchestration
   - Reads existing documentation from configured storage (local/OneDrive/SharePoint)
   - Synthesizes updates using LLM based on accumulated PR summaries
   - Pushes updated drafts to cloud storage
   - Supports auto-scaffolding for new documentation

4. **DocStorageService Interface** - Storage abstraction
   - **LocalDocService** - Local file system (default, zero setup)
   - **OneDriveDocService** - Personal OneDrive via delegated auth
   - **SharePointDocService** - Enterprise SharePoint via REST API + Azure Identity

5. **GeminiService** - LLM integration
   - Generates technical and business summaries from PR diffs
   - Synthesizes documentation updates
   - Performs impact analysis for cross-module dependencies
   - Generates embeddings for semantic discovery

## Workflow

### When a PR is Opened

```
GitHub Webhook (opened)
  ↓
GitHubWebhookController.handlePROpened()
  ↓
ImpactAnalysisService.analyzeImpact()
  ↓
GeminiService.analyzeImpact() - LLM analyzes diff
  ↓
Returns: affectedModules + docUpdatesNeeded
  ↓
Logs which modules need documentation updates
```

### When a PR is Merged

```
GitHub Webhook (closed + merged)
  ↓
GitHubWebhookController.handlePullRequestEvent()
  ↓
GitHubService.fetchPullRequestDiff()
  ↓
GeminiService.summarizeDiff() - Generate technical + business summaries
  ↓
Persist PrSummary to database
  ↓
VolatilityService.recalculate() - Update module volatility score
```

### Manual Healing / Scheduled Job

```
Trigger healing for a module
  ↓
HealingService.heal()
  ↓
Fetch unprocessed PrSummaries for module
  ↓
For each doc type (technical, business):
  - Read existing doc from cloud storage (SharePoint/OneDrive)
  - Synthesize update using LLM + accumulated summaries
  - Push draft to cloud storage
  - Update embeddings for semantic discovery
  ↓
Mark PrSummaries as processed
  ↓
Reset module volatility score
```

## Configuration

### Storage Mode Selection

Set via environment variable `DOC_STORAGE_MODE`:

- `local` (default) - Local file system, no setup required
- `onedrive` - Personal OneDrive (consumer account)
- `sharepoint` - Enterprise SharePoint/OneDrive for Business

### Local Storage (Default)

```yaml
docdebt:
  storage:
    mode: local
    local:
      technical-root: ./docs/Technical
      business-root: ./docs/Business
```

No additional setup needed.

### OneDrive Storage

```yaml
docdebt:
  storage:
    mode: onedrive
  onedrive:
    client-id: ${ONEDRIVE_CLIENT_ID}
    token-store-path: ./data/onedrive-token.json
    docs-root: ArchitectureDocs
```

**Setup Steps:**

1. Register app in Azure Portal (Microsoft Entra ID)
   - Platform: Mobile and desktop applications
   - Redirect URI: `https://login.microsoftonline.com/common/oauth2/nativeclient`
   - Permissions: `Files.ReadWrite`

2. Get Client ID from app registration

3. Configure environment variable:
   ```bash
   export ONEDRIVE_CLIENT_ID=your-client-id
   export DOC_STORAGE_MODE=onedrive
   ```

4. On first run, system will prompt for device code authentication
   - Visit `https://microsoft.com/devicelogin`
   - Enter the displayed code
   - Authorize the app

5. Token is stored locally and automatically refreshed

### SharePoint Storage

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

**Note:** The SharePoint integration uses direct REST API calls with Azure Identity for authentication, avoiding Microsoft Graph SDK dependency issues. This approach is simpler and more reliable for the current use case.

**Setup Steps:**

1. Register app in Azure Portal (Microsoft Entra ID)
   - Supported account types: "Accounts in this organizational directory only"
   - Client secret: Generate and save

2. Configure API Permissions:
   - Microsoft Graph → Application permissions
   - `Sites.ReadWrite.All`
   - Grant admin consent

3. Get identifiers:
   - **Tenant ID**: From Azure Portal → Microsoft Entra ID → Overview
   - **Client ID**: From app registration → Overview
   - **Client Secret**: From app registration → Certificates & secrets
   - **Site ID**: Run PowerShell:
     ```powershell
     Connect-PnPOnline -Url "https://yourtenant.sharepoint.com/sites/yoursite" -Interactive
     Get-PnPSite
     ```
   - **Drive ID**: Run PowerShell:
     ```powershell
     Get-PnPDrive
     ```

4. Configure environment variables:
   ```bash
   export SHAREPOINT_TENANT_ID=your-tenant-id
   export SHAREPOINT_CLIENT_ID=your-client-id
   export SHAREPOINT_CLIENT_SECRET=your-client-secret
   export SHAREPOINT_SITE_ID=your-site-id
   export SHAREPOINT_DRIVE_ID=your-drive-id
   export DOC_STORAGE_MODE=sharepoint
   ```

5. Create folder structure in SharePoint:
   - `{docs-root}/Technical/`
   - `{docs-root}/Business/`
   - `{docs-root}/Technical/Drafts/`
   - `{docs-root}/Business/Drafts/`

## GitHub Webhook Setup

1. Configure webhook in your GitHub repository:
   - URL: `https://your-domain.com/webhook/github`
   - Content type: `application/json`
   - Events: `Pull requests`

2. Set webhook secret (recommended for production):
   ```bash
   export GITHUB_WEBHOOK_SECRET=your-random-secret
   ```

3. Configure GitHub token (for fetching PR diffs):
   ```bash
   export GITHUB_TOKEN=ghp_your-personal-access-token
   ```

## LLM Configuration (Gemini)

The system uses Google Gemini for AI-powered analysis. Configuration is streamlined for seamless use.

```yaml
docdebt:
  gemini:
    api-key: ${GEMINI_API_KEY}
    fast-model: gemini-3.5-flash-lite
    power-model: gemini-3.8-flash
    embedding-model: gemini-embedding-001
    base-url: https://generativelanguage.googleapis.com/v1beta
```

**Setup (5 minutes):**

1. **Get API Key from Google AI Studio**
   - Visit: https://aistudio.google.com/app/apikey
   - Sign in with your Google account
   - Click "Create API Key"
   - Select or create a Google Cloud project
   - Copy the API key (starts with `AIza...`)

2. **Set Environment Variable**
   ```bash
   export GEMINI_API_KEY=AIzaSy...
   ```

3. **That's it!** The system will:
   - Automatically handle authentication via the API key
   - Manage retries and error handling
   - Log all LLM requests for debugging
   - Use appropriate models for each task

**Model Usage:**

- **`fast-model`** (gemini-3.5-flash-lite): Quick PR summaries on webhook
- **`power-model`** (gemini-3.8-flash): Complex doc synthesis and impact analysis
- **`embedding-model`** (gemini-embedding-001): Semantic discovery for matching docs

**Quotas & Pricing:**

- **Free tier**: 15 requests/minute, 1500 requests/day
- **Paid plans**: Start at $0.00025/1K characters (flash models)
- Check quotas: https://aistudio.google.com/app/apikey
- Production usage requires paid plan

**Testing Your Setup:**

```bash
# Quick test that your API key works
curl -H "x-goog-api-key: YOUR_API_KEY" \
  "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent" \
  -H "Content-Type: application/json" \
  -d '{"contents":[{"parts":[{"text":"Hello"}]}]}'
```

**Troubleshooting Gemini:**

- **403 Forbidden**: API key invalid or quota exceeded → regenerate key
- **429 Too Many Requests**: Rate limit → wait or upgrade plan
- **Empty Response**: Network issue or model down → check logs
- **Cost concerns**: Monitor usage at Google Cloud Console

## Database Configuration

### H2 (Default - Demo/Development)

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/docdebt;AUTO_SERVER=TRUE
    username: sa
    password:
    driver-class-name: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
```

No setup required. Database created automatically.

### PostgreSQL (Production)

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/docdebt}
    username: ${DB_USERNAME:docdebt}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

## Running the Application

### Development

```bash
# Set required environment variables
export GEMINI_API_KEY=your-api-key
export GITHUB_TOKEN=your-token

# Run with Maven
./mvnw spring-boot:run

# Or run the JAR
./mvnw clean package
java -jar target/doc-debt-tracker-1.0.0.jar
```

### Production

```bash
# Configure all required environment variables
export DOC_STORAGE_MODE=sharepoint
export SHAREPOINT_TENANT_ID=xxx
export SHAREPOINT_CLIENT_ID=xxx
export SHAREPOINT_CLIENT_SECRET=xxx
export SHAREPOINT_SITE_ID=xxx
export SHAREPOINT_DRIVE_ID=xxx
export GEMINI_API_KEY=xxx
export GITHUB_TOKEN=xxx
export GITHUB_WEBHOOK_SECRET=xxx
export DB_URL=jdbc:postgresql://prod-db:5432/docdebt
export DB_USERNAME=docdebt
export DB_PASSWORD=xxx

# Run
java -jar doc-debt-tracker-1.0.0.jar
```

### Docker

```bash
# Build image
docker build -t doc-debt-tracker .

# Run with environment variables
docker run -p 8080:8080 \
  -e DOC_STORAGE_MODE=sharepoint \
  -e SHAREPOINT_TENANT_ID=xxx \
  -e SHAREPOINT_CLIENT_ID=xxx \
  -e SHAREPOINT_CLIENT_SECRET=xxx \
  -e SHAREPOINT_SITE_ID=xxx \
  -e SHAREPOINT_DRIVE_ID=xxx \
  -e GEMINI_API_KEY=xxx \
  -e GITHUB_TOKEN=xxx \
  doc-debt-tracker
```

## API Endpoints

### Webhook

- `POST /webhook/github` - GitHub webhook receiver
  - Validates HMAC signature
  - Processes `pull_request` events
  - Returns 200 on success

### Dashboard (if frontend exists)

- `GET /api/modules` - List all modules
- `GET /api/modules/{name}` - Get module details
- `POST /api/modules/{name}/heal` - Trigger documentation healing

## Error Handling

The system includes comprehensive error handling:

- **Webhook validation**: Invalid signatures are rejected with 401
- **LLM failures**: Fallback to simple module inference
- **Storage failures**: Graceful degradation with logging
- **Configuration errors**: Clear error messages on startup

## Security Considerations

1. **Webhook secrets**: Always use in production to verify webhook authenticity
2. **Token storage**: OneDrive tokens stored locally, SharePoint secrets in environment
3. **API permissions**: Use least-privilege access (e.g., `Sites.ReadWrite.All` not `Sites.FullControl.All`)
4. **Database credentials**: Never commit to repository, use environment variables
5. **CORS**: Configure allowed origins for frontend integration

## Troubleshooting

### SharePoint Authentication Fails

- Verify tenant ID, client ID, and client secret are correct
- Ensure admin consent was granted for API permissions
- Check that site ID and drive ID correspond to the correct SharePoint site

### LLM Returns Empty Response

- Verify GEMINI_API_KEY is valid and has quota
- Check network connectivity to Google AI API
- Review logs for specific error messages

### Module Not Found in Impact Analysis

- Ensure modules exist in database (create via first PR or manually)
- Check that module names in database match those referenced in code

### Document Not Found in Cloud Storage

- Verify folder structure exists in SharePoint/OneDrive
- Check that `docs-root` configuration matches actual folder path
- Ensure storage mode is correctly configured

## Extending the System

### Adding New Storage Backend

1. Implement `DocStorageService` interface
2. Add `@ConditionalOnProperty` annotation for activation
3. Configure in `application.yml`
4. Add setup instructions to README

### Custom Impact Analysis

Modify `ImpactAnalysisService.analyzeImpact()` to:
- Use different LLM models
- Implement custom dependency parsing
- Add static analysis of code changes

### Additional Doc Types

1. Add new enum value to `DocType`
2. Update `HealingService` to handle new type
3. Add folder configuration in storage services
4. Update LLM prompts for new doc type

## Performance Considerations

- **LLM caching**: Consider caching repeated analyses
- **Batch processing**: Process multiple PRs in single LLM call
- **Async processing**: Use message queue for webhook processing at scale
- **Rate limiting**: Respect GitHub API rate limits (5000/hour authenticated)

## License

[Your License Here]
