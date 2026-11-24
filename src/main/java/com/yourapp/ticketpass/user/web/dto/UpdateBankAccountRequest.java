package com.yourapp.ticketpass.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateBankAccountRequest {

    @NotBlank
    @Size(max = 100)
    private String bankName;

    @NotBlank
    @Size(max = 50)
    private String bankAccountNumber;

    @NotBlank
    @Size(max = 150)
    private String bankAccountHolder;
}
