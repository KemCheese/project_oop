package com.yourapp.ticketpass.dispute.application;

import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidence;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceType;
import com.yourapp.ticketpass.dispute.domain.DisputeReason;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeWindowPolicy;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketType;

import lombok.RequiredArgsConstructor;

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
 * <p><b>Giao dịch:</b> {@code @Transactional} sẽ được thêm ở Prompt 2. Riêng việc ghi tệp là
 * tác vụ ngoài cơ sở dữ liệu nên không thể rollback được — cần ghi tệp trước rồi mới lưu bản
 * ghi, và dọn tệp mồ côi bằng job riêng nếu lưu bản ghi thất bại.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Service
@RequiredArgsConstructor
public class FileDisputeUseCase {

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

    /**
     * Người mua mở khiếu nại cho một vé.
     *
     * @param ticketId    ID vé bị khiếu nại
     * @param buyerId     ID người mua đứng ra khiếu nại
     * @param reason      lý do khiếu nại
     * @param description mô tả chi tiết vấn đề
     * @return hồ sơ khiếu nại vừa được tạo
     */
    public Dispute fileDispute(Long ticketId, Long buyerId, DisputeReason reason, String description) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: (1) tra vé qua ticketRepository, "
                + "ném ResourceNotFoundException nếu không có; "
                + "(2) xác minh buyerId đúng là người mua của vé, nếu không thì ném BusinessRuleViolationException; "
                + "(3) chặn khiếu nại trùng lặp bằng disputeRepository.existsByTicketIdAndStatusIn(ticketId, "
                + "List.of(OPEN, AWAITING_SELLER_RESPONSE, SELLER_RESPONDED, UNDER_REVIEW)); "
                + "(4) lấy policy = resolvePolicy(ticket.getTicketType()) và kiểm tra "
                + "policy.isWithinWindow(transferredAt, eventStartAt, Instant.now()) — hết cửa sổ thì từ chối; "
                + "(5) tạo Dispute với status = OPEN, filedAt = now, "
                + "windowClosesAt = policy.computeWindowClose(transferredAt, eventStartAt), "
                + "sellerResponseDeadline = now + policy.sellerResponseWindow(), "
                + "escrowTransactionId và depositId lấy từ vé; "
                + "(6) gọi ticketStateMachineService.transition(ticketId, TicketStatus.DISPUTE_UNDER_REVIEW) "
                + "để khoá tiền lại, rồi save và trả về. Nhớ thêm @Transactional.");
    }

    /**
     * Đính kèm một tệp bằng chứng vào khiếu nại.
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
    public DisputeEvidence attachEvidence(Long disputeId, Long uploadedByUserId, DisputeEvidenceType type,
                                          String originalFilename, String contentType, long sizeBytes,
                                          InputStream content) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: (1) tra Dispute theo disputeId và từ chối "
                + "nếu khiếu nại đã có phán quyết (RESOLVED_*/WITHDRAWN) vì hồ sơ đã đóng; "
                + "(2) xác minh uploadedByUserId là người mua, người bán của khiếu nại, hoặc admin; "
                + "(3) kiểm tra contentType nằm trong danh sách cho phép theo type và sizeBytes không vượt giới hạn; "
                + "(4) gọi fileStorageService.store(\"disputes\", originalFilename, content, sizeBytes, contentType) "
                + "để lấy StoredFileRef — TUYỆT ĐỐI KHÔNG tự ghi tệp ra đĩa (nguyên tắc 3); "
                + "(5) tạo DisputeEvidence với storageKey lấy từ StoredFileRef, uploadedAt = Instant.now(), "
                + "rồi save qua disputeEvidenceRepository và trả về. Nhớ thêm @Transactional.");
    }

    /**
     * Chọn chiến lược cửa sổ khiếu nại tương ứng với một loại vé.
     *
     * @param ticketType loại vé
     * @return chiến lược cửa sổ khiếu nại phụ trách loại vé đó
     */
    public DisputeWindowPolicy resolvePolicy(TicketType ticketType) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: duyệt windowPolicies tìm bean có "
                + "supportedType() == ticketType và trả về; nếu không có bean nào phụ trách loại vé này thì ném "
                + "BusinessRuleViolationException với thông điệp nêu rõ tên loại vé còn thiếu chiến lược. "
                + "Nên gom windowPolicies thành Map<TicketType, DisputeWindowPolicy> một lần trong "
                + "@PostConstruct để không phải duyệt danh sách mỗi lần gọi.");
    }
}
