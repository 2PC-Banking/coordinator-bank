package com.bank.coordinator.service;

import com.bank.coordinator.client.*;
import com.bank.coordinator.domain.CoordinatorTransaction;
import com.bank.coordinator.domain.Participant;
import com.bank.coordinator.dto.TransferRequest;
import com.bank.coordinator.dto.TransferResponse;
import com.bank.coordinator.repository.CoordinatorTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TransferService {
    private final CoordinatorTransactionRepository repository;
    private final BankClient bankClient;
    private final PreparePhaseService preparePhaseService;
    private final DecisionPhaseService decisionPhaseService;

    public TransferService(CoordinatorTransactionRepository repository, BankClient bankClient,
                          PreparePhaseService preparePhaseService, DecisionPhaseService decisionPhaseService) {
        this.repository = repository;
        this.bankClient = bankClient;
        this.preparePhaseService = preparePhaseService;
        this.decisionPhaseService = decisionPhaseService;
    }

    public TransferResponse initiateTransfer(TransferRequest request) {
        // Check idempotency
        if (request.getClientTxId() != null) {
            var existing = repository.findByClientTxId(request.getClientTxId());
            if (existing.isPresent()) {
                log.info("Duplicate client_tx_id: {}. Returning existing transaction.", request.getClientTxId());
                return mapToResponse(existing.get());
            }
        }

        // Create transaction
        String transactionId = generateTransactionId();
        List<Participant> participants = request.getParticipants().stream()
                .map(p -> Participant.builder()
                        .name(p.getName())
                        .baseUrl(p.getBaseUrl())
                        .accountId(p.getAccountId())
                        .operation(p.getOperation())
                        .prepareVote("UNKNOWN")
                        .decisionAck("UNKNOWN")
                        .retryCount(0)
                        .updatedAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        CoordinatorTransaction tx = CoordinatorTransaction.builder()
                .transactionId(transactionId)
                .clientTxId(request.getClientTxId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .participants(participants)
                .status("PROCESSING_PREPARE")
                .phase("PREPARE")
                .decision(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .history(List.of(createHistoryEvent("TX_CREATED", "Transfer initiated")))
                .build();

        repository.save(tx);
        log.info("Created transaction: {}", transactionId);

        // Execute async 2PC flow
        executeTwoPhaseCommit(tx);

        return mapToResponse(tx);
    }

    private void executeTwoPhaseCommit(CoordinatorTransaction tx) {
        new Thread(() -> {
            try {
                // Phase 1: PREPARE
                preparePhaseService.executePrepare(tx);

                // Phase 2: DECISION
                decisionPhaseService.executeDecision(tx);
            } catch (Exception e) {
                log.error("2PC flow error for transaction {}: {}", tx.getTransactionId(), e.getMessage(), e);
            }
        }).start();
    }

    public TransferResponse getTransactionStatus(String transactionId) {
        var tx = repository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));
        return mapToResponse(tx);
    }

    public TransferResponse retryDecision(String transactionId) {
        var tx = repository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

        if ("IN_DOUBT".equals(tx.getStatus())) {
            log.info("Retrying decision for transaction: {}", transactionId);
            decisionPhaseService.retryDecision(tx);
            repository.save(tx);
        }

        return mapToResponse(tx);
    }

    private TransferResponse mapToResponse(CoordinatorTransaction tx) {
        List<TransferResponse.ParticipantState> participantStates = tx.getParticipants().stream()
                .map(p -> TransferResponse.ParticipantState.builder()
                        .name(p.getName())
                        .operation(p.getOperation())
                        .prepareVote(p.getPrepareVote())
                        .decisionAck(p.getDecisionAck())
                        .retryCount(p.getRetryCount())
                        .lastError(p.getLastError())
                        .build())
                .collect(Collectors.toList());

        return TransferResponse.builder()
                .transactionId(tx.getTransactionId())
                .clientTxId(tx.getClientTxId())
                .status(tx.getStatus())
                .phase(tx.getPhase())
                .decision(tx.getDecision())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .participants(participantStates)
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }

    private String generateTransactionId() {
        return "TX-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Map<String, Object> createHistoryEvent(String event, String message) {
        return Map.of(
                "at", LocalDateTime.now().toString(),
                "event", event,
                "message", message
        );
    }
}
