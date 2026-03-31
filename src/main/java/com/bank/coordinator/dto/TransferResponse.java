package com.bank.coordinator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResponse {
    private String transactionId;
    private String clientTxId;
    private String status;
    private String phase;
    private String decision;
    private Double amount;
    private String currency;
    private List<ParticipantState> participants;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantState {
        private String name;
        private String operation;
        private String prepareVote;
        private String decisionAck;
        private int retryCount;
        private String lastError;
    }
}
