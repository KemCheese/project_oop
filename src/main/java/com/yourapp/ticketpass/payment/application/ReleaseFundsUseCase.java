package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.shared.dto.CommissionBreakdown;
import com.yourapp.ticketpass.shared.dto.TaxWithholding;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.shared.event.TicketTransactionCompletedEvent;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseFundsUseCase {

    private final EscrowTransactionRepository escrowTransactionRepository;

    private final DepositRepository depositRepository;

    private final CommissionCalculatorService commissionCalculatorService;

    private final TaxWithholdingService taxWithholdingService;

    private final PaymentGateway paymentGateway;

    private final ApplicationEventPublisher eventPublisher;

    private final TicketStateMachineService stateMachineService;

    @Transactional
    public void release(Long ticketId, CompletionReason reason) {
        EscrowTransaction tx = escrowTransactionRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy giao dịch ký quỹ cho vé #" + ticketId));

        if (tx.getStatus() == EscrowStatus.RELEASED) {
            log.info("[ReleaseFunds] Escrow id={} đã ở RELEASED, bỏ qua gọi trùng.", tx.getId());
            return;
        }

        if (tx.getStatus() != EscrowStatus.HELD) {
            throw new BusinessRuleViolationException(
                    "Giao dịch ký quỹ #" + tx.getId() + " đang ở trạng thái " + tx.getStatus()
                            + ", không thể giải ngân. Chỉ cho phép giải ngân khi status = HELD.");
        }

        BigDecimal salePrice = tx.getSalePrice();
        Long sellerId = tx.getSellerId();
        Long buyerId = tx.getBuyerId();

        CommissionBreakdown commission = commissionCalculatorService.calculate(salePrice);
        BigDecimal sellerCommission = commission.getSellerShare();
        BigDecimal buyerCommission = commission.getBuyerShare();

        BigDecimal sellerGross = salePrice.subtract(sellerCommission);
        if (sellerGross.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException(
                    "Số tiền giải ngân sau hoa hồng phải lớn hơn 0 (salePrice=" + salePrice
                            + ", sellerCommission=" + sellerCommission + ").");
        }
        TaxWithholding tax = taxWithholdingService.withhold(sellerGross, sellerId);
        BigDecimal taxWithheld = tax.getTaxAmount();
        BigDecimal sellerNetPayout = tax.getNetAmount();

        String payoutRef = "RELEASE-ESCROW-" + tx.getId();
        PaymentResult payoutResult = paymentGateway.payout(sellerId, sellerNetPayout, payoutRef);
        if (payoutResult == null || !payoutResult.isSuccess()) {
            String msg = (payoutResult != null && payoutResult.getMessage() != null)
                    ? payoutResult.getMessage()
                    : "Cổng thanh toán không phản hồi hoặc trả về lỗi";
            throw new BusinessRuleViolationException(
                    "Payout cho người bán thất bại (escrowId=" + tx.getId() + "): " + msg);
        }

        tx.setSellerCommission(sellerCommission);
        tx.setBuyerCommission(buyerCommission);
        tx.setTotalCommission(commission.getTotalCommission());
        tx.setTaxWithheld(taxWithheld);
        tx.setSellerNetPayout(sellerNetPayout);
        tx.setStatus(EscrowStatus.RELEASED);
        tx.setReleasedAt(Instant.now());
        tx = escrowTransactionRepository.save(tx);
        log.info("[ReleaseFunds] Escrow id={} RELEASED, sellerNetPayout={}, taxWithheld={}",
                tx.getId(), sellerNetPayout, taxWithheld);

        BigDecimal depositRefunded = BigDecimal.ZERO;
        Deposit deposit = null;
        try {
            deposit = refundSellerDeposit(ticketId);
            depositRefunded = deposit.getAmount();
        } catch (ResourceNotFoundException e) {
            log.warn("[ReleaseFunds] Không tìm thấy deposit cho vé #{}, bỏ qua hoàn cọc.", ticketId);
        }

        TicketStatus targetStatus = mapReasonToTicketStatus(reason);
        stateMachineService.transition(ticketId, targetStatus);
        log.info("[ReleaseFunds] Vé #{} chuyển sang {}", ticketId, targetStatus);

        TicketTransactionCompletedEvent event = new TicketTransactionCompletedEvent(
                ticketId,
                tx.getId(),
                deposit != null ? deposit.getId() : null,
                sellerId,
                buyerId,
                salePrice,
                buyerCommission,
                sellerCommission,
                taxWithheld,
                sellerNetPayout,
                depositRefunded,
                reason,
                Instant.now());
        eventPublisher.publishEvent(event);
        log.info("[ReleaseFunds] Publish TicketTransactionCompletedEvent cho vé #{}, reason={}", ticketId, reason);
    }

    @Transactional
    public Deposit refundSellerDeposit(Long ticketId) {
        Deposit deposit = depositRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khoản cọc cho vé #" + ticketId));

        if (deposit.getStatus() == DepositStatus.REFUNDED) {
            log.info("[ReleaseFunds] Deposit id={} đã ở REFUNDED, bỏ qua hoàn cọc trùng.", deposit.getId());
            return deposit;
        }

        if (deposit.getStatus() == DepositStatus.FORFEITED) {
            throw new BusinessRuleViolationException(
                    "Khoản cọc #" + deposit.getId() + " đã bị tịch thu, không thể hoàn lại.");
        }

        if (deposit.getStatus() != DepositStatus.LOCKED) {
            throw new BusinessRuleViolationException(
                    "Khoản cọc #" + deposit.getId() + " đang ở trạng thái " + deposit.getStatus()
                            + ", không thể hoàn. Chỉ cho phép hoàn khi status = LOCKED.");
        }

        String refundRef = "REFUND-DEPOSIT-" + deposit.getId();
        PaymentResult payoutResult = paymentGateway.payout(
                deposit.getSellerId(), deposit.getAmount(), refundRef);
        if (payoutResult == null || !payoutResult.isSuccess()) {
            String msg = (payoutResult != null && payoutResult.getMessage() != null)
                    ? payoutResult.getMessage()
                    : "Cổng thanh toán không phản hồi hoặc trả về lỗi";
            throw new BusinessRuleViolationException(
                    "Hoàn cọc thất bại");
        }

        deposit.setStatus(DepositStatus.REFUNDED);
        deposit.setRefundedAt(Instant.now());
        deposit = depositRepository.save(deposit);
        log.info("[ReleaseFunds] Deposit id={} REFUNDED, amount={}", deposit.getId(), deposit.getAmount());

        return deposit;
    }

    private TicketStatus mapReasonToTicketStatus(CompletionReason reason) {
        return switch (reason) {
            case AUTO_COMPLETE, BUYER_CONFIRMED -> TicketStatus.AUTO_COMPLETE;
            case DISPUTE_RESOLVED_SELLER_WINS -> TicketStatus.RESOLVED_SELLER_WINS;
        };
    }
}
