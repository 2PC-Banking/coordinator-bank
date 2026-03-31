package com.bank.coordinator.repository;

import com.bank.coordinator.domain.CoordinatorTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoordinatorTransactionRepository extends MongoRepository<CoordinatorTransaction, String> {
    Optional<CoordinatorTransaction> findByClientTxId(String clientTxId);

    List<CoordinatorTransaction> findByStatusAndUpdatedAtGreaterThan(String status, LocalDateTime since);

    List<CoordinatorTransaction> findByPhaseAndNextRetryAtLessThanEqual(String phase, LocalDateTime now);

    List<CoordinatorTransaction> findByStatusIn(List<String> statuses);

    @Query("{ 'status': ?0, 'participants.decision_ack': 'UNKNOWN' }")
    List<CoordinatorTransaction> findUnackedTransactions(String status);
}
