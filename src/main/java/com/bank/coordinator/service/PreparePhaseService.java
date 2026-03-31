package com.bank.coordinator.service;

import com.bank.coordinator.client.BankClient;
import com.bank.coordinator.client.ParticipantPrepareRequest;
import com.bank.coordinator.client.ParticipantPrepareResponse;
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
public class PreparePhaseService {
    private final BankClient bankClient;
    private final CoordinatorTransactionRepository repository;

    public PreparePhaseService(BankClient bankClient, CoordinatorTransactionRepository repository) {
        this.bankClient = bankClient;
        this.repository = repository;
    }

    public void executePrepare(CoordinatorTransaction tx) {
        log.info("Starting PREPARE phase for transaction: {}", tx.getTransactionId());

        List<Participant> participants = tx.getParticipants();
        List<CompletableFuture<Void>> futures = participants.stream()
                .map(p -> CompletableFuture.runAsync(() -> sendPrepareToParticipant(tx, p)))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Determine decision
        boolean allYes = participants.stream().allMatch(p -> "YES".equals(p.getPrepareVote()));
        String decision = allYes ? "COMMIT" : "ROLLBACK";

        tx.setDecision(decision);
        tx.setPhase("DECISION");
        tx.setStatus("PROCESSING_DECISION");
        tx.setUpdatedAt(LocalDateTime.now());

        repository.save(tx);
        log.info("PREPARE phase completed. Decision: {}", decision);
    }

    private void sendPrepareToParticipant(CoordinatorTransaction tx, Participant p) {
        try {
            ParticipantPrepareRequest request = new ParticipantPrepareRequest();
            request.setTransaction_id(tx.getTransactionId());
            request.setAccount_id(p.getAccountId());
            request.setOperation(p.getOperation());
            request.setAmount(tx.getAmount());
            request.setSimulate_delay_ms(0);
            request.setSimulate_crash_before_vote(false);

            ParticipantPrepareResponse response = bankClient.sendPrepare(p.getBaseUrl(), request);

            if (response != null) {
                p.setPrepareVote(response.getVote());
                log.info("PREPARE response from {}: vote={}", p.getName(), response.getVote());
            } else {
                p.setPrepareVote("UNKNOWN");
                log.warn("PREPARE timeout/error from {}", p.getName());
            }
        } catch (Exception e) {
            p.setPrepareVote("UNKNOWN");
            p.setLastError(e.getMessage());
            log.error("PREPARE error for {}: {}", p.getName(), e.getMessage());
        }
        p.setUpdatedAt(LocalDateTime.now());
    }
}
