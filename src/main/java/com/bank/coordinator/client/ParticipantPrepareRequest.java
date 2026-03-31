package com.bank.coordinator.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantPrepareRequest {
    private String transaction_id;
    private String account_id;
    private String operation;
    private Double amount;
    private int simulate_delay_ms;
    private boolean simulate_crash_before_vote;
}
