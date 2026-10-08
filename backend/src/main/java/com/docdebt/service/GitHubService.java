package com.docdebt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Base64;

/**
 * GitHub REST API client.
 *
 * Security fixes:
 * - Signature validation now fails CLOSED when no secret is configured
 *   (previously it silently accepted any payload when secret was empty).
 *   Set docdebt.github.allow-unsigned=true to opt back in to open mode
 *   for local development only.
 * - HMAC is computed over the raw UTF-8 bytes of the payload body, not
 *   the Java String (avoids encoding mismatch issues).
 */
@Slf4j
@Service
public class GitHubService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${docdebt.github.token:}")
    private String githubToken;

    @Value("${docdebt.github.webhook-secret:}")
    private String webhookSecret;

    @Value("${docdebt.github.api-base-url:https://api.github.com}")
    private String apiBaseUrl;

    /**
     * When true, webhooks with no signature are accepted. Set this ONLY for
     * local development/demo when you cannot configure a webhook secret.
     * Default is false (fail closed).
     */
    @Value("${docdebt.github.allow-unsigned:false}")
    private boolean allowUnsigned;

    /**
     * When true, the impact analysis for opened PRs is posted as a PR comment.
     * Off by default to avoid noise on production repos.
     */
    @Value("${docdebt.github.post-pr-comments:false}")
    private boolean postPrComments;

    private static final String HMAC_ALGO = "HmacSHA256";

    public GitHubService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Validates the X-Hub-Signature-256 header.
     *
     * Fail-closed behaviour:
     * - If no webhook secret is configured AND allow-unsigned is false → reject.
     * - If no webhook secret is configured AND allow-unsigned is true → accept
     *   (with a startup warning logged by the controller).
     * - HMAC is computed over the raw bytes of the payload to avoid encoding issues.
     */
    public boolean isValidSignature(byte[] payloadBytes, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            if (allowUnsigned) {
                log.warn("Webhook secret not configured — accepting unsigned webhook (allow-unsigned=true). " +
                        "Set docdebt.github.webhook-secret in production!");
                return true;
            }
            log.warn("Rejecting webhook: no secret configured and allow-unsigned=false. " +
                    "Set GITHUB_WEBHOOK_SECRET or set docdebt.github.allow-unsigned=true for local dev.");
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            // Use raw bytes to match GitHub's HMAC computation exactly
            byte[] hash = mac.doFinal(payloadBytes);
            String computed = "sha256=" + Hex.encodeHexString(hash);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("HMAC verification error", e);
            return false;
        }
    }

    /** @deprecated Use {@link #isValidSignature(byte[], String)} for correct byte-level HMAC */
    @Deprecated
    public boolean isValidSignature(String payloadBody, String signatureHeader) {
        return isValidSignature(payloadBody.getBytes(StandardCharsets.UTF_8), signatureHeader);
    }

    /**
     * Fetches the unified diff for a pull request.
     */
    public String fetchPullRequestDiff(String repoFullName, long prNumber) {
        String url = "%s/repos/%s/pulls/%d".formatted(apiBaseUrl, repoFullName, prNumber);
        HttpHeaders headers = createGitHubHeaders();
        headers.set("Accept", "application/vnd.github.v3.diff");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
    }

    /**
     * Posts an impact analysis as a comment on the given PR.
     * Only called when docdebt.github.post-pr-comments=true.
     */
    public void postPrComment(String repoFullName, long prNumber, String markdownBody) {
        if (!postPrComments) {
            log.debug("PR comments disabled — skipping comment on PR #{}", prNumber);
            return;
        }
        if (githubToken == null || githubToken.isBlank()) {
            log.warn("Cannot post PR comment: GITHUB_TOKEN not configured");
            return;
        }
        String url = "%s/repos/%s/issues/%d/comments".formatted(apiBaseUrl, repoFullName, prNumber);
        ObjectNode body = mapper.createObjectNode();
        body.put("body", markdownBody);

        HttpHeaders headers = createGitHubHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("Posted impact analysis comment on PR #{} in {}", prNumber, repoFullName);
        } catch (Exception e) {
            log.warn("Failed to post PR comment on PR #{} in {}: {}", prNumber, repoFullName, e.getMessage());
        }
    }

    /**
     * Record containing raw decoded file content and the file's current SHA
     * (needed by GitHub's Contents API for update operations).
     */
    public record GitHubFile(String content, String sha) {}

    /**
     * Reads a file from a GitHub repo via the Contents API.
     * Returns null if the file doesn't exist (404).
     * Throws for all other errors (auth, rate-limit, network).
     */
    public GitHubFile getFileContent(String repoFullName, String filePath, String branch) {
        String cleanPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        String encodedPath = java.net.URLEncoder.encode(cleanPath, StandardCharsets.UTF_8)
                .replace("+", "%20").replace("%2F", "/");
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

            var responseNode = mapper.readTree(response.getBody());
            String base64Content = responseNode.path("content").asText("").replaceAll("\\s", "");
            String sha = responseNode.path("sha").asText(null);
            String decoded = new String(Base64.getDecoder().decode(base64Content), StandardCharsets.UTF_8);
            return new GitHubFile(decoded, sha);

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) return null;
            throw new RuntimeException("Failed to fetch file from GitHub: " + filePath + " (" + e.getStatusCode() + ")", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch file from GitHub: " + filePath, e);
        }
    }

    /**
     * Creates or updates a file in a GitHub repo.
     * Returns the HTML URL of the file.
     */
    public String createOrUpdateFile(String repoFullName, String filePath, String content,
                                      String commitMessage, String branch) {
        GitHubFile existing = getFileContent(repoFullName, filePath, branch);
        String sha = existing != null ? existing.sha() : null;

        String cleanPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        String encodedPath = java.net.URLEncoder.encode(cleanPath, StandardCharsets.UTF_8)
                .replace("+", "%20").replace("%2F", "/");
        String url = "%s/repos/%s/contents/%s".formatted(apiBaseUrl, repoFullName, encodedPath);

        ObjectNode body = mapper.createObjectNode();
        body.put("message", commitMessage);
        body.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
        if (sha != null) body.put("sha", sha);
        if (branch != null && !branch.isBlank()) body.put("branch", branch);

        HttpHeaders headers = createGitHubHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        try {
            log.info("GitHub PUT: url={}, branch={}, shaPresent={}", url, branch, sha != null);
            var response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            if (response.getBody() != null) {
                var responseNode = mapper.readTree(response.getBody());
                return responseNode.path("content").path("html_url").asText(filePath);
            }
            return filePath;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("GitHub API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to write to GitHub: " + filePath + " (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write to GitHub: " + filePath, e);
        }
    }

    public boolean isPostPrCommentsEnabled() {
        return postPrComments;
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
