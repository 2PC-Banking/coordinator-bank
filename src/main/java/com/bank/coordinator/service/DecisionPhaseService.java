package com.bank.coordinator.service;

import com.bank.coordinator.client.*;
import com.bank.coordinator.domain.CoordinatorTransaction;
import com.bank.coordinator.domain.Participant;
import com.bank.coordinator.repository.CoordinatorTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DecisionPhaseService {
    private final BankClient bankClient;
    private final CoordinatorTransactionRepository repository;

    public DecisionPhaseService(BankClient bankClient, CoordinatorTransactionRepository repository) {
        this.bankClient = bankClient;
        this.repository = repository;
    }

    public void executeDecision(CoordinatorTransaction tx) {
        log.info("Starting DECISION phase for transaction: {} with decision: {}", tx.getTransactionId(), tx.getDecision());

        // Lưu thời điểm bắt đầu Phase 2 để phát hiện IN_DOUBT timeout
        if (tx.getDecisionSentAt() == null) {
            tx.setDecisionSentAt(LocalDateTime.now());
        }

        List<Participant> participants = tx.getParticipants();
        List<CompletableFuture<Void>> futures = participants.stream()
                .map(p -> CompletableFuture.runAsync(() -> sendDecisionToParticipant(tx, p)))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Check if all ACKed
        boolean allAcked = participants.stream().allMatch(p -> "ACK".equals(p.getDecisionAck()));

        if (allAcked) {
            String finalStatus = "COMMIT".equals(tx.getDecision()) ? "COMMITTED" : "ABORTED";
            tx.setStatus(finalStatus);
            tx.setPhase("DONE");
            tx.setDecisionSentAt(null); // Reset khi hoàn thành
        } else {
            tx.setStatus("IN_DOUBT");
            tx.setNextRetryAt(LocalDateTime.now().plusSeconds(5));
        }

        tx.setUpdatedAt(LocalDateTime.now());
        repository.save(tx);
        log.info("DECISION phase completed. Status: {}", tx.getStatus());
    }

    public void retryDecision(CoordinatorTransaction tx) {
        log.info("Retrying DECISION for transaction: {}", tx.getTransactionId());

        List<Participant> unackedParticipants = tx.getParticipants().stream()
                .filter(p -> !"ACK".equals(p.getDecisionAck()))
                .collect(Collectors.toList());

        List<CompletableFuture<Void>> futures = unackedParticipants.stream()
                .map(p -> CompletableFuture.runAsync(() -> sendDecisionToParticipant(tx, p)))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Check again
        boolean allAcked = tx.getParticipants().stream().allMatch(p -> "ACK".equals(p.getDecisionAck()));

        if (allAcked) {
            String finalStatus = "COMMIT".equals(tx.getDecision()) ? "COMMITTED" : "ABORTED";
            tx.setStatus(finalStatus);
            tx.setPhase("DONE");
            tx.setDecisionSentAt(null);
        }

        tx.setUpdatedAt(LocalDateTime.now());
        repository.save(tx);
        log.info("DECISION retry completed. Status: {}", tx.getStatus());
    }

    /**
     * Force ROLLBACK khi IN_DOUBT quá timeout (10 giây).
     * Gửi ROLLBACK tới TẤT CẢ participant, kể cả những bên đã COMMITTED
     * → bên đó phải tạo compensating transaction để hoàn tiền.
     */
    public void forceRollback(CoordinatorTransaction tx) {
        log.warn("⏱ TIMEOUT: Forcing ROLLBACK for IN_DOUBT transaction: {} (was COMMIT decision, now overriding to ROLLBACK)",
                tx.getTransactionId());

        // Override decision → ROLLBACK
        tx.setDecision("ROLLBACK");

        // Reset tất cả ACK về UNKNOWN để gửi lại ROLLBACK
        tx.getParticipants().forEach(p -> {
            if (!"ACK".equals(p.getDecisionAck()) || true) { // Gửi cho cả bên đã ACK COMMIT để compensate
                p.setDecisionAck("UNKNOWN");
            }
        });

        List<CompletableFuture<Void>> futures = tx.getParticipants().stream()
                .map(p -> CompletableFuture.runAsync(() -> sendDecisionToParticipant(tx, p)))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        boolean allAcked = tx.getParticipants().stream().allMatch(p -> "ACK".equals(p.getDecisionAck()));

        if (allAcked) {
            tx.setStatus("ABORTED");
            tx.setPhase("DONE");
            tx.setDecisionSentAt(null);
            log.info("✅ Force ROLLBACK completed. Transaction {} is now ABORTED.", tx.getTransactionId());
        } else {
            tx.setStatus("IN_DOUBT");
            tx.setNextRetryAt(LocalDateTime.now().plusSeconds(10));
            log.warn("⚠️ Force ROLLBACK sent but some participants did not ACK. Keeping IN_DOUBT: {}", tx.getTransactionId());
        }

        tx.setUpdatedAt(LocalDateTime.now());
        repository.save(tx);
    }

    private void sendDecisionToParticipant(CoordinatorTransaction tx, Participant p) {
        try {
            if ("COMMIT".equals(tx.getDecision())) {
                sendCommit(tx, p);
            } else {
                sendRollback(tx, p);
            }
        } catch (Exception e) {
            p.setLastError(e.getMessage());
            log.error("Decision error for {}: {}", p.getName(), e.getMessage());
        }
        p.setUpdatedAt(LocalDateTime.now());
    }

    private void sendCommit(CoordinatorTransaction tx, Participant p) {
        ParticipantCommitRequest request = new ParticipantCommitRequest();
        request.setTransaction_id(tx.getTransactionId());
        request.setSimulate_delay_ms(p.getSimulateCommitDelayMs());
        request.setSimulate_fail_before_apply(p.isSimulateCommitFailBeforeApply());
        request.setSimulate_crash(p.isSimulateCommitCrash());

        ParticipantCommitResponse response = bankClient.sendCommit(p.getBaseUrl(), request);

        if (response != null && "COMMITTED".equals(response.getStatus())) {
            p.setDecisionAck("ACK");
            log.info("COMMIT ACK from {}", p.getName());
        } else {
            p.setDecisionAck("UNKNOWN");
            log.warn("COMMIT timeout/error from {}", p.getName());
        }
    }

    private void sendRollback(CoordinatorTransaction tx, Participant p) {
        ParticipantRollbackRequest request = new ParticipantRollbackRequest();
        request.setTransaction_id(tx.getTransactionId());
        request.setSimulate_delay_ms(p.getSimulateRollbackDelayMs());
        request.setSimulate_crash_before_apply(p.isSimulateRollbackCrashBeforeApply());
        request.setSimulate_crash_after_apply(p.isSimulateRollbackCrashAfterApply());

        ParticipantRollbackResponse response = bankClient.sendRollback(p.getBaseUrl(), request);

        if (response != null && "ABORTED".equals(response.getStatus())) {
            p.setDecisionAck("ACK");
            log.info("ROLLBACK ACK from {}", p.getName());
        } else {
            p.setDecisionAck("UNKNOWN");
            log.warn("ROLLBACK timeout/error from {}", p.getName());
        }
    }
}
