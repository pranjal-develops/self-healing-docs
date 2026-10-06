# Quick Start Guide

## Get Gemini API Key

1. Visit [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Sign in with your Google account
3. Click "Create API Key"
4. Select or create a Google Cloud project
5. Copy the API key (starts with `AIza...`)
6. Set environment variable:
   ```bash
   export GEMINI_API_KEY=AIza...
   ```

**Notes:**
- Free tier includes 15 requests/minute
- Production requires a paid plan
- Keep your API key secret - never commit to git

## Minimal Setup (Local Storage + H2 Database)

```bash
# 1. Set required environment variables
export GEMINI_API_KEY=your-gemini-api-key
export GITHUB_TOKEN=ghp_your-github-token

# 2. Run the application
./mvnw spring-boot:run
```

That's it! The system will:
- Use local file storage (`./docs/Technical` and `./docs/Business`)
- Use H2 database (no setup required)
- Listen for GitHub webhooks on port 8080
- Use Gemini for AI-powered analysis

## Configure GitHub Webhook

1. Go to your GitHub repository → Settings → Webhooks
2. Add webhook:
   - Payload URL: `http://your-server:8080/webhook/github`
   - Content type: `application/json`
   - Events: `Pull requests`
3. (Optional) Set webhook secret and set `GITHUB_WEBHOOK_SECRET` env var

## Switch to SharePoint (Enterprise)

```bash
export DOC_STORAGE_MODE=sharepoint
export SHAREPOINT_TENANT_ID=your-tenant-id
export SHAREPOINT_CLIENT_ID=your-client-id
export SHAREPOINT_CLIENT_SECRET=your-client-secret
export SHAREPOINT_SITE_ID=your-site-id
export SHAREPOINT_DRIVE_ID=your-drive-id
```

See [README_IMPLEMENTATION.md](README_IMPLEMENTATION.md) for detailed SharePoint setup.

## Switch to PostgreSQL

```bash
export DB_URL=jdbc:postgresql://localhost:5432/docdebt
export DB_USERNAME=docdebt
export DB_PASSWORD=your-password
```

## How It Works

1. **PR Opened**: System analyzes diff → identifies affected modules → logs which docs need updates
2. **PR Merged**: System generates summaries → stores in database → updates volatility score
3. **Manual Healing**: Trigger healing for a module → reads docs from cloud → synthesizes updates → pushes drafts

## Key Files

- `pom.xml` - Dependencies (includes Azure Identity for SharePoint)
- `application.yml` - Configuration
- `ImpactAnalysisService.java` - LLM-powered dependency analysis
- `SharePointDocService.java` - Enterprise cloud storage integration
- `GitHubWebhookController.java` - Webhook handler (handles opened + merged PRs)

## For Complete Documentation

See [README_IMPLEMENTATION.md](README_IMPLEMENTATION.md)
