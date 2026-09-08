package com.yourapp.ticketpass.verification.application;

import com.yourapp.ticketpass.shared.util.ImageHashUtil;
import com.yourapp.ticketpass.shared.util.QrCodeGenerator;
import com.yourapp.ticketpass.verification.domain.VerificationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Chong ban trung ve: mot ma ve chi duoc phep ton tai tren san mot lan.
 *
 * <p>Hai lop phong ve bo tro nhau. Lop thu nhat bam payload QR bang SHA-256 roi so khop chinh xac,
 * bat duoc truong hop cung mot ve bi dang lai boi chinh nguoi ban hoac boi nguoi khac co anh ve.
 * Lop thu hai dung perceptual hash cua anh, bat duoc truong hop ke gian crop, xoay, hoac chinh sang toi
 * anh ve de qua mat phep so khop byte.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
@Service
@RequiredArgsConstructor
public class QrDuplicateCheckService {

    private final VerificationRepository verificationRepository;

    private final QrCodeGenerator qrCodeGenerator;

    private final ImageHashUtil imageHashUtil;

    /**
     * Bam payload QR thanh chuoi SHA-256 hex.
     *
     * @param qrPayload noi dung giai ma duoc tu ma QR
     * @return chuoi hex 64 ky tu
     */
    public String hashQrPayload(String qrPayload) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Chuan hoa qrPayload (cat khoang trang thua, thong nhat hoa thuong neu "
                        + "payload khong phan biet) roi goi qrCodeGenerator.hashPayload de lay SHA-256 hex.");
    }

    /**
     * Tim ve khac dang dung cung ma QR voi ve dang xet.
     *
     * @param ticketId id ve dang xet, duoc loai tru khoi phep so sanh
     * @param qrPayload noi dung giai ma duoc tu ma QR
     * @return id cua ve bi trung neu co
     */
    public Optional<Long> findDuplicateTicketId(Long ticketId, String qrPayload) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tinh hash bang hashQrPayload, goi "
                        + "verificationRepository.existsByQrPayloadHashAndTicketIdNot; neu co trung thi lay ho so qua "
                        + "findByQrPayloadHash va tra ve ticketId cua ho so do, nguoc lai tra Optional.empty().");
    }

    /**
     * Tinh perceptual hash cua mot anh ve.
     *
     * @param imageBytes noi dung anh dang byte
     * @return chuoi hash dung de so sanh do giong nhau
     */
    public String computeImagePerceptualHash(byte[] imageBytes) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Goi imageHashUtil.perceptualHash tren imageBytes va tra ve chuoi hash de "
                        + "luu vao TicketVerification.imagePerceptualHash.");
    }

    /**
     * So sanh hai perceptual hash xem co phai cung mot anh khong.
     *
     * @param hashA perceptual hash cua anh thu nhat
     * @param hashB perceptual hash cua anh thu hai
     * @return true neu hai anh du giong de coi la trung
     */
    public boolean looksLikeSameImage(String hashA, String hashB) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Goi imageHashUtil.hammingDistance(hashA, hashB) va so voi nguong da chot "
                        + "(khoang cach cang nho cang giong); tra ve true khi khoang cach duoi nguong.");
    }
}
