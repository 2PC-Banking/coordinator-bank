package com.bank.coordinator.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantRollbackRequest {
    private String transaction_id;
    private int simulate_delay_ms;
    private boolean simulate_crash_before_apply;
    private boolean simulate_crash_after_apply;
}
