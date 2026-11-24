package com.yourapp.ticketpass.dispute.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RespondToDisputeRequest {

    @NotBlank
    @Size(max = 2000)
    private String response;
}
