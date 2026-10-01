package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.shared.config.TaxConfig;
import com.yourapp.ticketpass.shared.dto.TaxWithholding;

import lombok.RequiredArgsConstructor;

/**
 * Khấu thuế trên khoản thu của người bán — <b>đây là một STUB phục vụ đồ án môn
 * học</b>.
 *
 * <p>
 * <b>Phạm vi rõ ràng:</b> service này KHÔNG mô phỏng luật thuế thật của Việt
 * Nam. Nó chỉ
 * áp một tỷ lệ phẳng {@code ratePercent} lên khoản thu vượt ngưỡng
 * {@code taxableThreshold},
 * với mục đích minh hoạ rằng luồng tiền có một bước khấu trừ tách biệt. Cờ
 * {@code TaxWithholding.mock} luôn phản ánh {@code TaxConfig.mock} để bên gọi
 * và người đọc
 * báo cáo biết đây là số liệu mô phỏng.
 * </p>
 *
 * <p>
 * <b>Single Responsibility — RẤT QUAN TRỌNG:</b> service này tách hoàn toàn
 * khỏi
 * {@link CommissionCalculatorService}. Hai lớp không import nhau, không gọi
 * nhau và không
 * chia sẻ cấu hình. Lý do: hoa hồng là chính sách kinh doanh của sàn, còn thuế
 * là nghĩa vụ
 * pháp lý bên ngoài — hai thứ thay đổi độc lập với nhau. Chỉ có
 * {@code ReleaseFundsUseCase} biết cả hai và điều phối theo trình tự: tính hoa
 * hồng trước,
 * khấu thuế sau.
 * </p>
 *
 * <p>
 * Khi {@code TaxConfig.enabled = false}, mọi khoản thu đều không bị khấu thuế:
 * {@code taxAmount = 0} và {@code netAmount = grossAmount}.
 * </p>
 *
 * <p>
 * Module: payment
 * </p>
 *
 * <p>
 * Chủ sở hữu: Người B
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TaxWithholdingService {

    /** Cấu hình thuế, nạp từ {@code app.tax.*}. */
    private final TaxConfig taxConfig;

    /**
     * Tính số thuế cần khấu trên khoản thu gộp của người bán.
     *
     * @param sellerGrossAmount khoản thu gộp của người bán, tức giá bán đã trừ hoa
     *                          hồng
     * @param sellerId          ID người bán, giữ trong chữ ký để Prompt 2 có thể
     *                          phân biệt
     *                          cá nhân/tổ chức nếu cần mở rộng
     * @return chi tiết khấu thuế gồm số thuế và số thực nhận
     */
    public TaxWithholding withhold(BigDecimal sellerGrossAmount, Long sellerId) {
        if (!isTaxable(sellerGrossAmount)) {
            // Không thuộc diện chịu thuế (disabled hoặc dưới ngưỡng): taxAmount = 0, netAmount = gross
            return new TaxWithholding(
                    sellerGrossAmount,
                    taxConfig.getRatePercent(),
                    BigDecimal.ZERO,
                    sellerGrossAmount,
                    taxConfig.isMock());
        }

        // taxAmount = sellerGrossAmount * ratePercent / 100, làm tròn HALF_UP tới 2 chữ số thập phân
        BigDecimal taxAmount = sellerGrossAmount
                .multiply(taxConfig.getRatePercent())
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal netAmount = sellerGrossAmount.subtract(taxAmount);

        return new TaxWithholding(
                sellerGrossAmount,
                taxConfig.getRatePercent(),
                taxAmount,
                netAmount,
                taxConfig.isMock());
    }

    /**
     * Kiểm tra khoản thu có thuộc diện chịu thuế theo cấu hình hiện tại hay không.
     *
     * @param sellerGrossAmount khoản thu gộp của người bán
     * @return {@code true} nếu khoản thu phải khấu thuế
     */
    public boolean isTaxable(BigDecimal sellerGrossAmount) {
        // Điều kiện: enabled = true VÀ amount không null VÀ amount >= threshold (BR-08: đạt ngưỡng là chịu thuế)
        return taxConfig.isEnabled()
                && sellerGrossAmount != null
                && sellerGrossAmount.compareTo(taxConfig.getTaxableThreshold()) >= 0;
    }
}
