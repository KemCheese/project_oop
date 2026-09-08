package com.yourapp.ticketpass.verification.domain;

import java.math.BigDecimal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ket qua doc anh ve bang OCR.
 *
 * <p>{@code fields} chua cac cap khoa - gia tri trich xuat duoc tu anh, vi du {@code eventName},
 * {@code seatInfo}, {@code orderCode}, {@code showTime}. Cac khoa nay duoc
 * {@code com.yourapp.ticketpass.verification.application.OcrVerificationService} doi chieu voi
 * du lieu nguoi ban tu khai de phat hien sai lech.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OcrExtraction {

    /** Toan bo van ban tho OCR doc duoc. */
    private String rawText;

    /** Do tin cay trung binh cua lan doc, thang diem phan tram. */
    private BigDecimal confidence;

    /** Cac truong da duoc bo tach tu rawText. */
    private Map<String, String> fields;
}
