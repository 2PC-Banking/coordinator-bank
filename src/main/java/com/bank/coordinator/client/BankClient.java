package com.bank.coordinator.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class BankClient {
    private final RestTemplate restTemplate;

    public BankClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ParticipantPrepareResponse sendPrepare(String bankUrl, ParticipantPrepareRequest request) {
        try {
            String url = bankUrl + "/api/prepare";
            log.info("Sending PREPARE to {} for transaction {}", url, request.getTransaction_id());
            
            ParticipantPrepareResponse response = restTemplate.postForObject(
                    url,
                    request,
                    ParticipantPrepareResponse.class
            );
            
            log.info("PREPARE response from {}: vote={}", url, response.getVote());
            return response;
        } catch (RestClientException e) {
            log.error("PREPARE failed for {}: {}", bankUrl, e.getMessage());
            return null;
        }
    }

    public ParticipantCommitResponse sendCommit(String bankUrl, ParticipantCommitRequest request) {
        try {
            String url = bankUrl + "/api/commit";
            log.info("Sending COMMIT to {} for transaction {}", url, request.getTransaction_id());
            
            ParticipantCommitResponse response = restTemplate.postForObject(
                    url,
                    request,
                    ParticipantCommitResponse.class
            );
            
            log.info("COMMIT response from {}: status={}", url, response.getStatus());
            return response;
        } catch (RestClientException e) {
            log.error("COMMIT failed for {}: {}", bankUrl, e.getMessage());
            return null;
        }
    }

    public ParticipantRollbackResponse sendRollback(String bankUrl, ParticipantRollbackRequest request) {
        try {
            String url = bankUrl + "/api/rollback";
            log.info("Sending ROLLBACK to {} for transaction {}", url, request.getTransaction_id());
            
            ParticipantRollbackResponse response = restTemplate.postForObject(
                    url,
                    request,
                    ParticipantRollbackResponse.class
            );
            
            log.info("ROLLBACK response from {}: status={}", url, response.getStatus());
            return response;
        } catch (RestClientException e) {
            log.error("ROLLBACK failed for {}: {}", bankUrl, e.getMessage());
            return null;
        }
    }
}
