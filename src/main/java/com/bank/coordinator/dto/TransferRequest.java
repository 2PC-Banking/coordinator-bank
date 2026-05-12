package com.bank.coordinator.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias("client_tx_id")
    private String clientTxId;
    @JsonAlias("from_account")
    private String fromAccount;
    @JsonAlias("to_account")
    private String toAccount;
    private Double amount;
    private String currency;
    private List<ParticipantDTO> participants;
    @JsonAlias("timeout_ms")
    private Long timeoutMs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantDTO {
        private String name;
        @JsonAlias("base_url")
        private String baseUrl;
        @JsonAlias("account_id")
        private String accountId;
        private String operation; // DEBIT or CREDIT
        @JsonAlias("simulate_prepare_delay_ms")
        private Integer simulatePrepareDelayMs;
        @JsonAlias("simulate_prepare_crash_before_vote")
        private Boolean simulatePrepareCrashBeforeVote;
        @JsonAlias("simulate_commit_delay_ms")
        private Integer simulateCommitDelayMs;
        @JsonAlias("simulate_commit_fail_before_apply")
        private Boolean simulateCommitFailBeforeApply;
        @JsonAlias("simulate_commit_crash")
        private Boolean simulateCommitCrash;
        @JsonAlias("simulate_rollback_delay_ms")
        private Integer simulateRollbackDelayMs;
        @JsonAlias("simulate_rollback_crash_before_apply")
        private Boolean simulateRollbackCrashBeforeApply;
        @JsonAlias("simulate_rollback_crash_after_apply")
        private Boolean simulateRollbackCrashAfterApply;
    }
}
