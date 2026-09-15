package com.docdebt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Default storage backend: plain local files, no auth needed at all. Great
 * for getting the Map-Reduce pipeline working end-to-end before wiring up
 * OneDrive (or any other real doc store) later.
 *
 * Layout:
 *   {technical-root}/{path}              <- published technical docs
 *   {technical-root}/Drafts/{path}       <- healed technical drafts
 *   {business-root}/{path}               <- published business docs
 *   {business-root}/Drafts/{path}        <- healed business drafts
 */
@Service
@ConditionalOnProperty(name = "docdebt.storage.mode", havingValue = "local", matchIfMissing = true)
public class LocalDocService implements DocStorageService {

    @Value("${docdebt.storage.local.technical-root}")
    private String technicalRoot;

    @Value("${docdebt.storage.local.business-root}")
    private String businessRoot;

    @Override
    public String getDocumentContent(DocType type, String path) {
        Path file = Path.of(rootFor(type), path);
        try {
            if (!Files.exists(file)) return null;
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + file, e);
        }
    }

    @Override
    public String pushDraft(DocType type, String fileName, String content) {
        Path file = Path.of(rootFor(type), "Drafts", fileName);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + file, e);
        }
    }

    private String rootFor(DocType type) {
        return type == DocType.TECHNICAL ? technicalRoot : businessRoot;
    }
}
