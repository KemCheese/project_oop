package com.yourapp.ticketpass.dispute.web.admin;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import com.yourapp.ticketpass.dispute.application.ReviewDisputeUseCase;
import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeReason;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.dispute.web.dto.DisputeDepositDto;
import com.yourapp.ticketpass.dispute.web.dto.DisputeEscrowDto;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.user.application.UserQueryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử đơn vị cho DisputeReviewController - xác minh đóng gói DTO an toàn cho View.
 * Đảm bảo khắc phục triệt để lỗi rò rỉ JPA Entity (BUG-MED-01 / Leaky Abstraction).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeReviewController Unit Tests")
class DisputeReviewControllerTest {

    @Mock
    private ReviewDisputeUseCase reviewDisputeUseCase;

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private DisputeEvidenceRepository disputeEvidenceRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private EscrowTransactionRepository escrowTransactionRepository;

    @Mock
    private DepositRepository depositRepository;

    @Mock
    private UserQueryService userQueryService;

    @InjectMocks
    private DisputeReviewController controller;

    @Test
    @DisplayName("showDetail - nạp DTO sạch sẽ (DisputeEscrowDto, DisputeDepositDto), không rò rỉ Entity thô")
    void showDetail_populatesCleanDtos_withoutEntityLeakage() {
        Long disputeId = 1L;
        Long ticketId = 100L;
        Long escrowId = 10L;
        Long depositId = 20L;

        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setTicketId(ticketId);
        dispute.setBuyerId(2L);
        dispute.setSellerId(3L);
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        dispute.setReason(DisputeReason.TICKET_INVALID);
        dispute.setEscrowTransactionId(escrowId);
        dispute.setDepositId(depositId);

        EscrowTransaction escrow = new EscrowTransaction();
        escrow.setId(escrowId);
        escrow.setTicketId(ticketId);
        escrow.setSalePrice(new BigDecimal("500000.00"));
        escrow.setAmountHeld(new BigDecimal("500000.00"));
        escrow.setStatus(EscrowStatus.HELD);
        escrow.setGatewayName("MOMO_TEST");

        Deposit deposit = new Deposit();
        deposit.setId(depositId);
        deposit.setTicketId(ticketId);
        deposit.setSellerId(3L);
        deposit.setAmount(new BigDecimal("250000.00"));
        deposit.setDepositPercent(new BigDecimal("50.00"));
        deposit.setStatus(DepositStatus.LOCKED);

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeEvidenceRepository.findByDisputeId(disputeId)).thenReturn(Collections.emptyList());
        when(escrowTransactionRepository.findById(escrowId)).thenReturn(Optional.of(escrow));
        when(depositRepository.findById(depositId)).thenReturn(Optional.of(deposit));

        Model model = new ConcurrentModel();
        String viewName = controller.showDetail(disputeId, model);

        assertThat(viewName).isEqualTo("admin/dispute/detail");

        // Khẳng định 1: Escrow được map sang DisputeEscrowDto
        Object escrowAttr = model.getAttribute("escrow");
        assertThat(escrowAttr).isNotNull();
        assertThat(escrowAttr).isInstanceOf(DisputeEscrowDto.class);
        DisputeEscrowDto escrowDto = (DisputeEscrowDto) escrowAttr;
        assertThat(escrowDto.getId()).isEqualTo(escrowId);
        assertThat(escrowDto.getAmount()).isEqualByComparingTo("500000.00");
        assertThat(escrowDto.getStatus()).isEqualTo(EscrowStatus.HELD);

        // Khẳng định 2: Deposit được map sang DisputeDepositDto
        Object depositAttr = model.getAttribute("deposit");
        assertThat(depositAttr).isNotNull();
        assertThat(depositAttr).isInstanceOf(DisputeDepositDto.class);
        DisputeDepositDto depositDto = (DisputeDepositDto) depositAttr;
        assertThat(depositDto.getId()).isEqualTo(depositId);
        assertThat(depositDto.getDepositAmount()).isEqualByComparingTo("250000.00");
        assertThat(depositDto.getStatus()).isEqualTo(DepositStatus.LOCKED);

        // Khẳng định 3: Không có raw ticket entity nào bị rò rỉ vào model
        assertThat(model.getAttribute("ticket")).isNull();
    }
}
