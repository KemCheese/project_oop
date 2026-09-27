package com.yourapp.ticketpass.verification.infrastructure;

import com.yourapp.ticketpass.verification.domain.RiskTier;
import com.yourapp.ticketpass.verification.domain.TicketVerification;
import com.yourapp.ticketpass.verification.domain.VerificationRepository;
import com.yourapp.ticketpass.verification.domain.VerificationStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * [DEPRECATED] Module verification da bi loai bo khoi pham vi du an.
 * Du an chuyen huong sang mo hinh marketplace. Xem 00-tong-quan-du-an.md.
 *
 * Hien thuc port {@link VerificationRepository} bang Spring Data JPA.
 *
 * <p>Day la glue code thuan: moi method chi delegate sang {@link TicketVerificationJpaRepository},
 * giup tang domain va application khong phu thuoc vao Spring Data.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
@Repository
@RequiredArgsConstructor
public class VerificationRepositoryImpl implements VerificationRepository {

    private final TicketVerificationJpaRepository ticketVerificationJpaRepository;

    @Override
    public TicketVerification save(TicketVerification verification) {
        return ticketVerificationJpaRepository.save(verification);
    }

    @Override
    public Optional<TicketVerification> findById(Long id) {
        return ticketVerificationJpaRepository.findById(id);
    }

    @Override
    public Optional<TicketVerification> findByTicketId(Long ticketId) {
        return ticketVerificationJpaRepository.findByTicketId(ticketId);
    }

    @Override
    public List<TicketVerification> findByStatus(VerificationStatus status) {
        return ticketVerificationJpaRepository.findByStatus(status);
    }

    @Override
    public List<TicketVerification> findByRiskTier(RiskTier riskTier) {
        return ticketVerificationJpaRepository.findByRiskTier(riskTier);
    }

    @Override
    public Optional<TicketVerification> findByQrPayloadHash(String qrPayloadHash) {
        return ticketVerificationJpaRepository.findByQrPayloadHash(qrPayloadHash);
    }

    @Override
    public boolean existsByQrPayloadHashAndTicketIdNot(String qrPayloadHash, Long ticketId) {
        return ticketVerificationJpaRepository.existsByQrPayloadHashAndTicketIdNot(qrPayloadHash, ticketId);
    }
}
