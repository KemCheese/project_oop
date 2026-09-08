package com.yourapp.ticketpass.ticket.adapter;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.domain.TransferInstruction;
import com.yourapp.ticketpass.ticket.domain.ValidationResult;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Adapter cho ve xem phim.
 *
 * <p>Cua so khieu nai DU KIEN: 2 gio sau khi ve duoc chuyen, doc tu cau hinh
 * {@code app.dispute.window.movie} o Prompt 2, va luon phai dong truoc {@code eventStartAt}
 * vi ve phim thuong duoc mua sat gio chieu. Ve phim bat buoc co thong tin cho ngoi
 * (phong chieu + so ghe) va thuong chuyen qua ma dat cho tren app cua rap.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Component
public class MovieTicketAdapter implements TicketAdapter {

    @Override
    public TicketType supportedType() {
        return TicketType.MOVIE;
    }

    @Override
    public Duration disputeWindowAfterTransfer() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve cua so khieu nai 2 gio cho ve phim, doc tu cau hinh "
                        + "app.dispute.window.movie thay vi hard-code.");
    }

    @Override
    public Duration autoCompleteDelay() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve do tre tu dong hoan tat cho ve phim, tinh bang "
                        + "cua so khieu nai cong bien an toan, va khong duoc vuot qua thoi diem eventStartAt.");
    }

    @Override
    public boolean requiresSeatInfo() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve true vi ve phim bat buoc khai phong chieu va so ghe "
                        + "de nguoi mua doi chieu voi ve nhan duoc.");
    }

    @Override
    public boolean requiresEmailForwardProof() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Quyet dinh ve phim co bat buoc email forward tu rap hay khong; "
                        + "neu co thi VerificationService se doi chieu ma dat cho trong email voi providerOrderCode.");
    }

    @Override
    public ValidationResult validateListing(Ticket ticket) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Kiem tra ve phim truoc khi dang ban - bat buoc co seatInfo, "
                        + "eventStartAt phai o tuong lai va con du thoi gian chuyen ve, salePrice khong vuot "
                        + "originalPrice qua muc tran cho phep, phai co anh ve va anh QR. Tra ValidationResult.ok() "
                        + "hoac ValidationResult.fail(danh sach loi).");
    }

    @Override
    public TransferInstruction buildTransferInstruction(Ticket ticket) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Sinh huong dan chuyen ve phim theo kenh ma dat cho - cac buoc "
                        + "gom mo app cua rap, tim don hang theo providerOrderCode, gui ma dat cho va anh QR "
                        + "cho nguoi mua qua chat cua san; kem ghi chu han chot truoc gio chieu.");
    }
}
