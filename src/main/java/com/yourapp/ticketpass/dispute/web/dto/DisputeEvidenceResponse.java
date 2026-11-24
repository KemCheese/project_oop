package com.yourapp.ticketpass.dispute.web.dto;

import java.time.Instant;

import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DisputeEvidenceResponse {

    private Long id;

    private DisputeEvidenceType evidenceType;

    private String originalFilename;

    private String publicUrl;

    private String note;

    private Instant uploadedAt;

    public String getFileUrl() {
        return publicUrl;
    }
}
