package com.docdebt.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * OneDrive storage backend via Microsoft Graph /me/drive endpoint.
 * Enable with: docdebt.storage.mode=onedrive
 *
 * Fix: only HttpClientErrorException.NotFound (404) means "file missing" —
 * other errors (auth failures, network issues) now propagate instead of
 * returning null, which would previously cause healing to scaffold a new
 * doc over an existing one.
 */
@Slf4j
@Service
public class OneDriveDocService implements DocStorageService {

    private final RestTemplate restTemplate;
    private final OneDriveAuthService authService;

    @Value("${docdebt.onedrive.docs-root:ArchitectureDocs}")
    private String docsRoot;

    public OneDriveDocService(RestTemplate restTemplate, OneDriveAuthService authService) {
        this.restTemplate = restTemplate;
        this.authService = authService;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authService.getAccessToken());
        return headers;
    }

    @Override
    public String getDocumentContent(DocType type, String path) {
        String fullPath = docsRoot + "/" + subfolder(type) + "/" + path;
        String url = "https://graph.microsoft.com/v1.0/me/drive/root:/%s:/content"
                .formatted(encodePath(fullPath));
        try {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            // 404 is the only case that means "document does not exist yet"
            return null;
        }
        // All other exceptions propagate — don't return null on auth failures or server errors
    }

    @Override
    public String pushDraft(DocType type, String fileName, String newContent) {
        String draftPath = docsRoot + "/" + subfolder(type) + "/Drafts/" + fileName;
        String url = "https://graph.microsoft.com/v1.0/me/drive/root:/%s:/content"
                .formatted(encodePath(draftPath));

        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> entity = new HttpEntity<>(newContent, headers);

        restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        return draftPath;
    }

    private String subfolder(DocType type) {
        return type == DocType.TECHNICAL ? "Technical" : "Business";
    }

    private String encodePath(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .map(seg -> java.net.URLEncoder.encode(seg, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(java.util.stream.Collectors.joining("/"));
    }
}
