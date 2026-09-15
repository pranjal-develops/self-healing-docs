package com.docdebt.service;

/**
 * Storage-backend abstraction for reading/writing the technical and business
 * docs. Two implementations exist: LocalDocService (default - plain files on
 * disk, zero auth) and OneDriveDocService (personal OneDrive via delegated
 * auth - opt-in once you're ready to scale past local files). Swap between
 * them with docdebt.storage.mode = local | onedrive.
 */
public interface DocStorageService {

    /** Returns the doc's current text content, or null if it doesn't exist yet. */
    String getDocumentContent(DocType type, String path);

    /** Writes new content as an unpublished draft; returns the draft's path/identifier. */
    String pushDraft(DocType type, String fileName, String content);
}
