package com.bank.coordinator.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantRollbackResponse {
    private String transaction_id;
    private String status; // ABORTED
    private String message;
    private String account_id;
}
