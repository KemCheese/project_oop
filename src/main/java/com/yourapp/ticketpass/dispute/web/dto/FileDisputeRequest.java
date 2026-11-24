package com.yourapp.ticketpass.dispute.web.dto;

import com.yourapp.ticketpass.dispute.domain.DisputeReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FileDisputeRequest {

    @NotNull
    private Long ticketId;

    @NotNull
    private DisputeReason reason;

    @NotBlank
    @Size(max = 2000)
    private String description;
}
