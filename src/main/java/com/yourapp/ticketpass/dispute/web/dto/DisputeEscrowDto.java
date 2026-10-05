package com.yourapp.ticketpass.dispute.web.dto;

import java.math.BigDecimal;

import com.yourapp.ticketpass.payment.domain.EscrowStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO đại diện cho thông tin tóm tắt ký quỹ (Escrow) phục vụ hiển thị trên view phán quyết tranh chấp.
 * Tránh rò rỉ trực tiếp JPA Entity EscrowTransaction ra tầng View.
 *
 * <p>Module: dispute</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeEscrowDto {

    /** ID của giao dịch ký quỹ. */
    private Long id;

    /** Số tiền ký quỹ hiển thị ra giao diện (khớp với escrow.amount trong template detail.html). */
    private BigDecimal amount;

    /** Giá bán của vé. */
    private BigDecimal salePrice;

    /** Trạng thái ký quỹ. */
    private EscrowStatus status;

    /** Tên cổng thanh toán. */
    private String gatewayName;
}
