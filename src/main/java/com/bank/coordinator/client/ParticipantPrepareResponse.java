package com.bank.coordinator.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantPrepareResponse {
    private String transaction_id;
    private String vote; // YES, NO
    private String message;
    private String account_id;
    private String operation;
    private Double amount;
}
