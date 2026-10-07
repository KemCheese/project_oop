package com.yourapp.ticketpass.dispute.web;

import java.security.Principal;
import java.time.Instant;
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

import com.yourapp.ticketpass.dispute.application.FileDisputeUseCase;
import com.yourapp.ticketpass.dispute.application.RespondToDisputeUseCase;
import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeReason;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.dispute.domain.DisputeWindowPolicy;
import com.yourapp.ticketpass.dispute.web.dto.DisputeResponse;
import com.yourapp.ticketpass.dispute.web.dto.FileDisputeRequest;
import com.yourapp.ticketpass.dispute.web.dto.RespondToDisputeRequest;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử đơn vị cho {@link DisputeController}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeController Unit Tests")
class DisputeControllerTest {

    @Mock
    private FileDisputeUseCase fileDisputeUseCase;

    @Mock
    private RespondToDisputeUseCase respondToDisputeUseCase;

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private DisputeEvidenceRepository disputeEvidenceRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DisputeController controller;

    @Test
    @DisplayName("redirectToDisputeByTicket - Khi có khiếu nại, chuyển hướng tới /disputes/{id}")
    void redirectToDisputeByTicket_whenDisputeExists_redirectsToDetail() {
        Long ticketId = 10L;
        Dispute dispute = new Dispute();
        dispute.setId(55L);
        when(disputeRepository.findByTicketId(ticketId)).thenReturn(Optional.of(dispute));

        String view = controller.redirectToDisputeByTicket(ticketId);

        assertThat(view).isEqualTo("redirect:/disputes/55");
    }

    @Test
    @DisplayName("redirectToDisputeByTicket - Khi chưa có khiếu nại, chuyển hướng tới /disputes/new?ticketId={id}")
    void redirectToDisputeByTicket_whenDisputeDoesNotExist_redirectsToNew() {
        Long ticketId = 10L;
        when(disputeRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());

        String view = controller.redirectToDisputeByTicket(ticketId);

        assertThat(view).isEqualTo("redirect:/disputes/new?ticketId=10");
    }

    @Test
    @DisplayName("showCreateForm - Người mua mở form khiếu nại thành công")
    void showCreateForm_whenUserIsBuyer_returnsCreateView() {
        Long ticketId = 100L;
        Ticket ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setBuyerId(2L);
        ticket.setTicketType(TicketType.CONCERT);

        User buyer = new User();
        buyer.setId(2L);
        buyer.setEmail("buyer@example.com");

        Principal principal = () -> "buyer@example.com";

        DisputeWindowPolicy mockPolicy = mock(DisputeWindowPolicy.class);
        when(mockPolicy.computeWindowClose(any(), any())).thenReturn(Instant.now().plusSeconds(3600));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(buyer));
        when(fileDisputeUseCase.resolvePolicy(TicketType.CONCERT)).thenReturn(mockPolicy);

        Model model = new ConcurrentModel();
        String view = controller.showCreateForm(ticketId, model, principal);

        assertThat(view).isEqualTo("dispute/create");
        assertThat(model.containsAttribute("ticket")).isTrue();
        assertThat(model.containsAttribute("form")).isTrue();
    }

    @Test
    @DisplayName("fileDispute - Nộp khiếu nại thành công, chuyển hướng tới /disputes/{id}")
    void fileDispute_success_redirectsToDisputeDetail() {
        User buyer = new User();
        buyer.setId(2L);
        buyer.setEmail("buyer@example.com");

        Principal principal = () -> "buyer@example.com";
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(buyer));

        Dispute createdDispute = new Dispute();
        createdDispute.setId(88L);
        when(fileDisputeUseCase.fileDispute(eq(100L), eq(2L), eq(DisputeReason.TICKET_INVALID), eq("Lỗi vé"), any()))
                .thenReturn(createdDispute);

        FileDisputeRequest request = new FileDisputeRequest();
        request.setTicketId(100L);
        request.setReason(DisputeReason.TICKET_INVALID);
        request.setDescription("Lỗi vé");

        String view = controller.fileDispute(request, null, principal);

        assertThat(view).isEqualTo("redirect:/disputes/88");
    }

    @Test
    @DisplayName("showDetail - Người mua truy cập chi tiết khiếu nại của mình thành công")
    void showDetail_whenViewerIsBuyer_returnsDetailView() {
        Long disputeId = 55L;
        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setBuyerId(2L);
        dispute.setSellerId(3L);
        dispute.setStatus(DisputeStatus.AWAITING_SELLER_RESPONSE);

        User buyer = new User();
        buyer.setId(2L);
        buyer.setEmail("buyer@example.com");
        buyer.setRole(UserRole.USER);

        Principal principal = () -> "buyer@example.com";

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(buyer));
        when(disputeEvidenceRepository.findByDisputeId(disputeId)).thenReturn(Collections.emptyList());

        Model model = new ConcurrentModel();
        String view = controller.showDetail(disputeId, model, principal);

        assertThat(view).isEqualTo("dispute/detail");
        assertThat(model.getAttribute("isBuyer")).isEqualTo(true);
        assertThat(model.getAttribute("isSeller")).isEqualTo(false);
        assertThat(model.getAttribute("dispute")).isInstanceOf(DisputeResponse.class);
    }

    @Test
    @DisplayName("showDetail - Người không liên quan truy cập bị từ chối")
    void showDetail_whenViewerIsStranger_throwsException() {
        Long disputeId = 55L;
        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setBuyerId(2L);
        dispute.setSellerId(3L);

        User stranger = new User();
        stranger.setId(99L);
        stranger.setEmail("stranger@example.com");
        stranger.setRole(UserRole.USER);

        Principal principal = () -> "stranger@example.com";

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(userRepository.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));

        Model model = new ConcurrentModel();
        assertThatThrownBy(() -> controller.showDetail(disputeId, model, principal))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("không có quyền truy cập");
    }

    @Test
    @DisplayName("respond - Người bán gửi giải trình thành công và redirect về /disputes/{id}")
    void respond_whenSellerSubmitsResponse_redirectsToDetail() {
        Long disputeId = 55L;
        User seller = new User();
        seller.setId(3L);
        seller.setEmail("seller@example.com");

        Principal principal = () -> "seller@example.com";
        when(userRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));

        RespondToDisputeRequest req = new RespondToDisputeRequest();
        req.setResponse("Vé tôi đã chuyển là hoàn toàn chính xác.");

        String view = controller.respond(disputeId, req, principal);

        verify(respondToDisputeUseCase).submitSellerResponse(disputeId, 3L, "Vé tôi đã chuyển là hoàn toàn chính xác.");
        assertThat(view).isEqualTo("redirect:/disputes/55");
    }

    @Test
    @DisplayName("respond - Khi có ngoại lệ nghiệp vụ, chuyển hướng về /disputes/{id} kèm flash errorMessage")
    void respond_whenBusinessRuleFails_redirectsWithErrorMessage() {
        Long disputeId = 55L;
        User seller = new User();
        seller.setId(3L);
        seller.setEmail("seller@example.com");

        Principal principal = () -> "seller@example.com";
        when(userRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));

        RespondToDisputeRequest req = new RespondToDisputeRequest();
        req.setResponse("Giải trình");

        org.mockito.Mockito.doThrow(new BusinessRuleViolationException("Bạn đã gửi giải trình trước đó"))
                .when(respondToDisputeUseCase).submitSellerResponse(disputeId, 3L, "Giải trình");

        org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap redirectAttributes =
                new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();

        String view = controller.respond(disputeId, req,
                new org.springframework.validation.BeanPropertyBindingResult(req, "request"),
                redirectAttributes, principal);

        assertThat(view).isEqualTo("redirect:/disputes/55");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("errorMessage");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Bạn đã gửi giải trình trước đó");
    }

    @Test
    @DisplayName("fileDispute - Khi có ngoại lệ nghiệp vụ, chuyển hướng về /disputes/new kèm flash errorMessage")
    void fileDispute_whenBusinessRuleFails_redirectsWithErrorMessage() {
        User buyer = new User();
        buyer.setId(2L);
        buyer.setEmail("buyer@example.com");

        Principal principal = () -> "buyer@example.com";
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(buyer));

        FileDisputeRequest req = new FileDisputeRequest();
        req.setTicketId(100L);
        req.setReason(DisputeReason.TICKET_INVALID);
        req.setDescription("Mô tả khiếu nại");

        when(fileDisputeUseCase.fileDispute(eq(100L), eq(2L), eq(DisputeReason.TICKET_INVALID), eq("Mô tả khiếu nại"), any()))
                .thenThrow(new BusinessRuleViolationException("Hết hạn khiếu nại"));

        org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap redirectAttributes =
                new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();

        String view = controller.fileDispute(req,
                new org.springframework.validation.BeanPropertyBindingResult(req, "request"),
                null, redirectAttributes, principal);

        assertThat(view).isEqualTo("redirect:/disputes/new?ticketId=100");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("errorMessage");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Hết hạn khiếu nại");
    }

    @Test
    @DisplayName("withdraw - Người mua rút khiếu nại thành công và redirect về /disputes/{id}")
    void withdraw_whenBuyerWithdraws_redirectsToDetail() {
        Long disputeId = 55L;
        User buyer = new User();
        buyer.setId(2L);
        buyer.setEmail("buyer@example.com");

        Principal principal = () -> "buyer@example.com";
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(buyer));

        String view = controller.withdraw(disputeId, "Người mua đã tự giải quyết", principal);

        verify(respondToDisputeUseCase).withdrawDispute(disputeId, 2L, "Người mua đã tự giải quyết");
        assertThat(view).isEqualTo("redirect:/disputes/55");
    }
}
