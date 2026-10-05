package com.yourapp.ticketpass.user.web.dto;

/**
 * DTO phản hồi kết quả toggle trạng thái sao của vé qua AJAX.
 *
 * @param success Trạng thái xử lý thành công
 * @param ticketId ID của vé
 * @param starred Trạng thái sao sau thao tác (true = đã lưu, false = đã hủy lưu)
 * @param message Thông điệp thông báo cho người dùng
 */
public record ToggleStarResponse(
    boolean success,
    Long ticketId,
    boolean starred,
    String message
) {}
