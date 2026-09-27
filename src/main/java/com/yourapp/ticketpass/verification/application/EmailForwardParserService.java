package com.yourapp.ticketpass.verification.application;

import com.yourapp.ticketpass.verification.domain.EmailForwardInfo;
import com.yourapp.ticketpass.verification.domain.VerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * [DEPRECATED] Module verification da bi loai bo khoi pham vi du an.
 * Du an chuyen huong sang mo hinh marketplace. Xem 00-tong-quan-du-an.md.
 *
 * Bo tach va doi chieu email xac nhan ve ma nguoi ban forward lai.
 *
 * <p>Email den truc tiep tu don vi phat hanh la bang chung so huu manh nhat ma san co the thu thap
 * ma khong can tich hop API voi tung nha ban ve. Service nay chuyen email tho thanh
 * {@link EmailForwardInfo}, sau do doi chieu ten don vi phat hanh va ma don hang voi du lieu ve.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
@Service
@RequiredArgsConstructor
public class EmailForwardParserService {

    private final VerificationRepository verificationRepository;

    /**
 * [DEPRECATED] Module verification da bi loai bo khoi pham vi du an.
 * Du an chuyen huong sang mo hinh marketplace. Xem 00-tong-quan-du-an.md.
 *
     * Bo tach noi dung email tho thanh cac truong co cau truc.
     *
     * @param rawEmailSource noi dung email dang tho, gom header va body
     * @return thong tin bo tach duoc tu email
     */
    public EmailForwardInfo parse(String rawEmailSource) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Doc header de lay From, Subject, Date va danh sach ten tep dinh kem; "
                        + "suy ra providerName tu ten mien cua dia chi gui; bat orderCode trong body bang regex theo "
                        + "mau ma don hang cua tung nha ban ve; kiem tra so bo cac header SPF/DKIM con nguyen ven "
                        + "de dat signatureLooksValid; tra ve EmailForwardInfo.");
    }

    /**
 * [DEPRECATED] Module verification da bi loai bo khoi pham vi du an.
 * Du an chuyen huong sang mo hinh marketplace. Xem 00-tong-quan-du-an.md.
 *
     * Doi chieu thong tin email voi mot ve cu the.
     *
     * @param info thong tin da bo tach tu email
     * @param ticketId id thuan cua ve can doi chieu
     * @param providerName ten don vi phat hanh nguoi ban khai
     * @param providerOrderCode ma don hang nguoi ban khai
     * @return true neu email khop voi ve
     */
    public boolean matchesTicket(EmailForwardInfo info, Long ticketId,
                                 String providerName, String providerOrderCode) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: So khop providerName va providerOrderCode trong info voi hai tham so dua "
                        + "vao (bo qua hoa thuong va khoang trang), kiem tra info.signatureLooksValid va receivedAt "
                        + "khong qua cu; cap nhat cac truong emailForwardVerified, emailForwardFrom, "
                        + "emailForwardSubject, emailForwardReceivedAt cua TicketVerification tuong ung roi luu qua "
                        + "verificationRepository.save.");
    }
}
