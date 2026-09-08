package com.yourapp.ticketpass.ticket.adapter;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.domain.TransferInstruction;
import com.yourapp.ticketpass.ticket.domain.ValidationResult;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Adapter cho ve tau hoa.
 *
 * <p>Cua so khieu nai DU KIEN: 6 gio sau khi ve duoc chuyen, doc tu cau hinh
 * {@code app.dispute.window.train} o Prompt 2, va luon phai dong truoc {@code eventStartAt}
 * (o day la gio tau chay). Ve tau gan voi giay to tuy than nen viec chuyen nhuong phai qua
 * thu tuc doi ten hanh khach; neu khong doi duoc ten thi ve coi nhu khong the su dung.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Component
public class TrainTicketAdapter implements TicketAdapter {

    @Override
    public TicketType supportedType() {
        return TicketType.TRAIN;
    }

    @Override
    public Duration disputeWindowAfterTransfer() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve cua so khieu nai 6 gio cho ve tau, doc tu cau hinh "
                        + "app.dispute.window.train thay vi hard-code.");
    }

    @Override
    public Duration autoCompleteDelay() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve do tre tu dong hoan tat cho ve tau; phai dong cua so truoc "
                        + "gio tau chay vi sau khi tau khoi hanh thi khong con co so xu ly tranh chap.");
    }

    @Override
    public boolean requiresSeatInfo() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve true vi ve tau bat buoc khai so toa va so giuong/ghe "
                        + "de nguoi mua doi chieu khi len tau.");
    }

    @Override
    public boolean requiresEmailForwardProof() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Xac dinh ve tau co bat buoc email forward hay khong; ve tau da co "
                        + "thu tuc doi ten chinh chu nen co the chap nhan anh chup ve sau doi ten thay cho email.");
    }

    @Override
    public ValidationResult validateListing(Ticket ticket) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Kiem tra ve tau truoc khi dang ban - bat buoc co ga di va ga den trong "
                        + "eventName hoac venue, so toa va so ghe trong seatInfo, gio tau chay phai con du thoi gian "
                        + "lam thu tuc doi ten, va salePrice khong vuot originalPrice theo quy dinh nganh duong sat. "
                        + "Tra ValidationResult.ok() hoac fail(...).");
    }

    @Override
    public TransferInstruction buildTransferInstruction(Ticket ticket) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Sinh huong dan chuyen ve tau theo kenh doi ten hanh khach - cac buoc gom "
                        + "thu thap ho ten va so giay to cua nguoi mua, thuc hien doi ten tren cong ban ve hoac tai "
                        + "nha ga, sau do gui lai ve da doi ten kem anh QR moi cho nguoi mua.");
    }
}
