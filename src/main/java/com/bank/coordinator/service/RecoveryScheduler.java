package com.bank.coordinator.service;

import com.bank.coordinator.domain.CoordinatorTransaction;
import com.bank.coordinator.repository.CoordinatorTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
public class RecoveryScheduler {

    /**
     * Nếu IN_DOUBT quá bao nhiêu giây thì force ROLLBACK thay vì retry.
     * Đặt = 10 giây theo yêu cầu của thầy:
     * "lỗi trước khi apply commit → sau timeout 10s coordinator phải gọi rollback"
     */
    private static final int FORCE_ROLLBACK_TIMEOUT_SECONDS = 10;

    private final CoordinatorTransactionRepository repository;
    private final TransferService transferService;
    private final DecisionPhaseService decisionPhaseService;

    public RecoveryScheduler(CoordinatorTransactionRepository repository,
                             TransferService transferService,
                             DecisionPhaseService decisionPhaseService) {
        this.repository = repository;
        this.transferService = transferService;
        this.decisionPhaseService = decisionPhaseService;
    }

    @Scheduled(fixedDelay = 5000) // Quét mỗi 5 giây
    public void recoverInDoubtTransactions() {
        List<CoordinatorTransaction> inDoubtTxs = repository.findByStatusIn(List.of("IN_DOUBT"));
        if (inDoubtTxs.isEmpty()) return;

        log.info("RecoveryScheduler: Found {} IN_DOUBT transactions.", inDoubtTxs.size());

        for (CoordinatorTransaction tx : inDoubtTxs) {
            try {
                LocalDateTime decisionSentAt = tx.getDecisionSentAt();

                // Nếu không có decisionSentAt (data cũ), set lại ngay và retry bình thường
                if (decisionSentAt == null) {
                    tx.setDecisionSentAt(LocalDateTime.now());
                    repository.save(tx);
                    log.info("RecoveryScheduler: [{}] No decisionSentAt, setting now and retrying.", tx.getTransactionId());
                    decisionPhaseService.retryDecision(tx);
                    continue;
                }

                long secondsInDoubt = ChronoUnit.SECONDS.between(decisionSentAt, LocalDateTime.now());

                if (secondsInDoubt >= FORCE_ROLLBACK_TIMEOUT_SECONDS && "COMMIT".equals(tx.getDecision())) {
                    // IN_DOUBT quá 10 giây với decision là COMMIT → force ROLLBACK
                    log.warn("RecoveryScheduler: [{}] IN_DOUBT for {}s (≥ {}s timeout). Forcing ROLLBACK.",
                            tx.getTransactionId(), secondsInDoubt, FORCE_ROLLBACK_TIMEOUT_SECONDS);
                    decisionPhaseService.forceRollback(tx);

                } else if (secondsInDoubt >= FORCE_ROLLBACK_TIMEOUT_SECONDS && "ROLLBACK".equals(tx.getDecision())) {
                    // IN_DOUBT quá 10 giây với decision là ROLLBACK:
                    // Kịch bản "mất ACK sau rollback" — participant đã rollback DB nhưng HTTP response bị mất.
                    // ROLLBACK là idempotent → retry an toàn, sau đó force ABORTED.
                    log.warn("RecoveryScheduler: [{}] IN_DOUBT for {}s with ROLLBACK decision (ACK lost after rollback). Final retry then force ABORTED.",
                            tx.getTransactionId(), secondsInDoubt);
                    decisionPhaseService.retryDecision(tx);

                    // Nếu sau retry vẫn UNKNOWN → participant đã thực sự rollback, chỉ mất ACK → force ABORTED
                    if ("IN_DOUBT".equals(tx.getStatus())) {
                        log.warn("RecoveryScheduler: [{}] Still IN_DOUBT after final rollback retry. " +
                                "Assuming participant rolled back (ACK lost). Marking ABORTED.",
                                tx.getTransactionId());
                        tx.setStatus("ABORTED");
                        tx.setPhase("DONE");
                        tx.setDecisionSentAt(null);
                        tx.setUpdatedAt(LocalDateTime.now());
                        repository.save(tx);
                    }

                } else {
                    // Chưa quá timeout → retry bình thường
                    log.info("RecoveryScheduler: [{}] IN_DOUBT for {}s. Retrying {} decision.",
                            tx.getTransactionId(), secondsInDoubt, tx.getDecision());
                    decisionPhaseService.retryDecision(tx);
                }
            } catch (Exception e) {
                log.error("RecoveryScheduler: Failed to recover transaction {}: {}", tx.getTransactionId(), e.getMessage());
            }
        }
    }
}
