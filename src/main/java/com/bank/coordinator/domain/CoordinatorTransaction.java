package com.bank.coordinator.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "coordinator_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
    @CompoundIndex(name = "idx_status_updated", def = "{'status': 1, 'updated_at': -1}"),
    @CompoundIndex(name = "idx_phase_retry", def = "{'phase': 1, 'next_retry_at': 1}")
})
public class CoordinatorTransaction {
    @Id
    private String transactionId; // TX-2026-0001

    @Indexed(unique = true, sparse = true)
    private String clientTxId; // CLI-2026-0001

    private String status; // PROCESSING_PREPARE, PROCESSING_DECISION, COMMITTED, ABORTED, IN_DOUBT
    private String phase; // PREPARE, DECISION, DONE
    private String decision; // COMMIT, ROLLBACK, null

    private Double amount;
    private String currency; // VND, USD, ...

    private List<Participant> participants; // DEBIT + CREDIT banks

    private List<Map<String, Object>> history; // Event log

    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
