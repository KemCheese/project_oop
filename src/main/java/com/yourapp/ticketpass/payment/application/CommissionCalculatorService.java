package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.payment.domain.CommissionTier;
import com.yourapp.ticketpass.shared.config.CommissionConfig;
import com.yourapp.ticketpass.shared.dto.CommissionBreakdown;

import lombok.RequiredArgsConstructor;

/**
 * Tính hoa hồng sàn cho một giao dịch vé.
 *
 * <p><b>Bảng phí đã chốt (nguyên văn):</b></p>
 * <pre>
 * Duoi  1.000.000d : 7%
 * Duoi  5.000.000d : 5%
 * Duoi 10.000.000d : 3%
 * Tu   10.000.000d : 2%, toi da 400.000d/giao dich
 * Phi san toi thieu: max(tier% * gia_ban, 5.000d), chia doi buyer/seller
 * </pre>
 *
 * <p><b>Cách tính là TRA BẢNG, KHÔNG LŨY TIẾN.</b> Nghĩa là toàn bộ giá bán được nhân với
 * đúng một tỷ lệ của bậc mà nó rơi vào, chứ không chia giá bán thành nhiều đoạn rồi cộng dồn.
 * Ví dụ vé 6.000.000đ thuộc bậc "dưới 10 triệu" nên hoa hồng thô là
 * {@code 3% * 6.000.000 = 180.000đ}, KHÔNG phải cộng 7% của một triệu đầu với 5% của bốn triệu
 * tiếp theo.</p>
 *
 * <p><b>Phí tối thiểu:</b> {@code totalCommission = max(tier% * salePrice, 5.000d)}. Với vé rất
 * rẻ, phí theo tỷ lệ có thể nhỏ hơn 5.000đ nên phải nâng lên mức sàn 5.000đ. Khi mức sàn được
 * áp dụng, đánh dấu {@code minFeeApplied = true} trong {@link CommissionBreakdown}.</p>
 *
 * <p><b>Chặn trần:</b> chỉ bậc {@link CommissionTier#FROM_10M} có trần 400.000đ/giao dịch.
 * Khi trần được áp dụng, đánh dấu {@code capApplied = true}. Trường hợp cả mức sàn và trần đều
 * có thể áp dụng thì trần được xét sau cùng, vì trần là cam kết tuyệt đối với người dùng.</p>
 *
 * <p><b>Chia đôi:</b> tổng hoa hồng được chia đôi cho người mua và người bán theo
 * {@code buyerShareRatio} và {@code sellerShareRatio} trong {@link CommissionConfig}
 * (mặc định 50/50). Phần lẻ do làm tròn phải được xử lý sao cho
 * {@code buyerShare + sellerShare == totalCommission} tuyệt đối, không được sai một đồng.</p>
 *
 * <p><b>Single Responsibility — RẤT QUAN TRỌNG:</b> class này KHÔNG được biết bất cứ điều gì
 * về thuế. Không import, không gọi, không tham chiếu {@code TaxWithholdingService} hay
 * {@code TaxConfig}. Hoa hồng và thuế là hai mối quan tâm hoàn toàn tách biệt; việc khấu thuế
 * do {@link TaxWithholdingService} đảm nhiệm và được {@code ReleaseFundsUseCase} gọi sau khi
 * hoa hồng đã tính xong.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Service
@RequiredArgsConstructor
public class CommissionCalculatorService {

    /** Cấu hình bảng phí runtime, nạp từ {@code app.commission.*}. */
    private final CommissionConfig commissionConfig;

    /**
     * Tra bảng để tìm bậc phí tương ứng với giá bán.
     *
     * @param salePrice giá bán vé
     * @return bậc phí áp dụng cho giá bán này
     */
    public CommissionTier resolveTier(BigDecimal salePrice) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: tra bảng phí theo giá bán — "
                + "duyệt CommissionTier.values() theo thứ tự khai báo, trả về bậc đầu tiên có "
                + "exclusiveUpperBound == null (bậc cao nhất) hoặc salePrice < exclusiveUpperBound; "
                + "biên trên là exclusive nên salePrice đúng bằng 1.000.000đ phải rơi vào UNDER_5M. "
                + "Ném BusinessRuleViolationException nếu salePrice null hoặc <= 0.");
    }

    /**
     * Tính chi tiết hoa hồng cho một giá bán.
     *
     * @param salePrice giá bán vé
     * @return chi tiết hoa hồng gồm tổng, phần người mua, phần người bán và các cờ sàn/trần
     */
    public CommissionBreakdown calculate(BigDecimal salePrice) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: tính hoa hồng theo trình tự — "
                + "(1) resolveTier(salePrice) để lấy bậc và tierRatePercent; "
                + "(2) rawCommission = salePrice * ratePercent / 100; "
                + "(3) áp mức sàn totalCommission = max(rawCommission, commissionConfig.getMinFee()) "
                + "và đặt minFeeApplied = true nếu mức sàn được dùng; "
                + "(4) nếu bậc có maxCommission thì totalCommission = min(totalCommission, maxCommission) "
                + "và đặt capApplied = true nếu trần được dùng; "
                + "(5) chia đôi theo buyerShareRatio/sellerShareRatio, làm tròn HALF_UP tới 2 chữ số thập phân "
                + "rồi dồn phần lẻ vào sellerShare để bảo đảm buyerShare + sellerShare == totalCommission; "
                + "(6) trả CommissionBreakdown với tierCode = tier.name().");
    }

    /**
     * Lấy phần hoa hồng do người mua chịu từ một chi tiết hoa hồng đã tính.
     *
     * @param breakdown chi tiết hoa hồng
     * @return số tiền hoa hồng người mua phải chịu
     */
    public BigDecimal buyerShareOf(CommissionBreakdown breakdown) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: trả về breakdown.getBuyerShare(), "
                + "kiểm tra breakdown không null và ném BusinessRuleViolationException nếu null.");
    }

    /**
     * Lấy phần hoa hồng do người bán chịu từ một chi tiết hoa hồng đã tính.
     *
     * @param breakdown chi tiết hoa hồng
     * @return số tiền hoa hồng người bán phải chịu
     */
    public BigDecimal sellerShareOf(CommissionBreakdown breakdown) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: trả về breakdown.getSellerShare(), "
                + "kiểm tra breakdown không null và ném BusinessRuleViolationException nếu null.");
    }
}
