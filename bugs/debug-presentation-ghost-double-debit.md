# Ghost Double Debit: Timeout, Retry & Idempotency - Truy vết lỗi trừ tiền kép do Timeout và Idempotency

**Ghost Double Debit** là bug double debit trong luồng `transfer-service` tích hợp với `core banking` để thực hiện **debit** (**trừ tiền**) tài khoản.

Hệ thống được tách thành 2 project: `transfer-service` là hệ thống của mình, `mock-core-banking` là hệ thống core giả lập bên ngoài. Hai hệ thống có **transaction**, **database** và **lifecycle** riêng.

Khi `transfer-service` gọi core debit, request đầu tiên bị **timeout** ở phía `transfer-service` sau 2 giây. Tuy nhiên timeout chỉ có nghĩa là `transfer-service` không nhận được response đúng hạn, **không có nghĩa core chưa xử lý**. Core vẫn có thể tiếp tục xử lý và **commit** debit sau đó.

Bug xảy ra khi `transfer-service` lưu **idempotency**/status trong cùng transaction với HTTP call, nên khi timeout → transaction **rollback** → mất idempotency record. Sau đó user/partner retry lại cùng giao dịch. Nếu `transfer-service` không **normalize** `externalRef` trước khi build idempotency key và trước khi gọi core, request retry có thể tạo idempotency key khác, khiến hệ thống coi đây là giao dịch mới và gọi core debit lần 2.

**Expected đúng** trong banking: timeout phải đưa giao dịch về `PENDING_CONFIRMATION`, **không phải `FAILED`**. Retry đúng sau timeout là **inquiry business status từ core** hoặc trả lại trạng thái giao dịch cũ, tuyệt đối không gọi debit lại nếu cùng business `externalRef` sau normalize.

---

## 1. Phân tích (Analysis)

### 1.1 Tư duy nghiệp vụ (Business Logic)

Một yêu cầu chuyển tiền thường đi kèm với một **mã tham chiếu ngoại bộ** (**external reference**) đóng vai trò là **khóa nghiệp vụ** (**business key**):
- Ví dụ: `externalRef = FT202605040007`

Tuy nhiên, do sai sót ở phía đối tác hoặc ứng dụng, lần đầu gửi lên có thể chứa **khoảng trắng thừa** (**trailing space**):
- `"FT202605040007 "`

### 1.2 Nguyên tắc Banking quan trọng

1. **Timeout ≠ Failed**: Khi gọi core bị timeout, phía mình **không được kết luận failed** và cũng **không được debit lại mù**.
2. **Trạng thái là trách nhiệm riêng**: Việc save trạng thái giao dịch ở phía mình là trách nhiệm của hệ thống mình. Phía core banking xử lý và lưu trạng thái bên core là việc của họ.
3. **Retry đúng = Inquiry**: Sau timeout, retry đúng là **retry hỏi trạng thái giao dịch** (**inquiry**), không phải retry tạo lệnh debit mới.

### 1.3 Kịch bản lỗi (Failure Scenario)

**Lần đầu (First attempt):**
1. `transfer-service` nhận request với `externalRef = "FT202605040007 "` (có trailing space).
2. `transfer-service` chèn bản ghi **idempotency** với trạng thái `PROCESSING`.
3. `transfer-service` gọi `mock-core-banking` (port 9090) để thực hiện trừ tiền (**debit**).
4. Core banking "ngủ" 3 giây rồi thực hiện trừ tiền thành công.
5. `transfer-service` **hết thời gian chờ** (**timeout**) sau 2 giây.
6. **Giao dịch** (**transaction**) của `transfer-service` bị **rollback**, bản ghi idempotency bị xóa.
7. Core banking vẫn hoàn tất và **commit** việc trừ tiền.

**Lần thử lại sai — Blind Retry (Wrong retry):**
1. Đối tác gửi lại request với `externalRef = "FT202605040007"` (đã cắt bỏ khoảng trắng).
2. `transfer-service` không tìm thấy bản ghi idempotency (vì lần trước đã rollback).
3. `transfer-service` tạo bản ghi idempotency mới.
4. Do `transfer-service` gửi sang core hai `externalRef` khác nhau ở cấp string/byte, core có thể coi đây là hai request debit khác nhau → xử lý debit lần 2.
5. `transfer-service` lưu trạng thái `SUCCESS`.

**Lần thử lại đúng — Inquiry (Correct retry):**
1. Đối tác gửi lại request → `transfer-service` phát hiện giao dịch `PENDING_CONFIRMATION`.
2. `transfer-service` **inquiry** core banking: `GET /core/transactions?externalRef=FT202605040007`.
3. Core trả `status = SUCCESS` → `transfer-service` cập nhật `PENDING_CONFIRMATION → SUCCESS`.
4. **Không gọi debit lại** → không bị trừ tiền lần 2.

**Kết quả cuối cùng (với bug):**
- Ứng dụng chỉ ghi nhận 1 giao dịch thành công.
- Tài khoản khách hàng tại Core bị trừ tiền 2 lần.
- **Đối soát** (**Reconciliation**) cuối ngày bị lệch.

---

## 2. Lập kế hoạch (Planning)

### 2.1 Kiến trúc 2 Project (Two-Project Architecture)

> **Nguyên tắc**: Core banking là hệ thống khác, có database/transaction/lifecycle riêng. Tách ra 2 project để sát thực tế banking.

```
Frontend/Postman
    │
    │  POST /api/v1/transfers
    │  GET  /api/v1/transfers/{externalRef}
    ▼
┌─────────────────────────────────┐
│  transfer-service :8080         │  Schema: TRANSFER_SVC
│  (Hệ thống của mình)           │
│                                 │
│  ├── TransferController         │  POST /api/v1/transfers
│  ├── TransferInquiryController  │  GET  /api/v1/transfers/{externalRef}
│  ├── TransferServiceImpl        │  Orchestrator
│  ├── IdempotencyServiceImpl     │  Buggy: same tx / Fixed: REQUIRES_NEW
│  ├── CoreBankingClient          │  HTTP → localhost:9090
│  ├── InquiryScheduler           │  @Scheduled inquiry
│  └── DB: transfer_request       │
└────────────────┬────────────────┘
                 │
                 │  HTTP (RestClient, timeout 2s)
                 ▼
┌─────────────────────────────────┐
│  mock-core-banking :9090        │  Schema: CORE_BANK
│  (Core Banking giả lập)        │
│                                 │
│  ├── CoreDebitController        │  POST /core/debit
│  ├── CoreInquiryController      │  GET  /core/transactions?externalRef=...
│  ├── CoreBankingServiceImpl     │  Debit + idempotency logic
│  └── DB: core_account,          │
│       core_transaction          │
└─────────────────────────────────┘
```

### 2.2 State Machine — Trạng thái giao dịch phía transfer-service

```
INIT → PROCESSING → SUCCESS
INIT → PROCESSING → PENDING_CONFIRMATION → SUCCESS
INIT → PROCESSING → PENDING_CONFIRMATION → FAILED
INIT → PROCESSING → FAILED
```

| Trạng thái | Ý nghĩa |
|:-----------|:---------|
| `INIT` | Request vừa được nhận, chưa xử lý |
| `PROCESSING` | Đang gọi core banking |
| `PENDING_CONFIRMATION` | Timeout/lỗi kết nối → chưa biết kết quả core |
| `SUCCESS` | Core xác nhận trừ tiền thành công |
| `FAILED` | Core xác nhận trừ tiền thất bại |

> ⛔ **KHÔNG BAO GIỜ** có flow: `TIMEOUT → FAILED → retry debit`. Đây chính là nguyên nhân gây double debit.
>
> `FAILED` chỉ được set khi core trả về kết quả thất bại **rõ ràng về mặt nghiệp vụ** (ví dụ: `insufficient funds`, `account blocked`, `invalid account`). Nếu lỗi là timeout/network/read timeout thì **phải** chuyển sang `PENDING_CONFIRMATION`, không được set `FAILED`.

### 2.3 Cơ chế cập nhật status sau timeout

| Cách | Mô tả | Áp dụng trong lab |
|:-----|:------|:-------------------|
| **Frontend polling** | Frontend gọi `GET /api/v1/transfers/{externalRef}` để check status | ✅ Có |
| **Backend scheduled job** | `@Scheduled` mỗi 30 giây inquiry core cho giao dịch `PENDING_CONFIRMATION` | ✅ Có |
| **Core callback/webhook** | Core gọi ngược về `transfer-service` khi xử lý xong | ⚠️ Nice-to-have |

---

## 3. Giải pháp tiêm lỗi (Solutioning)

> **Nguyên tắc quan trọng**: Trong lab này, `mock-core-banking` được giả lập như hệ thống bên ngoài. `transfer-service` **không được giả định** rằng core sẽ tự normalize dữ liệu thay mình. Nếu `transfer-service` gửi hai `externalRef` khác nhau ở cấp byte/string, core có thể coi đó là hai giao dịch khác nhau. Vì vậy **trách nhiệm normalize business key phải nằm ở `transfer-service`** trước khi gọi core.

### 3.1 Bugs cài cắm trong code (2 bugs + 1 design flaw)

Student tìm thấy bằng **breakpoint** và fix bằng **code change**:

| # | Loại | Bug (trong code transfer-service) | Student tìm thấy gì? | Fix bằng gì? |
|:--|:-----|:----------------------------------|:---------------------|:-------------|
| 1 | **Bug** | **`@Transactional` scope quá rộng** — idempotency save + HTTP call chung 1 transaction | Breakpoint: record idempotency biến mất sau timeout (bị rollback) | Tách idempotency sang `REQUIRES_NEW` |
| 2 | **Bug** | **`buildAppIdempotencyKey()` dùng raw string** — không normalize externalRef trước khi build key và gọi core | Breakpoint: key lần 1 = `"APP_TRANSFER:FT...07 "`, lần 2 = `"APP_TRANSFER:FT...07"` → khác nhau | Tạo Value Object `ExternalReference` với `strip()` |
| 3 | **Design flaw** | **`external_ref` dùng `CHAR(16)`** — gây nhiễu trailing space, làm việc đối chiếu business key dễ sai khi kết hợp với raw string + idempotency key `VARCHAR2` | `DUMP()` trong Oracle: byte `32` (space) ở cuối. Không trực tiếp gây double debit một mình, nhưng làm bug khó phát hiện hơn. | Đổi sang `VARCHAR2(32)` + normalized externalRef |

### 3.2 Điều kiện kích hoạt (Trigger Conditions)

Đây **không phải bugs** — đây là điều kiện để kịch bản xảy ra, giống như điều kiện thực tế trong production:

| Điều kiện | Vai trò | Giải thích |
|:----------|:--------|:-----------|
| **App timeout 2s, Core xử lý 3s** | Kích hoạt timeout | Trong production luôn có timeout. Vấn đề không phải timeout ngắn mà là **cách xử lý** khi timeout xảy ra. |
| **User/Partner retry thủ công** | Kích hoạt double debit | User gửi request lần 2 là hành vi bình thường. Hệ thống phải chặn được, không phải lỗi của user. |
| **externalRef có trailing space** | Kích hoạt key mismatch | Dữ liệu bẩn từ đối tác là chuyện thường. Hệ thống phải normalize được. |

---

## 4. Triển khai (Implementation)

### 4.1 Oracle Schema — 2 Schema riêng biệt

**Setup Oracle Users:**
```sql
-- Tạo user cho transfer-service
CREATE USER transfer_svc IDENTIFIED BY root123;
GRANT CONNECT, RESOURCE, UNLIMITED TABLESPACE TO transfer_svc;

-- Tạo user cho mock-core-banking
CREATE USER core_bank IDENTIFIED BY root123;
GRANT CONNECT, RESOURCE, UNLIMITED TABLESPACE TO core_bank;
```

**Schema TRANSFER_SVC** (transfer-service sở hữu):
```sql
CREATE TABLE transfer_request (
    id               NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    external_ref     CHAR(16) NOT NULL,        -- ⚠️ Design flaw: cố tình dùng CHAR để minh họa trailing-space issue
    idempotency_key  VARCHAR2(128) NOT NULL,
    debit_account_no VARCHAR2(20) NOT NULL,
    amount           NUMBER(19, 2) NOT NULL,
    status           VARCHAR2(30) DEFAULT 'INIT' NOT NULL,
    core_reference   VARCHAR2(64),
    error_code       VARCHAR2(50),
    inquiry_count    NUMBER DEFAULT 0,
    created_at       TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at       TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT uq_transfer_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_transfer_status CHECK (
        status IN ('INIT','PROCESSING','PENDING_CONFIRMATION','SUCCESS','FAILED')
    ),
    CONSTRAINT ck_transfer_amount CHECK (amount > 0)
);
```

**Schema CORE_BANK** (mock-core-banking sở hữu):
```sql
CREATE TABLE core_account (
    id                NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    account_no        VARCHAR2(20) NOT NULL UNIQUE,
    available_balance NUMBER(19, 2) NOT NULL,
    created_at        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT ck_core_balance CHECK (available_balance >= 0)
);

CREATE TABLE core_transaction (
    id                   NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    external_ref         VARCHAR2(32) NOT NULL,
    core_ref             VARCHAR2(64) NOT NULL UNIQUE,
    core_idempotency_key VARCHAR2(128) NOT NULL UNIQUE,
    account_no           VARCHAR2(20) NOT NULL,
    amount               NUMBER(19, 2) NOT NULL,
    status               VARCHAR2(20) DEFAULT 'SUCCESS' NOT NULL,
    created_at           TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT ck_core_tx_amount CHECK (amount > 0)
);

-- Seed data
INSERT INTO core_account (account_no, available_balance) VALUES ('100000001', 10000000);
COMMIT;
```

### 4.2 Request DTO (transfer-service)

```java
@Getter
@Setter
public class TransferRequest {
    @NotBlank
    private String externalRef;

    @NotBlank
    private String debitAccountNo;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    private long coreDelayMillis; // Simulate Core latency
}
```

### 4.3 Buggy TransferService (transfer-service :8080)

```java
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final AppTransferRequestRepository transferRequestRepository;
    private final CoreBankingClient coreBankingClient;

    @Transactional
    public void transfer(TransferRequest request) {
        // ❌ Bug 2: buildIdempotencyKey dùng raw string — không normalize externalRef
        String idempotencyKey = buildAppIdempotencyKey(request.getExternalRef());

        if (transferRequestRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        AppTransferRequest transferRequest = AppTransferRequest.processing(
                request.getExternalRef(),
                idempotencyKey,
                request.getDebitAccountNo(),
                request.getAmount()
        );

        // ❌ Bug 1: Idempotency nằm chung transaction với core call
        transferRequestRepository.save(transferRequest);

        // Trigger condition: App timeout 2s < Core processing 3s
        coreBankingClient.debit(
                request.getExternalRef(),
                request.getDebitAccountNo(),
                request.getAmount(),
                request.getCoreDelayMillis()
        );

        transferRequest.markSuccess();
    }

    private String buildAppIdempotencyKey(String externalRef) {
        // ❌ Bug 2: raw string, không trim/strip trước khi build key
        return "APP_TRANSFER:" + externalRef;
    }
}
```

### 4.4 CoreBankingClient (transfer-service → mock-core-banking)

```java
@Component
public class CoreBankingClient {
    private final RestClient restClient;

    public CoreBankingClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);  // Lab setup: short timeout to reproduce unknown core result
        factory.setReadTimeout(2000);     // Trigger condition: App timeout 2s < Core processing 3s

        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:9090")  // Core Banking port
                .requestFactory(factory)
                .build();
    }

    public void debit(String externalRef, String accountNo, BigDecimal amount, long coreDelayMillis) {
        CoreDebitRequest request = new CoreDebitRequest(externalRef, accountNo, amount, coreDelayMillis);

        restClient.post()
                .uri("/core/debit")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
```

> **Note**: Trong lab hardcode `localhost:9090` và timeout `2000ms` để dễ tái hiện. Production nên cấu hình qua `application.yml` / `@ConfigurationProperties`. Field `coreDelayMillis` chỉ phục vụ lab để giả lập core xử lý chậm — production không nên cho client truyền tham số này.

### 4.5 Mock Core Banking Service (mock-core-banking :9090)

> **Lưu ý**: Trong lab này, `mock-core-banking` hoạt động như một external system mà `transfer-service` không kiểm soát. Core dùng `externalRef` nguyên bản theo contract giả lập của lab. `transfer-service` không được phụ thuộc vào việc core normalize thay mình.
>
> Trong production, core có thể có cơ chế idempotency riêng, nhưng `transfer-service` vẫn không được phụ thuộc vào việc core normalize thay mình.

```java
@Service
@RequiredArgsConstructor
public class CoreBankingServiceImpl implements CoreBankingService {
    private final CoreAccountRepository coreAccountRepository;
    private final CoreTransactionRepository coreTransactionRepository;

    @Transactional
    public void debit(CoreDebitRequest request) {
        // Giả lập độ trễ xử lý của Core Banking (configurable qua request)
        sleep(request.getDelayMillis());

        // Core dùng externalRef nguyên bản theo contract giả lập
        // Transfer-service không được phụ thuộc vào core normalize thay mình
        String coreIdempotencyKey = buildCoreIdempotencyKey(request.getExternalRef());

        if (coreTransactionRepository.existsByCoreIdempotencyKey(coreIdempotencyKey)) {
            return; // Idempotency check — chặn duplicate đúng cách
        }

        int updatedRows = coreAccountRepository.debit(request.getAccountNo(), request.getAmount());
        if (updatedRows != 1) {
            throw new IllegalStateException("Core account debit failed");
        }

        String coreRef = generateCoreRef();
        CoreTransaction tx = CoreTransaction.of(
                request.getExternalRef(), coreRef, coreIdempotencyKey,
                request.getAccountNo(), request.getAmount()
        );
        coreTransactionRepository.save(tx);
    }

    // Core dùng externalRef nguyên bản theo contract giả lập
    private String buildCoreIdempotencyKey(String externalRef) {
        return "CORE_DEBIT:" + externalRef;
    }
}
```

### 4.6 Core Inquiry Controller (mock-core-banking :9090)

> **Lưu ý**: Core nhận gì lưu nấy, inquiry cũng dùng externalRef nguyên bản. Trách nhiệm normalize nằm ở `transfer-service` — sau khi fix, `transfer-service` sẽ luôn inquiry bằng externalRef đã normalize. Dùng `@RequestParam` thay vì `@PathVariable` để tránh URL encoding vấn đề với trailing space.

```java
@RestController
@RequestMapping("/core/transactions")
public class CoreInquiryController {
    private final CoreTransactionRepository coreTransactionRepository;

    // Core dùng externalRef nguyên bản — nhất quán với debit endpoint
    // Transfer-service phải normalize trước khi gọi
    // Dùng @RequestParam vì path variable có thể bị encode/trim trailing space
    @GetMapping
    public ResponseEntity<CoreInquiryResponse> inquiry(@RequestParam String externalRef) {
        return coreTransactionRepository.findByExternalRef(externalRef)
                .map(tx -> ResponseEntity.ok(CoreInquiryResponse.from(tx)))
                .orElse(ResponseEntity.ok(CoreInquiryResponse.notFound(externalRef)));
    }
}
```

### 4.7 API Contracts

**POST /api/v1/transfers** — Timeout response cho frontend:

HTTP `202 Accepted` (request đã được tiếp nhận, kết quả cuối cùng đang chờ xác nhận)

> **Lưu ý**: `success: true` nghĩa là **API request** đã được hệ thống tiếp nhận hợp lệ, không có nghĩa money movement đã thành công. Kết quả nghiệp vụ thật nằm ở field `data.status`.

```json
{
    "success": true,
    "data": {
        "externalRef": "FT202605040007",
        "status": "PENDING_CONFIRMATION",
        "message": "Giao dịch đang được xác nhận với core banking"
    }
}
```

**GET /api/v1/transfers/{externalRef}** — Frontend polling:
```json
{
    "success": true,
    "data": {
        "externalRef": "FT202605040007",
        "status": "PENDING_CONFIRMATION",
        "inquiryCount": 2,
        "message": "Giao dịch đang chờ xác nhận từ core banking"
    }
}
```

**GET /core/transactions?externalRef=FT202605040007** — Core inquiry:
```json
{
    "externalRef": "FT202605040007",
    "coreRef": "CORE202605040001",
    "status": "SUCCESS",
    "amount": 1000000
}
```

---

## 5. Cách tái hiện (Reproduction)

### Request 1: Gây lỗi Timeout

Gửi request với khoảng trắng ở cuối `externalRef` và độ trễ 3 giây.
```json
{
    "externalRef": "FT202605040007 ",
    "debitAccountNo": "100000001",
    "amount": 1000000,
    "coreDelayMillis": 3000
}
```

**Buggy actual:**
- Client nhận timeout/500
- `transfer_request` bị rollback (không có record)
- Core đã debit thành công (balance giảm)

**Fixed expected:**
- Client nhận `202 Accepted` với `status = PENDING_CONFIRMATION`
- Idempotency/reservation record vẫn còn nhờ `REQUIRES_NEW`
- Business transfer status = `PENDING_CONFIRMATION` (bảng `transfer_request` đóng vai trò idempotency + business status record, persist độc lập trước khi gọi core)

> **Note**: Trong lab gộp `transfer_request` làm idempotency + business status record để đơn giản. Production có thể tách riêng bảng `idempotency_record` để quản lý reservation, TTL, retry count và audit rõ hơn.
- Core có thể đã debit — sẽ xác nhận bằng inquiry

### Request 2: Đối tác Retry — Blind Retry ở bản lỗi

Gửi lại mã tham chiếu đã được cắt trắng.
```json
{
    "externalRef": "FT202605040007",
    "debitAccountNo": "100000001",
    "amount": 1000000,
    "coreDelayMillis": 0
}
```

**Buggy actual:**
- Client nhận HTTP 200. App lưu `SUCCESS`. Core bị trừ tiền thêm lần nữa → **double debit**.

**Fixed expected:**
- Idempotency chặn request trùng → trả lại trạng thái giao dịch cũ (`PENDING_CONFIRMATION` hoặc `SUCCESS`, tùy thời điểm inquiry)
- Không gọi debit lại → Core chỉ bị trừ tiền **1 lần**.

### Request 3: Frontend polling / Transfer inquiry (sau khi fix)

```bash
GET http://localhost:8080/api/v1/transfers/FT202605040007
```

- **Kỳ vọng** (sau khi fix):
```json
{
    "success": true,
    "data": {
        "externalRef": "FT202605040007",
        "status": "PENDING_CONFIRMATION",
        "message": "Giao dịch đang chờ xác nhận từ core banking"
    }
}
```

---

## 6. Kiểm tra lỗi (Verification)

Truy vấn số dư tài khoản tại **schema CORE_BANK**:
```sql
SELECT account_no, available_balance FROM core_bank.core_account WHERE account_no = '100000001';
-- Kết quả sai: 8,000,000 (Bị trừ 2 lần)
-- Kết quả đúng: 9,000,000
```

Kiểm tra nhật ký trừ tiền tại Core và chứng minh trailing space bằng `DUMP()`:
```sql
SELECT id,
       '[' || external_ref || ']' AS visible_ref,
       DUMP(external_ref) AS dumped_ref,
       core_idempotency_key,
       DUMP(core_idempotency_key) AS dumped_key,
       core_ref,
       amount
FROM core_bank.core_transaction;
-- 2 dòng với ref khác nhau về khoảng trắng
-- DUMP() cho thấy byte 32 (space) ở cuối externalRef lần 1
```

Kiểm tra trạng thái giao dịch tại **schema TRANSFER_SVC**:
```sql
SELECT id, external_ref, status, inquiry_count FROM transfer_svc.transfer_request;
-- Buggy: 1 row, status = SUCCESS (chỉ thấy request 2)
-- Fixed: 1 row, status = PENDING_CONFIRMATION → sau inquiry → SUCCESS
```

---

## 7. Điểm đặt Breakpoint (Conditional Breakpoints)

1. **TransferServiceImpl.transfer** (transfer-service): Đặt tại dòng build `idempotencyKey`.
   - Condition: `request.getExternalRef().trim().equals("FT202605040007")`
   - Inspect: `request.getExternalRef().length()` → 15 (có space) vs 14 (không space)

2. **CoreBankingServiceImpl.debit** (mock-core-banking): Đặt sau dòng `sleep()`.
   - Mục tiêu: Quan sát thread Core **vẫn chạy** sau khi Client đã Timeout.
   - Chứng minh: **2 hệ thống, 2 process, 2 lifecycle riêng biệt.**

3. **InquiryScheduler** (transfer-service — sau khi fix): Đặt tại dòng inquiry core.
   - Mục tiêu: Quan sát scheduled job tự động cập nhật `PENDING_CONFIRMATION → SUCCESS`.

---

## 8. Nguyên nhân gốc rễ (Root Cause)

Trong phạm vi lab, `mock-core-banking` hoạt động như một external system mà `transfer-service` không kiểm soát. Vì vậy root cause được quy về `transfer-service`: không normalize, không giữ idempotency đúng, và blind retry.

**Root cause trực tiếp** (gây double debit):

1. **Timeout** bị hiểu lầm là giao dịch **thất bại** → thực tế chỉ là "chưa biết kết quả".
2. Bản ghi Idempotency bị **rollback** cùng transaction chính → mất dấu vết.
3. Khóa Idempotency build từ raw `externalRef` chưa **normalize** → `transfer-service` gửi 2 giá trị khác nhau sang core.
4. **Blind retry** gọi debit lại thay vì inquiry business status.

**Design gap** (làm hệ thống không có cách xử lý chuẩn sau timeout):

5. Thiếu cơ chế **inquiry/status reconciliation** — không có endpoint/job phía mình để query trạng thái business từ core sau timeout.

---

## 9. Giải pháp chuẩn Banking (Banking Fix)

> **Nguyên tắc**: Tất cả fix nằm ở phía **transfer-service**. Core banking không cần sửa — đó là hệ thống của bên khác.

### 9.1 Fix 2 bugs + 1 design flaw

| Vấn đề | Loại | Fix | Chi tiết |
|:-------|:-----|:----|:---------|
| `@Transactional` scope quá rộng | **Bug** | **Tách idempotency sang `REQUIRES_NEW`** | Tạo `IdempotencyService` với `@Transactional(propagation = REQUIRES_NEW)`. Dù transfer() rollback do timeout, record idempotency vẫn tồn tại. |
| `buildAppIdempotencyKey()` raw string | **Bug** | **Value Object `ExternalReference`** | `ExternalReference.from(rawValue)` tự động `strip()` ngay khi nhận dữ liệu. Mọi nơi dùng externalRef đều qua Value Object. |
| `external_ref` dùng `CHAR(16)` | **Design flaw** | **Đổi sang `VARCHAR2(32)`** | Không trực tiếp gây double debit nhưng gây nhiễu. Migration: `ALTER TABLE` đổi kiểu. Chỉ dùng `CHAR` khi dữ liệu thật sự fixed-length. |

### 9.2 Fixed Flow (sau khi fix)

```
1. Normalize externalRef bằng Value Object
2. Reserve idempotency/business record bằng REQUIRES_NEW với status PROCESSING
3. Sau khi reserve commit thành công, mới gọi core debit
4. Nếu core timeout → catch timeout exception (ResourceAccessException)
5. Gọi `IdempotencyService.markPendingConfirmation(...)` với REQUIRES_NEW
   để đảm bảo trạng thái không bị rollback theo transaction chính
6. Trả HTTP `202 Accepted` với business status = `PENDING_CONFIRMATION`
   (không để timeout exception throw ra thành 500)
7. Retry sau đó chỉ đọc lại record cũ hoặc inquiry core, không gọi debit lại
```

### 9.3 Cải thiện kiến trúc (Architecture Improvements)

Đây không phải fix bug mà là **thiết kế đúng** cho hệ thống banking:

| Cải thiện | Chi tiết |
|:----------|:---------|
| **Timeout → `PENDING_CONFIRMATION`** | Khi timeout, chuyển status sang `PENDING_CONFIRMATION` thay vì throw 500. Trả status này cho frontend. Trong banking: `Timeout ≠ Failed`. |
| **Thêm cơ chế inquiry** | Tạo endpoint `GET /api/v1/transfers/{externalRef}` cho frontend polling + `@Scheduled` job tự động query core cho các giao dịch `PENDING_CONFIRMATION`. |

---

## 10. Bài kiểm tra hồi quy (Regression Test)

Sử dụng **JUnit 5**, **AssertJ** và **Testcontainers** để kiểm tra kịch bản:
- Gửi request timeout → Xác nhận status = `PENDING_CONFIRMATION`.
- Gửi lại request cùng `externalRef` → Bị chặn bởi idempotency (không debit lại).
- Inquiry core → Xác nhận `PENDING_CONFIRMATION → SUCCESS`.
- Xác nhận Core chỉ bị trừ tiền **1 lần**.

---

## 11. Kế hoạch Commit (Level-by-Level Commit)

```bash
# mock-core-banking project (port 9090)
feat(core-bank): init mock core banking project on port 9090
feat(core-bank): add debit endpoint with delayed processing
feat(core-bank): add transaction inquiry endpoint

# transfer-service project (port 8080)
refactor(transfer): update CoreBankingClient to target port 9090
feat(transfer): add PENDING_CONFIRMATION status and state machine
feat(transfer): add transfer inquiry endpoint for frontend polling
feat(transfer): add scheduled job for pending confirmation inquiry

# bug fixes (2 bugs + 1 design flaw — tất cả ở transfer-service)
fix(transfer): normalize external reference with Value Object
fix(transfer): persist idempotency reservation in REQUIRES_NEW transaction
fix(transfer): change CHAR(16) to VARCHAR2(32) for external_ref

# architecture improvements
feat(transfer): treat timeout as PENDING_CONFIRMATION not FAILED

# documentation
docs(debug-lab): update ghost double debit for 2-project architecture
```

> **Câu chốt**: Bug không nằm ở việc core chậm hay user retry. Bug nằm ở `transfer-service`: xử lý timeout sai, lưu idempotency sai transaction, build idempotency key từ `externalRef` chưa normalize, và blind retry debit thay vì inquiry trạng thái giao dịch.

---

## 12. Chi tiết triển khai (Implementation Details)

> Section này chứa đủ config, entity, repository, DTO, controller và service để tái tạo lab từ đầu chỉ từ tài liệu này.

### 12.1 Dependencies (pom.xml — cả 2 project giống nhau)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.6</version>
</parent>

<dependencies>
    <!-- Web + REST -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- Oracle JDBC -->
    <dependency>
        <groupId>com.oracle.database.jdbc</groupId>
        <artifactId>ojdbc17</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### 12.2 application.yml — transfer-service (:8080)

```yaml
server:
  port: 8080

spring:
  application:
    name: transfer-service
  datasource:
    url: jdbc:oracle:thin:@localhost:1521/FREEPDB1
    username: transfer_svc
    password: root123
    driver-class-name: oracle.jdbc.OracleDriver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true

# Lab config
core-banking:
  base-url: http://localhost:9090
  connect-timeout: 2000
  read-timeout: 2000

# Inquiry scheduler
inquiry:
  scheduler:
    enabled: true
    fixed-delay: 30000  # 30 seconds
```

### 12.3 application.yml — mock-core-banking (:9090)

```yaml
server:
  port: 9090

spring:
  application:
    name: mock-core-banking
  datasource:
    url: jdbc:oracle:thin:@localhost:1521/FREEPDB1
    username: core_bank
    password: root123
    driver-class-name: oracle.jdbc.OracleDriver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true
```

### 12.4 Entity — AppTransferRequest (transfer-service)

```java
@Entity
@Table(name = "transfer_request")
@Getter
@Setter
public class AppTransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_ref", nullable = false, length = 16, columnDefinition = "CHAR(16)")
    private String externalRef;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "debit_account_no", nullable = false, length = 20)
    private String debitAccountNo;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "INIT";

    @Column(name = "core_reference", length = 64)
    private String coreReference;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "inquiry_count")
    private int inquiryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Timestamp.from(Instant.now());
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Timestamp.from(Instant.now());
    }

    public static AppTransferRequest processing(String externalRef, String idempotencyKey,
                                                 String debitAccountNo, BigDecimal amount) {
        AppTransferRequest req = new AppTransferRequest();
        req.setExternalRef(externalRef);
        req.setIdempotencyKey(idempotencyKey);
        req.setDebitAccountNo(debitAccountNo);
        req.setAmount(amount);
        req.setStatus("PROCESSING");
        return req;
    }

    public void markSuccess() {
        this.status = "SUCCESS";
    }

    public void markPendingConfirmation() {
        this.status = "PENDING_CONFIRMATION";
    }

    public void markFailed(String errorCode) {
        this.status = "FAILED";
        this.errorCode = errorCode;
    }
}
```

### 12.5 Entity — CoreAccount (mock-core-banking)

```java
@Entity
@Table(name = "core_account")
@Getter
@Setter
public class CoreAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_no", nullable = false, unique = true, length = 20)
    private String accountNo;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;
}
```

### 12.6 Entity — CoreTransaction (mock-core-banking)

```java
@Entity
@Table(name = "core_transaction")
@Getter
@Setter
public class CoreTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_ref", nullable = false, length = 32)
    private String externalRef;

    @Column(name = "core_ref", nullable = false, unique = true, length = 64)
    private String coreRef;

    @Column(name = "core_idempotency_key", nullable = false, unique = true, length = 128)
    private String coreIdempotencyKey;

    @Column(name = "account_no", nullable = false, length = 20)
    private String accountNo;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "SUCCESS";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Timestamp.from(Instant.now());
    }

    public static CoreTransaction of(String externalRef, String coreRef, String coreIdempotencyKey,
                                      String accountNo, BigDecimal amount) {
        CoreTransaction tx = new CoreTransaction();
        tx.setExternalRef(externalRef);
        tx.setCoreRef(coreRef);
        tx.setCoreIdempotencyKey(coreIdempotencyKey);
        tx.setAccountNo(accountNo);
        tx.setAmount(amount);
        tx.setStatus("SUCCESS");
        return tx;
    }
}
```

### 12.7 Repository — transfer-service

```java
public interface AppTransferRequestRepository extends JpaRepository<AppTransferRequest, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<AppTransferRequest> findByExternalRef(String externalRef);

    List<AppTransferRequest> findByStatus(String status);
}
```

### 12.8 Repository — mock-core-banking

```java
public interface CoreAccountRepository extends JpaRepository<CoreAccount, Long> {

    @Modifying
    @Query("UPDATE CoreAccount a SET a.availableBalance = a.availableBalance - :amount, " +
           "a.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE a.accountNo = :accountNo AND a.availableBalance >= :amount")
    int debit(@Param("accountNo") String accountNo, @Param("amount") BigDecimal amount);
}
```

```java
public interface CoreTransactionRepository extends JpaRepository<CoreTransaction, Long> {

    boolean existsByCoreIdempotencyKey(String coreIdempotencyKey);

    Optional<CoreTransaction> findByExternalRef(String externalRef);
}
```

### 12.9 DTO — CoreDebitRequest (transfer-service gửi, mock-core-banking nhận)

```java
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CoreDebitRequest {
    private String externalRef;
    private String accountNo;
    private BigDecimal amount;
    private long delayMillis;  // Lab only: simulate core processing delay
}
```

### 12.10 DTO — CoreInquiryResponse (mock-core-banking trả)

```java
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CoreInquiryResponse {
    private String externalRef;
    private String coreRef;
    private String status;
    private BigDecimal amount;
    private boolean found;

    public static CoreInquiryResponse from(CoreTransaction tx) {
        CoreInquiryResponse resp = new CoreInquiryResponse();
        resp.setExternalRef(tx.getExternalRef());
        resp.setCoreRef(tx.getCoreRef());
        resp.setStatus(tx.getStatus());
        resp.setAmount(tx.getAmount());
        resp.setFound(true);
        return resp;
    }

    public static CoreInquiryResponse notFound(String externalRef) {
        CoreInquiryResponse resp = new CoreInquiryResponse();
        resp.setExternalRef(externalRef);
        resp.setStatus("NOT_FOUND");
        resp.setFound(false);
        return resp;
    }
}
```

### 12.11 Controller — TransferController (transfer-service :8080)

```java
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<?> transfer(@Valid @RequestBody TransferRequest request) {
        transferService.transfer(request);
        return ResponseEntity.ok(Map.of("success", true, "message", "Transfer completed"));
    }
}
```

> **Note**: Đây là buggy version. Sau khi fix, controller phải catch timeout exception và trả `202 Accepted` với `PENDING_CONFIRMATION`.

### 12.12 Controller — TransferInquiryController (transfer-service :8080, sau khi fix)

```java
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferInquiryController {

    private final AppTransferRequestRepository transferRequestRepository;

    @GetMapping("/{externalRef}")
    public ResponseEntity<?> inquiry(@PathVariable String externalRef) {
        return transferRequestRepository.findByExternalRef(externalRef.strip())
                .map(tx -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "data", Map.of(
                                "externalRef", tx.getExternalRef().strip(),
                                "status", tx.getStatus(),
                                "inquiryCount", tx.getInquiryCount(),
                                "message", buildMessage(tx.getStatus())
                        )
                )))
                .orElse(ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Transaction not found"
                )));
    }

    private String buildMessage(String status) {
        return switch (status) {
            case "PENDING_CONFIRMATION" -> "Giao dịch đang chờ xác nhận từ core banking";
            case "SUCCESS" -> "Giao dịch thành công";
            case "FAILED" -> "Giao dịch thất bại";
            default -> "Đang xử lý";
        };
    }
}
```

### 12.13 Controller — CoreDebitController (mock-core-banking :9090)

```java
@RestController
@RequestMapping("/core")
@RequiredArgsConstructor
public class CoreDebitController {

    private final CoreBankingService coreBankingService;

    @PostMapping("/debit")
    public ResponseEntity<?> debit(@Valid @RequestBody CoreDebitRequest request) {
        coreBankingService.debit(request);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Debit completed"));
    }
}
```

### 12.14 Value Object — ExternalReference (transfer-service, sau khi fix)

```java
public class ExternalReference {

    private final String value;

    private ExternalReference(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("externalRef must not be blank");
        }
        this.value = value.strip();
    }

    public static ExternalReference from(String rawValue) {
        return new ExternalReference(rawValue);
    }

    public String value() {
        return this.value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExternalReference that = (ExternalReference) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
```

### 12.15 IdempotencyService (transfer-service, sau khi fix)

```java
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final AppTransferRequestRepository transferRequestRepository;

    /**
     * Reserve idempotency record trong transaction độc lập (REQUIRES_NEW).
     * Dù transaction chính rollback do timeout, record này vẫn tồn tại.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AppTransferRequest reserve(String externalRef, String idempotencyKey,
                                       String debitAccountNo, BigDecimal amount) {
        if (transferRequestRepository.existsByIdempotencyKey(idempotencyKey)) {
            return null; // Already reserved — idempotency check
        }

        AppTransferRequest request = AppTransferRequest.processing(
                externalRef, idempotencyKey, debitAccountNo, amount
        );
        return transferRequestRepository.save(request);
    }

    /**
     * Cập nhật status thành PENDING_CONFIRMATION trong transaction độc lập.
     * Đảm bảo status không bị rollback theo transaction chính khi timeout.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPendingConfirmation(Long transferRequestId) {
        transferRequestRepository.findById(transferRequestId).ifPresent(req -> {
            req.markPendingConfirmation();
            transferRequestRepository.save(req);
        });
    }

    /**
     * Cập nhật status sau khi inquiry core thành công.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFromCoreInquiry(Long transferRequestId, String coreStatus, String coreRef) {
        transferRequestRepository.findById(transferRequestId).ifPresent(req -> {
            if ("SUCCESS".equals(coreStatus)) {
                req.markSuccess();
                req.setCoreReference(coreRef);
            } else if ("FAILED".equals(coreStatus)) {
                req.markFailed("CORE_REJECTED");
            }
            req.setInquiryCount(req.getInquiryCount() + 1);
            transferRequestRepository.save(req);
        });
    }
}
```

### 12.16 InquiryScheduler (transfer-service, sau khi fix)

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class InquiryScheduler {

    private final AppTransferRequestRepository transferRequestRepository;
    private final IdempotencyService idempotencyService;
    private final RestClient coreInquiryClient;

    public InquiryScheduler(AppTransferRequestRepository transferRequestRepository,
                             IdempotencyService idempotencyService) {
        this.transferRequestRepository = transferRequestRepository;
        this.idempotencyService = idempotencyService;
        this.coreInquiryClient = RestClient.builder()
                .baseUrl("http://localhost:9090")
                .build();
    }

    @Scheduled(fixedDelayString = "${inquiry.scheduler.fixed-delay:30000}")
    public void inquirePendingTransactions() {
        List<AppTransferRequest> pendingList =
                transferRequestRepository.findByStatus("PENDING_CONFIRMATION");

        for (AppTransferRequest req : pendingList) {
            try {
                String normalizedRef = req.getExternalRef().strip();

                CoreInquiryResponse response = coreInquiryClient.get()
                        .uri("/core/transactions?externalRef={ref}", normalizedRef)
                        .retrieve()
                        .body(CoreInquiryResponse.class);

                if (response != null && response.isFound()) {
                    idempotencyService.updateFromCoreInquiry(
                            req.getId(), response.getStatus(), response.getCoreRef()
                    );
                    log.info("Inquiry completed: externalRef={}, coreStatus={}",
                            normalizedRef, response.getStatus());
                }
            } catch (Exception e) {
                log.warn("Inquiry failed for externalRef={}: {}",
                        req.getExternalRef(), e.getMessage());
            }
        }
    }
}
```

> **Note**: Trong lab hardcode `localhost:9090`. Production nên inject `coreInquiryClient` từ `@Bean` configuration.

### 12.17 Utility — CoreBankingServiceImpl helper

```java
// Trong CoreBankingServiceImpl, method generateCoreRef():
private String generateCoreRef() {
    return "CORE" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            + String.format("%04d", new Random().nextInt(10000));
}

// Trong CoreBankingServiceImpl, method sleep():
private void sleep(long millis) {
    if (millis > 0) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 12.18 Package Structure

```
transfer-service/
├── src/main/java/com/smartosc/transfer/
│   ├── TransferServiceApplication.java
│   ├── controller/
│   │   ├── TransferController.java
│   │   └── TransferInquiryController.java
│   ├── service/
│   │   ├── TransferService.java              (interface)
│   │   ├── TransferServiceImpl.java          (buggy orchestrator)
│   │   └── IdempotencyService.java           (REQUIRES_NEW — after fix)
│   ├── client/
│   │   └── CoreBankingClient.java
│   ├── domain/
│   │   ├── AppTransferRequest.java           (entity)
│   │   └── ExternalReference.java            (value object — after fix)
│   ├── repository/
│   │   └── AppTransferRequestRepository.java
│   ├── scheduler/
│   │   └── InquiryScheduler.java             (after fix)
│   └── dto/
│       ├── TransferRequest.java
│       └── CoreInquiryResponse.java          (shared DTO)
└── src/main/resources/
    └── application.yml

mock-core-banking/
├── src/main/java/com/smartosc/corebank/
│   ├── MockCoreBankingApplication.java
│   ├── controller/
│   │   ├── CoreDebitController.java
│   │   └── CoreInquiryController.java
│   ├── service/
│   │   ├── CoreBankingService.java           (interface)
│   │   └── CoreBankingServiceImpl.java
│   ├── domain/
│   │   ├── CoreAccount.java                  (entity)
│   │   └── CoreTransaction.java              (entity)
│   ├── repository/
│   │   ├── CoreAccountRepository.java
│   │   └── CoreTransactionRepository.java
│   └── dto/
│       ├── CoreDebitRequest.java
│       └── CoreInquiryResponse.java
└── src/main/resources/
    └── application.yml
```

---
*Tài liệu này thuộc series Java Core Training - SmartOSC.*
