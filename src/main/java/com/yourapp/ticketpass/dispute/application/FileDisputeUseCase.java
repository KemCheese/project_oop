package com.yourapp.ticketpass.dispute.application;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidence;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceType;
import com.yourapp.ticketpass.dispute.domain.DisputeReason;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.dispute.domain.DisputeWindowPolicy;
import com.yourapp.ticketpass.shared.event.DisputeOpenedEvent;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.shared.storage.StoredFileRef;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileDisputeUseCase {

    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024;

    private final DisputeRepository disputeRepository;

    private final DisputeEvidenceRepository disputeEvidenceRepository;

    private final List<DisputeWindowPolicy> windowPolicies;

    private final FileStorageService fileStorageService;

    private final TicketRepository ticketRepository;

    private final TicketStateMachineService ticketStateMachineService;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    private Map<TicketType, DisputeWindowPolicy> policyMap;

    @PostConstruct
    void initPolicyMap() {
        policyMap = new EnumMap<>(TicketType.class);
        if (windowPolicies != null) {
            for (DisputeWindowPolicy policy : windowPolicies) {
                if (policy.supportedType() != null) {
                    policyMap.put(policy.supportedType(), policy);
                }
            }
        }
    }

    public record DisputeEvidenceAttachment(
            DisputeEvidenceType type,
            String originalFilename,
            String contentType,
            long sizeBytes,
            InputStream content
    ) {}

    public DisputeEvidenceType inferEvidenceType(String contentType) {
        if (contentType == null) {
            return DisputeEvidenceType.IMAGE;
        }
        String lower = contentType.toLowerCase().trim();
        if (lower.startsWith("video/")) {
            return DisputeEvidenceType.VIDEO;
        }
        if ("application/pdf".equals(lower)) {
            return DisputeEvidenceType.PDF;
        }
        if (lower.startsWith("image/")) {
            return DisputeEvidenceType.IMAGE;
        }
        if (lower.startsWith("text/")) {
            return DisputeEvidenceType.CHAT_LOG;
        }
        return DisputeEvidenceType.IMAGE;
    }

    @Transactional
    public Dispute fileDispute(Long ticketId, Long buyerId, DisputeReason reason, String description) {
        return fileDispute(ticketId, buyerId, reason, description, Collections.emptyList());
    }

    @Transactional
    public Dispute fileDispute(Long ticketId, Long buyerId, DisputeReason reason, String description,
                               List<DisputeEvidenceAttachment> evidences) {
        if (ticketId == null || buyerId == null || reason == null) {
            throw new BusinessRuleViolationException("ticketId, buyerId và reason không được để trống.");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessRuleViolationException("Mô tả chi tiết khiếu nại không được để trống.");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé #" + ticketId));

        if (ticket.getBuyerId() == null || !ticket.getBuyerId().equals(buyerId)) {
            throw new BusinessRuleViolationException(
                    "Người dùng #" + buyerId + " không phải là người mua của vé #" + ticketId
                    + ", không có quyền mở khiếu nại.");
        }

        if (ticket.getStatus() == TicketStatus.AUTO_COMPLETE
                || ticket.getStatus() == TicketStatus.RESOLVED_BUYER_WINS
                || ticket.getStatus() == TicketStatus.RESOLVED_SELLER_WINS) {
            throw new BusinessRuleViolationException(
                    "Vé #" + ticketId + " đã ở trạng thái kết thúc (" + ticket.getStatus()
                    + "), không thể mở khiếu nại.");
        }

        boolean hasActiveDispute = disputeRepository.existsByTicketIdAndStatusIn(ticketId, List.of(
                DisputeStatus.OPEN,
                DisputeStatus.AWAITING_SELLER_RESPONSE,
                DisputeStatus.SELLER_RESPONDED,
                DisputeStatus.UNDER_REVIEW,
                DisputeStatus.EXPIRED
        ));
        if (hasActiveDispute) {
            throw new BusinessRuleViolationException(
                    "Vé #" + ticketId + " đã có khiếu nại đang hiệu lực. Không thể mở thêm khiếu nại mới.");
        }

        boolean hasWithdrawnDispute = disputeRepository.existsByTicketIdAndStatusIn(
                ticketId, List.of(DisputeStatus.WITHDRAWN));
        if (hasWithdrawnDispute) {
            throw new BusinessRuleViolationException(
                    "Vé #" + ticketId + " đã từng có khiếu nại được rút trước đó. Không thể mở lại khiếu nại mới.");
        }

        DisputeWindowPolicy policy = resolvePolicy(ticket.getTicketType());
        Instant now = Instant.now();
        Instant referenceTime = ticket.getReceivedAt() != null ? ticket.getReceivedAt() : ticket.getTransferredAt();
        if (referenceTime == null) {
            referenceTime = ticket.getUpdatedAt() != null ? ticket.getUpdatedAt() : now;
        }
        Instant eventStartAt = ticket.getEventStartAt();

        if (!policy.isWithinWindow(referenceTime, eventStartAt, now)) {
            throw new BusinessRuleViolationException(
                    "Đã hết cửa sổ khiếu nại cho vé #" + ticketId + " (loại vé: " + ticket.getTicketType()
                    + "). Không thể mở khiếu nại sau thời hạn quy định hoặc sau giờ sự kiện + 30 phút.");
        }

        boolean isGateDispute = eventStartAt != null && now.isAfter(eventStartAt);
        if (isGateDispute) {
            if (evidences == null || evidences.isEmpty()) {
                throw new BusinessRuleViolationException(
                        "Khiếu nại tại cổng soát vé (sau giờ bắt đầu sự kiện) bắt buộc phải nộp Video quay liên tục tại quầy/máy soát vé hoặc Biên bản từ chối vào cổng (PDF).");
            }
            boolean hasValidGateEvidence = evidences.stream().anyMatch(e ->
                    e.type() == DisputeEvidenceType.VIDEO || e.type() == DisputeEvidenceType.PDF
                    || (e.contentType() != null && (e.contentType().toLowerCase().startsWith("video/") || "application/pdf".equalsIgnoreCase(e.contentType())))
            );
            if (!hasValidGateEvidence) {
                throw new BusinessRuleViolationException(
                        "Khiếu nại tại cổng soát vé từ chối bằng chứng chỉ là ảnh chụp màn hình đơn thuần. Bắt buộc phải nộp Video soát vé lỗi hoặc Biên bản tại quầy (PDF).");
            }
        }

        Instant windowClosesAt = policy.computeWindowClose(referenceTime, eventStartAt);
        Instant sellerResponseDeadline = isGateDispute
                ? now.plus(Duration.ofHours(24))
                : now.plus(policy.sellerResponseWindow());

        Dispute dispute = new Dispute();
        dispute.setTicketId(ticketId);
        dispute.setBuyerId(buyerId);
        dispute.setSellerId(ticket.getSellerId());
        dispute.setEscrowTransactionId(ticket.getEscrowTransactionId());
        dispute.setDepositId(ticket.getDepositId());
        dispute.setStatus(DisputeStatus.OPEN);
        dispute.setReason(reason);
        dispute.setDescription(description.trim());
        dispute.setFiledAt(now);
        dispute.setWindowClosesAt(windowClosesAt);
        dispute.setSellerResponseDeadline(sellerResponseDeadline);

        ticketStateMachineService.transition(ticketId, TicketStatus.DISPUTE_UNDER_REVIEW);

        dispute = disputeRepository.save(dispute);
        log.info("[FileDispute] Mở khiếu nại id={} thành công cho vé #{}, buyerId={}, sellerId={}, isGateDispute={}, deadline={}",
                dispute.getId(), ticketId, buyerId, dispute.getSellerId(), isGateDispute, sellerResponseDeadline);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new DisputeOpenedEvent(
                    dispute.getId(),
                    ticketId,
                    dispute.getSellerId(),
                    buyerId,
                    ticket.getTitle(),
                    reason.name(),
                    now
            ));
        }

        if (evidences != null && !evidences.isEmpty()) {
            for (DisputeEvidenceAttachment att : evidences) {
                attachEvidence(dispute.getId(), buyerId, att.type(), att.originalFilename(),
                        att.contentType(), att.sizeBytes(), att.content());
            }
        }

        return dispute;
    }

    @Transactional
    public DisputeEvidence attachEvidence(Long disputeId, Long uploadedByUserId, DisputeEvidenceType type,
                                          String originalFilename, String contentType, long sizeBytes,
                                          InputStream content) {
        if (disputeId == null || uploadedByUserId == null || type == null) {
            throw new BusinessRuleViolationException("disputeId, uploadedByUserId và type không được để trống.");
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessRuleViolationException("Tên tệp gốc (originalFilename) không được để trống.");
        }
        if (content == null) {
            throw new BusinessRuleViolationException("Luồng dữ liệu tệp (content) không được để trống.");
        }

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại #" + disputeId));

        if (dispute.getStatus() == DisputeStatus.RESOLVED_BUYER_WINS
                || dispute.getStatus() == DisputeStatus.RESOLVED_SELLER_WINS
                || dispute.getStatus() == DisputeStatus.WITHDRAWN) {
            throw new BusinessRuleViolationException(
                    "Khiếu nại #" + disputeId + " đã đóng (trạng thái: " + dispute.getStatus()
                    + "), không thể đính kèm thêm bằng chứng.");
        }

        boolean isBuyer = uploadedByUserId.equals(dispute.getBuyerId());
        boolean isSeller = uploadedByUserId.equals(dispute.getSellerId());
        boolean isAdmin = false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority()));
        }
        if (!isBuyer && !isSeller && !isAdmin) {
            throw new BusinessRuleViolationException(
                    "Người dùng #" + uploadedByUserId + " không có quyền đính kèm bằng chứng cho khiếu nại #"
                    + disputeId + ". Chỉ người mua, người bán hoặc admin mới có quyền này.");
        }

        if (sizeBytes <= 0) {
            throw new BusinessRuleViolationException("Kích thước tệp bằng chứng phải lớn hơn 0 byte.");
        }
        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new BusinessRuleViolationException(
                    "Kích thước tệp bằng chứng (" + (sizeBytes / (1024 * 1024))
                    + "MB) vượt quá giới hạn tối đa cho phép (25MB).");
        }
        validateContentType(type, contentType);

        StoredFileRef fileRef = fileStorageService.store("disputes", originalFilename, content, sizeBytes, contentType);
        if (fileRef == null || fileRef.getStorageKey() == null) {
            throw new BusinessRuleViolationException("Lỗi lưu trữ tệp bằng chứng qua FileStorageService.");
        }

        DisputeEvidence evidence = new DisputeEvidence();
        evidence.setDisputeId(disputeId);
        evidence.setUploadedByUserId(uploadedByUserId);
        evidence.setEvidenceType(type);
        evidence.setStorageKey(fileRef.getStorageKey());
        evidence.setOriginalFilename(originalFilename);
        evidence.setContentType(contentType);
        evidence.setSizeBytes(sizeBytes);
        evidence.setUploadedAt(Instant.now());

        evidence = disputeEvidenceRepository.save(evidence);
        log.info("[FileDispute] Đính kèm bằng chứng id={} (loại {}) thành công cho khiếu nại #{}",
                evidence.getId(), type, disputeId);
        return evidence;
    }

    public DisputeWindowPolicy resolvePolicy(TicketType ticketType) {
        if (ticketType == null) {
            throw new BusinessRuleViolationException(
                    "Loại vé không được để trống khi xác định chính sách cửa sổ khiếu nại.");
        }
        DisputeWindowPolicy policy = policyMap != null ? policyMap.get(ticketType) : null;
        if (policy == null && windowPolicies != null) {
            policy = windowPolicies.stream()
                    .filter(p -> p.supportedType() == ticketType)
                    .findFirst()
                    .orElse(null);
        }
        if (policy == null) {
            throw new BusinessRuleViolationException(
                    "Chưa có chính sách cửa sổ khiếu nại cho loại vé: " + ticketType);
        }
        return policy;
    }

    private void validateContentType(DisputeEvidenceType type, String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new BusinessRuleViolationException("Kiểu MIME (contentType) của tệp bằng chứng không được để trống.");
        }
        String lower = contentType.toLowerCase().trim();
        boolean valid = switch (type) {
            case IMAGE -> lower.startsWith("image/");
            case VIDEO -> lower.startsWith("video/");
            case PDF -> "application/pdf".equals(lower);
            case CHAT_LOG -> lower.startsWith("text/")
                    || lower.startsWith("image/")
                    || "application/pdf".equals(lower)
                    || "application/json".equals(lower);
            case TEXT_NOTE -> lower.startsWith("text/");
        };
        if (!valid) {
            throw new BusinessRuleViolationException(
                    "Kiểu tệp " + contentType + " không phù hợp với loại bằng chứng " + type + ".");
        }
    }
}
