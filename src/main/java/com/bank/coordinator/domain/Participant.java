package com.bank.coordinator.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participant {
    private String name;
    private String baseUrl;
    private String accountId;
    private String operation; // DEBIT or CREDIT
    private String prepareVote; // YES, NO, UNKNOWN
    private String decisionAck; // ACK, NACK, UNKNOWN
    private int retryCount;
    private String lastError;
    private LocalDateTime updatedAt;
}
