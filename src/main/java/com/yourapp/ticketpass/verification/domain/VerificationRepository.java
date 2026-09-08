package com.yourapp.ticketpass.verification.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port luu tru cua module {@code verification}, khai bao o tang domain.
 *
 * <p>Theo nguyen tac Dependency Inversion, tang domain va application chi phu thuoc vao interface nay
 * chu khong phu thuoc vao Spring Data JPA. Hien thuc nam o
 * {@code com.yourapp.ticketpass.verification.infrastructure.VerificationRepositoryImpl}.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
public interface VerificationRepository {

    /**
     * Luu moi hoac cap nhat mot ho so xac thuc.
     *
     * @param verification ho so can luu
     * @return ho so sau khi luu
     */
    TicketVerification save(TicketVerification verification);

    /**
     * Tim ho so xac thuc theo khoa chinh.
     *
     * @param id id cua ho so
     * @return ho so neu ton tai
     */
    Optional<TicketVerification> findById(Long id);

    /**
     * Tim ho so xac thuc cua mot ve.
     *
     * @param ticketId id thuan cua ve
     * @return ho so cua ve do neu ton tai
     */
    Optional<TicketVerification> findByTicketId(Long ticketId);

    /**
     * Lay cac ho so theo trang thai, phuc vu hang doi duyet cua admin.
     *
     * @param status trang thai can loc
     * @return danh sach ho so o trang thai do
     */
    List<TicketVerification> findByStatus(VerificationStatus status);

    /**
     * Lay cac ho so theo bac rui ro.
     *
     * @param riskTier bac rui ro can loc
     * @return danh sach ho so thuoc bac do
     */
    List<TicketVerification> findByRiskTier(RiskTier riskTier);

    /**
     * Tim ho so theo hash payload QR.
     *
     * @param qrPayloadHash SHA-256 hex cua payload QR
     * @return ho so co cung hash neu ton tai
     */
    Optional<TicketVerification> findByQrPayloadHash(String qrPayloadHash);

    /**
     * Kiem tra co ve KHAC dang dung cung ma QR khong - day la phep kiem chong ban trung ve.
     *
     * @param qrPayloadHash SHA-256 hex cua payload QR
     * @param ticketId id ve dang xet, duoc loai tru khoi phep so sanh
     * @return true neu ton tai ve khac dung cung ma QR
     */
    boolean existsByQrPayloadHashAndTicketIdNot(String qrPayloadHash, Long ticketId);
}
