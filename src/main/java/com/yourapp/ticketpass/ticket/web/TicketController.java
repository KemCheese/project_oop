package com.yourapp.ticketpass.ticket.web;

import com.yourapp.ticketpass.shared.dto.ConflictingTicketInfo;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.shared.storage.StoredFileRef;
import com.yourapp.ticketpass.ticket.application.TicketScheduleService;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.web.dto.CreateTicketRequest;
import com.yourapp.ticketpass.ticket.web.dto.TicketResponse;
import com.yourapp.ticketpass.ticket.web.dto.TicketSuggestionResponse;
import com.yourapp.ticketpass.ticket.web.dto.TicketSummaryResponse;
import com.yourapp.ticketpass.user.application.StarredTicketUseCase;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.web.dto.UserSummaryResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller MVC cho san marketplace ban ve — cac man hinh lien quan den ve.
 *
 * <p>Bang route:</p>
 * <pre>
 * GET  /tickets                -&gt; danh sach ve dang ban (view "ticket/list")
 * GET  /tickets/{id}           -&gt; chi tiet ve (view "ticket/detail")
 * GET  /tickets/new            -&gt; form dang ve (view "ticket/create")
 * POST /tickets                -&gt; tao listing (@Valid CreateTicketRequest, MultipartFile ticketImage, MultipartFile qrImage)
 * POST /tickets/{id}/transfer  -&gt; san mo khoa anh QR an cho buyer sau khi seller da khoa coc
 * POST /tickets/{id}/received  -&gt; buyer xac nhan da nhan ve (da xem duoc anh QR)
 * POST /tickets/{id}/cancel    -&gt; seller huy listing
 * </pre>
 *
 * <p>Tang {@code web} o day la lop mong: chi nhan input, uy quyen cho service, roi chon view.
 * Khong dat business logic trong controller va khong tra entity truc tiep ra view.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Controller
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TransferTicketService transferTicketService;

    private final TicketRepository ticketRepository;

    private final FileStorageService fileStorageService;

    private final UserRepository userRepository;

    private final StarredTicketUseCase starredTicketUseCase;

    private final TicketScheduleService ticketScheduleService;

    /**
     * Lấy danh sách 3 loại vé khả dụng trên sàn (MOVIE, CONCERT, BUS).
     */
    private TicketType[] getActiveTicketTypes() {
        return new TicketType[] { TicketType.MOVIE, TicketType.CONCERT, TicketType.BUS };
    }

    /**
     * Danh sach ve dang duoc rao ban, ho tro tim kiem theo ten su kien va loc theo loai ve.
     *
     * @param ticketType loc theo loai ve, tuy chon
     * @param keyword tu khoa tim kiem theo ten su kien hoac tieu de, tuy chon
     * @param model model de truyen du lieu sang view
     * @return ten view "ticket/list"
     */
    @GetMapping
    public String list(@RequestParam(required = false) String ticketType,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) Integer size,
                       @CookieValue(name = "tp_page_size", required = false) Integer cookiePageSize,
                       Principal principal,
                       Model model) {
        TicketType type = null;
        if (ticketType != null && !ticketType.isBlank()) {
            try {
                type = TicketType.valueOf(ticketType.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Loai ve khong hop le -> bo qua loc theo loai
                type = null;
                ticketType = null;
            }
        }

        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        // Xac dinh so ve moi trang (uu tien param size > cookie tu dong theo man hinh > default 16)
        int pageSize = 16;
        if (size != null && size >= 1 && size <= 50) {
            pageSize = size;
        } else if (cookiePageSize != null && cookiePageSize >= 1 && cookiePageSize <= 50) {
            pageSize = cookiePageSize;
        }

        int targetPage = Math.max(0, page);
        Pageable pageable = PageRequest.of(targetPage, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Ticket> ticketPage = ticketRepository.searchListed(cleanKeyword, type, pageable);

        // Neu page vuot qua tong so trang do thay doi pageSize/loc, chuyen ve trang cuoi kha dung
        if (targetPage >= ticketPage.getTotalPages() && ticketPage.getTotalPages() > 0) {
            targetPage = ticketPage.getTotalPages() - 1;
            pageable = PageRequest.of(targetPage, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
            ticketPage = ticketRepository.searchListed(cleanKeyword, type, pageable);
        }

        List<TicketSummaryResponse> summaries = ticketPage.getContent().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());

        int totalPages = ticketPage.getTotalPages();
        int currentPage = ticketPage.getNumber();
        long totalElements = ticketPage.getTotalElements();
        long startItem = totalElements == 0 ? 0 : (long) currentPage * pageSize + 1;
        long endItem = Math.min((long) (currentPage + 1) * pageSize, totalElements);
        List<Integer> pageNumbers = calculatePageNumbers(currentPage, totalPages);

        model.addAttribute("tickets", summaries);
        model.addAttribute("ticketTypes", getActiveTicketTypes());
        model.addAttribute("selectedType", ticketType);
        model.addAttribute("keyword", cleanKeyword != null ? cleanKeyword : "");
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("hasPrevious", ticketPage.hasPrevious());
        model.addAttribute("hasNext", ticketPage.hasNext());
        model.addAttribute("pageNumbers", pageNumbers);
        model.addAttribute("startItem", startItem);
        model.addAttribute("endItem", endItem);

        if (principal != null) {
            Long currentUserId = resolveUserId(principal);
            model.addAttribute("starredTicketIds", starredTicketUseCase.getStarredTicketIds(currentUserId));
        }

        return "ticket/list";
    }

    private List<Integer> calculatePageNumbers(int currentPage, int totalPages) {
        List<Integer> pages = new ArrayList<>();
        if (totalPages <= 1) {
            return pages;
        }
        int maxButtons = 5;
        int start = Math.max(0, currentPage - maxButtons / 2);
        int end = Math.min(totalPages - 1, start + maxButtons - 1);
        if (end - start + 1 < maxButtons) {
            start = Math.max(0, end - maxButtons + 1);
        }
        for (int i = start; i <= end; i++) {
            pages.add(i);
        }
        return pages;
    }

    /**
     * API tra ve danh sach toi da 5 ve goi y nhanh (live autocomplete) theo tu khoa va thoi gian dang gan nhat.
     *
     * @param keyword tu khoa tim kiem theo ten su kien hoac tieu de
     * @return danh sach toi da 5 goi y ve
     */
    @GetMapping("/suggestions")
    @ResponseBody
    public ResponseEntity<List<TicketSuggestionResponse>> suggestions(@RequestParam(required = false) String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Ticket> tickets = ticketRepository.findTopSuggestions(keyword.trim(), 5);

        List<TicketSuggestionResponse> responses = tickets.stream()
                .map(this::toSuggestion)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Chi tiet mot ve.
     *
     * @param id id cua ve
     * @param model model de truyen du lieu sang view
     * @return ten view "ticket/detail"
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, Principal principal) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));

        // Dong bo cua so khieu nai: dong sau thoi gian bat dau 1 ngay (eventStartAt + 1 day)
        if (ticket.getEventStartAt() != null && ticket.getDisputeWindowClosesAt() != null) {
            java.time.Instant expectedClose = ticket.getEventStartAt().plus(java.time.Duration.ofDays(1));
            if (!ticket.getDisputeWindowClosesAt().equals(expectedClose)) {
                ticket.setDisputeWindowClosesAt(expectedClose);
                ticketRepository.save(ticket);
            }
        }

        TicketResponse response = toResponse(ticket);
        model.addAttribute("ticket", response);

        // Xac dinh thong tin seller (BR: Dung UserSummaryResponse de tranh leak thong tin ngan hang)
        User seller = userRepository.findById(ticket.getSellerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", ticket.getSellerId()));
        UserSummaryResponse sellerSummary = new UserSummaryResponse();
        sellerSummary.setId(seller.getId());
        sellerSummary.setEmail(seller.getEmail());
        sellerSummary.setFullName(seller.getFullName());
        sellerSummary.setRole(seller.getRole());
        sellerSummary.setStatus(seller.getStatus());
        model.addAttribute("seller", sellerSummary);

        // Xac dinh vai tro cua nguoi dang xem de hien thi nut hanh dong phu hop
        List<ConflictingTicketInfo> scheduleConflicts = Collections.emptyList();
        boolean hasScheduleConflict = false;
        if (principal != null) {
            Long currentUserId = resolveUserId(principal);
            boolean viewerIsSeller = ticket.getSellerId().equals(currentUserId);
            model.addAttribute("currentUserId", currentUserId);
            model.addAttribute("isSeller", viewerIsSeller);
            model.addAttribute("isBuyer",
                    ticket.getBuyerId() != null && ticket.getBuyerId().equals(currentUserId));
            model.addAttribute("isStarred", starredTicketUseCase.isStarred(currentUserId, id));

            // Chi kiem tra trung gio cho nguoi mua tiem nang khi ve con dang rao ban (LISTED)
            if (!viewerIsSeller && ticket.getStatus() == TicketStatus.LISTED && ticketScheduleService != null) {
                scheduleConflicts = ticketScheduleService.findConflictingTickets(currentUserId, ticket);
                hasScheduleConflict = scheduleConflicts != null && !scheduleConflicts.isEmpty();
            }
        }
        model.addAttribute("scheduleConflicts", scheduleConflicts != null ? scheduleConflicts : Collections.emptyList());
        model.addAttribute("hasScheduleConflict", hasScheduleConflict);

        return "ticket/detail";
    }

    /**
     * Form dang ve moi.
     *
     * @param model model de truyen form object sang view
     * @return ten view "ticket/create"
     */
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("createTicketRequest", new CreateTicketRequest());
        model.addAttribute("ticketTypes", getActiveTicketTypes());
        return "ticket/create";
    }

    /**
     * Tao listing moi tu form dang ve.
     *
     * @param request du lieu form da qua Bean Validation
     * @param bindingResult ket qua validation, dung de tra loi form khi co loi
     * @param ticketImage anh ve nguoi ban tai len
     * @param qrImage anh ma QR nguoi ban tai len
     * @param principal nguoi dang dang nhap
     * @param model model de truyen du lieu sang view khi co loi
     * @return chuyen huong sang trang chi tiet ve vua tao
     */
    @PostMapping
    public String create(@Valid @ModelAttribute CreateTicketRequest request,
                         BindingResult bindingResult,
                         @RequestParam(value = "ticketImage", required = false) MultipartFile ticketImage,
                         @RequestParam(value = "qrImage", required = false) MultipartFile qrImage,
                         Principal principal,
                         Model model) {
        // Validate file upload: phai co ca hai anh
        if (ticketImage == null || ticketImage.isEmpty()) {
            bindingResult.rejectValue("ticketImageStorageKey", "required",
                    "Phai upload anh ve cong khai.");
        }
        if (qrImage == null || qrImage.isEmpty()) {
            bindingResult.rejectValue("qrImageStorageKey", "required",
                    "Phai upload anh QR an (anh check-in bi mat).");
        }
        if (request.getEventStartAt() != null && request.getEventEndAt() != null
                && !request.getEventEndAt().isAfter(request.getEventStartAt())) {
            bindingResult.rejectValue("eventEndAt", "invalidRange",
                    "Thời gian kết thúc sự kiện phải nằm sau thời gian bắt đầu.");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("ticketTypes", getActiveTicketTypes());
            return "ticket/create";
        }

        // Upload 2 file anh qua FileStorageService
        try {
            StoredFileRef ticketImageRef = fileStorageService.store(
                    "tickets",
                    ticketImage.getOriginalFilename(),
                    ticketImage.getInputStream(),
                    ticketImage.getSize(),
                    ticketImage.getContentType());
            request.setTicketImageStorageKey(ticketImageRef.getStorageKey());

            StoredFileRef qrImageRef = fileStorageService.store(
                    "tickets-qr",
                    qrImage.getOriginalFilename(),
                    qrImage.getInputStream(),
                    qrImage.getSize(),
                    qrImage.getContentType());
            request.setQrImageStorageKey(qrImageRef.getStorageKey());
        } catch (IOException e) {
            bindingResult.reject("file.upload.error",
                    "Khong the tai anh len. Vui long thu lai.");
            model.addAttribute("ticketTypes", TicketType.values());
            return "ticket/create";
        }

        Long sellerId = resolveUserId(principal);
        TicketResponse created = transferTicketService.createListing(request, sellerId);

        return "redirect:/tickets/" + created.getId();
    }

    /**
     * Nguoi ban danh dau da chuyen ve cho nguoi mua.
     *
     * @param id id cua ve
     * @param principal nguoi dang dang nhap (seller)
     * @return chuyen huong sang trang chi tiet ve
     */
    @PostMapping("/{id}/transfer")
    public String markTransferred(@PathVariable Long id, Principal principal) {
        Long sellerId = resolveUserId(principal);
        transferTicketService.markTransferred(id, sellerId);
        return "redirect:/tickets/" + id;
    }

    /**
     * Nguoi mua xac nhan da nhan duoc ve.
     *
     * @param id id cua ve
     * @param principal nguoi dang dang nhap (buyer)
     * @return chuyen huong sang trang chi tiet ve
     */
    @PostMapping("/{id}/received")
    public String markReceived(@PathVariable Long id, Principal principal) {
        Long buyerId = resolveUserId(principal);
        transferTicketService.markAwaitingUse(id, buyerId);
        return "redirect:/tickets/" + id;
    }

    /**
     * Nguoi mua bao loi ky thuat: anh QR khong xem duoc / anh hong trong 30 phut (UC-06b).
     *
     * @param id id cua ve
     * @param reason ly do loi
     * @param principal nguoi dang dang nhap (buyer)
     * @return chuyen huong sang trang chi tiet ve
     */
    @PostMapping("/{id}/technical-issue")
    public String reportTechnicalIssue(@PathVariable Long id,
                                       @RequestParam(defaultValue = "Ảnh QR không xem được hoặc bị hỏng") String reason,
                                       Principal principal) {
        Long buyerId = resolveUserId(principal);
        transferTicketService.reportTechnicalIssue(id, buyerId, reason);
        return "redirect:/tickets/" + id;
    }

    /**
     * Nguoi ban huy listing.
     *
     * @param id id cua ve
     * @param reason ly do huy
     * @param principal nguoi dang dang nhap (seller)
     * @return chuyen huong ve danh sach ve
     */
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam String reason,
                         Principal principal) {
        Long sellerId = resolveUserId(principal);
        transferTicketService.cancelListing(id, sellerId, reason);
        return "redirect:/tickets";
    }

    /**
     * API lay danh sach ve da mua va da ban cua user (tra ve JSON).
     */
    @org.springframework.web.bind.annotation.GetMapping("/api/my-tickets")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, java.util.List<TicketSummaryResponse>> getMyTickets(Principal principal) {
        Long userId = resolveUserId(principal);
        java.util.List<TicketSummaryResponse> bought = ticketRepository.findByBuyerId(userId).stream()
                .map(this::toSummary).collect(java.util.stream.Collectors.toList());
        java.util.List<TicketSummaryResponse> sold = ticketRepository.findBySellerId(userId).stream()
                .map(this::toSummary).collect(java.util.stream.Collectors.toList());
        
        java.util.Map<String, java.util.List<TicketSummaryResponse>> result = new java.util.HashMap<>();
        result.put("bought", bought);
        result.put("sold", sold);
        return result;
    }

    // ========================= Helper methods =========================

    /**
     * Lay userId (Long) tu Principal. UserDetailsServiceImpl dung email lam username,
     * nen principal.getName() tra ve email → tim User qua UserRepository de lay id.
     */
    private Long resolveUserId(Principal principal) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return user.getId();
    }

    /** Map entity Ticket sang DTO TicketSummaryResponse (ban gon, dung cho danh sach). */
    private TicketSummaryResponse toSummary(Ticket ticket) {
        TicketSummaryResponse summary = new TicketSummaryResponse();
        summary.setId(ticket.getId());
        summary.setTicketType(ticket.getTicketType());
        summary.setStatus(ticket.getStatus());
        summary.setTitle(ticket.getTitle());
        summary.setEventName(ticket.getEventName());
        summary.setEventStartAt(ticket.getEventStartAt());
        if (ticket.getEventEndAt() != null) {
            summary.setEventEndAt(ticket.getEventEndAt());
        } else if (ticketScheduleService != null) {
            ticketScheduleService.resolveTimeSlot(ticket).ifPresent(slot -> summary.setEventEndAt(slot.end()));
        }
        summary.setSalePrice(ticket.getSalePrice());
        if (ticket.getTicketImageStorageKey() != null
                && !ticket.getTicketImageStorageKey().isBlank()) {
            summary.setThumbnailUrl(
                    fileStorageService.buildPublicUrl(ticket.getTicketImageStorageKey()));
        }
        return summary;
    }

    /** Map entity Ticket sang DTO TicketResponse day du (dung cho trang chi tiet). */
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
        if (ticket.getEventEndAt() != null) {
            response.setEventEndAt(ticket.getEventEndAt());
            response.setEstimatedEndTime(false);
        } else if (ticketScheduleService != null) {
            ticketScheduleService.resolveTimeSlot(ticket).ifPresent(slot -> {
                response.setEventEndAt(slot.end());
                response.setEstimatedEndTime(true);
            });
        }
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
        
        java.time.Instant now = java.time.Instant.now();
        if (ticket.getReceivedAt() != null) {
            java.time.Instant expiredAt = ticket.getReceivedAt().plus(java.time.Duration.ofMinutes(30));
            response.setQrExpired(expiredAt.isBefore(java.time.Instant.now()));
            response.setQrExpired(expiredAt.isBefore(now));
        } else {
            response.setQrExpired(false);
        }
        java.time.Instant effectiveEnd = response.getEventEndAt() != null
                ? response.getEventEndAt()
                : response.getEventStartAt();
        response.setEventExpired(effectiveEnd != null && effectiveEnd.isBefore(now));
        if (ticket.getTicketImageStorageKey() != null
                && !ticket.getTicketImageStorageKey().isBlank()) {
            response.setTicketImageUrl(
                    fileStorageService.buildPublicUrl(ticket.getTicketImageStorageKey()));
        }
        if (ticket.getQrImageStorageKey() != null
                && !ticket.getQrImageStorageKey().isBlank()) {
            response.setQrImageUrl(
                    fileStorageService.buildPublicUrl(ticket.getQrImageStorageKey()));
        }
        return response;
    }

    private TicketSuggestionResponse toSuggestion(Ticket ticket) {
        String label = "Vé";
        if (ticket.getTicketType() != null) {
            switch (ticket.getTicketType()) {
                case MOVIE -> label = "Phim";
                case CONCERT -> label = "Concert";
                case BUS -> label = "Xe khách";
                default -> label = "Vé";
            }
        }

        String formattedPrice = "Liên hệ";
        if (ticket.getSalePrice() != null) {
            formattedPrice = String.format("%,d ₫", ticket.getSalePrice().longValue()).replace(',', '.');
        }

        String thumbnailUrl = null;
        if (ticket.getTicketImageStorageKey() != null && !ticket.getTicketImageStorageKey().isBlank()) {
            thumbnailUrl = fileStorageService.buildPublicUrl(ticket.getTicketImageStorageKey());
        }

        return TicketSuggestionResponse.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .eventName(ticket.getEventName())
                .ticketType(ticket.getTicketType())
                .ticketTypeLabel(label)
                .formattedPrice(formattedPrice)
                .thumbnailUrl(thumbnailUrl)
                .detailUrl("/tickets/" + ticket.getId())
                .venue(ticket.getVenue())
                .build();
    }
}
