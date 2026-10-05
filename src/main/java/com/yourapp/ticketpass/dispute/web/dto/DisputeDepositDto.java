package com.yourapp.ticketpass.dispute.web.dto;

import java.math.BigDecimal;

import com.yourapp.ticketpass.payment.domain.DepositStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO đại diện cho thông tin tóm tắt khoản tiền cọc (Deposit) phục vụ hiển thị trên view phán quyết tranh chấp.
 * Tránh rò rỉ trực tiếp JPA Entity Deposit ra tầng View.
 *
 * <p>Module: dispute</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeDepositDto {

    /** ID của khoản cọc. */
    private Long id;

    /** Số tiền cọc bị khóa (khớp với deposit.depositAmount trong template detail.html). */
    private BigDecimal depositAmount;

    /** Trạng thái khoản cọc. */
    private DepositStatus status;

    /** Tỷ lệ cọc (phần trăm). */
    private BigDecimal depositPercent;
}
