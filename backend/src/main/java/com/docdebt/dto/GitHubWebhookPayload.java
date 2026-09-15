package com.docdebt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// Minimal shape of a GitHub "pull_request" webhook event, only the fields we use.
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubWebhookPayload {

    public String action; // e.g. "closed"

    @JsonProperty("pull_request")
    public PullRequest pullRequest;

    public Repository repository;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PullRequest {
        public long number;
        public String title;
        public boolean merged;
        public String html_url;
        public User user;
        public String base_sha_placeholder; // not part of real payload; diff fetched separately
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        public String login;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        @JsonProperty("full_name")
        public String fullName; // "owner/repo"
    }
}
