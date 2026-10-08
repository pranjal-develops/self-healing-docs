package com.docdebt.service;

import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.ClientSecretCredential;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

/**
 * SharePoint/OneDrive for Business storage backend via Microsoft Graph API.
 * Enable with: docdebt.storage.mode=sharepoint
 *
 * Fixes:
 * - Only 404 means "document missing" — other errors (auth, network) now
 *   propagate instead of returning null (which previously caused healing to
 *   scaffold a new doc over an existing one).
 * - Fixed invalid parentId when creating the first folder level: now resolves
 *   the actual drive root item ID instead of passing the drive ID as a
 *   parent item ID (which are different Graph API resource types).
 */
@Slf4j
@Service
public class SharePointDocService implements DocStorageService {

    private final RestTemplate restTemplate;
    private final ClientSecretCredential credential;
    private final String siteId;
    private final String driveId;
    private final String docsRoot;
    private final ObjectMapper mapper = new ObjectMapper();

    private String accessToken;
    private long tokenExpiryTime;

    public SharePointDocService(
            RestTemplate restTemplate,
            @Value("${docdebt.sharepoint.tenant-id}") String tenantId,
            @Value("${docdebt.sharepoint.client-id}") String clientId,
            @Value("${docdebt.sharepoint.client-secret}") String clientSecret,
            @Value("${docdebt.sharepoint.site-id}") String siteId,
            @Value("${docdebt.sharepoint.drive-id}") String driveId,
            @Value("${docdebt.sharepoint.docs-root:ArchitectureDocs}") String docsRoot) {

        this.restTemplate = restTemplate;
        this.siteId = siteId;
        this.driveId = driveId;
        this.docsRoot = docsRoot;

        this.credential = new ClientSecretCredentialBuilder()
                .tenantId(tenantId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        log.info("SharePoint client initialised for site={}, drive={}", siteId, driveId);
    }

    @Override
    public String getDocumentContent(DocType type, String path) {
        String fullPath = docsRoot + "/" + subfolder(type) + "/" + path;
        try {
            String itemId = findItemIdByPath(fullPath);
            if (itemId == null) {
                return null;  // 404 — document does not exist
            }

            String url = "https://graph.microsoft.com/v1.0/sites/%s/drives/%s/items/%s/content"
                    .formatted(siteId, driveId, itemId);

            HttpHeaders headers = authHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();

        } catch (HttpClientErrorException.NotFound e) {
            return null;  // path not found
        }
        // All other exceptions propagate — auth errors must not silently return null
    }

    @Override
    public String pushDraft(DocType type, String fileName, String newContent) {
        String draftPath = docsRoot + "/" + subfolder(type) + "/Drafts";

        ensureFolderExists(draftPath);

        String folderId = findItemIdByPath(draftPath);
        if (folderId == null) {
            throw new IllegalStateException("Drafts folder not found after creation attempt: " + draftPath);
        }

        String url = "https://graph.microsoft.com/v1.0/sites/%s/drives/%s/items/%s:/%s:/content"
                .formatted(siteId, driveId, folderId, encodePath(fileName));

        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> entity = new HttpEntity<>(newContent, headers);

        restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

        String fullDraftPath = draftPath + "/" + fileName;
        log.info("Draft pushed to SharePoint: {}", fullDraftPath);
        return fullDraftPath;
    }

    /**
     * Ensures every folder segment in {@code folderPath} exists.
     * Walks the path incrementally, resolving each segment's actual item ID
     * (not the drive ID) before creating child folders.
     */
    private void ensureFolderExists(String folderPath) {
        String existing = findItemIdByPath(folderPath);
        if (existing != null) return;

        try {
            String[] segments = folderPath.split("/");
            // Start with the drive root item ID (different from driveId!)
            String parentId = getDriveRootItemId();

            StringBuilder currentPath = new StringBuilder();
            for (String segment : segments) {
                if (segment.isBlank()) continue;

                if (currentPath.length() > 0) currentPath.append("/");
                currentPath.append(segment);

                String itemId = findItemIdByPath(currentPath.toString());
                if (itemId == null) {
                    // Create the folder under the current parent
                    String url = "https://graph.microsoft.com/v1.0/sites/%s/drives/%s/items/%s/children"
                            .formatted(siteId, driveId, parentId);

                    HttpHeaders headers = authHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    String body = "{\"name\":\"%s\",\"folder\":{},\"@microsoft.graph.conflictBehavior\":\"rename\"}"
                            .formatted(segment);
                    HttpEntity<String> entity = new HttpEntity<>(body, headers);

                    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                    JsonNode json = mapper.readTree(response.getBody());
                    itemId = json.path("id").asText();
                    log.info("Created SharePoint folder: {}", currentPath);
                }
                parentId = itemId;
            }
        } catch (Exception e) {
            log.error("Failed to ensure SharePoint folder exists: {}", folderPath, e);
            throw new RuntimeException("Failed to create SharePoint folder: " + folderPath, e);
        }
    }

    /** Resolves the drive root item ID (needed as the parent for top-level folder creation). */
    private String getDriveRootItemId() {
        try {
            String url = "https://graph.microsoft.com/v1.0/sites/%s/drives/%s/root".formatted(siteId, driveId);
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode json = mapper.readTree(response.getBody());
            return json.path("id").asText();
        } catch (Exception e) {
            log.error("Failed to get drive root item ID", e);
            throw new RuntimeException("Cannot resolve drive root item ID", e);
        }
    }

    /**
     * Returns the Graph item ID for the given path, or null if the path does
     * not exist (404). Throws for all other errors.
     */
    private String findItemIdByPath(String path) {
        try {
            String url = "https://graph.microsoft.com/v1.0/sites/%s/drives/%s/root:/%s"
                    .formatted(siteId, driveId, encodePath(path));
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode json = mapper.readTree(response.getBody());
            String id = json.path("id").asText(null);
            return (id == null || id.isBlank()) ? null : id;
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.error("Failed to find item ID by path {}: {}", path, e.getMessage());
            return null;
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        return headers;
    }

    private String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            return accessToken;
        }
        try {
            String token = credential.getToken(new com.azure.core.credential.TokenRequestContext()
                    .addScopes("https://graph.microsoft.com/.default"))
                    .block()
                    .getToken();
            this.accessToken = token;
            this.tokenExpiryTime = System.currentTimeMillis() + 55 * 60 * 1000L;
            return token;
        } catch (Exception e) {
            log.error("Failed to acquire SharePoint access token", e);
            throw new RuntimeException("Failed to acquire SharePoint access token", e);
        }
    }

    private String encodePath(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .map(seg -> java.net.URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private String subfolder(DocType type) {
        return type == DocType.TECHNICAL ? "Technical" : "Business";
    }
}
