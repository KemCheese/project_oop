package com.yourapp.ticketpass.verification.domain;

/**
 * Bac rui ro cua mot ve dang cho xac thuc.
 *
 * <p>Quy tac phan bac da chot:</p>
 * <pre>
 * HIGH   : gia ban &gt;= 5.000.000d, HOAC chua xac thuc email forward, HOAC diem uy tin seller &lt; 60, HOAC phat hien QR hash trung
 * MEDIUM : 1.000.000d &lt;= gia ban &lt; 5.000.000d, HOAC seller co duoi 5 giao dich thanh cong
 * LOW    : cac truong hop con lai
 * He qua: HIGH -&gt; bat buoc admin review tay; MEDIUM -&gt; OCR tu dong + nguong confidence cao hon; LOW -&gt; auto-approve neu OCR confidence &gt;= app.ocr.min-confidence
 * </pre>
 *
 * <p>Viec phan bac do
 * {@code com.yourapp.ticketpass.verification.application.RiskTierResolver} thuc hien.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
public enum RiskTier {

    /** Rui ro thap: co the auto-approve neu OCR du tin cay. */
    LOW,

    /** Rui ro trung binh: van chay OCR tu dong nhung ap nguong confidence cao hon. */
    MEDIUM,

    /** Rui ro cao: bat buoc admin review tay truoc khi ve duoc giao dich. */
    HIGH
}
