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

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github.v3.diff");
        headers.set("X-GitHub-Api-Version", "2022-11-28");

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
}
