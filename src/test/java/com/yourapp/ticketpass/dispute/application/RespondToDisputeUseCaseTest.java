package com.yourapp.ticketpass.dispute.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;

@ExtendWith(MockitoExtension.class)
@DisplayName("RespondToDisputeUseCase Unit Tests")
class RespondToDisputeUseCaseTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private DisputeEvidenceRepository disputeEvidenceRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private TicketStateMachineService ticketStateMachineService;

    @InjectMocks
    private RespondToDisputeUseCase useCase;

    @Test
    @DisplayName("submitSellerResponse - Trạng thái UNDER_REVIEW nhưng chưa có giải trình -> cho phép gửi và chuyển sang SELLER_RESPONDED")
    void submitSellerResponse_whenUnderReviewWithoutPriorResponse_success() {
        Long disputeId = 1L;
        Long sellerId = 10L;

        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setSellerId(sellerId);
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        dispute.setSellerResponseDeadline(Instant.now().plus(1, ChronoUnit.DAYS));
        dispute.setSellerResponse(null);

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        Dispute result = useCase.submitSellerResponse(disputeId, sellerId, "Vé của tôi hoàn toàn chuẩn.");

        assertThat(result.getStatus()).isEqualTo(DisputeStatus.SELLER_RESPONDED);
        assertThat(result.getSellerResponse()).isEqualTo("Vé của tôi hoàn toàn chuẩn.");
        assertThat(result.getSellerRespondedAt()).isNotNull();
        verify(disputeRepository).save(dispute);
    }

    @Test
    @DisplayName("submitSellerResponse - Khi người bán đã gửi giải trình trước đó -> ném ngoại lệ chặn gửi lặp lại")
    void submitSellerResponse_whenAlreadyResponded_throwsException() {
        Long disputeId = 1L;
        Long sellerId = 10L;

        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setSellerId(sellerId);
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        dispute.setSellerResponse("Tôi đã giải trình từ trước.");

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> useCase.submitSellerResponse(disputeId, sellerId, "Gửi thêm lần nữa"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Bạn đã gửi giải trình cho khiếu nại này trước đó");
    }

    @Test
    @DisplayName("submitSellerResponse - Người gửi không phải seller của khiếu nại -> ném ngoại lệ")
    void submitSellerResponse_whenNotSeller_throwsException() {
        Long disputeId = 1L;
        Long sellerId = 10L;

        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setSellerId(999L);
        dispute.setStatus(DisputeStatus.OPEN);

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> useCase.submitSellerResponse(disputeId, sellerId, "Nội dung"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("không phải là người bán");
    }
}
