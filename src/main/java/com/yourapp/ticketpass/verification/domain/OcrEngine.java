package com.yourapp.ticketpass.verification.domain;

/**
 * Hop dong cho mot bo may OCR (Adapter pattern).
 *
 * <p>Nho interface nay, Prompt 2 co the them {@code CloudVisionOcrEngine} chay tren dich vu dam may
 * ma khong phai sua mot dong nao trong
 * {@code com.yourapp.ticketpass.verification.application.OcrVerificationService}. Hien thuc mac dinh
 * la {@code com.yourapp.ticketpass.verification.infrastructure.TesseractOcrEngine} chay cuc bo.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
public interface OcrEngine {

    /**
     * Ten dinh danh cua bo may, ghi lai trong nhat ky de biet ket qua den tu dau.
     *
     * @return ten bo may, vi du "TESSERACT"
     */
    String engineName();

    /**
     * Doc van ban tu anh.
     *
     * @param imageBytes noi dung anh ve dang byte
     * @param language ma ngon ngu, vi du "vie+eng"
     * @return van ban tho, do tin cay, va cac truong bo tach duoc
     */
    OcrExtraction extract(byte[] imageBytes, String language);

    /**
     * Kiem tra bo may co san sang phuc vu khong.
     *
     * @return true neu bo may da duoc cai dat va cau hinh dung
     */
    boolean isAvailable();
}
