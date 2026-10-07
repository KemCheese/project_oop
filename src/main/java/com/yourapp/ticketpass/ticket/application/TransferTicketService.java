package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.shared.event.TicketDepositLockedEvent;
import com.yourapp.ticketpass.shared.event.TicketEscrowPaidEvent;
import com.yourapp.ticketpass.shared.event.TicketReceivedEvent;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TransferInstruction;
import com.yourapp.ticketpass.ticket.domain.ValidationResult;
import com.yourapp.ticketpass.ticket.web.dto.CreateTicketRequest;
import com.yourapp.ticketpass.ticket.web.dto.TicketResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Tao mot listing moi tu du lieu nguoi ban nhap.
     *
     * <p>Luong xu ly:
     * 1. Map CreateTicketRequest sang entity Ticket (gan sellerId, status LISTED, listedAt)
     * 2. Goi validateTicketService.validateForListing de kiem tra luat chung + luat rieng
     * 3. Sinh transferInstruction qua adapter tuong ung voi loai ve
     * 4. Luu ve vao database
     * 5. Map sang TicketResponse voi URL anh lay tu fileStorageService</p>
     *
     * @param request du lieu ve tu form dang ve
     * @param sellerId id nguoi ban, lay tu phien dang nhap
     * @return thong tin ve vua tao de hien thi lai cho nguoi ban
     */
    @Transactional
    public TicketResponse createListing(CreateTicketRequest request, Long sellerId) {
        // --- 1. Map DTO sang entity ---
        Ticket ticket = new Ticket();
        ticket.setSellerId(sellerId);
        ticket.setTicketType(request.getTicketType());
        ticket.setTitle(request.getTitle());
        ticket.setEventName(request.getEventName());
        ticket.setVenue(request.getVenue());
        ticket.setSeatInfo(request.getSeatInfo());
        ticket.setEventStartAt(request.getEventStartAt() != null
                ? request.getEventStartAt().atZone(ZoneId.systemDefault()).toInstant()
                : null);
        ticket.setOriginalPrice(request.getOriginalPrice());
        ticket.setSalePrice(request.getSalePrice());
        ticket.setProviderName(request.getProviderName());
        ticket.setProviderOrderCode(request.getProviderOrderCode());
        ticket.setTicketImageStorageKey(request.getTicketImageStorageKey());
        ticket.setQrImageStorageKey(request.getQrImageStorageKey());
        ticket.setStatus(TicketStatus.LISTED);
        ticket.setListedAt(Instant.now());

        // --- 2. Validate luat chung + luat rieng theo loai ve ---
        ValidationResult result = validateTicketService.validateForListing(ticket);
        if (!result.isValid()) {
            String errorMessage = result.getErrors().stream()
                    .collect(Collectors.joining("; "));
            throw new BusinessRuleViolationException(errorMessage);
        }

        // --- 3. Sinh huong dan chuyen ve ---
        TicketAdapter adapter = stateMachineService.resolveAdapter(ticket.getTicketType());
        TransferInstruction instruction = adapter.buildTransferInstruction(ticket);
        // Serialize thanh chuoi de luu vao cot TEXT cua entity
        ticket.setTransferInstruction(formatTransferInstruction(instruction));

        // --- 4. Luu vao database ---
        Ticket saved = ticketRepository.save(ticket);

        // --- 5. Map sang TicketResponse ---
        return toResponse(saved);
    }

    /**
     * Danh dau tien nguoi mua da vao escrow. Duoc module {@code payment} goi dong bo.
     *
     * @param ticketId id cua ve
     * @param buyerId id nguoi mua
     * @param escrowTransactionId id giao dich escrow ben module payment
     */
    @Transactional
    public void markPaymentEscrowed(Long ticketId, Long buyerId, Long escrowTransactionId) {
        // Validate nguoi mua co quyen mua ve nay
        ValidationResult result = validateTicketService.validateForPurchase(ticketId, buyerId);
        if (!result.isValid()) {
            String errorMessage = result.getErrors().stream()
                    .collect(Collectors.joining("; "));
            throw new BusinessRuleViolationException(errorMessage);
        }

        // Gan thong tin nguoi mua va escrow vao ve
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        ticket.setBuyerId(buyerId);
        ticket.setEscrowTransactionId(escrowTransactionId);
        ticketRepository.save(ticket);

        // Chuyen trang thai sang PAYMENT_ESCROWED qua state machine
        stateMachineService.transition(ticketId, TicketStatus.PAYMENT_ESCROWED);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new TicketEscrowPaidEvent(
                    ticket.getId(),
                    ticket.getSellerId(),
                    ticket.getBuyerId(),
                    ticket.getTitle(),
                    ticket.getSalePrice(),
                    Instant.now()
            ));
        }
    }

    /**
     * Danh dau tien coc cua nguoi ban da bi khoa. Duoc module {@code payment} goi dong bo.
     *
     * @param ticketId id cua ve
     * @param depositId id ban ghi tien coc ben module payment
     */
    @Transactional
    public void markDepositLocked(Long ticketId, Long depositId) {
        // Gan depositId vao ve
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        ticket.setDepositId(depositId);
        ticketRepository.save(ticket);

        // Chuyen trang thai sang DEPOSIT_LOCKED qua state machine
        stateMachineService.transition(ticketId, TicketStatus.DEPOSIT_LOCKED);

        // Tren san marketplace, ma QR bi mat da duoc nguoi ban upload san tu luc dang ve.
        // Khi nguoi ban hoan tat khoa coc 50%, he thong tu dong mo khoa va ban giao ma QR cho nguoi mua
        // bang cach chuyen tiep ngay sang TRANSFERRED ma khong bat nguoi ban phai bam nut thu cong.
        stateMachineService.transition(ticketId, TicketStatus.TRANSFERRED);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new TicketDepositLockedEvent(
                    ticket.getId(),
                    ticket.getSellerId(),
                    ticket.getBuyerId(),
                    ticket.getTitle(),
                    ticket.getSalePrice(),
                    Instant.now()
            ));
        }
    }

    /**
     * Nguoi ban danh dau da chuyen ve cho nguoi mua.
     *
     * <p>Phuong thuc giu tinh idempotent: neu ve da o TRANSFERRED thi bo qua khong bao loi.</p>
     *
     * @param ticketId id cua ve
     * @param sellerId id nguoi ban thuc hien thao tac
     */
    @Transactional
    public void markTransferred(Long ticketId, Long sellerId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (ticket.getStatus() == TicketStatus.TRANSFERRED) {
            return; // Idempotent
        }

        // Kiem tra quyen chuyen ve (phai la chu ve, status phai la DEPOSIT_LOCKED, phai co QR)
        ValidationResult result = validateTicketService.validateForTransfer(ticketId, sellerId);
        if (!result.isValid()) {
            String errorMessage = result.getErrors().stream()
                    .collect(Collectors.joining("; "));
            throw new BusinessRuleViolationException(errorMessage);
        }

        // Chuyen trang thai sang TRANSFERRED qua state machine
        // (transferredAt duoc gan tu dong trong TicketStateMachineService.transition)
        stateMachineService.transition(ticketId, TicketStatus.TRANSFERRED);
    }

    /**
     * Nguoi mua xac nhan da nhan duoc ve, bat dau dem cua so khieu nai.
     *
     * <p>Sau khi buyer nhan duoc anh QR an va kiem tra ve, bam nut "Da nhan ve".
     * State machine se tu tinh disputeWindowClosesAt theo BR-09.</p>
     *
     * @param ticketId id cua ve
     * @param buyerId id nguoi mua thuc hien thao tac
     */
    @Transactional
    public void markAwaitingUse(Long ticketId, Long buyerId) {
        // Kiem tra buyerId dung la nguoi mua cua ve
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (ticket.getBuyerId() == null || !ticket.getBuyerId().equals(buyerId)) {
            throw new BusinessRuleViolationException(
                    "Ban khong phai la nguoi mua cua ve nay.");
        }

        // Chuyen trang thai sang AWAITING_USE qua state machine
        // (disputeWindowClosesAt duoc tinh tu dong trong TicketStateMachineService.transition)
        stateMachineService.transition(ticketId, TicketStatus.AWAITING_USE);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new TicketReceivedEvent(
                    ticket.getId(),
                    ticket.getSellerId(),
                    ticket.getBuyerId(),
                    ticket.getTitle(),
                    false,
                    Instant.now()
            ));
        }
    }

    /**
     * He thong tu dong xac nhan da nhan ve khi qua han 30 phut khong co thao tac (UC-06b).
     *
     * @param ticketId id cua ve
     */
    @Transactional
    public void autoConfirmReceipt(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (ticket.getStatus() != TicketStatus.TRANSFERRED) {
            return;
        }

        stateMachineService.transition(ticketId, TicketStatus.AWAITING_USE);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new TicketReceivedEvent(
                    ticket.getId(),
                    ticket.getSellerId(),
                    ticket.getBuyerId(),
                    ticket.getTitle(),
                    true,
                    Instant.now()
            ));
        }
    }

    /**
     * Nguoi mua bao loi ky thuat: anh QR khong xem duoc / bi hong trong vong 30 phut (UC-06b).
     * He thong dong bang bo dem timeout, chuyen sang TECHNICAL_ISSUE.
     *
     * @param ticketId id cua ve
     * @param buyerId id nguoi mua
     * @param reason mo ta loi ky thuat
     */
    @Transactional
    public void reportTechnicalIssue(Long ticketId, Long buyerId, String reason) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (ticket.getBuyerId() == null || !ticket.getBuyerId().equals(buyerId)) {
            throw new BusinessRuleViolationException("Ban khong phai la nguoi mua cua ve nay.");
        }

        if (ticket.getStatus() != TicketStatus.TRANSFERRED) {
            throw new BusinessRuleViolationException(
                    "Chi co the bao loi ky thuat khi ve dang o trang thai TRANSFERRED.");
        }

        // Kiem tra con trong cua so 30 phut tu luc transferredAt
        Instant transferredAt = ticket.getTransferredAt();
        if (transferredAt != null && transferredAt.plus(java.time.Duration.ofMinutes(30)).isBefore(Instant.now())) {
            throw new BusinessRuleViolationException(
                    "Da qua thoi han 30 phut de bao loi ky thuat anh QR.");
        }

        // Chuyen sang TECHNICAL_ISSUE qua state machine de dong bang timeout
        stateMachineService.transition(ticketId, TicketStatus.TECHNICAL_ISSUE);
    }

    /**
     * Nguoi ban huy listing khi ve chua co nguoi mua.
     *
     * <p>Chi ap dung khi ve van o trang thai LISTED (chua co tien escrow).
     * Khi da co nguoi mua thanh toan (PAYMENT_ESCROWED tro di), nguoi ban
     * khong duoc tu huy ma phai di qua luong hoan tien cua module payment.</p>
     *
     * @param ticketId id cua ve
     * @param sellerId id nguoi ban thuc hien thao tac
     * @param reason ly do huy, luu lai phuc vu doi soat va tinh uy tin
     */
    @Transactional
    public void cancelListing(Long ticketId, Long sellerId, String reason) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        // Kiem tra nguoi thuc hien la chu ve
        if (!ticket.getSellerId().equals(sellerId)) {
            throw new BusinessRuleViolationException(
                    "Ban khong phai la chu so huu cua ve nay.");
        }

        // Chi cho phep huy khi ve con o trang thai LISTED (chua co tien lien quan)
        if (ticket.getStatus() != TicketStatus.LISTED) {
            throw new BusinessRuleViolationException(
                    "Khong the huy ve o trang thai " + ticket.getStatus()
                            + ". Chi co the huy khi ve dang o trang thai LISTED.");
        }

        // Chuyen trang thai sang CANCELLED qua state machine (giu lai lich su tren he thong)
        stateMachineService.transition(ticketId, TicketStatus.CANCELLED);
    }

    // ========================= Helper methods =========================

    /**
     * Chuyen TransferInstruction thanh chuoi van ban de luu vao cot TEXT.
     */
    private String formatTransferInstruction(TransferInstruction instruction) {
        if (instruction == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(instruction.getTicketType()).append("] ");
        sb.append("Kenh: ").append(instruction.getChannel()).append("\n");

        if (instruction.getSteps() != null) {
            for (int i = 0; i < instruction.getSteps().size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(instruction.getSteps().get(i)).append("\n");
            }
        }

        if (instruction.getNote() != null && !instruction.getNote().isBlank()) {
            sb.append("Luu y: ").append(instruction.getNote());
        }
        return sb.toString();
    }

    /**
     * Map entity Ticket sang DTO TicketResponse, chuyen storageKey thanh URL cong khai.
     */
    private TicketResponse toResponse(Ticket ticket) {
        TicketResponse response = new TicketResponse();
        response.setId(ticket.getId());
        response.setSellerId(ticket.getSellerId());
        response.setBuyerId(ticket.getBuyerId());
        response.setTicketType(ticket.getTicketType());
        response.setStatus(ticket.getStatus());
        response.setTitle(ticket.getTitle());
        response.setEventName(ticket.getEventName());
        response.setVenue(ticket.getVenue());
        response.setSeatInfo(ticket.getSeatInfo());
        response.setEventStartAt(ticket.getEventStartAt());
        response.setOriginalPrice(ticket.getOriginalPrice());
        response.setSalePrice(ticket.getSalePrice());
        response.setTransferInstruction(ticket.getTransferInstruction());
        if (ticket.getEventStartAt() != null && ticket.getDisputeWindowClosesAt() != null) {
            response.setDisputeWindowClosesAt(ticket.getEventStartAt().plus(java.time.Duration.ofDays(1)));
        } else {
            response.setDisputeWindowClosesAt(ticket.getDisputeWindowClosesAt());
        }
        response.setCreatedAt(ticket.getCreatedAt());
        response.setReceivedAt(ticket.getReceivedAt());
        
        if (ticket.getReceivedAt() != null) {
            java.time.Instant expiredAt = ticket.getReceivedAt().plus(java.time.Duration.ofMinutes(30));
            response.setQrExpired(expiredAt.isBefore(java.time.Instant.now()));
        } else {
            response.setQrExpired(false);
        }

        // Chuyen storageKey thanh URL cong khai qua FileStorageService
        if (ticket.getTicketImageStorageKey() != null && !ticket.getTicketImageStorageKey().isBlank()) {
            response.setTicketImageUrl(fileStorageService.buildPublicUrl(ticket.getTicketImageStorageKey()));
        }
        if (ticket.getQrImageStorageKey() != null && !ticket.getQrImageStorageKey().isBlank()) {
            response.setQrImageUrl(fileStorageService.buildPublicUrl(ticket.getQrImageStorageKey()));
        }

        return response;
    }
}
