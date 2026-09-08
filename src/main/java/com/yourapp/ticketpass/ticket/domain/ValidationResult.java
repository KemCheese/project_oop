package com.yourapp.ticketpass.ticket.domain;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ket qua kiem tra hop le cua mot ve, do {@link TicketAdapter} hoac
 * {@code com.yourapp.ticketpass.ticket.application.ValidateTicketService} tra ve.
 *
 * <p>Day la value object thuan: {@code valid} cho biet ve co qua duoc kiem tra hay khong,
 * {@code errors} liet ke cac loi de hien thi lai cho nguoi dung. Hai static factory
 * {@link #ok()} va {@link #fail(List)} chi khoi tao doi tuong, khong chua business logic.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    private boolean valid;

    private List<String> errors;

    /**
     * Tao ket qua hop le, khong co loi nao.
     *
     * @return ket qua voi {@code valid = true} va danh sach loi rong
     */
    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    /**
     * Tao ket qua khong hop le kem danh sach loi.
     *
     * @param errors cac thong bao loi mo ta ly do ve khong hop le
     * @return ket qua voi {@code valid = false}
     */
    public static ValidationResult fail(List<String> errors) {
        return new ValidationResult(false, errors);
    }
}
