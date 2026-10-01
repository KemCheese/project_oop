package com.yourapp.ticketpass.dispute.application;

import java.io.InputStream;
import java.time.Instant;
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

/**
 * Use case người mua mở khiếu nại và đính kèm bằng chứng.
 *
 * <p><b>Điều kiện tiên quyết bắt buộc kiểm tra trước khi cho mở khiếu nại:</b></p>
 * <ol>
 *   <li>Người gọi đúng là người mua của vé đó.</li>
 *   <li>Vé đang ở trạng thái cho phép khiếu nại (đã chuyển, chưa hoàn tất giao dịch).</li>
 *   <li><b>Còn trong cửa sổ khiếu nại</b> — kiểm tra qua {@link DisputeWindowPolicy} tương ứng
 *       với loại vé. Đây là chốt quan trọng nhất: hết cửa sổ thì tiền đã hoặc sắp được giải ngân
 *       cho người bán, mở khiếu nại lúc đó là vô nghĩa.</li>
 *   <li>Vé chưa có khiếu nại nào đang mở, tránh khiếu nại trùng lặp.</li>
 * </ol>
 *
 * <p><b>Chọn chiến lược theo loại vé:</b> Spring tiêm toàn bộ bean
 * {@link DisputeWindowPolicy} vào {@code windowPolicies}, và {@link #resolvePolicy} chọn ra bean
 * có {@code supportedType()} khớp với loại vé. Thêm một loại vé mới chỉ cần thêm một bean chiến
 * lược, không sửa use case này.</p>
 *
 * <p><b>Nguyên tắc 3 — lưu trữ tệp:</b> tệp bằng chứng BẮT BUỘC đi qua
 * {@link FileStorageService}. Use case này KHÔNG được dùng {@code java.io.File},
 * {@code Files.write} hay bất kỳ cách ghi trực tiếp ra đĩa nào. Chỉ {@code storageKey} do
 * {@code FileStorageService} trả về được lưu vào cơ sở dữ liệu.</p>
 *
 * <p><b>Giao dịch:</b> {@code @Transactional} bảo đảm tính nguyên tử của luồng mở khiếu nại và
 * đính kèm bằng chứng.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDisputeUseCase {

    /** Giới hạn kích thước tệp đính kèm tối đa: 25MB (khớp với spring.servlet.multipart.max-file-size). */
    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024;

    /** Cổng truy cập dữ liệu khiếu nại. */
    private final DisputeRepository disputeRepository;

    /** Cổng truy cập dữ liệu bằng chứng. */
    private final DisputeEvidenceRepository disputeEvidenceRepository;

    /** Toàn bộ chiến lược cửa sổ khiếu nại, Spring tiêm tự động theo loại vé. */
    private final List<DisputeWindowPolicy> windowPolicies;

    /** Cổng lưu trữ tệp; mọi tệp bằng chứng đều phải đi qua đây (nguyên tắc 3). */
    private final FileStorageService fileStorageService;

    /** Truy cập dữ liệu vé để đọc loại vé, giờ sự kiện và thời điểm chuyển vé. */
    private final TicketRepository ticketRepository;

    /** Máy trạng thái vé — nơi duy nhất được phép đổi trạng thái vé. */
    private final TicketStateMachineService ticketStateMachineService;

    /** Bảng tra cứu nhanh chính sách theo loại vé, khởi tạo tại {@code @PostConstruct}. */
    private Map<TicketType, DisputeWindowPolicy> policyMap;

    /**
     * Khởi tạo bảng ánh xạ nhanh giữa {@link TicketType} và {@link DisputeWindowPolicy}.
     */
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

    /**
     * Người mua mở khiếu nại cho một vé.
     *
     * <p>Trình tự thực hiện:
     * (1) Tra vé qua ticketRepository, ném ResourceNotFoundException nếu không có.
     * (2) Xác minh buyerId đúng là người mua của vé, ném BusinessRuleViolationException nếu không khớp.
     * (3) Chặn khiếu nại trùng lặp bằng existsByTicketIdAndStatusIn với các trạng thái đang hiệu lực.
     * (4) Lấy policy = resolvePolicy(ticket.getTicketType()) và kiểm tra isWithinWindow.
     * (5) Tạo Dispute với status = OPEN, filedAt = now, windowClosesAt, sellerResponseDeadline.
     * (6) Gọi ticketStateMachineService.transition(ticketId, TicketStatus.DISPUTE_UNDER_REVIEW),
     *     rồi save qua disputeRepository và trả về.</p>
     *
     * @param ticketId    ID vé bị khiếu nại
     * @param buyerId     ID người mua đứng ra khiếu nại
     * @param reason      lý do khiếu nại
     * @param description mô tả chi tiết vấn đề
     * @return hồ sơ khiếu nại vừa được tạo
     */
    @Transactional
    public Dispute fileDispute(Long ticketId, Long buyerId, DisputeReason reason, String description) {
        if (ticketId == null || buyerId == null || reason == null) {
            throw new BusinessRuleViolationException("ticketId, buyerId và reason không được để trống.");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessRuleViolationException("Mô tả chi tiết khiếu nại không được để trống.");
        }

        // (1) Tra vé qua ticketRepository
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vé #" + ticketId));

        // (2) Xác minh buyerId đúng là người mua của vé
        if (ticket.getBuyerId() == null || !ticket.getBuyerId().equals(buyerId)) {
            throw new BusinessRuleViolationException(
                    "Người dùng #" + buyerId + " không phải là người mua của vé #" + ticketId
                    + ", không có quyền mở khiếu nại.");
        }

        // Kiểm tra vé chưa ở trạng thái kết thúc
        if (ticket.getStatus() == TicketStatus.AUTO_COMPLETE
                || ticket.getStatus() == TicketStatus.RESOLVED_BUYER_WINS
                || ticket.getStatus() == TicketStatus.RESOLVED_SELLER_WINS) {
            throw new BusinessRuleViolationException(
                    "Vé #" + ticketId + " đã ở trạng thái kết thúc (" + ticket.getStatus()
                    + "), không thể mở khiếu nại.");
        }

        // (3) Chặn khiếu nại trùng lặp
        boolean hasActiveDispute = disputeRepository.existsByTicketIdAndStatusIn(ticketId, List.of(
                DisputeStatus.OPEN,
                DisputeStatus.AWAITING_SELLER_RESPONSE,
                DisputeStatus.SELLER_RESPONDED,
                DisputeStatus.UNDER_REVIEW
        ));
        if (hasActiveDispute) {
            throw new BusinessRuleViolationException(
                    "Vé #" + ticketId + " đã có khiếu nại đang hiệu lực. Không thể mở thêm khiếu nại mới.");
        }

        // (4) Lấy policy = resolvePolicy(ticket.getTicketType()) và kiểm tra cửa sổ khiếu nại
        DisputeWindowPolicy policy = resolvePolicy(ticket.getTicketType());
        Instant now = Instant.now();
        Instant transferredAt = ticket.getTransferredAt();
        Instant eventStartAt = ticket.getEventStartAt();

        if (transferredAt == null) {
            transferredAt = ticket.getUpdatedAt() != null ? ticket.getUpdatedAt() : now;
        }

        if (!policy.isWithinWindow(transferredAt, eventStartAt, now)) {
            throw new BusinessRuleViolationException(
                    "Đã hết cửa sổ khiếu nại cho vé #" + ticketId + " (loại vé: " + ticket.getTicketType()
                    + "). Không thể mở khiếu nại sau thời hạn quy định hoặc sau giờ sự kiện.");
        }

        // (5) Tạo Dispute
        Instant windowClosesAt = policy.computeWindowClose(transferredAt, eventStartAt);
        Instant sellerResponseDeadline = now.plus(policy.sellerResponseWindow());

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

        // (6) Chuyển trạng thái vé sang DISPUTE_UNDER_REVIEW để khoá tiền lại
        ticketStateMachineService.transition(ticketId, TicketStatus.DISPUTE_UNDER_REVIEW);

        dispute = disputeRepository.save(dispute);
        log.info("[FileDispute] Mở khiếu nại id={} thành công cho vé #{}, buyerId={}, sellerId={}, deadline={}",
                dispute.getId(), ticketId, buyerId, dispute.getSellerId(), sellerResponseDeadline);
        return dispute;
    }

    /**
     * Đính kèm một tệp bằng chứng vào khiếu nại.
     *
     * <p>Trình tự thực hiện:
     * (1) Tra Dispute theo disputeId và từ chối nếu khiếu nại đã có phán quyết (RESOLVED_* / WITHDRAWN).
     * (2) Xác minh uploadedByUserId là người mua, người bán của khiếu nại, hoặc admin.
     * (3) Kiểm tra contentType nằm trong danh sách cho phép theo type và sizeBytes không vượt giới hạn (tối đa 25MB).
     * (4) Gọi fileStorageService.store("disputes", originalFilename, content, sizeBytes, contentType) lấy StoredFileRef.
     * (5) Tạo DisputeEvidence với storageKey lấy từ StoredFileRef, uploadedAt = Instant.now(), rồi save và trả về.</p>
     *
     * @param disputeId        ID khiếu nại
     * @param uploadedByUserId ID người tải lên: người mua, người bán hoặc admin
     * @param type             loại bằng chứng
     * @param originalFilename tên tệp gốc
     * @param contentType      kiểu MIME của tệp
     * @param sizeBytes        kích thước tệp tính bằng byte
     * @param content          luồng dữ liệu của tệp
     * @return bản ghi bằng chứng vừa được tạo
     */
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

        // (1) Tra Dispute theo disputeId và từ chối nếu khiếu nại đã đóng
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại #" + disputeId));

        if (dispute.getStatus() == DisputeStatus.RESOLVED_BUYER_WINS
                || dispute.getStatus() == DisputeStatus.RESOLVED_SELLER_WINS
                || dispute.getStatus() == DisputeStatus.WITHDRAWN) {
            throw new BusinessRuleViolationException(
                    "Khiếu nại #" + disputeId + " đã đóng (trạng thái: " + dispute.getStatus()
                    + "), không thể đính kèm thêm bằng chứng.");
        }

        // (2) Xác minh uploadedByUserId là người mua, người bán hoặc admin
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

        // (3) Kiểm tra contentType và sizeBytes
        if (sizeBytes <= 0) {
            throw new BusinessRuleViolationException("Kích thước tệp bằng chứng phải lớn hơn 0 byte.");
        }
        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new BusinessRuleViolationException(
                    "Kích thước tệp bằng chứng (" + (sizeBytes / (1024 * 1024))
                    + "MB) vượt quá giới hạn tối đa cho phép (25MB).");
        }
        validateContentType(type, contentType);

        // (4) Lưu trữ tệp qua FileStorageService (nguyên tắc 3: không tự ghi tệp ra đĩa)
        StoredFileRef fileRef = fileStorageService.store("disputes", originalFilename, content, sizeBytes, contentType);
        if (fileRef == null || fileRef.getStorageKey() == null) {
            throw new BusinessRuleViolationException("Lỗi lưu trữ tệp bằng chứng qua FileStorageService.");
        }

        // (5) Tạo DisputeEvidence và lưu qua disputeEvidenceRepository
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

    /**
     * Chọn chiến lược cửa sổ khiếu nại tương ứng với một loại vé.
     *
     * @param ticketType loại vé
     * @return chiến lược cửa sổ khiếu nại phụ trách loại vé đó
     */
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

    /**
     * Xác thực kiểu MIME (contentType) phù hợp với loại bằng chứng.
     */
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
