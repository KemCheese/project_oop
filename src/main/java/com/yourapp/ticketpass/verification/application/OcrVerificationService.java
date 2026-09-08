package com.yourapp.ticketpass.verification.application;

import com.yourapp.ticketpass.shared.config.OcrConfig;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.verification.domain.OcrEngine;
import com.yourapp.ticketpass.verification.domain.OcrExtraction;
import com.yourapp.ticketpass.verification.domain.VerificationRepository;
import com.yourapp.ticketpass.verification.domain.VerificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Xac thuc ve bang cach doc anh ve va doi chieu voi du lieu nguoi ban tu khai.
 *
 * <p>Nguyen tac: van ban OCR doc duoc phai khop voi ten su kien va thong tin cho ngoi ma nguoi ban
 * nhap trong form. Neu do lech vuot qua nguong cho phep - hoac do tin cay OCR thap hon
 * {@code app.ocr.min-confidence} - thi ho so bi day sang {@code MANUAL_REVIEW_REQUIRED} thay vi
 * tu choi ngay, vi anh chup mo hoac ve viet tay van co the la ve that.</p>
 *
 * <p>Service khong phu thuoc vao mot bo may OCR cu the ma chi lam viec qua {@link OcrEngine}.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
@Service
@RequiredArgsConstructor
public class OcrVerificationService {

    private final VerificationRepository verificationRepository;

    private final OcrEngine ocrEngine;

    private final FileStorageService fileStorageService;

    private final OcrConfig ocrConfig;

    /**
     * Chay OCR tren mot anh da luu trong kho file.
     *
     * @param imageStorageKey khoa file anh trong FileStorageService
     * @return van ban va cac truong doc duoc tu anh
     */
    public OcrExtraction runOcr(String imageStorageKey) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Kiem tra ocrConfig.isEnabled() va ocrEngine.isAvailable(), tai noi dung anh "
                        + "qua fileStorageService theo imageStorageKey, goi ocrEngine.extract(bytes, "
                        + "ocrConfig.getLanguage()), roi bo tach cac truong eventName, seatInfo, orderCode, showTime "
                        + "tu rawText bang regex va dat vao OcrExtraction.fields.");
    }

    /**
     * Doi chieu ket qua OCR voi du lieu nguoi ban tu khai va ket luan trang thai xac thuc.
     *
     * @param ticketId id thuan cua ve dang xac thuc
     * @param imageStorageKey khoa file anh ve
     * @param expectedEventName ten su kien nguoi ban khai
     * @param expectedSeatInfo thong tin cho ngoi nguoi ban khai
     * @return ket qua xac thuc kem cac phat hien
     */
    public VerificationResult verifyTicketImage(Long ticketId, String imageStorageKey,
                                                String expectedEventName, String expectedSeatInfo) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Nap hoac tao TicketVerification cho ticketId, dat status OCR_IN_PROGRESS, "
                        + "goi runOcr, so khop mem (bo dau, chuan hoa hoa thuong, do tuong dong chuoi) giua text OCR "
                        + "voi expectedEventName va expectedSeatInfo, ghi cac sai lech vao findings; neu confidence "
                        + ">= ocrConfig.getMinConfidence() va khong co sai lech nghiem trong thi dat AUTO_APPROVED, "
                        + "nguoc lai dat MANUAL_REVIEW_REQUIRED; luu lai qua verificationRepository.save va tra ve "
                        + "VerificationResult tuong ung.");
    }
}
