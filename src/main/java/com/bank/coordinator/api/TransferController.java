package com.bank.coordinator.api;

import com.bank.coordinator.dto.RetryDecisionRequest;
import com.bank.coordinator.dto.TransferRequest;
import com.bank.coordinator.dto.TransferResponse;
import com.bank.coordinator.dto.ErrorResponse;
import com.bank.coordinator.service.TransferService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/coordinator")
@Validated
public class TransferController {
    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> initiateTransfer(@RequestBody TransferRequest request) {
        try {
            log.info("Received transfer request. client_tx_id: {}", request.getClientTxId());
            TransferResponse response = transferService.initiateTransfer(request);
            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            log.error("Error initiating transfer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/transfers/{transaction_id}")
    public ResponseEntity<TransferResponse> getTransactionStatus(@PathVariable String transaction_id) {
        try {
            TransferResponse response = transferService.getTransactionStatus(transaction_id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Transaction not found: {}", transaction_id);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/transfers/{transaction_id}/retry-decision")
    public ResponseEntity<TransferResponse> retryDecision(
            @PathVariable String transaction_id,
            @RequestBody(required = false) RetryDecisionRequest request) {
        try {
            log.info("Retrying decision for transaction: {}", transaction_id);
            TransferResponse response = transferService.retryDecision(transaction_id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrying decision", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Coordinator is healthy");
    }
}
