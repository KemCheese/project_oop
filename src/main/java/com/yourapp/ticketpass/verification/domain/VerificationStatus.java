package com.yourapp.ticketpass.verification.domain;

/**
 * Trang thai quy trinh xac thuc mot ve truoc khi ve duoc phep giao dich.
 *
 * <p>Luong thuong gap: {@code PENDING} khi ve vua duoc dang, chuyen sang {@code OCR_IN_PROGRESS}
 * khi may doc anh ve, roi re nhanh thanh {@code AUTO_APPROVED} neu do tin cay du cao va rui ro thap,
 * hoac {@code MANUAL_REVIEW_REQUIRED} neu can nguoi that xem. Sau khi admin xu ly, ket qua cuoi cung
 * la {@code APPROVED} hoac {@code REJECTED}.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
public enum VerificationStatus {

    /** Da tao ho so xac thuc, chua xu ly. */
    PENDING,

    /** Dang chay OCR tren anh ve. */
    OCR_IN_PROGRESS,

    /** He thong tu dong chap nhan dua tren do tin cay OCR va bac rui ro thap. */
    AUTO_APPROVED,

    /** Can admin xem xet bang tay. */
    MANUAL_REVIEW_REQUIRED,

    /** Da duoc chap nhan (tu dong hoac sau khi admin duyet). */
    APPROVED,

    /** Bi tu choi, ly do luu trong {@code rejectionReason}. */
    REJECTED
}
