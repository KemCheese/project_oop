package com.yourapp.ticketpass.wallet.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO yêu cầu nạp tiền vào ví điện tử.
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@Setter
public class TopUpRequest {

    @NotNull(message = "Số tiền nạp không được để trống")
    @DecimalMin(value = "10000", message = "Số tiền nạp tối thiểu là 10.000 VNĐ")
    private BigDecimal amount;

    private String note;
}
