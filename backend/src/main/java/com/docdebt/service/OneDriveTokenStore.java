package com.docdebt.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the OneDrive delegated OAuth tokens to a small local JSON file so
 * the one-time device-code login doesn't need to be repeated every restart.
 *
 * Personal Microsoft accounts don't support app-only (client_credentials)
 * auth, so unlike the SharePoint/app-only version, this is a *delegated*
 * token tied to whichever person completed the device-code login.
 */
public class OneDriveTokenStore {

    public record TokenRecord(String accessToken, String refreshToken, long expiresAtEpochSeconds) {}

    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper();

    public OneDriveTokenStore(String pathStr) {
        this.path = Path.of(pathStr);
    }

    public TokenRecord load() {
        try {
            if (!Files.exists(path)) return null;
            return mapper.readValue(path.toFile(), TokenRecord.class);
        } catch (IOException e) {
            return null;
        }
    }

    public void save(TokenRecord record) {
        try {
            File parent = path.toFile().getParentFile();
            if (parent != null) parent.mkdirs();
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), record);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save OneDrive token to " + path, e);
        }
    }
}
