package com.yourapp.ticketpass.verification.infrastructure;

import com.yourapp.ticketpass.shared.config.OcrConfig;
import com.yourapp.ticketpass.verification.domain.OcrEngine;
import com.yourapp.ticketpass.verification.domain.OcrExtraction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Hien thuc {@link OcrEngine} chay cuc bo bang Tesseract.
 *
 * <p>Day la adapter mac dinh cua he thong: khong phu thuoc mang, khong ton chi phi goi dich vu ngoai,
 * phu hop voi pham vi mot do an. Khi can do chinh xac cao hon, chi viec them mot hien thuc
 * {@code OcrEngine} khac ma khong sua tang application.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
@Component
@RequiredArgsConstructor
public class TesseractOcrEngine implements OcrEngine {

    private final OcrConfig ocrConfig;

    @Override
    public String engineName() {
        return "TESSERACT";
    }

    @Override
    public OcrExtraction extract(byte[] imageBytes, String language) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Khoi tao net.sourceforge.tess4j.Tesseract, set datapath bang "
                        + "ocrConfig.getTessdataPath() va ngon ngu bang ocrConfig.getLanguage() (dung tham so language "
                        + "neu duoc truyen vao), doc imageBytes thanh BufferedImage qua ImageIO, goi doOCR de lay "
                        + "rawText, lay do tin cay trung binh tu getWords, roi dong goi thanh OcrExtraction. "
                        + "Bat TesseractException va nem lai duoi dang loi co nghia thay vi de ro ri ra tang tren.");
    }

    @Override
    public boolean isAvailable() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve true khi ocrConfig.isEnabled() va thu muc "
                        + "ocrConfig.getTessdataPath() ton tai kem file du lieu ngon ngu tuong ung; "
                        + "tra ve false thay vi nem loi de he thong tu dong chuyen sang MANUAL_REVIEW_REQUIRED.");
    }
}
