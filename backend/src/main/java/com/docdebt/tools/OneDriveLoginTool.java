package com.docdebt.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.docdebt.service.OneDriveTokenStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One-time setup tool for personal OneDrive access. Run this once (and again
 * only if you ever delete the token file or revoke consent):
 *
 *   mvn compile exec:java -Dexec.mainClass=com.docdebt.tools.OneDriveLoginTool
 *
 * It walks you through Microsoft's Device Code Flow: prints a short code and
 * a URL, you approve it once in any browser (phone is fine), and it caches
 * an access_token + refresh_token to ./data/onedrive-token.json for the main
 * app to use from then on.
 *
 * Requires env var ONEDRIVE_CLIENT_ID (the Application (client) ID from your
 * Entra app registration - see README for how to register one that supports
 * "Personal Microsoft accounts").
 */
public class OneDriveLoginTool {

    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "Files.ReadWrite offline_access";

    private static final HttpClient http = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String clientId = System.getenv("ONEDRIVE_CLIENT_ID");
        if (clientId == null || clientId.isBlank()) {
            System.err.println("Set ONEDRIVE_CLIENT_ID first, e.g.:");
            System.err.println("  export ONEDRIVE_CLIENT_ID=your-app-client-id");
            System.exit(1);
        }
        String tokenStorePath = System.getenv().getOrDefault("ONEDRIVE_TOKEN_STORE_PATH", "./data/onedrive-token.json");

        JsonNode deviceCodeResponse = requestDeviceCode(clientId);
        String deviceCode = deviceCodeResponse.path("device_code").asText();
        String userCode = deviceCodeResponse.path("user_code").asText();
        String verificationUri = deviceCodeResponse.path("verification_uri").asText();
        int expiresIn = deviceCodeResponse.path("expires_in").asInt(900);
        int interval = deviceCodeResponse.path("interval").asInt(5);

        System.out.println();
        System.out.println("===========================================================");
        System.out.println(" 1. Open:  " + verificationUri);
        System.out.println(" 2. Enter this code:  " + userCode);
        System.out.println(" 3. Sign in with the personal Microsoft account whose");
        System.out.println("    OneDrive should store the architecture docs.");
        System.out.println("===========================================================");
        System.out.println("Waiting for you to approve (checking every " + interval + "s)...");

        Instant deadline = Instant.now().plusSeconds(expiresIn);
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(interval * 1000L);
            JsonNode tokenResponse = pollForToken(clientId, deviceCode);

            String error = tokenResponse.path("error").asText(null);
            if (error == null) {
                String accessToken = tokenResponse.path("access_token").asText();
                String refreshToken = tokenResponse.path("refresh_token").asText();
                int expiresInSec = tokenResponse.path("expires_in").asInt(3600);
                long expiresAt = Instant.now().plusSeconds(expiresInSec - 60).getEpochSecond();

                new OneDriveTokenStore(tokenStorePath)
                        .save(new OneDriveTokenStore.TokenRecord(accessToken, refreshToken, expiresAt));

                System.out.println();
                System.out.println("Success! Token cached at " + tokenStorePath);
                System.out.println("You can now start the main app normally.");
                return;
            }

            switch (error) {
                case "authorization_pending" -> { /* keep polling */ }
                case "slow_down" -> Thread.sleep(interval * 1000L);
                default -> {
                    System.err.println("Login failed: " + error + " - " +
                            tokenResponse.path("error_description").asText());
                    System.exit(1);
                }
            }
        }
        System.err.println("Timed out waiting for approval. Run the tool again.");
        System.exit(1);
    }

    private static JsonNode requestDeviceCode(String clientId) throws IOException, InterruptedException {
        String form = "client_id=" + clientId + "&scope=" + SCOPE.replace(" ", "%20");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEVICE_CODE_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }

    private static JsonNode pollForToken(String clientId, String deviceCode) throws IOException, InterruptedException {
        Map<String, String> params = Map.of(
                "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                "client_id", clientId,
                "device_code", deviceCode
        );
        String form = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(form))
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(response.body());
    }
}
