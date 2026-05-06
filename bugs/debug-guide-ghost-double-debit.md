# 🏦 Debug Guide: Ghost Double Debit — Hướng dẫn chạy từng bước

> Bug trừ tiền kép do Timeout + Retry + Idempotency + Oracle CHAR.
> Tài liệu này hướng dẫn **cách chạy**, **luồng call**, **vị trí bug**, **cách fix** và **thuật ngữ cần nắm**.

---

## 📋 Mục lục

1. [Thuật ngữ cần nắm](#1-thuật-ngữ-cần-nắm)
2. [Điều kiện tiên quyết](#2-điều-kiện-tiên-quyết)
3. [Cấu trúc module](#3-cấu-trúc-module)
4. [Luồng call chi tiết](#4-luồng-call-chi-tiết)
5. [Hướng dẫn chạy từng bước](#5-hướng-dẫn-chạy-từng-bước)
6. [Vị trí Bug — 5 lỗi được cài cắm](#6-vị-trí-bug--5-lỗi-được-cài-cắm)
7. [Hướng dẫn Debug với Breakpoint](#7-hướng-dẫn-debug-với-breakpoint)
8. [Cách Fix từng lỗi](#8-cách-fix-từng-lỗi)

---

## 1. Thuật ngữ cần nắm

| Thuật ngữ | Tiếng Anh | Giải thích |
|:----------|:----------|:-----------|
| **Idempotency** | Tính lũy đẳng | Gửi cùng 1 request nhiều lần → kết quả luôn giống nhau. Trong banking: gửi lệnh chuyển tiền 2 lần → chỉ trừ tiền 1 lần. |
| **Idempotency Key** | Khóa lũy đẳng | Chuỗi duy nhất đại diện cho 1 giao dịch, dùng để phát hiện request trùng. Ví dụ: `APP_TRANSFER:FT202605040007`. |
| **Timeout** | Hết thời gian chờ | Client đặt giới hạn thời gian chờ response. Hết hạn → client ném exception, **nhưng server vẫn tiếp tục xử lý**. |
| **Rollback** | Hoàn tác | Khi transaction gặp exception, Spring tự động hủy tất cả thay đổi DB trong transaction đó. |
| **@Transactional** | Quản lý giao dịch | Annotation của Spring. Mọi thao tác DB trong method sẽ thuộc 1 transaction. Lỗi → rollback tất cả. |
| **REQUIRES_NEW** | Transaction độc lập | Propagation level: tạo transaction mới hoàn toàn tách biệt. Dù transaction cha rollback, transaction con vẫn commit. |
| **Trailing Space** | Khoảng trắng thừa | Ký tự space ở cuối chuỗi: `"ABC "` vs `"ABC"`. Mắt thường khó thấy nhưng Java coi là 2 chuỗi khác nhau. |
| **Oracle CHAR** | Kiểu ký tự cố định | Luôn padding space đến đủ độ dài. `CHAR(16)` lưu `"ABC"` → `"ABC             "` (13 space). |
| **Oracle VARCHAR2** | Kiểu ký tự động | Lưu đúng độ dài thực tế. `VARCHAR2(16)` lưu `"ABC"` → `"ABC"` (3 ký tự). |
| **Value Object** | Đối tượng giá trị | Object bất biến, đại diện cho 1 khái niệm nghiệp vụ, validate ngay khi khởi tạo. |
| **Debit** | Trừ tiền | Giảm số dư tài khoản. |
| **Reconciliation** | Đối soát | Cuối ngày so khớp dữ liệu giữa App và Core để phát hiện sai lệch. |
| **RestClient** | HTTP client | API của Spring Framework 7 để gọi HTTP request. Thay thế `RestTemplate`. |
| **Blind Retry** | Thử lại mù quáng | Retry mà không kiểm tra trạng thái giao dịch cũ → gây duplicate. |

---

## 2. Điều kiện tiên quyết

| Yêu cầu | Chi tiết |
|:---------|:---------|
| Java | 21+ |
| Oracle DB | 23c Free, PDB: `FREEPDB1`, user: `backend_debug`, password: `root123` |
| Spring Boot | 4.0.6 |
| Build tool | Maven |
| IDE | IntelliJ IDEA (để dùng Conditional Breakpoint) |
| Tool test API | Postman, curl, hoặc Swagger UI (`http://localhost:8080/swagger-ui.html`) |

---

## 3. Cấu trúc module

```
modules/transfer/
├── client/
│   └── CoreBankingClient.java               ← HTTP client gọi Core, timeout 2s
├── controller/
│   ├── TransferController.java              ← POST /api/v1/transfer
│   └── CoreBankingController.java           ← POST /core/debit
├── dto/request/
│   ├── TransferRequest.java                 ← DTO nhận từ client
│   └── CoreDebitRequest.java                ← DTO gửi đến Core
├── entity/
│   ├── AppTransferRequest.java              ← Entity app-side (CHAR(16))
│   ├── CoreAccount.java                     ← Entity số dư Core
│   └── CoreDebitLog.java                    ← Entity nhật ký trừ tiền Core
├── repository/
│   ├── AppTransferRequestRepository.java
│   ├── CoreAccountRepository.java           ← Chứa query debit có điều kiện
│   └── CoreDebitLogRepository.java
└── service/
    ├── TransferService.java                 ← Interface
    ├── CoreBankingService.java              ← Interface
    └── impl/
        ├── TransferServiceImpl.java         ← ❌ Buggy impl, 3 bugs
        └── CoreBankingServiceImpl.java      ← ❌ Buggy impl, 1 bug
```

**Database tables** (V5 + V6):
- `APP_TRANSFER_REQUEST` — bảng App lưu yêu cầu chuyển tiền
- `CORE_ACCOUNT` — bảng Core lưu số dư (seed: `100000001` = 10,000,000)
- `CORE_DEBIT_LOG` — bảng Core log mỗi lần trừ tiền

---

## 4. Luồng call chi tiết

### 4.1 Sơ đồ tổng quan

```
Client (Postman/curl)
  │
  │  POST /api/v1/transfer
  │  Body: { externalRef, debitAccountNo, amount, coreDelayMillis }
  ▼
┌─────────────────────────────────────────┐
│  TransferController                     │  ← Glue only
│    transferService.transfer(req)        │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│  TransferServiceImpl.transfer()         │  ← @Transactional (APP DB)
│                                         │
│  1. buildAppIdempotencyKey(externalRef) │  ← ❌ Bug: raw string
│  2. existsByIdempotencyKey(key)?        │  ← Check trùng
│  3. save(AppTransferRequest.PROCESSING) │  ← Insert vào APP DB
│  4. coreBankingClient.debit(...)        │  ← HTTP call ↓↓↓
│  5. markSuccess()                       │  ← Chỉ chạy nếu không timeout
└──────────────┬──────────────────────────┘
               │
               │  HTTP POST /core/debit (timeout 2s)
               ▼
┌─────────────────────────────────────────┐
│  CoreBankingController                  │  ← Glue only
│    coreBankingService.debit(req)        │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│  CoreBankingServiceImpl.debit()         │  ← @Transactional (CORE DB)
│                                         │
│  1. sleep(delayMillis)                  │  ← Mô phỏng xử lý chậm
│  2. buildCoreIdempotencyKey(externalRef)│  ← ❌ Bug: raw string
│  3. existsByCoreIdempotencyKey(key)?    │  ← Check trùng
│  4. coreAccountRepository.debit(...)    │  ← UPDATE balance
│  5. save(CoreDebitLog)                  │  ← Ghi log trừ tiền
└─────────────────────────────────────────┘
```

### 4.2 Luồng Request 1 — Timeout xảy ra

```
Thời gian    App Thread                         Core Thread (Tomcat)
─────────    ──────────                         ────────────────────
  0.0s       TransferServiceImpl.transfer()
             → build key "APP_TRANSFER:FT...07 "
             → save PROCESSING
             → coreBankingClient.debit() ──────► CoreBankingServiceImpl.debit()
                                                 → sleep(3000) bắt đầu...
  2.0s       ⚡ TIMEOUT! ReadTimeoutException
             → Spring rollback @Transactional
             → APP_TRANSFER_REQUEST bị XÓA       → sleep(3000) vẫn đang chạy...
             → Client nhận 500 Error
                                                 
  3.0s                                           → sleep(3000) kết thúc
                                                 → build key "CORE_DEBIT:FT...07 "
                                                 → debit account: 10M → 9M
                                                 → save CoreDebitLog
                                                 → ✅ COMMIT thành công!
```

> **Điểm mấu chốt**: App thread đã chết, nhưng Core thread vẫn sống và commit. Đây là bản chất của HTTP — khi client đóng kết nối, server không tự động hủy request.

### 4.3 Luồng Request 2 — Retry gây duplicate

```
Thời gian    App Thread                         Core Thread
─────────    ──────────                         ────────────
  0.0s       TransferServiceImpl.transfer()
             → build key "APP_TRANSFER:FT...07"  ← KHÔNG có trailing space
             → existsByIdempotencyKey? → FALSE    ← vì key cũ đã bị rollback
             → save PROCESSING
             → coreBankingClient.debit() ──────► CoreBankingServiceImpl.debit()
                                                 → sleep(0) — không delay
                                                 → build key "CORE_DEBIT:FT...07"
                                                 → existsByCoreIdempotencyKey?
                                                   → FALSE! (key cũ là "..07 ")
                                                 → debit account: 9M → 8M ❌
                                                 → save CoreDebitLog (dòng 2)
             ← HTTP 200 OK ◄──────────────────── → COMMIT
             → markSuccess()
             → COMMIT
```

> **Kết quả**: Tài khoản bị trừ 2,000,000 thay vì 1,000,000. App chỉ thấy 1 giao dịch SUCCESS.

---

## 5. Hướng dẫn chạy từng bước

### Bước 1: Khởi động Oracle Database

Đảm bảo Oracle 23c Free đang chạy và PDB `FREEPDB1` mở.

```bash
# Kiểm tra Oracle
sqlplus backend_debug/root123@//localhost:1521/FREEPDB1
```

### Bước 2: Tạo bảng và seed data

Copy nội dung file `V5__create_ghost_debit_tables.sql` và `V6__insert_ghost_debit_sample_data.sql` chạy tay trong SQL Developer hoặc sqlplus.

### Bước 3: Chạy ứng dụng Spring Boot

```bash
cd d:\SmartOSC\Project\backend-debug
mvn spring-boot:run
```

### Bước 4: Kiểm tra số dư ban đầu

```sql
SELECT account_no, available_balance FROM core_account WHERE account_no = '100000001';
-- Kỳ vọng: 10,000,000
```

### Bước 5: Gửi Request 1 — Tạo Timeout

Mở Postman hoặc dùng curl:

```bash
curl -X POST http://localhost:8080/api/v1/transfer \
  -H "Content-Type: application/json" \
  -d "{\"externalRef\":\"FT202605040007 \",\"debitAccountNo\":\"100000001\",\"amount\":1000000,\"coreDelayMillis\":3000}"
```

> **Lưu ý**: `externalRef` có 1 **khoảng trắng ở cuối**. `coreDelayMillis = 3000` (Core sẽ sleep 3s, App timeout sau 2s).

**Kỳ vọng:**
- Client nhận HTTP 500 (Timeout Error)
- Kiểm tra bảng App: **KHÔNG** có record nào (đã bị rollback)
- **Đợi ít nhất 4 giây** để Core thread commit xong

```sql
-- App side: không có gì
SELECT * FROM app_transfer_request;
-- 0 rows

-- Core side: đã trừ tiền!
SELECT account_no, available_balance FROM core_account WHERE account_no = '100000001';
-- 9,000,000 (đã bị trừ 1,000,000)

SELECT * FROM core_debit_log;
-- 1 row: business_ref = "FT202605040007 " (có trailing space)
```

### Bước 6: Gửi Request 2 — Partner Retry

```bash
curl -X POST http://localhost:8080/api/v1/transfer \
  -H "Content-Type: application/json" \
  -d "{\"externalRef\":\"FT202605040007\",\"debitAccountNo\":\"100000001\",\"amount\":1000000,\"coreDelayMillis\":0}"
```

> **Lưu ý**: `externalRef` **KHÔNG** có khoảng trắng. `coreDelayMillis = 0`.

**Kỳ vọng:**
- Client nhận HTTP 200

```sql
-- App side: 1 record SUCCESS
SELECT id, external_ref, idempotency_key, status FROM app_transfer_request;
-- 1 row, status = SUCCESS

-- Core side: bị trừ LẦN 2
SELECT account_no, available_balance FROM core_account WHERE account_no = '100000001';
-- 8,000,000 ❌ (đúng phải là 9,000,000)
```

### Bước 7: Xác nhận bug — Kiểm tra Core Debit Log

```sql
SELECT id,
       '[' || CAST(business_ref AS VARCHAR2(16)) || ']' AS visible_ref,
       '[' || core_idempotency_key || ']' AS visible_key,
       amount,
       created_at
FROM core_debit_log
ORDER BY id;
```

**Kết quả**: 2 dòng với key khác nhau:

| id | visible_ref | visible_key | amount |
|:---|:------------|:------------|:-------|
| 1 | `[FT202605040007 ]` | `[CORE_DEBIT:FT202605040007 ]` | 1,000,000 |
| 2 | `[FT202605040007]` | `[CORE_DEBIT:FT202605040007]` | 1,000,000 |

### Bước 8 (Nâng cao): Kiểm tra raw byte trong Oracle

```sql
SELECT id,
       DUMP(business_ref) AS raw_bytes,
       DUMP(core_idempotency_key) AS raw_key_bytes
FROM core_debit_log
ORDER BY id;
```

Bạn sẽ thấy dòng 1 có byte `32` (space) ở cuối `business_ref`, dòng 2 thì không.

### Reset data để test lại

```sql
DELETE FROM core_debit_log;
DELETE FROM app_transfer_request;
UPDATE core_account SET available_balance = 10000000 WHERE account_no = '100000001';
COMMIT;
```

---

## 6. Vị trí Bug — 5 lỗi được cài cắm

### Bug 1: Idempotency Key dùng Raw String (App)

**File**: `TransferServiceImpl.java` — dòng 54-57

```java
private String buildAppIdempotencyKey(String externalRef) {

    return "APP_TRANSFER:" + externalRef;  // ❌ raw string, không trim
}
```

**Hậu quả**: `"FT202605040007 "` và `"FT202605040007"` tạo ra 2 key khác nhau → không phát hiện được trùng lặp.

---

### Bug 2: Idempotency nằm trong Transaction chính

**File**: `TransferServiceImpl.java` — dòng 21-52

```java
@Override
@Transactional  // ← Tất cả nằm trong 1 transaction
public void transfer(TransferRequest request) {
    // ...
    transferRequestRepository.save(transferRequest);  // ← Insert idempotency
    coreBankingClient.debit(...);                      // ← HTTP call → timeout
    // ← Exception ở đây → Spring ROLLBACK toàn bộ
    //   → Record idempotency vừa insert bị XÓA
}
```

**Hậu quả**: Khi timeout, Spring rollback xóa sạch record idempotency → lần retry không biết giao dịch đã tồn tại.

---

### Bug 3: App Timeout 2s < Core Sleep 3s

**File**: `CoreBankingClient.java` — dòng 19-21

```java
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(2000);  // 2 giây
factory.setReadTimeout(2000);     // 2 giây
```

**File**: `CoreBankingServiceImpl.java` — dòng 25

```java
sleep(request.getDelayMillis());  // Client gửi 3000ms
```

**Hậu quả**: App ném `ReadTimeoutException` sau 2s, nhưng Core thread tiếp tục chạy và commit debit sau 3s.

---

### Bug 4: Idempotency Key dùng Raw String (Core)

**File**: `CoreBankingServiceImpl.java` — dòng 54-56

```java
private String buildCoreIdempotencyKey(String externalRef) {
    return "CORE_DEBIT:" + externalRef;  // ❌ raw string, không trim
}
```

**Hậu quả**: Giống Bug 1 nhưng ở phía Core. Core không chặn được request trùng khi externalRef khác trailing space.

---

### Bug 5: Oracle CHAR(16) cho Business Reference

**File**: `V5__create_ghost_debit_tables.sql`

```sql
external_ref  CHAR(16) NOT NULL   -- Luôn padding đến 16 ký tự
business_ref  CHAR(16) NOT NULL   -- Luôn padding đến 16 ký tự
```

**Hậu quả**: Khi query bằng `SELECT`, Oracle tự động padding nên 2 giá trị trông giống nhau. Nhưng idempotency key (VARCHAR2) lưu raw value → khác nhau. Gây nhiễu khi debug: nhìn DB thấy giống, nhưng logic lại khác.

---

## 7. Hướng dẫn Debug với Breakpoint

### Breakpoint 1: Kiểm tra Idempotency Key App

**File**: `TransferServiceImpl.java` dòng 25

```java
String idempotencyKey = buildAppIdempotencyKey(request.getExternalRef());
```

**Condition**: `request.getExternalRef().trim().equals("FT202605040007")`

**Inspect** (Evaluate Expression):
```java
request.getExternalRef()                          // → "FT202605040007 " hoặc "FT202605040007"
request.getExternalRef().length()                  // → 15 hoặc 14
request.getExternalRef().chars().boxed().toList()   // → [70, 84, ..., 55, 32] (32 = space)
idempotencyKey                                     // → "APP_TRANSFER:FT202605040007 "
```

### Breakpoint 2: Trước khi gửi HTTP đến Core

**File**: `CoreBankingClient.java` dòng 39

```java
restClient.post()
```

**Inspect**:
- `externalRef` — xem có trailing space không
- `externalRef.length()` — so sánh 14 vs 15

**Mục tiêu**: Xác nhận app gửi raw string sang Core.

### Breakpoint 3: Core vẫn chạy sau khi App timeout

**File**: `CoreBankingServiceImpl.java` dòng 27

```java
String coreIdempotencyKey = buildCoreIdempotencyKey(request.getExternalRef());
```

**Condition**: `request.getExternalRef().trim().equals("FT202605040007")`

**Mục tiêu**: Khi request 1 gửi với `coreDelayMillis=3000`, đặt breakpoint ở đây. Sau khi Postman/curl đã nhận timeout (2s), thread này vẫn dừng ở breakpoint → chứng minh **Core thread sống độc lập với App thread**.

### Breakpoint 4: So sánh 2 idempotency key của Core

Chạy Request 1, ghi lại `coreIdempotencyKey`. Chạy Request 2, so sánh:
- Request 1: `"CORE_DEBIT:FT202605040007 "` (có space)
- Request 2: `"CORE_DEBIT:FT202605040007"` (không space)

→ 2 key khác nhau → Core không chặn được → **trừ tiền lần 2**.

---

## 8. Cách Fix từng lỗi

### Fix 1: Value Object — Normalize ExternalReference

Tạo Value Object để trim ngay khi nhận dữ liệu:

```java
public final class ExternalReference {
    private final String value;

    private ExternalReference(String value) {
        this.value = value;
    }

    public static ExternalReference from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("External reference must not be blank");
        }
        String normalized = rawValue.strip();
        if (normalized.length() > 32) {
            throw new IllegalArgumentException("External reference too long");
        }
        return new ExternalReference(normalized);
    }

    public String value() {
        return value;
    }
}
```

**Nguyên tắc**: Mọi nơi dùng `externalRef` đều qua `ExternalReference.from()` → đảm bảo đã trim.

### Fix 2: Tách Idempotency ra Transaction độc lập

```java
@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final AppTransferRequestRepository transferRequestRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)  // ← Transaction mới
    public boolean reserve(ExternalReference ref, TransferRequest request) {
        String key = "APP_TRANSFER:" + ref.value();  // ← Dùng normalized value
        if (transferRequestRepository.existsByIdempotencyKey(key)) {
            return false;
        }
        transferRequestRepository.save(
            AppTransferRequest.processing(ref.value(), key,
                request.getDebitAccountNo(), request.getAmount())
        );
        return true;
    }
}
```

**Nguyên tắc**: Dù `transfer()` rollback do timeout, record idempotency **vẫn tồn tại** trong DB vì nó được commit ở transaction riêng.

### Fix 3: Timeout → trạng thái UNKNOWN, không retry mù

```java
@Transactional
public void transfer(TransferRequest request) {
    ExternalReference ref = ExternalReference.from(request.getExternalRef());
    boolean reserved = idempotencyService.reserve(ref, request);
    if (!reserved) return;

    try {
        coreBankingClient.debit(ref.value(), request.getDebitAccountNo(),
            request.getAmount(), request.getCoreDelayMillis());
        idempotencyService.markSuccess(ref);
    } catch (ResourceAccessException ex) {
        // Timeout → UNKNOWN, không phải FAILED
        idempotencyService.markUnknown(ref);
        throw ex;
    }
}
```

**Nguyên tắc**: Trong banking, `Timeout ≠ Failed`. Trạng thái đúng là `UNKNOWN` → cần quy trình riêng để xác nhận (query Core hoặc đợi reconciliation).

### Fix 4: Core cũng dùng Normalized Key

```java
private String buildCoreIdempotencyKey(String externalRef) {
    return "CORE_DEBIT:" + externalRef.strip();  // ← Đã normalize
}
```

### Fix 5: Đổi CHAR sang VARCHAR2

```sql
-- Migration an toàn
ALTER TABLE app_transfer_request ADD external_ref_v2 VARCHAR2(32);
UPDATE app_transfer_request SET external_ref_v2 = TRIM(external_ref);
ALTER TABLE app_transfer_request DROP COLUMN external_ref;
ALTER TABLE app_transfer_request RENAME COLUMN external_ref_v2 TO external_ref;
```

**Nguyên tắc**: Chỉ dùng `CHAR` khi dữ liệu **thật sự fixed-length** (ví dụ: mã quốc gia 2 ký tự). Mọi trường hợp khác → `VARCHAR2`.

---

## Tóm tắt nhanh

| Câu hỏi | Trả lời |
|:---------|:--------|
| Bug xảy ra ở đâu? | `TransferServiceImpl` + `CoreBankingServiceImpl` + `CoreBankingClient` |
| Root cause? | Idempotency key dùng raw string + nằm chung transaction bị rollback |
| Tại sao Core vẫn trừ tiền? | HTTP timeout chỉ đóng kết nối client, server thread vẫn chạy |
| Tại sao retry trừ tiền lần 2? | Trailing space tạo key khác + record cũ đã bị rollback |
| Fix chính? | Value Object normalize + `REQUIRES_NEW` cho idempotency + trạng thái UNKNOWN |

---

> **Endpoint chạy lab**: `POST http://localhost:8080/api/v1/transfer`
> **Mock Core**: `POST http://localhost:8080/core/debit` (internal, không gọi trực tiếp)
> **Security**: Đã whitelist, không cần JWT token.

---
*Tài liệu thuộc series Java Core Training — SmartOSC.*
