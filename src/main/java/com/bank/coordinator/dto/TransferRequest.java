package com.bank.coordinator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequest {
    private String clientTxId;
    private String fromAccount;
    private String toAccount;
    private Double amount;
    private String currency;
    private List<ParticipantDTO> participants;
    private Long timeoutMs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantDTO {
        private String name;
        private String baseUrl;
        private String accountId;
        private String operation; // DEBIT or CREDIT
    }
}
