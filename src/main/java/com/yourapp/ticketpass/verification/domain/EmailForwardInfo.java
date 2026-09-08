package com.yourapp.ticketpass.verification.domain;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thong tin bo tach duoc tu email xac nhan ve ma nguoi ban forward lai.
 *
 * <p>Email forward la bang chung manh nhat cho thay nguoi ban thuc su so huu ve, vi no den truc tiep
 * tu don vi phat hanh. {@code signatureLooksValid} ghi nhan ket qua kiem tra so bo cac dau hieu
 * xac thuc trong header email (vi du SPF/DKIM con nguyen ven sau khi forward).</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailForwardInfo {

    /** Dia chi gui goc, phai thuoc ten mien cua don vi phat hanh. */
    private String fromAddress;

    private String subject;

    private Instant receivedAt;

    private String bodyText;

    /** Ten don vi phat hanh suy ra tu email, doi chieu voi providerName cua ve. */
    private String providerName;

    /** Ma don hang doc duoc tu email, doi chieu voi providerOrderCode cua ve. */
    private String orderCode;

    private List<String> attachmentNames;

    /** Ket qua kiem tra so bo tinh nguyen ven cua chu ky email. */
    private boolean signatureLooksValid;
}
