package com.yourapp.ticketpass.shared.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConflictingTicketInfo {

    private Long ticketId;

    private String title;

    private String eventName;

    private String venue;

    private String statusCode;

    private String statusLabel;

    private Instant eventStartAt;

    private Instant eventEndAt;

    private boolean estimatedEndTime;

    private String formattedTimeRange;
}
