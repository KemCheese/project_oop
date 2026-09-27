package com.yourapp.ticketpass.verification.infrastructure;

import com.yourapp.ticketpass.verification.domain.RiskTier;
import com.yourapp.ticketpass.verification.domain.TicketVerification;
import com.yourapp.ticketpass.verification.domain.VerificationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [DEPRECATED] Module verification da bi loai bo khoi pham vi du an.
 * Du an chuyen huong sang mo hinh marketplace. Xem 00-tong-quan-du-an.md.
 *
 * Repository Spring Data JPA cho entity {@code TicketVerification}.
 *
 * <p>Interface nay la chi tiet ky thuat cua tang {@code infrastructure}; tang domain va application
 * di qua port {@code com.yourapp.ticketpass.verification.domain.VerificationRepository}.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
public interface TicketVerificationJpaRepository extends JpaRepository<TicketVerification, Long> {

    /**
 * [DEPRECATED] Module verification da bi loai bo khoi pham vi du an.
 * Du an chuyen huong sang mo hinh marketplace. Xem 00-tong-quan-du-an.md.
 *
     * @param ticketId id thuan cua ve
     * @return ho so xac thuc cua ve do neu ton tai
     */
    Optional<TicketVerification> findByTicketId(Long ticketId);

    /**
 * [DEPRECATED] Module verification da bi loai bo khoi pham vi du an.
 * Du an chuyen huong sang mo hinh marketplace. Xem 00-tong-quan-du-an.md.
 *
     * @param status trang thai can loc
     * @return cac ho so o trang thai do
     */
    List<TicketVerification> findByStatus(VerificationStatus status);

    /**
 * [DEPRECATED] Module verification da bi loai bo khoi pham vi du an.
 * Du an chuyen huong sang mo hinh marketplace. Xem 00-tong-quan-du-an.md.
 *
     * @param riskTier bac rui ro can loc
     * @return cac ho so thuoc bac rui ro do
     */
    List<TicketVerification> findByRiskTier(RiskTier riskTier);

    /**
 * [DEPRECATED] Module verification da bi loai bo khoi pham vi du an.
 * Du an chuyen huong sang mo hinh marketplace. Xem 00-tong-quan-du-an.md.
 *
     * @param qrPayloadHash SHA-256 hex cua payload QR
     * @return ho so co cung hash neu ton tai
     */
    Optional<TicketVerification> findByQrPayloadHash(String qrPayloadHash);

    /**
 * [DEPRECATED] Module verification da bi loai bo khoi pham vi du an.
 * Du an chuyen huong sang mo hinh marketplace. Xem 00-tong-quan-du-an.md.
 *
     * @param qrPayloadHash SHA-256 hex cua payload QR
     * @param ticketId id ve dang xet, duoc loai tru khoi phep so sanh
     * @return true neu ton tai ve khac dung cung ma QR
     */
    boolean existsByQrPayloadHashAndTicketIdNot(String qrPayloadHash, Long ticketId);
}
