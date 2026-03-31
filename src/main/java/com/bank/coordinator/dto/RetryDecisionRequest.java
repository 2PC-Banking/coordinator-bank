package com.bank.coordinator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryDecisionRequest {
    private Integer maxRetry;
    private Integer retryIntervalMs;
}
