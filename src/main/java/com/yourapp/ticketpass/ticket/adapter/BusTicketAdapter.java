package com.yourapp.ticketpass.ticket.adapter;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.domain.TransferInstruction;
import com.yourapp.ticketpass.ticket.domain.ValidationResult;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Adapter cho ve xe khach cua cac nha xe tren san marketplace.
 *
 * <p>LUU Y: Day la ve xe khach duong dai cua cac nha xe (vi du Phuong Trang, Thanh Buoi),
 * KHONG phai ve xe buyt noi do.</p>
 *
 * <p>Truong bat buoc khi dang ve xe khach: hang xe (lua chon trong danh sach co san),
 * diem xuat phat, diem den, gio chay, so luong cho, ten ghe (so luong cho phai khop
 * voi so ghe nhap vao), gia ban, anh cong khai (de buyer xem truoc) va anh QR an
 * (chi mo khoa sau escrow + coc).</p>
 *
 * <p>Cua so khieu nai DU KIEN: 3 gio sau khi ve duoc chuyen (anh QR an mo khoa), doc tu
 * cau hinh {@code app.dispute.window.bus} o Prompt 2, va luon phai dong truoc
 * {@code eventStartAt} (gio xe xuat ben).</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Component
public class BusTicketAdapter implements TicketAdapter {

    @Override
    public TicketType supportedType() {
        return TicketType.BUS;
    }

    @Override
    public Duration disputeWindowAfterTransfer() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve cua so khieu nai 3 gio cho ve xe khach, doc tu cau hinh "
                        + "app.dispute.window.bus thay vi hard-code.");
    }

    @Override
    public Duration autoCompleteDelay() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve do tre tu dong hoan tat cho ve xe khach; phai dong cua so "
                        + "truoc gio xe xuat ben vi nha xe khong giai quyet khieu nai sau khi xe da chay.");
    }

    @Override
    public boolean requiresSeatInfo() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Xac dinh yeu cau seatInfo cho ve xe khach - xe giuong nam co danh so "
                        + "giuong nen can, con xe ghe ngoi tu do thi khong the bat buoc.");
    }

    @Override
    public boolean requiresEmailForwardProof() {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Tra ve false vi nha xe thuong chi gui SMS ma dat cho chu khong gui email; "
                        + "thay vao do se dua vao OCR anh ve va doi chieu providerOrderCode.");
    }

    @Override
    public ValidationResult validateListing(Ticket ticket) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Kiem tra ve xe khach truoc khi dang ban - bat buoc co tuyen di trong "
                        + "eventName, diem don trong venue, gio xuat ben con o tuong lai va du thoi gian bao nha xe "
                        + "doi thong tin hanh khach, phai co anh ve. Tra ValidationResult.ok() hoac fail(...).");
    }

    @Override
    public TransferInstruction buildTransferInstruction(Ticket ticket) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Sinh huong dan chuyen ve xe khach theo kenh ma dat cho - cac buoc gom "
                        + "goi tong dai nha xe doi so dien thoai hanh khach sang cua nguoi mua, gui ma dat cho cho "
                        + "nguoi mua, va nhac nguoi mua co mat truoc gio xuat ben.");
    }
}
