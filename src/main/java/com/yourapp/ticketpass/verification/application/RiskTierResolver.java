package com.yourapp.ticketpass.verification.application;

import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.verification.domain.RiskTier;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Phan bac rui ro cho mot ve truoc khi quyet dinh mac do xac thuc can ap dung.
 *
 * <p>Quy tac phan bac da chot:</p>
 * <pre>
 * HIGH   : gia ban &gt;= 5.000.000d, HOAC chua xac thuc email forward, HOAC diem uy tin seller &lt; 60, HOAC phat hien QR hash trung
 * MEDIUM : 1.000.000d &lt;= gia ban &lt; 5.000.000d, HOAC seller co duoi 5 giao dich thanh cong
 * LOW    : cac truong hop con lai
 * He qua: HIGH -&gt; bat buoc admin review tay; MEDIUM -&gt; OCR tu dong + nguong confidence cao hon; LOW -&gt; auto-approve neu OCR confidence &gt;= app.ocr.min-confidence
 * </pre>
 *
 * <p>LUU Y ve rang buoc module: {@code sellerReputationScore} duoc truyen vao duoi dang
 * {@code BigDecimal} tu module {@code reputation}. Service nay KHONG import entity
 * {@code UserReputationScore}, dung theo nguyen tac 1 - cac module chi trao doi voi nhau qua
 * gia tri va ID thuan.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
@Service
@RequiredArgsConstructor
public class RiskTierResolver {

    /**
     * Phan bac rui ro theo dung quy tac mo ta o tren.
     *
     * @param salePrice gia ban cua ve
     * @param ticketType loai ve, dung de tinh chinh nguong theo dac thu tung loai
     * @param sellerReputationScore diem uy tin nguoi ban, truyen tu module reputation
     * @param sellerSuccessfulTransactions so giao dich thanh cong cua nguoi ban
     * @param emailForwardVerified da xac thuc email forward hay chua
     * @param qrDuplicateDetected co phat hien ma QR trung hay khong
     * @return bac rui ro tuong ung
     */
    public RiskTier resolve(BigDecimal salePrice, TicketType ticketType, BigDecimal sellerReputationScore,
                            int sellerSuccessfulTransactions, boolean emailForwardVerified,
                            boolean qrDuplicateDetected) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Ap dung quy tac trong javadoc theo thu tu uu tien - tra HIGH neu salePrice "
                        + ">= 5.000.000 hoac !emailForwardVerified hoac sellerReputationScore < 60 hoac "
                        + "qrDuplicateDetected; tra MEDIUM neu salePrice trong khoang [1.000.000, 5.000.000) hoac "
                        + "sellerSuccessfulTransactions < 5; con lai tra LOW. Dua cac nguong ra hang so hoac cau hinh "
                        + "thay vi hard-code rai rac.");
    }

    /**
     * Bac rui ro nay co bat buoc admin xem xet bang tay khong.
     *
     * @param riskTier bac rui ro can xet
     * @return true neu phai qua admin review
     */
    public boolean requiresManualReview(RiskTier riskTier) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve true khi riskTier la HIGH vi bac nay bat buoc admin review tay; "
                        + "MEDIUM va LOW van di theo luong xac thuc tu dong.");
    }
}
