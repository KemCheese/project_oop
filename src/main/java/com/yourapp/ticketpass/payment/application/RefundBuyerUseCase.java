package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;
import java.time.Instant;

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
import com.yourapp.ticketpass.shared.dto.ForfeitBreakdown;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundBuyerUseCase {

    private final EscrowTransactionRepository escrowTransactionRepository;

    private final DepositRepository depositRepository;

    private final ForfeitDepositPolicy forfeitDepositPolicy;

    private final PaymentGateway paymentGateway;

    @Transactional
    public EscrowTransaction refundFull(Long ticketId, String reason) {
        EscrowTransaction tx = escrowTransactionRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy giao dịch ký quỹ cho vé #" + ticketId));

        if (tx.getStatus() == EscrowStatus.REFUNDED) {
            log.info("[RefundBuyer] Escrow id={} đã ở REFUNDED, bỏ qua gọi trùng.", tx.getId());
            return tx;
        }

        if (tx.getStatus() != EscrowStatus.HELD) {
            throw new BusinessRuleViolationException(
                    "Giao dịch ký quỹ #" + tx.getId() + " đang ở trạng thái " + tx.getStatus()
                    + ", không thể hoàn tiền. Chỉ cho phép hoàn tiền khi status = HELD.");
        }

        BigDecimal amountToRefund = tx.getAmountHeld();
        String refundRef = (tx.getGatewayTransactionId() != null && !tx.getGatewayTransactionId().isBlank())
                ? tx.getGatewayTransactionId()
                : String.valueOf(tx.getId());
        PaymentResult refundResult = paymentGateway.refund(
                refundRef, amountToRefund, reason);
        if (refundResult == null || !refundResult.isSuccess()) {
            String msg = (refundResult != null && refundResult.getMessage() != null)
                    ? refundResult.getMessage() : "Cổng thanh toán không phản hồi hoặc trả về lỗi";
            throw new BusinessRuleViolationException(
                    "Hoàn tiền thất bại (escrowId=" + tx.getId() + "): " + msg);
        }

        tx.setAmountRefunded(amountToRefund);
        tx.setAmountHeld(BigDecimal.ZERO);
        tx.setStatus(EscrowStatus.REFUNDED);
        tx.setRefundedAt(Instant.now());
        tx = escrowTransactionRepository.save(tx);
        log.info("[RefundBuyer] Escrow id={} REFUNDED, amountRefunded={}", tx.getId(), tx.getAmountRefunded());
        return tx;
    }

    @Transactional
    public ForfeitBreakdown refundWithForfeitedDeposit(Long ticketId, String reason) {
        Deposit deposit = depositRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khoản cọc cho vé #" + ticketId));

        if (deposit.getStatus() == DepositStatus.FORFEITED) {
            log.info("[RefundBuyer] Deposit id={} đã ở FORFEITED, trả về breakdown từ snapshot.", deposit.getId());
            return new ForfeitBreakdown(deposit.getAmount(), deposit.getFounderCut(), deposit.getBuyerCompensation());
        }

        if (deposit.getStatus() != DepositStatus.LOCKED) {
            throw new BusinessRuleViolationException(
                    "Khoản cọc #" + deposit.getId() + " đang ở trạng thái " + deposit.getStatus()
                    + ", không thể tịch thu. Chỉ cho phép tịch thu khi status = LOCKED.");
        }

        EscrowTransaction tx = refundFull(ticketId, reason);

        ForfeitBreakdown breakdown = forfeitDepositPolicy.computeForfeit(deposit.getAmount());

        BigDecimal buyerCompensation = breakdown.getBuyerCompensation();
        if (buyerCompensation.compareTo(BigDecimal.ZERO) > 0) {
            Long buyerId = tx.getBuyerId();

            String payoutRef = "FORFEIT-COMPENSATION-" + deposit.getId();
            PaymentResult payoutResult = paymentGateway.payout(buyerId, buyerCompensation, payoutRef);
            if (payoutResult == null || !payoutResult.isSuccess()) {
                String msg = (payoutResult != null && payoutResult.getMessage() != null)
                        ? payoutResult.getMessage() : "Cổng thanh toán không phản hồi hoặc trả về lỗi";
                throw new BusinessRuleViolationException(
                        "Chi bồi thường cho người mua thất bại (depositId=" + deposit.getId()
                        + "): " + msg);
            }
            log.info("[RefundBuyer] Chi bồi thường {} cho buyerId={}", buyerCompensation, buyerId);
        } else {
            log.info("[RefundBuyer] buyerCompensation = 0 (cọc quá nhỏ), bỏ qua payout.");
        }

        deposit.setStatus(DepositStatus.FORFEITED);
        deposit.setFounderCut(breakdown.getFounderCut());
        deposit.setBuyerCompensation(buyerCompensation);
        deposit.setForfeitReason(reason);
        deposit.setForfeitedAt(Instant.now());
        depositRepository.save(deposit);
        log.info("[RefundBuyer] Deposit id={} FORFEITED, founderCut={}, buyerCompensation={}",
                deposit.getId(), breakdown.getFounderCut(), buyerCompensation);

        return breakdown;
    }
}
