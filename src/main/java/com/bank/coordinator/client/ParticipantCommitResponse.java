package com.bank.coordinator.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantCommitResponse {
    private String transaction_id;
    private String status; // COMMITTED
    private String message;
    private String account_id;
    private String operation;
    private Double amount;
    private Double new_balance;
}
