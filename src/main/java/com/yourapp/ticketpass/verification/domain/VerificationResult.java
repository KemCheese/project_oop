package com.yourapp.ticketpass.verification.domain;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ket qua tong hop cua mot lan xac thuc ve.
 *
 * <p>Day la DTO noi bo ma module {@code ticket} duoc phep import: no chi la value object,
 * khong phai entity, nen viec chia se no khong tao rang buoc vao lop luu tru cua module
 * {@code verification} va khong vi pham nguyen tac 1.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResult {

    private VerificationStatus status;

    private RiskTier riskTier;

    /** Do tin cay OCR theo thang diem phan tram. */
    private BigDecimal ocrConfidence;

    /** Cac phat hien trong qua trinh xac thuc, vi du lech ten su kien, thieu so ghe. */
    private List<String> findings;

    /** ID thuan cua ve bi trung ma QR neu co. */
    private Long duplicateOfTicketId;

    /** Ly do tu choi, chi co gia tri khi status la REJECTED. */
    private String rejectionReason;
}
