package com.docdebt.service;

import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class GitHubService {

    private final RestTemplate restTemplate;

    @Value("${docdebt.github.token}")
    private String githubToken;

    @Value("${docdebt.github.webhook-secret}")
    private String webhookSecret;

    @Value("${docdebt.github.api-base-url}")
    private String apiBaseUrl;

    private static final String HMAC_ALGO = "HmacSHA256";

    public GitHubService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Verifies the X-Hub-Signature-256 header GitHub sends with every webhook
     * delivery. See: https://docs.github.com/webhooks/using-webhooks/validating-webhook-deliveries
     */
    public boolean isValidSignature(String payloadBody, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            // No secret configured (local/demo mode) - skip verification.
            return true;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hash = mac.doFinal(payloadBody.getBytes(StandardCharsets.UTF_8));
            String computed = "sha256=" + Hex.encodeHexString(hash);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Fetches the unified diff for a merged pull request.
     * GitHub serves the diff format when you set Accept: application/vnd.github.v3.diff
     * on the PR resource endpoint.
     */
    public String fetchPullRequestDiff(String repoFullName, long prNumber) {
        String url = "%s/repos/%s/pulls/%d".formatted(apiBaseUrl, repoFullName, prNumber);

        HttpHeaders headers = createGitHubHeaders();
        headers.set("Accept", "application/vnd.github.v3.diff");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
    }

    /**
     * Heuristic: infer which "module" a PR touches from the files changed.
     * Swap this for something smarter (e.g. CODEOWNERS parsing, or feed the
     * diff to Gemini and ask it to classify) as a next step.
     */
    public String inferModuleFromDiff(String diff) {
        if (diff == null) return "Unclassified";
        // naive: look at the first "diff --git a/X/..." path segment
        for (String line : diff.split("\n")) {
            if (line.startsWith("diff --git")) {
                String[] parts = line.split(" ");
                if (parts.length >= 3) {
                    String path = parts[2].replaceFirst("^a/", "");
                    String[] segments = path.split("/");
                    if (segments.length > 0) {
                        return segments[0];
                    }
                }
            }
        }
        return "Unclassified";
    }

    /**
     * Reads a file's content from a GitHub repository via Contents API.
     * Returns a Record containing raw decoded string content and file SHA.
     */
    public record GitHubFile(String content, String sha) {}

    public GitHubFile getFileContent(String repoFullName, String filePath, String branch) {
        String cleanPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        String encodedPath = java.net.URLEncoder.encode(cleanPath, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
        String url = "%s/repos/%s/contents/%s".formatted(apiBaseUrl, repoFullName, encodedPath);
        if (branch != null && !branch.isBlank()) {
            url += "?ref=" + branch;
        }

        HttpHeaders headers = createGitHubHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            var response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getBody() == null) return null;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode responseNode = mapper.readTree(response.getBody());

            String base64Content = responseNode.path("content").asText("").replaceAll("\\s", "");
            String sha = responseNode.path("sha").asText(null);
            String decoded = new String(java.util.Base64.getDecoder().decode(base64Content), StandardCharsets.UTF_8);

            return new GitHubFile(decoded, sha);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            throw new RuntimeException("Failed to fetch file from GitHub: " + filePath, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch file from GitHub: " + filePath, e);
        }
    }

    /**
     * Creates or updates a file directly in the target GitHub repository.
     * Uses PUT /repos/{owner}/{repo}/contents/{path}
     */
    public String createOrUpdateFile(String repoFullName, String filePath, String content, String commitMessage, String branch) {
        GitHubFile existing = getFileContent(repoFullName, filePath, branch);
        String sha = existing != null ? existing.sha() : null;

        String cleanPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        String encodedPath = java.net.URLEncoder.encode(cleanPath, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
        String url = "%s/repos/%s/contents/%s".formatted(apiBaseUrl, repoFullName, encodedPath);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();

        body.put("message", commitMessage);
        body.put("content", java.util.Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        if (sha != null) {
            body.put("sha", sha);
        }
        if (branch != null && !branch.isBlank()) {
            body.put("branch", branch);
        }

        HttpHeaders headers = createGitHubHeaders();
        headers.set("Content-Type", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
        try {
            org.slf4j.LoggerFactory.getLogger(GitHubService.class).info(
                    "Sending GitHub PUT request: url={}, branch={}, shaPresent={}", url, branch, sha != null
            );
            var response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            if (response.getBody() != null) {
                com.fasterxml.jackson.databind.JsonNode responseNode = mapper.readTree(response.getBody());
                return responseNode.path("content").path("html_url").asText(filePath);
            }
            return filePath;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            org.slf4j.LoggerFactory.getLogger(GitHubService.class).error(
                    "GitHub API HTTP Error: status={}, responseBody={}", e.getStatusCode(), e.getResponseBodyAsString()
            );
            throw new RuntimeException("Failed to write file to GitHub: " + filePath + " (HTTP " + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write file to GitHub: " + filePath + " on branch " + branch, e);
        }
    }

    private HttpHeaders createGitHubHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (githubToken != null && !githubToken.isBlank()) {
            headers.set("Authorization", "Bearer " + githubToken);
        }
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return headers;
    }
}
