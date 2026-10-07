package com.yourapp.ticketpass.dispute.application;

import com.yourapp.ticketpass.dispute.domain.*;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.shared.storage.StoredFileRef;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileDisputeUseCaseTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private DisputeEvidenceRepository disputeEvidenceRepository;

    @Mock
    private DisputeWindowPolicy moviePolicy;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketStateMachineService ticketStateMachineService;

    private FileDisputeUseCase fileDisputeUseCase;

    @BeforeEach
    void setUp() {
        when(moviePolicy.supportedType()).thenReturn(TicketType.MOVIE);
        fileDisputeUseCase = new FileDisputeUseCase(
                disputeRepository,
                disputeEvidenceRepository,
                List.of(moviePolicy),
                fileStorageService,
                ticketRepository,
                ticketStateMachineService
        );
        fileDisputeUseCase.initPolicyMap();
    }

    @Test
    @DisplayName("Gate Dispute: Nộp khiếu nại sau giờ sự kiện mà KHÔNG có bằng chứng -> Từ chối (AC-03.2)")
    void testGateDisputeWithoutEvidenceRejected() {
        Instant now = Instant.now();
        Instant eventStartAt = now.minus(Duration.ofMinutes(10)); // sự kiện đã bắt đầu 10 phút trước

        Ticket ticket = new Ticket();
        ticket.setId(501L);
        ticket.setBuyerId(101L);
        ticket.setSellerId(202L);
        ticket.setTicketType(TicketType.MOVIE);
        ticket.setStatus(TicketStatus.AWAITING_USE);
        ticket.setEventStartAt(eventStartAt);

        when(ticketRepository.findById(501L)).thenReturn(Optional.of(ticket));
        when(disputeRepository.existsByTicketIdAndStatusIn(eq(501L), anyList())).thenReturn(false);
        when(moviePolicy.isWithinWindow(any(), eq(eventStartAt), any())).thenReturn(true);

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                fileDisputeUseCase.fileDispute(501L, 101L, DisputeReason.TICKET_INVALID, "Cổng soát vé từ chối"));

        assertTrue(ex.getMessage().contains("bắt buộc phải nộp Video") || ex.getMessage().contains("Biên bản"));
    }

    @Test
    @DisplayName("Gate Dispute: Nộp khiếu nại sau giờ sự kiện CHỈ có ảnh chụp màn hình -> Từ chối (AC-03.2)")
    void testGateDisputeOnlyImageRejected() {
        Instant now = Instant.now();
        Instant eventStartAt = now.minus(Duration.ofMinutes(10));

        Ticket ticket = new Ticket();
        ticket.setId(502L);
        ticket.setBuyerId(101L);
        ticket.setSellerId(202L);
        ticket.setTicketType(TicketType.MOVIE);
        ticket.setStatus(TicketStatus.AWAITING_USE);
        ticket.setEventStartAt(eventStartAt);

        when(ticketRepository.findById(502L)).thenReturn(Optional.of(ticket));
        when(disputeRepository.existsByTicketIdAndStatusIn(eq(502L), anyList())).thenReturn(false);
        when(moviePolicy.isWithinWindow(any(), eq(eventStartAt), any())).thenReturn(true);

        var imageAttachment = new FileDisputeUseCase.DisputeEvidenceAttachment(
                DisputeEvidenceType.IMAGE,
                "screenshot.png",
                "image/png",
                1024L,
                new ByteArrayInputStream(new byte[]{1, 2, 3})
        );

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                fileDisputeUseCase.fileDispute(502L, 101L, DisputeReason.TICKET_INVALID, "Quét lỗi tại cổng",
                        List.of(imageAttachment)));

        assertTrue(ex.getMessage().contains("từ chối bằng chứng chỉ là ảnh chụp màn hình đơn thuần"));
    }

    @Test
    @DisplayName("Gate Dispute: Nộp khiếu nại sau giờ sự kiện có VIDEO hợp lệ -> Chấp nhận & cấp Seller 24h (AC-03.2, AC-03.3)")
    void testGateDisputeWithVideoSuccess() {
        Instant now = Instant.now();
        Instant eventStartAt = now.minus(Duration.ofMinutes(10)); // sau giờ diễn 10 phút, còn trong buffer 30m

        Ticket ticket = new Ticket();
        ticket.setId(503L);
        ticket.setBuyerId(101L);
        ticket.setSellerId(202L);
        ticket.setTicketType(TicketType.MOVIE);
        ticket.setStatus(TicketStatus.AWAITING_USE);
        ticket.setEventStartAt(eventStartAt);

        when(ticketRepository.findById(503L)).thenReturn(Optional.of(ticket));
        when(disputeRepository.existsByTicketIdAndStatusIn(eq(503L), anyList())).thenReturn(false);
        when(moviePolicy.isWithinWindow(any(), eq(eventStartAt), any())).thenReturn(true);
        when(moviePolicy.computeWindowClose(any(), eq(eventStartAt))).thenReturn(eventStartAt.plus(Duration.ofMinutes(30)));

        Dispute savedDispute = new Dispute();
        savedDispute.setId(888L);
        savedDispute.setBuyerId(101L);
        savedDispute.setSellerId(202L);
        savedDispute.setStatus(DisputeStatus.OPEN);

        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> {
            Dispute d = invocation.getArgument(0);
            d.setId(888L);
            return d;
        });
        when(disputeRepository.findById(888L)).thenReturn(Optional.of(savedDispute));

        when(fileStorageService.store(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn(new StoredFileRef("disputes/video_123.mp4", "video_123.mp4", "video/mp4", 5000000L, "http://localhost/video_123.mp4"));

        when(disputeEvidenceRepository.save(any(DisputeEvidence.class))).thenAnswer(inv -> inv.getArgument(0));

        var videoAttachment = new FileDisputeUseCase.DisputeEvidenceAttachment(
                DisputeEvidenceType.VIDEO,
                "gate_scan_failure.mp4",
                "video/mp4",
                5000000L,
                new ByteArrayInputStream(new byte[]{1, 2, 3})
        );

        Dispute result = fileDisputeUseCase.fileDispute(503L, 101L, DisputeReason.TICKET_INVALID, "Máy quét báo vé đã dùng",
                List.of(videoAttachment));

        assertNotNull(result);
        assertEquals(888L, result.getId());
        assertEquals(DisputeStatus.OPEN, result.getStatus());
        // AC-03.3: Seller được cấp 24 giờ phản hồi
        assertTrue(result.getSellerResponseDeadline().isAfter(now.plus(Duration.ofHours(23))));
        // Khóa tiền vé
        verify(ticketStateMachineService).transition(503L, TicketStatus.DISPUTE_UNDER_REVIEW);
        // Lưu video
        verify(fileStorageService).store(eq("disputes"), eq("gate_scan_failure.mp4"), any(), anyLong(), eq("video/mp4"));
        verify(disputeEvidenceRepository).save(any(DisputeEvidence.class));
    }
}
