# 👻 Debug Presentation: "The Ghost Transaction"

> **Khi @Transactional "nuốt" lỗi — Tiền bốc hơi mà không ai biết**
> Áp dụng thực tế trong Core Banking: Chuyển khoản liên ngân hàng

---

## 📋 Mục lục

1. [Bối cảnh nghiệp vụ Banking](#1-bối-cảnh-nghiệp-vụ-banking)
2. [Chuẩn bị Project Demo](#2-chuẩn-bị-project-demo)
3. [Bước 1: Xây dựng hệ thống chuyển tiền](#3-bước-1-xây-dựng-hệ-thống-chuyển-tiền)
4. [Bước 2: Tái hiện Bug — Tiền "bốc hơi"](#4-bước-2-tái-hiện-bug--tiền-bốc-hơi)
5. [Bước 3: Quá trình điều tra](#5-bước-3-quá-trình-điều-tra)
6. [Bước 4: Root Cause — Spring Transaction Internals](#6-bước-4-root-cause--spring-transaction-internals)
7. [Bước 5: Giải pháp & Best Practice](#7-bước-5-giải-pháp--best-practice)
8. [BONUS: Combo với Self-Invocation Bug](#8-bonus-combo-với-self-invocation-bug)
9. [Key Takeaways](#9-key-takeaways)

---

## 1. Bối cảnh nghiệp vụ Banking

### Quy trình chuyển khoản liên ngân hàng (đơn giản hóa)

```
Khách hàng A (Ngân hàng X) → Chuyển 5,000,000 VND → Khách hàng B (Ngân hàng Y)

Bước 1: Trừ tiền tài khoản A          → UPDATE account SET balance = balance - 5000000
Bước 2: Gọi API Ngân hàng Y để cộng B → POST /api/partner-bank/credit
Bước 3: Ghi log giao dịch             → INSERT INTO transaction_log(...)
```

### Yêu cầu bắt buộc

- Nếu **bất kỳ bước nào thất bại**, toàn bộ giao dịch phải **ROLLBACK**.
- Tiền không được "biến mất" — Đây là quy định pháp luật (SLA của Ngân hàng Nhà nước).

---

## 2. Chuẩn bị Project Demo

### Tech Stack

- Java 21
- Spring Boot 4.0.6 (Spring Framework 7)
- Spring Data JPA + Oracle Database
- Lombok

### Dependencies (pom.xml)

```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>4.0.6</spring-boot.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <!-- Oracle JDBC Driver -->
    <dependency>
        <groupId>com.oracle.database.jdbc</groupId>
        <artifactId>ojdbc11</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:bankdb
    username: bank_user
    password: bank_password
    driver-class-name: oracle.jdbc.OracleDriver
  jpa:
    database-platform: org.hibernate.dialect.OracleDialect
    hibernate:
      ddl-auto: update
    show-sql: true           # ← Quan trọng: show SQL để demo
```

---

## 3. Bước 1: Xây dựng hệ thống chuyển tiền

### 3.1 Entity — Tài khoản ngân hàng

```java
@Entity
@Table(name = "tbl_account")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tbl_account_id_gen")
    @SequenceGenerator(name = "tbl_account_id_gen", sequenceName = "TBL_ACCOUNT_SEQ", allocationSize = 1)
    private Long id;

    @Column(name = "account_number", unique = true, nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;        // ← Dùng BigDecimal, KHÔNG dùng double!

    @Column(nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    private String status;             // ACTIVE | BLOCKED

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

### 3.2 Repository

```java
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);
}
```

### 3.3 Custom Checked Exception (Banking Style)

```java
// ⚠️ Đây là CHECKED Exception (extends Exception, KHÔNG phải RuntimeException)
public class BankTransferException extends Exception {

    public BankTransferException(String message) {
        super(message);
    }

    public BankTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 3.4 Service giả lập API Ngân hàng đối tác

```java
@Service
public class PartnerBankApiService {

    /**
     * Giả lập gọi API ngân hàng đối tác để cộng tiền cho người nhận.
     * Trong thực tế, đây sẽ là một HTTP call (RestTemplate/WebClient).
     *
     * @throws BankTransferException (checked) khi API ngân hàng đối tác lỗi
     */
    public void creditToPartnerBank(String toAccountNumber, BigDecimal amount)
            throws BankTransferException {

        // Giả lập: API đối tác trả về lỗi 50% thời gian (để demo)
        if (toAccountNumber.startsWith("PARTNER")) {
            throw new BankTransferException(
                "Ngân hàng đối tác từ chối giao dịch: Tài khoản " + toAccountNumber + " không tồn tại"
            );
        }
    }
}
```

### 3.5 Transfer Service — Code "trông đúng" nhưng có BUG! 🐛

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final AccountRepository accountRepository;
    private final PartnerBankApiService partnerBankApiService;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ❌ BUG Ẩn NÁU Ở ĐÂY — Bạn thấy nó không?
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Transactional
    public void transferToPartnerBank(String fromAccountNumber,
                                       String toPartnerAccountNumber,
                                       BigDecimal amount) throws BankTransferException {

        // Bước 1: Lấy tài khoản nguồn
        Account fromAccount = accountRepository
                .findByAccountNumber(fromAccountNumber)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        // Bước 2: Kiểm tra số dư
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Số dư không đủ");
        }

        // Bước 3: TRỪ TIỀN người gửi
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        accountRepository.save(fromAccount);

        log.info("✅ Đã trừ {} từ tài khoản {}", amount, fromAccountNumber);

        // Bước 4: Gọi API ngân hàng đối tác để CỘNG TIỀN cho người nhận
        // ← BankTransferException là CHECKED Exception!
        partnerBankApiService.creditToPartnerBank(toPartnerAccountNumber, amount);

        log.info("✅ Đã cộng {} vào tài khoản đối tác {}", amount, toPartnerAccountNumber);
    }
}
```

### 3.6 Controller

```java
@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final AccountRepository accountRepository;

    // API chuyển tiền
    @PostMapping("/partner")
    public ResponseEntity<?> transferToPartner(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {
        try {
            transferService.transferToPartnerBank(from, to, amount);
            return ResponseEntity.ok("Chuyển tiền thành công!");
        } catch (BankTransferException e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    // API kiểm tra số dư (để verify sau khi chuyển)
    @GetMapping("/balance/{accountNumber}")
    public ResponseEntity<?> getBalance(@PathVariable String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .map(acc -> ResponseEntity.ok(
                    Map.of("account", acc.getAccountNumber(),
                           "owner", acc.getOwnerName(),
                           "balance", acc.getBalance())))
                .orElse(ResponseEntity.notFound().build());
    }
}
```

### 3.7 Dữ liệu khởi tạo (SQL — đã seed sẵn trong V2)

> Dữ liệu được khởi tạo qua file `V2__insert_sample_data.sql`, KHÔNG dùng `CommandLineRunner`.

```sql
-- Tài khoản người gửi — 10 triệu VND
INSERT INTO tbl_account (account_number, owner_name, balance, status)
VALUES ('ACC001', 'Nguyen Van Admin', 10000000.00, 'ACTIVE');

-- Tài khoản nội bộ — 5 triệu VND
INSERT INTO tbl_account (account_number, owner_name, balance, status)
VALUES ('ACC002', 'Tran Thi Binh', 5000000.00, 'ACTIVE');

-- Tài khoản nội bộ — 8 triệu VND
INSERT INTO tbl_account (account_number, owner_name, balance, status)
VALUES ('ACC003', 'Le Minh Cuong', 8000000.00, 'ACTIVE');
```

| Tài khoản | Chủ sở hữu       | Số dư ban đầu  | Trạng thái |
|-----------|-------------------|----------------|------------|
| ACC001    | Nguyen Van Admin  | 10,000,000 VND | ACTIVE     |
| ACC002    | Tran Thi Binh     | 5,000,000 VND  | ACTIVE     |
| ACC003    | Le Minh Cuong     | 8,000,000 VND  | ACTIVE     |

---

## 4. Bước 2: Tái hiện Bug — Tiền "bốc hơi"

### Kịch bản demo (dùng curl hoặc Postman)

#### Bước 4.1: Kiểm tra số dư ban đầu

```bash
curl http://localhost:8080/api/transfer/balance/ACC001
```
```json
{
  "account": "ACC001",
  "owner": "Nguyen Van Admin",
  "balance": 10000000
}
```

#### Bước 4.2: Chuyển tiền tới ngân hàng đối tác (SẼ LỖI)

```bash
curl -X POST "http://localhost:8080/api/transfer/partner?from=ACC001&to=PARTNER999&amount=5000000"
```
```json
{
  "error": "Lỗi: Ngân hàng đối tác từ chối giao dịch: Tài khoản PARTNER999 không tồn tại"
}
```

> API trả về lỗi — OK, đúng rồi. **Nhưng hãy kiểm tra số dư...**

#### Bước 4.3: Kiểm tra số dư — 💀 TIỀN ĐÃ BỊ TRỪ!

```bash
curl http://localhost:8080/api/transfer/balance/ACC001
```
```json
{
  "account": "ACC001",
  "owner": "Nguyen Van Admin",
  "balance": 5000000          ← 😱 MẤT 5 TRIỆU! Không rollback!
}
```

### Slide Demo (Tóm tắt visual)

```
╔══════════════════════════════════════════════════════════════╗
║                    KẾT QUẢ THỰC TẾ                          ║
║                                                              ║
║  Tài khoản ACC001 (Nguyen Van Admin):                          ║
║    Ban đầu:    10,000,000 VND                                ║
║    Sau lỗi:     5,000,000 VND  ← 💀 TIỀN MẤT!              ║
║                                                              ║
║  Tài khoản đối tác PARTNER999:                               ║
║    Không nhận được tiền (API lỗi)                            ║
║                                                              ║
║  ➜ 5,000,000 VND "BỐC HƠI" khỏi hệ thống!                 ║
║  ➜ Không có Error Log, không có Alert!                       ║
║  ➜ Chỉ phát hiện khi khách hàng khiếu nại                   ║
╚══════════════════════════════════════════════════════════════╝
```

---

## 5. Bước 3: Quá trình điều tra

### Câu hỏi 1: "Tôi đã đặt @Transactional rồi, tại sao không rollback?"

Hãy nhìn lại method signature:

```java
@Transactional  // ← Có @Transactional
public void transferToPartnerBank(...) throws BankTransferException {
    //                                        ↑ throws CHECKED Exception
}
```

### Câu hỏi 2: "Exception có được throw ra không?"

Có! `BankTransferException` được throw ra đúng rồi, Controller cũng catch được. Nhưng **Spring Transaction Manager KHÔNG rollback**.

### Câu hỏi 3: Mở Spring Source Code ra xem...

Đây là logic bên trong `DefaultTransactionAttribute`:

```java
// Spring Framework Source Code — DefaultTransactionAttribute.java
@Override
public boolean rollbackOn(Throwable ex) {
    return (ex instanceof RuntimeException || ex instanceof Error);
    //      ↑ CHỈ rollback RuntimeException và Error!
    //      ❌ KHÔNG rollback checked Exception (như BankTransferException)
}
```

---

## 6. Bước 4: Root Cause — Spring Transaction Internals

### Flow thực tế khi Exception xảy ra

```
[Request đến]
    ↓
[Spring AOP Proxy bắt đầu Transaction]
    ↓
[TransferService.transferToPartnerBank() thực thi]
    ↓
[accountRepository.save() — Trừ tiền → ĐÃ FLUSH vào DB]
    ↓
[partnerBankApiService.creditToPartnerBank() → throws BankTransferException ❌]
    ↓
[Spring AOP Proxy kiểm tra: BankTransferException instanceof RuntimeException?]
    ↓
[KẾT QUẢ: FALSE — Vì BankTransferException extends Exception, KHÔNG phải RuntimeException]
    ↓
[Spring quyết định: COMMIT TRANSACTION! 😱]
    ↓
[Tiền đã bị trừ → COMMIT → Không còn cách nào lấy lại]
```

### Phân loại Exception trong Java (Slide quan trọng)

```
                    Throwable
                   /          \
                Error      Exception ←── @Transactional KHÔNG rollback!
                  ↓        /        \
            (rollback)  (checked)   RuntimeException
                          ↑              ↓
                   BankTransferException  (rollback ✅)
                   IOException
                   SQLException
```

### Bảng tóm tắt hành vi @Transactional mặc định

| Exception Type            | Rollback? | Ví dụ                          |
|---------------------------|-----------|--------------------------------|
| `RuntimeException`        | ✅ CÓ     | `NullPointerException`, `IllegalArgumentException` |
| `Error`                   | ✅ CÓ     | `OutOfMemoryError`, `StackOverflowError` |
| **Checked `Exception`**   | ❌ **KHÔNG** | `IOException`, `SQLException`, **`BankTransferException`** |

---

## 7. Bước 5: Giải pháp & Best Practice

### Fix 1: Thêm `rollbackFor` (Fix nhanh, trực tiếp)

```diff
- @Transactional
+ @Transactional(rollbackFor = Exception.class)
  public void transferToPartnerBank(...) throws BankTransferException {
```

> Chỉ 1 dòng khác biệt — nhưng quyết định 5 triệu VND của khách hàng!

### Fix 2: Wrap thành RuntimeException (Phổ biến trong Banking)

```java
@Transactional
public void transferToPartnerBank(String from, String to, BigDecimal amount) {
    // ...trừ tiền...

    try {
        partnerBankApiService.creditToPartnerBank(to, amount);
    } catch (BankTransferException e) {
        // Wrap checked → unchecked → Spring SẼ rollback
        throw new RuntimeException("Transfer failed: " + e.getMessage(), e);
    }
}
```

### Fix 3: Thiết kế đúng từ đầu (Best Practice)

```java
// Custom exception PHẢI extends RuntimeException cho Banking
public class BankTransferException extends RuntimeException {
    // ...
}
```

> **Quy tắc trong Banking**: Mọi business exception nên extends `RuntimeException` để đảm bảo Spring luôn rollback.

### So sánh giải pháp

| Fix | Ưu điểm | Nhược điểm | Khi nào dùng |
|-----|---------|------------|-------------|
| `rollbackFor` | Fix nhanh, rõ ràng | Dễ quên ở method khác | Legacy code |
| Wrap RuntimeException | Tường minh | Verbose, mất stack trace gốc | Khi không sửa được Exception gốc |
| Extends RuntimeException | Đúng từ gốc | Cần refactor Exception hierarchy | Project mới / Greenfield |

---

## 8. BONUS: Combo với Self-Invocation Bug

### Nếu bạn muốn nâng level thêm, kết hợp 2 bug:

```java
@Service
public class TransferService {

    // ❌ BUG 1: Self-Invocation — @Transactional bị bypass hoàn toàn
    public void processTransfer(TransferRequest request) {
        validateRequest(request);
        executeTransfer(request);  // ← Gọi nội bộ → Proxy bị bypass
    }

    // ❌ BUG 2: Checked Exception — Nếu bằng cách nào đó @Transactional chạy,
    //    nó vẫn KHÔNG rollback vì BankTransferException là checked
    @Transactional
    public void executeTransfer(TransferRequest request) throws BankTransferException {
        accountRepo.debit(request.getFrom(), request.getAmount());
        partnerBankApi.credit(request.getTo(), request.getAmount());
    }
}
```

> **"Double Kill"**: Transaction vừa bị bypass (Bug 1), vừa không rollback (Bug 2). Tiền mất 100%.

---

## 9. Key Takeaways

### Cho mọi Developer:

1. **`@Transactional` mặc định CHỈ rollback `RuntimeException`** — Đây là kiến thức BẮT BUỘC phải biết.
2. **Luôn dùng `rollbackFor = Exception.class`** cho các nghiệp vụ quan trọng (tiền, tài chính).
3. **Business Exception nên extends `RuntimeException`** — Đừng dùng checked exception cho business logic.

### Cho Banking/Fintech Developer:

4. **Mọi giao dịch tài chính PHẢI có Idempotency Key** — Để retry an toàn.
5. **Tách External API call ra khỏi Transaction** — Dùng Saga Pattern hoặc Outbox Pattern.
6. **Luôn có Reconciliation job** kiểm tra đối soát cuối ngày.

### Cho Team Lead/Architect:

7. **Code Review PHẢI kiểm tra `@Transactional`** — Xem có `rollbackFor` chưa? Có self-invocation không?
8. **Viết Integration Test cho Transaction boundary** — Unit test KHÔNG phát hiện được bug này.

---

## 📚 Tài liệu tham khảo

- [Spring Docs — @Transactional rollback rules](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)
- [JavaBeans Specification — Exception Hierarchy](https://docs.oracle.com/javase/specs/)
- [Saga Pattern for Banking](https://microservices.io/patterns/data/saga.html)

---

> **Nguồn gốc ý tưởng**: Module `pricing_policy_management` — Pomina Backend Project
> **Tác giả**: Team Backend
> **Ngày tạo**: 25/04/2026
