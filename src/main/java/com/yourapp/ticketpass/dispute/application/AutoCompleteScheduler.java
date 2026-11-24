package com.yourapp.ticketpass.dispute.application;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.payment.application.ReleaseFundsUseCase;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCompleteScheduler {

    private final TicketRepository ticketRepository;

    private final DisputeRepository disputeRepository;

    private final ReleaseFundsUseCase releaseFundsUseCase;

    @Scheduled(cron = "0 */10 * * * *")
    void autoCompleteExpiredTickets() {
        Instant now = Instant.now();
        List<Ticket> dueTickets = ticketRepository.findDueForAutoComplete(TicketStatus.AWAITING_USE, now);
        if (dueTickets == null || dueTickets.isEmpty()) {
            return;
        }

        List<DisputeStatus> blockingStatuses = List.of(
                DisputeStatus.OPEN,
                DisputeStatus.AWAITING_SELLER_RESPONSE,
                DisputeStatus.SELLER_RESPONDED,
                DisputeStatus.UNDER_REVIEW,
                DisputeStatus.EXPIRED
        );

        for (Ticket ticket : dueTickets) {
            Long ticketId = ticket.getId();
            try {
                boolean hasActiveDispute = disputeRepository.existsByTicketIdAndStatusIn(ticketId, blockingStatuses);
                if (hasActiveDispute) {
                    log.warn("[AutoCompleteScheduler] Vé #{} đang có khiếu nại chưa xử lý xong, bỏ qua tự động hoàn tất.", ticketId);
                    continue;
                }

                releaseFundsUseCase.release(ticketId, CompletionReason.AUTO_COMPLETE);
                log.info("[AutoCompleteScheduler] Tự động hoàn tất và giải ngân thành công cho vé #{}", ticketId);
            } catch (Exception e) {
                log.error("[AutoCompleteScheduler] Lỗi khi tự động hoàn tất cho vé #{}: {}", ticketId, e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    void expireOverdueSellerResponses() {
        Instant now = Instant.now();
        List<Dispute> overdueDisputes = disputeRepository.findOverdueSellerResponses(now);
        if (overdueDisputes == null || overdueDisputes.isEmpty()) {
            return;
        }

        for (Dispute dispute : overdueDisputes) {
            try {
                dispute.setStatus(DisputeStatus.EXPIRED);
                disputeRepository.save(dispute);
                log.info("[AutoCompleteScheduler] Khiếu nại #{} quá hạn phản hồi của người bán, đã chuyển sang EXPIRED", dispute.getId());
            } catch (Exception e) {
                log.error("[AutoCompleteScheduler] Lỗi khi chuyển trạng thái EXPIRED cho khiếu nại #{}: {}", dispute.getId(), e.getMessage(), e);
            }
        }
    }
}
