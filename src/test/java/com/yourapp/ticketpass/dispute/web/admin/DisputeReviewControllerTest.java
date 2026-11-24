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

    @Mock
    private com.yourapp.ticketpass.ticket.domain.TicketRepository ticketRepository;

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

        Object escrowAttr = model.getAttribute("escrow");
        assertThat(escrowAttr).isNotNull();
        assertThat(escrowAttr).isInstanceOf(DisputeEscrowDto.class);
        DisputeEscrowDto escrowDto = (DisputeEscrowDto) escrowAttr;
        assertThat(escrowDto.getId()).isEqualTo(escrowId);
        assertThat(escrowDto.getAmount()).isEqualByComparingTo("500000.00");
        assertThat(escrowDto.getStatus()).isEqualTo(EscrowStatus.HELD);

        Object depositAttr = model.getAttribute("deposit");
        assertThat(depositAttr).isNotNull();
        assertThat(depositAttr).isInstanceOf(DisputeDepositDto.class);
        DisputeDepositDto depositDto = (DisputeDepositDto) depositAttr;
        assertThat(depositDto.getId()).isEqualTo(depositId);
        assertThat(depositDto.getDepositAmount()).isEqualByComparingTo("250000.00");
        assertThat(depositDto.getStatus()).isEqualTo(DepositStatus.LOCKED);

        assertThat(model.getAttribute("ticket")).isNull();
    }

    @Test
    @DisplayName("resolveBuyerWins - Admin phán quyết Buyer thắng thành công, trả về redirect kèm flash attribute")
    void resolveBuyerWins_success() {
        Long disputeId = 1L;
        com.yourapp.ticketpass.dispute.web.dto.ReviewDisputeRequest request = new com.yourapp.ticketpass.dispute.web.dto.ReviewDisputeRequest();
        request.setResolutionNote("Chấp thuận bằng chứng của người mua");

        java.security.Principal principal = () -> "admin@ticketpass.vn";
        com.yourapp.ticketpass.user.domain.User adminUser = new com.yourapp.ticketpass.user.domain.User();
        adminUser.setId(99L);
        when(userQueryService.findByEmail("admin@ticketpass.vn")).thenReturn(Optional.of(adminUser));

        org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes =
                new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();

        String view = controller.resolveBuyerWins(disputeId, request, principal, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/admin/disputes/1");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("successMessage");
        org.mockito.Mockito.verify(reviewDisputeUseCase).resolveBuyerWins(1L, 99L, "Chấp thuận bằng chứng của người mua");
    }

    @Test
    @DisplayName("resolveSellerWins - Admin phán quyết Seller thắng thành công, trả về redirect kèm flash attribute")
    void resolveSellerWins_success() {
        Long disputeId = 1L;
        com.yourapp.ticketpass.dispute.web.dto.ReviewDisputeRequest request = new com.yourapp.ticketpass.dispute.web.dto.ReviewDisputeRequest();
        request.setResolutionNote("Bằng chứng người bán hợp lệ");

        java.security.Principal principal = () -> "admin@ticketpass.vn";
        com.yourapp.ticketpass.user.domain.User adminUser = new com.yourapp.ticketpass.user.domain.User();
        adminUser.setId(99L);
        when(userQueryService.findByEmail("admin@ticketpass.vn")).thenReturn(Optional.of(adminUser));

        org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes =
                new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();

        String view = controller.resolveSellerWins(disputeId, request, principal, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/admin/disputes/1");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("successMessage");
        org.mockito.Mockito.verify(reviewDisputeUseCase).resolveSellerWins(1L, 99L, "Bằng chứng người bán hợp lệ");
    }
}
