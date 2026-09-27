package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.shared.util.QrCodeGenerator;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.web.dto.CreateTicketRequest;
import com.yourapp.ticketpass.ticket.web.dto.TicketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Use case chinh cua module {@code ticket} tren san marketplace: dang ve va dieu phoi
 * ve di qua cac moc cua giao dich.
 *
 * <p>Tren san marketplace, khi seller da khoa coc, san tu dong mo khoa anh QR an cho buyer.
 * Day la buoc "chuyen ve" - buyer nhan duoc ma QR ve that de su dung.</p>
 *
 * <p>CHU THICH QUAN TRONG ve rang buoc giua cac module: cac method {@code markXxx} duoc module
 * {@code payment} va {@code dispute} goi DONG BO trong cung mot transaction. Day la ngoai le co y
 * cua nguyen tac 2 (giao tiep giua cac module qua domain event) - phan lien quan den tien phai nhat quan
 * ngay lap tuc, khong the chap nhan eventual consistency: neu tien da vao escrow ma ve chua doi trang thai
 * thi ve co the bi ban lai cho nguoi khac.</p>
 *
 * <p>Nguoc lai, cac side-effect khong lien quan den tien - cap nhat diem uy tin nguoi ban, gui thong bao,
 * ghi nhat ky kiem toan - deu di qua domain event de cac module do khong lam cham hoac lam hong
 * transaction thanh toan.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Service
@RequiredArgsConstructor
public class TransferTicketService {

    private final TicketRepository ticketRepository;

    private final TicketStateMachineService stateMachineService;

    private final ValidateTicketService validateTicketService;

    private final FileStorageService fileStorageService;

    private final QrCodeGenerator qrCodeGenerator;

    /**
     * Tao mot listing moi tu du lieu nguoi ban nhap.
     *
     * @param request du lieu ve tu form dang ve
     * @param sellerId id nguoi ban, lay tu phien dang nhap
     * @return thong tin ve vua tao de hien thi lai cho nguoi ban
     */
    public TicketResponse createListing(CreateTicketRequest request, Long sellerId) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Map CreateTicketRequest sang entity Ticket, gan sellerId va status LISTED, "
                        + "giai ma anh QR bang qrCodeGenerator.decodeFromImage roi tinh qrPayloadHash bang "
                        + "qrCodeGenerator.hashPayload, goi validateTicketService.validateForListing va nem "
                        + "BusinessRuleViolationException neu khong hop le, sinh transferInstruction qua adapter, "
                        + "luu bang ticketRepository.save, cuoi cung map sang TicketResponse voi URL anh lay tu "
                        + "fileStorageService.");
    }

    /**
     * Danh dau tien nguoi mua da vao escrow. Duoc module {@code payment} goi dong bo.
     *
     * @param ticketId id cua ve
     * @param buyerId id nguoi mua
     * @param escrowTransactionId id giao dich escrow ben module payment
     */
    public void markPaymentEscrowed(Long ticketId, Long buyerId, Long escrowTransactionId) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Goi validateTicketService.validateForPurchase, gan buyerId va "
                        + "escrowTransactionId cho ve, roi chuyen trang thai sang PAYMENT_ESCROWED qua "
                        + "stateMachineService.transition. Phai chay trong cung transaction voi ben payment.");
    }

    /**
     * Danh dau tien coc cua nguoi ban da bi khoa. Duoc module {@code payment} goi dong bo.
     *
     * @param ticketId id cua ve
     * @param depositId id ban ghi tien coc ben module payment
     */
    public void markDepositLocked(Long ticketId, Long depositId) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Gan depositId cho ve va chuyen trang thai sang DEPOSIT_LOCKED qua "
                        + "stateMachineService.transition. Phai chay trong cung transaction voi ben payment.");
    }

    /**
     * Nguoi ban danh dau da chuyen ve cho nguoi mua.
     *
     * @param ticketId id cua ve
     * @param sellerId id nguoi ban thuc hien thao tac
     */
    public void markTransferred(Long ticketId, Long sellerId) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Goi validateTicketService.validateForTransfer, chuyen trang thai sang "
                        + "TRANSFERRED qua stateMachineService.transition, ghi lai transferredAt va phat domain event "
                        + "bao cho nguoi mua biet ve da duoc chuyen.");
    }

    /**
     * Nguoi mua xac nhan da nhan duoc ve, bat dau dem cua so khieu nai.
     *
     * @param ticketId id cua ve
     * @param buyerId id nguoi mua thuc hien thao tac
     */
    public void markAwaitingUse(Long ticketId, Long buyerId) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Kiem tra buyerId dung la nguoi mua cua ve, tinh disputeWindowClosesAt bang "
                        + "stateMachineService.computeDisputeWindowClose, roi chuyen trang thai sang AWAITING_USE.");
    }

    /**
     * Nguoi ban huy listing khi ve chua co nguoi mua.
     *
     * @param ticketId id cua ve
     * @param sellerId id nguoi ban thuc hien thao tac
     * @param reason ly do huy, luu lai phuc vu doi soat va tinh uy tin
     */
    public void cancelListing(Long ticketId, Long sellerId, String reason) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Kiem tra sellerId dung la chu ve va status van la LISTED (da co tien escrow "
                        + "thi khong duoc tu huy ma phai qua luong hoan tien cua module payment), xoa listing hoac "
                        + "danh dau ngung ban, luu reason va phat domain event de module reputation ghi nhan.");
    }
}
