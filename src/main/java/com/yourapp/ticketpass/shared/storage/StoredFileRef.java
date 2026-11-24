package com.yourapp.ticketpass.shared.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoredFileRef {

    private String storageKey;

    private String originalFilename;

    private String contentType;

    private long sizeBytes;

    private String publicUrl;
}
