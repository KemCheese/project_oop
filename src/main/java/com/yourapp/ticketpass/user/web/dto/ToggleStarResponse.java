package com.yourapp.ticketpass.user.web.dto;

public record ToggleStarResponse(
    boolean success,
    Long ticketId,
    boolean starred,
    String message
) {}
