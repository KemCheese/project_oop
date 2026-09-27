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
 * [DEPRECATED] Module verification da bi loai bo khoi pham vi du an.
 *
 * <p>Du an da chuyen huong sang mo hinh marketplace (kieu Shopee cho ve).
 * San bao ve giao dich bang hai lop kinh te (coc 50% + escrow 100%) thay vi
 * xac thuc ky thuat (OCR, hash QR, email forward). Class nay giu lai de
 * tuong thich voi code cu nhung KHONG nen duoc su dung.</p>
 *
 * <p>Module: verification [DEPRECATED]</p>
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
