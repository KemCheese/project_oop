package com.yourapp.ticketpass.dispute.domain;

import java.time.Duration;
import java.time.Instant;

import com.yourapp.ticketpass.ticket.domain.TicketType;

/**
 * Hợp đồng xác định cửa sổ khiếu nại theo từng loại vé — áp dụng Strategy pattern.
 *
 * <p>Mỗi {@link TicketType} có một hiện thực riêng, vì thời gian hợp lý để phát hiện vé có vấn
 * đề khác nhau rất nhiều giữa các loại vé: vé xem phim thì biết ngay tại cửa soát vé, còn vé
 * concert thì người mua cần thời gian đối chiếu thông tin trước ngày diễn.</p>
 *
 * <p><b>Giá trị đã chốt (đọc từ {@code app.dispute.window.*}):</b></p>
 * <pre>
 * MOVIE   :  2 gio  sau khi chuyen ve
 * CONCERT : 24 gio  sau khi chuyen ve
 * BUS     :  3 gio  sau khi chuyen ve
 * </pre>
 *
 * <p><b>Ràng buộc bắt buộc của {@link #computeWindowClose}:</b> kết quả phải là thời điểm
 * SỚM HƠN giữa {@code transferredAt + windowAfterTransfer()} và {@code eventStartAt}. Lý do:
 * một khi sự kiện đã bắt đầu thì không còn cách nào xác minh vé đúng hay sai, nên cửa sổ khiếu
 * nại buộc phải đóng trước giờ sự kiện dù thời hạn theo loại vé vẫn còn. Ví dụ vé concert được
 * chuyển 3 giờ trước giờ diễn: cửa sổ 24 giờ bị cắt lại còn 3 giờ.</p>
 *
 * <p><b>Trạng thái hiện tại: CHƯA CÓ HIỆN THỰC — cố ý để trống cho Prompt 2.</b> Người B sẽ tạo
 * {@code dispute/adapter/MovieDisputeWindowPolicy}, {@code ConcertDisputeWindowPolicy},
 * {@code BusDisputeWindowPolicy}. Spring sẽ tiêm toàn bộ các
 * bean này vào {@code List<DisputeWindowPolicy>} của {@code FileDisputeUseCase}, và use case
 * chọn đúng chiến lược qua {@code resolvePolicy(ticketType)} dựa trên {@link #supportedType()}.
 * Nhờ vậy, thêm một loại vé mới chỉ cần thêm một class, không sửa use case.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public interface DisputeWindowPolicy {

    /**
     * Loại vé mà chiến lược này phụ trách.
     *
     * @return loại vé tương ứng
     */
    TicketType supportedType();

    /**
     * Độ dài cửa sổ khiếu nại tính từ lúc vé được chuyển cho người mua.
     *
     * @return khoảng thời gian cho phép khiếu nại: 2 giờ với MOVIE, 24 giờ với CONCERT,
     *         3 giờ với BUS
     */
    Duration windowAfterTransfer();

    /**
     * Tính thời điểm cửa sổ khiếu nại đóng lại.
     *
     * <p>Phải trả về thời điểm SỚM HƠN giữa {@code transferredAt + windowAfterTransfer()} và
     * {@code eventStartAt}.</p>
     *
     * @param transferredAt thời điểm vé được chuyển cho người mua
     * @param eventStartAt  thời điểm sự kiện bắt đầu (giờ chiếu, giờ diễn, giờ khởi hành)
     * @return thời điểm cửa sổ khiếu nại đóng
     */
    Instant computeWindowClose(Instant transferredAt, Instant eventStartAt);

    /**
     * Kiểm tra một thời điểm có còn nằm trong cửa sổ khiếu nại hay không.
     *
     * @param transferredAt thời điểm vé được chuyển cho người mua
     * @param eventStartAt  thời điểm sự kiện bắt đầu
     * @param now           thời điểm cần kiểm tra, thường là lúc người mua nộp khiếu nại
     * @return {@code true} nếu {@code now} chưa vượt qua thời điểm cửa sổ đóng
     */
    boolean isWithinWindow(Instant transferredAt, Instant eventStartAt, Instant now);

    /**
     * Thời hạn cho người bán gửi giải trình, tính từ lúc khiếu nại được mở.
     *
     * @return khoảng thời gian người bán được phép phản hồi
     */
    Duration sellerResponseWindow();
}
