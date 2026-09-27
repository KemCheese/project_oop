package com.yourapp.ticketpass.ticket.web;

import com.yourapp.ticketpass.ticket.application.TransferTicketService;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.web.dto.CreateTicketRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * Danh sach ve dang duoc rao ban.
     *
     * @param ticketType loc theo loai ve, tuy chon
     * @param model model de truyen du lieu sang view
     * @return ten view "ticket/list"
     */
    @GetMapping
    public String list(@RequestParam(required = false) String ticketType, Model model) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Nap ve dang ban qua ticketRepository.findByStatus(LISTED) hoac "
                        + "findListedByType khi co bo loc, map sang TicketSummaryResponse, dua vao model duoi khoa "
                        + "\"tickets\" va tra ve view \"ticket/list\".");
    }

    /**
     * Chi tiet mot ve.
     *
     * @param id id cua ve
     * @param model model de truyen du lieu sang view
     * @return ten view "ticket/detail"
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Nap ve qua ticketRepository.findById, khong thay thi nem "
                        + "ResourceNotFoundException(\"Ticket\", id), map sang TicketResponse kem URL anh, dua vao "
                        + "model duoi khoa \"ticket\" va tra ve view \"ticket/detail\".");
    }

    /**
     * Form dang ve moi.
     *
     * @param model model de truyen form object sang view
     * @return ten view "ticket/create"
     */
    @GetMapping("/new")
    public String createForm(Model model) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Dua CreateTicketRequest rong va danh sach TicketType vao model, "
                        + "roi tra ve view \"ticket/create\".");
    }

    /**
     * Tao listing moi tu form dang ve.
     *
     * @param request du lieu form da qua Bean Validation
     * @param ticketImage anh ve nguoi ban tai len
     * @param qrImage anh ma QR nguoi ban tai len
     * @return chuyen huong sang trang chi tiet ve vua tao
     */
    @PostMapping
    public String create(@Valid @ModelAttribute CreateTicketRequest request,
                         @RequestParam("ticketImage") MultipartFile ticketImage,
                         @RequestParam("qrImage") MultipartFile qrImage) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Upload hai file qua FileStorageService de lay storage key, gan vao request, "
                        + "lay sellerId tu phien dang nhap, goi transferTicketService.createListing, roi redirect sang "
                        + "\"/tickets/{id}\" cua ve vua tao.");
    }

    /**
     * Nguoi ban danh dau da chuyen ve cho nguoi mua.
     *
     * @param id id cua ve
     * @return chuyen huong sang trang chi tiet ve
     */
    @PostMapping("/{id}/transfer")
    public String markTransferred(@PathVariable Long id) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Lay sellerId tu phien dang nhap, goi "
                        + "transferTicketService.markTransferred(id, sellerId), roi redirect ve \"/tickets/{id}\".");
    }

    /**
     * Nguoi mua xac nhan da nhan duoc ve.
     *
     * @param id id cua ve
     * @return chuyen huong sang trang chi tiet ve
     */
    @PostMapping("/{id}/received")
    public String markReceived(@PathVariable Long id) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Lay buyerId tu phien dang nhap, goi "
                        + "transferTicketService.markAwaitingUse(id, buyerId), roi redirect ve \"/tickets/{id}\" "
                        + "kem thong bao thoi diem dong cua so khieu nai.");
    }

    /**
     * Nguoi ban huy listing.
     *
     * @param id id cua ve
     * @param reason ly do huy
     * @return chuyen huong ve danh sach ve
     */
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam String reason) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Lay sellerId tu phien dang nhap, goi "
                        + "transferTicketService.cancelListing(id, sellerId, reason), roi redirect ve \"/tickets\".");
    }
}
