package com.yourapp.ticketpass.ticket.adapter;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.domain.TransferInstruction;
import com.yourapp.ticketpass.ticket.domain.ValidationResult;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Adapter cho ve concert / su kien am nhac.
 *
 * <p>Cua so khieu nai DU KIEN: 24 gio sau khi ve duoc chuyen, doc tu cau hinh
 * {@code app.dispute.window.concert} o Prompt 2, va luon phai dong truoc {@code eventStartAt}.
 * Ve concert thuong duoc sang tay truoc su kien hang tuan nen cua so dai nhat trong bon loai;
 * day cung la loai ve co gia tri cao va rui ro gia mao lon nhat, nen bat buoc co bang chung
 * email forward tu don vi phat hanh.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Component
public class ConcertTicketAdapter implements TicketAdapter {

    @Override
    public TicketType supportedType() {
        return TicketType.CONCERT;
    }

    @Override
    public Duration disputeWindowAfterTransfer() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve cua so khieu nai 24 gio cho ve concert, doc tu cau hinh "
                        + "app.dispute.window.concert thay vi hard-code.");
    }

    @Override
    public Duration autoCompleteDelay() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve do tre tu dong hoan tat cho ve concert; vi su kien co the "
                        + "cach ngay chuyen ve rat lau, phai lay moc som hon giua (transferredAt + cua so khieu nai) "
                        + "va eventStartAt.");
    }

    @Override
    public boolean requiresSeatInfo() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Xac dinh yeu cau seatInfo cho ve concert - ve co danh so ghe thi bat buoc, "
                        + "ve khu vuc dung (standing/fan zone) thi chi can hang ve nen khong the bat buoc cung.");
    }

    @Override
    public boolean requiresEmailForwardProof() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve true vi ve concert gia tri cao, bat buoc nop email xac nhan "
                        + "tu don vi phat hanh de EmailForwardParserService doi chieu providerName va providerOrderCode.");
    }

    @Override
    public ValidationResult validateListing(Ticket ticket) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Kiem tra ve concert truoc khi dang ban - bat buoc co eventName, venue, "
                        + "hang ve trong seatInfo, anh ve va anh QR ro net; chan truong hop salePrice cao bat thuong "
                        + "so voi originalPrice (dau hieu dau co ve). Tra ValidationResult.ok() hoac fail(...).");
    }

    @Override
    public TransferInstruction buildTransferInstruction(Ticket ticket) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Sinh huong dan chuyen ve concert theo kenh email forward / doi ten "
                        + "tren cong ban ve - cac buoc gom dang nhap cong ban ve, chon chuc nang chuyen nhuong ve, "
                        + "nhap email nguoi mua, va forward email xac nhan cho nguoi mua lam bang chung.");
    }
}
