package com.docdebt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Opt-in storage backend: reads/writes docs on a personal OneDrive via
 * Microsoft Graph's /me/drive endpoint (delegated auth - see
 * OneDriveAuthService for the token lifecycle). Disabled by default -
 * enable with docdebt.storage.mode=onedrive once local storage has proven
 * the pipeline out and you're ready to deal with the Entra app registration.
 *
 * Docs: https://learn.microsoft.com/graph/api/driveitem-put-content
 */
@Service
@ConditionalOnProperty(name = "docdebt.storage.mode", havingValue = "onedrive")
public class OneDriveDocService implements DocStorageService {

    private final RestTemplate restTemplate;
    private final OneDriveAuthService authService;

    @Value("${docdebt.onedrive.docs-root}")
    private String docsRoot; // e.g. "ArchitectureDocs"

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
            return null;
        }
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
