package com.docdebt.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Personal Microsoft accounts (outlook.com/hotmail, "consumers" tenant) do
 * NOT support the client_credentials (app-only) grant - only work/school
 * tenants do. So for personal OneDrive we use delegated auth:
 *
 *   1. One-time setup: run OneDriveLoginTool, which does the Device Code
 *      Flow - you approve it once in a browser - and caches the resulting
 *      access_token + refresh_token via OneDriveTokenStore.
 *   2. At runtime, this service just refreshes the access_token using the
 *      cached refresh_token (refresh tokens for MSA are long-lived and are
 *      rotated automatically on each use).
 *
 * Docs: https://learn.microsoft.com/entra/identity-platform/v2-oauth2-device-code
 */
@Service
public class OneDriveAuthService {

    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "Files.ReadWrite offline_access";

    private final RestTemplate restTemplate;
    private final OneDriveTokenStore tokenStore;

    @Value("${docdebt.onedrive.client-id}")
    private String clientId;

    private volatile String cachedAccessToken;
    private volatile Instant cachedExpiry = Instant.EPOCH;

    public OneDriveAuthService(RestTemplate restTemplate,
                                @Value("${docdebt.onedrive.token-store-path}") String tokenStorePath) {
        this.restTemplate = restTemplate;
        this.tokenStore = new OneDriveTokenStore(tokenStorePath);
    }

    public synchronized String getAccessToken() {
        if (cachedAccessToken != null && Instant.now().isBefore(cachedExpiry)) {
            return cachedAccessToken;
        }

        OneDriveTokenStore.TokenRecord stored = tokenStore.load();
        if (stored == null) {
            throw new IllegalStateException(
                    "No OneDrive token found. Run the one-time login tool first:\n" +
                    "  mvn compile exec:java -Dexec.mainClass=com.docdebt.tools.OneDriveLoginTool");
        }

        // If the cached access token in the file is still fresh, use it directly.
        if (Instant.now().isBefore(Instant.ofEpochSecond(stored.expiresAtEpochSeconds()))) {
            cachedAccessToken = stored.accessToken();
            cachedExpiry = Instant.ofEpochSecond(stored.expiresAtEpochSeconds());
            return cachedAccessToken;
        }

        return refresh(stored.refreshToken());
    }

    private String refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("scope", SCOPE);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        JsonNode response = restTemplate.postForObject(TOKEN_URL, entity, JsonNode.class);

        if (response.has("error")) {
            throw new IllegalStateException("OneDrive token refresh failed: " + response.path("error_description").asText());
        }

        String newAccessToken = response.path("access_token").asText();
        // MSA rotates refresh tokens on every use - always save the new one.
        String newRefreshToken = response.path("refresh_token").asText(refreshToken);
        int expiresIn = response.path("expires_in").asInt(3600);
        long expiresAt = Instant.now().plusSeconds(expiresIn - 60).getEpochSecond();

        tokenStore.save(new OneDriveTokenStore.TokenRecord(newAccessToken, newRefreshToken, expiresAt));

        cachedAccessToken = newAccessToken;
        cachedExpiry = Instant.ofEpochSecond(expiresAt);
        return cachedAccessToken;
    }
}
