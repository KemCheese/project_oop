package com.yourapp.ticketpass.shared.storage;

import java.io.InputStream;
import java.util.Optional;

public interface FileStorageService {

    StoredFileRef store(String folder, String originalFilename, InputStream content, long contentLength, String contentType);

    Optional<InputStream> load(String storageKey);

    boolean delete(String storageKey);

    String buildPublicUrl(String storageKey);

    boolean exists(String storageKey);
}
