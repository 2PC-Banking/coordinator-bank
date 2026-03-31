# Coordinator 2PC - Java + MongoDB

Coordinator service để điều phối 2PC (Two-Phase Commit) cho chuyển tiền liên bank.

## Kiến trúc

- **Language**: Java 17
- **Framework**: Spring Boot 3.2+
- **Database**: MongoDB (collection: coordinator_transactions)
- **API**: RESTful JSON

## Yêu cầu tiên quyết

- Java 17+
- Maven 3.8+
- MongoDB 5.0+ (chạy local hoặc Docker)

## Cài đặt và chạy local

### 1. Khởi động MongoDB

Dùng Docker:

```bash
docker run --name coordinator-mongo -d -p 27017:27017 mongo:latest
```

Hoặc nếu đã cài MongoDB locally:

```bash
mongod --dbpath /path/to/db
```

### 2. Build project

```bash
mvn clean package
```

### 3. Chạy Coordinator

```bash
mvn spring-boot:run
```

Server sẽ chạy trên `http://localhost:8002`

### 4. Kiểm tra health

```bash
curl http://localhost:8002/coordinator/health
```

Expected response:
```
Coordinator is healthy
```

## API Endpoints

### 1. POST /coordinator/transfers

Tạo transfer và thực hiện 2PC.

**Request:**

```json
{
  "client_tx_id": "CLI-2026-0001",
  "from_account": "ACC001",
  "to_account": "ACC002",
  "amount": 100000,
  "currency": "VND",
  "participants": [
    {
      "name": "bank-source",
      "base_url": "http://localhost:8001",
      "account_id": "ACC001",
      "operation": "DEBIT"
    },
    {
      "name": "bank-dest",
      "base_url": "http://localhost:8003",
      "account_id": "ACC002",
      "operation": "CREDIT"
    }
  ],
  "timeout_ms": 3000
}
```

**Response (202 Accepted):**

```json
{
  "transaction_id": "TX-1711868400000-a1b2c3d4",
  "client_tx_id": "CLI-2026-0001",
  "status": "PROCESSING_PREPARE",
  "phase": "PREPARE",
  "decision": null,
  "amount": 100000,
  "currency": "VND",
  "participants": [
    {
      "name": "bank-source",
      "operation": "DEBIT",
      "prepare_vote": "UNKNOWN",
      "decision_ack": "UNKNOWN",
      "retry_count": 0
    }
  ],
  "created_at": "2026-03-31T10:00:00",
  "updated_at": "2026-03-31T10:00:00"
}
```

### 2. GET /coordinator/transfers/{transaction_id}

Lấy trạng thái transaction.

**Response (200 OK):**

```json
{
  "transaction_id": "TX-1711868400000-a1b2c3d4",
  "status": "COMMITTED",
  "phase": "DONE",
  "decision": "COMMIT",
  "participants": [
    {
      "name": "bank-source",
      "prepare_vote": "YES",
      "decision_ack": "ACK",
      "retry_count": 0
    }
  ],
  "created_at": "2026-03-31T10:00:00",
  "updated_at": "2026-03-31T10:00:05"
}
```

### 3. POST /coordinator/transfers/{transaction_id}/retry-decision

Retry phase 2 khi status = IN_DOUBT.

**Request (optional):**

```json
{
  "max_retry": 3,
  "retry_interval_ms": 500
}
```

**Response (200 OK):**

```json
{
  "transaction_id": "TX-1711868400000-a1b2c3d4",
  "status": "COMMITTED",
  "phase": "DONE",
  "decision": "COMMIT"
}
```

## Luồng 2PC

### Phase 1 - PREPARE

1. Coordinator nhận request.
2. Tạo transaction_id và lưu vào MongoDB với status = PROCESSING_PREPARE.
3. Gửi POST /api/prepare đến cả 2 participant.
4. Thu vote (YES/NO/UNKNOWN).
5. Quyết định:
   - All YES => decision = COMMIT
   - Có NO/UNKNOWN => decision = ROLLBACK
6. Chuyển sang phase 2 với status = PROCESSING_DECISION.

### Phase 2 - DECISION

1. Broadcast decision (COMMIT/ROLLBACK).
2. Ghi ACK/UNKNOWN tương ứng.
3. Nếu all ACK:
   - status = COMMITTED (nếu COMMIT) hoặc ABORTED (nếu ROLLBACK)
   - phase = DONE
4. Nếu tồn tại UNKNOWN:
   - status = IN_DOUBT
   - Schedule retry sau 5 giây

## Monitoring

### Log levels

- `DEBUG`: coordinator package
- `INFO`: root + Spring Boot startup

### Metrics

Available at `http://localhost:8002/actuator/metrics`

## MongoDB CLI

```bash
docker exec -it coordinator-mongo mongosh

# Inside mongosh
use coordinator_db
db.coordinator_transactions.find().pretty()
```

## Docker Build

```bash
docker build -t coordinator .
docker run -p 8002:8002 --network bank_network coordinator
```

## Next steps

- [ ] Thêm retry scheduler job (Spring Task).
- [ ] Thêm recovery startup (scan PROCESSING_DECISION).
- [ ] Thêm metrics (Micrometer).
- [ ] Thêm integration test với mock participant.
