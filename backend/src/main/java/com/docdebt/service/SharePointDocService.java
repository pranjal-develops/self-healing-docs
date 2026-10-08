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
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
@ConditionalOnProperty(name = "docdebt.storage.mode", havingValue = "sharepoint")
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
            @Value("${docdebt.sharepoint.docs-root}") String docsRoot) {

        this.restTemplate = restTemplate;
        this.siteId = siteId;
        this.driveId = driveId;
        this.docsRoot = docsRoot;

        this.credential = new ClientSecretCredentialBuilder()
                .tenantId(tenantId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        log.info("SharePoint client initialized for site: {}, drive: {}", siteId, driveId);
    }

    @Override
    public String getDocumentContent(DocType type, String path) {
        try {
            String fullPath = docsRoot + "/" + subfolder(type) + "/" + path;
            String itemId = findItemIdByPath(fullPath);

            if (itemId == null) {
                log.warn("Document not found: {}", fullPath);
                return null;
            }

            String url = "https://graph.microsoft.com/v1.0/sites/%s/drives/%s/items/%s/content"
                    .formatted(siteId, driveId, itemId);

            HttpHeaders headers = authHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch document from SharePoint: path={}", path, e);
            return null;
        }
    }

    @Override
    public String pushDraft(DocType type, String fileName, String newContent) {
        try {
            String draftPath = docsRoot + "/" + subfolder(type) + "/Drafts/" + fileName;
            String folderPath = docsRoot + "/" + subfolder(type) + "/Drafts";

            ensureFolderExists(folderPath);

            String folderId = findItemIdByPath(folderPath);
            if (folderId == null) {
                throw new IllegalStateException("Drafts folder not found: " + folderPath);
            }

            String url = "https://graph.microsoft.com/v1.0/sites/%s/drives/%s/items/%s:/%s:/content"
                    .formatted(siteId, driveId, folderId, encodePath(fileName));

            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            HttpEntity<String> entity = new HttpEntity<>(newContent, headers);

            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

            log.info("Draft pushed to SharePoint: {}", draftPath);
            return draftPath;
        } catch (Exception e) {
            log.error("Failed to push draft to SharePoint: fileName={}", fileName, e);
            throw new RuntimeException("Failed to push draft to SharePoint", e);
        }
    }

    private String findItemIdByPath(String path) {
        try {
            String encodedPath = encodePath(path);
            String url = "https://graph.microsoft.com/v1.0/sites/%s/drives/%s/root:/%s"
                    .formatted(siteId, driveId, encodedPath);

            HttpHeaders headers = authHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode json = mapper.readTree(response.getBody());
            return json.path("id").asText();
        } catch (Exception e) {
            log.debug("Item not found at path: {}", path);
            return null;
        }
    }

    private void ensureFolderExists(String folderPath) {
        String existingId = findItemIdByPath(folderPath);
        if (existingId != null) {
            return;
        }

        try {
            String[] segments = folderPath.split("/");
            String currentPath = "";
            String parentId = driveId;

            for (String segment : segments) {
                if (segment.isBlank()) continue;

                currentPath = currentPath.isBlank() ? segment : currentPath + "/" + segment;
                String itemId = findItemIdByPath(currentPath);

                if (itemId == null) {
                    String url = "https://graph.microsoft.com/v1.0/sites/%s/drives/%s/items/%s/children"
                            .formatted(siteId, driveId, parentId);

                    HttpHeaders headers = authHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);

                    String body = "{\"name\":\"%s\",\"folder\":{}}".formatted(segment);
                    HttpEntity<String> entity = new HttpEntity<>(body, headers);

                    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                    JsonNode json = mapper.readTree(response.getBody());
                    itemId = json.path("id").asText();

                    log.info("Created folder: {}", currentPath);
                }
                parentId = itemId;
            }
        } catch (Exception e) {
            log.error("Failed to ensure folder exists: {}", folderPath, e);
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        return headers;
    }

    private String getAccessToken() {
        try {
            if (accessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
                return accessToken;
            }

            String token = credential.getToken(new com.azure.core.credential.TokenRequestContext()
                    .addScopes("https://graph.microsoft.com/.default"))
                    .block()
                    .getToken();

            this.accessToken = token;
            this.tokenExpiryTime = System.currentTimeMillis() + 55 * 60 * 1000;

            return token;
        } catch (Exception e) {
            log.error("Failed to get access token", e);
            throw new RuntimeException("Failed to get access token", e);
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
