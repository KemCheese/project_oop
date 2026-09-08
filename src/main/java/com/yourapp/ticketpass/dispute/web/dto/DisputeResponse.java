package com.yourapp.ticketpass.dispute.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.yourapp.ticketpass.dispute.domain.DisputeReason;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Hồ sơ khiếu nại đầy đủ để hiển thị trên giao diện, kèm danh sách bằng chứng.
 *
 * <p>Danh sách bằng chứng được nạp riêng qua
 * {@code DisputeEvidenceRepository.findByDisputeId(...)} rồi gắn vào DTO, vì entity
 * {@code Dispute} không có tập hợp {@code @OneToMany} theo nguyên tắc kiến trúc số 1.</p>
 *
 * <p>Ba trường tiền {@code totalDeposit}, {@code founderCut} và {@code buyerCompensation} chỉ
 * có giá trị khi khiếu nại đã được phán quyết người mua thắng và cọc của người bán bị tịch thu.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class DisputeResponse {

    /** ID của khiếu nại. */
    private Long id;

    /** ID vé bị khiếu nại. */
    private Long ticketId;

    /** ID người mua đã mở khiếu nại. */
    private Long buyerId;

    /** ID người bán bị khiếu nại. */
    private Long sellerId;

    /** Trạng thái hiện tại của khiếu nại. */
    private DisputeStatus status;

    /** Lý do khiếu nại. */
    private DisputeReason reason;

    /** Mô tả chi tiết vấn đề do người mua trình bày. */
    private String description;

    /** Thời điểm người mua nộp khiếu nại. */
    private Instant filedAt;

    /** Hạn cuối được mở khiếu nại theo loại vé. */
    private Instant windowClosesAt;

    /** Hạn cuối để người bán gửi giải trình. */
    private Instant sellerResponseDeadline;

    /** Nội dung giải trình của người bán. */
    private String sellerResponse;

    /** Kết quả phán quyết cuối cùng. */
    private DisputeOutcome outcome;

    /** Tổng tiền cọc của người bán tại thời điểm phán quyết. */
    private BigDecimal totalDeposit;

    /** Phần cọc sàn giữ lại khi tịch thu. */
    private BigDecimal founderCut;

    /** Phần cọc bồi thường cho người mua khi tịch thu. */
    private BigDecimal buyerCompensation;

    /** Danh sách bằng chứng của khiếu nại. */
    private List<DisputeEvidenceResponse> evidences;
}
