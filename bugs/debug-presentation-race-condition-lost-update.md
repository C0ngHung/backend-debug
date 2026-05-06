# ⚡ Debug Presentation: "The Lost Update"

> **Race Condition trong chuyển khoản — Trừ tiền 2 lần, cộng tiền 1 lần**
> Giả lập bằng `CyclicBarrier` + 1000 concurrent requests — Fix bằng Atomic Update `@Query`

---

## 📋 Mục lục

1. [Bối cảnh nghiệp vụ Banking](#1-bối-cảnh-nghiệp-vụ-banking)
2. [Chuẩn bị Project Demo](#2-chuẩn-bị-project-demo)
3. [Bước 1: Code chuyển tiền "bình thường"](#3-bước-1-code-chuyển-tiền-bình-thường)
4. [Bước 2: Tái hiện Bug bằng CyclicBarrier](#4-bước-2-tái-hiện-bug-bằng-cyclicbarrier)
5. [Bước 3: Đọc Hibernate Log để "bắt bệnh"](#5-bước-3-đọc-hibernate-log-để-bắt-bệnh)
6. [Bước 4: Root Cause — Vi phạm ACID (Isolation)](#6-bước-4-root-cause--vi-phạm-acid-isolation)
7. [Bước 5: Giải pháp — Atomic Update bằng @Query](#7-bước-5-giải-pháp--atomic-update-bằng-query)
8. [Bước 6: Tại sao Log vẫn "ảo" nhưng DB lại đúng?](#8-bước-6-tại-sao-log-vẫn-ảo-nhưng-db-lại-đúng)
9. [Bước 7: Cảnh báo — Persistence Context (L1 Cache)](#9-bước-7-cảnh-báo--persistence-context-l1-cache)
10. [Bước 8: Verify lại bằng CyclicBarrier](#10-bước-8-verify-lại-bằng-cyclicbarrier)
11. [Key Takeaways](#11-key-takeaways)

---

## 1. Bối cảnh nghiệp vụ Banking

### Scenario

Hệ thống Core Banking xử lý chuyển khoản:
- **Thread A**: Trừ tiền tài khoản người gửi
- **Thread B**: Cộng tiền tài khoản người nhận

Khi **nhiều giao dịch xảy ra đồng thời** (1000 request cùng lúc), kết quả bị sai:

```
Tài khoản A: 10,000,000 VND (ban đầu)
Tài khoản B:          0 VND (ban đầu)

Thực hiện 1000 lần chuyển 1,000 VND từ A → B

KẾT QUẢ ĐÚNG:
  A = 10,000,000 - (1,000 × 1000) = 9,000,000 VND
  B =          0 + (1,000 × 1000) = 1,000,000 VND
  Tổng: 10,000,000 VND ✅

KẾT QUẢ THỰC TẾ (BUG):
  A = 9,500,000 VND   ← Trừ bị thiếu (mất update)
  B =   300,000 VND   ← Cộng bị thiếu (mất update)
  Tổng: 9,800,000 VND ❌ → 200,000 VND "BỐC HƠI"!
```

---

## 2. Chuẩn bị Project Demo

### Tech Stack

- Java 21
- Spring Boot 4.0.6 (Spring Framework 7)
- Spring Data JPA + Oracle Database
- `java.util.concurrent.CyclicBarrier` — Để giả lập concurrent requests
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
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
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
    show-sql: true
```

---

## 3. Bước 1: Code chuyển tiền "bình thường"

### 3.1 Entity

```java
@Entity
@Table(name = "accounts")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_seq")
    @SequenceGenerator(name = "account_seq", sequenceName = "ACCOUNT_SEQ", allocationSize = 1)
    private Long id;

    @Column(unique = true, nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
}
```

### 3.2 Repository (phiên bản CHƯA fix)

```java
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);
}
```

### 3.3 Transfer Service — ❌ BUG Ở ĐÂY!

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final AccountRepository accountRepository;

    /**
     * Chuyển tiền: Trừ tài khoản A, cộng tài khoản B.
     *
     * ❌ BUG: Đọc balance → Tính toán trong Java → Ghi lại.
     * Khi 2 thread đọc CÙNG LÚC, cả 2 đều thấy balance cũ
     * → Một trong hai update bị "đè" (Lost Update).
     */
    @Transactional
    public void transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // BƯỚC 1: ĐỌC balance hiện tại (READ)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        Account from = accountRepository.findByAccountNumber(fromAccountNumber)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản gửi"));

        Account to = accountRepository.findByAccountNumber(toAccountNumber)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản nhận"));

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // BƯỚC 2: TÍNH TOÁN trong Java (MODIFY)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        BigDecimal newFromBalance = from.getBalance().subtract(amount);
        BigDecimal newToBalance = to.getBalance().add(amount);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // BƯỚC 3: GHI balance mới (WRITE)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        from.setBalance(newFromBalance);
        to.setBalance(newToBalance);

        accountRepository.save(from);
        accountRepository.save(to);
    }
}
```

---

## 4. Bước 2: Tái hiện Bug bằng CyclicBarrier

### CyclicBarrier là gì?

`CyclicBarrier` là một synchronization aid trong `java.util.concurrent`. Nó cho phép **tất cả các thread phải đợi nhau** tại một điểm (barrier) trước khi tiếp tục. Khi tất cả thread đã "tới barrier" → Tất cả được release **đồng thời**.

```
Thread 1:  ─────●───(chờ)───▶ BẮT ĐẦU cùng lúc!
Thread 2:  ──●──────(chờ)───▶ BẮT ĐẦU cùng lúc!
Thread 3:  ────────●(chờ)───▶ BẮT ĐẦU cùng lúc!
                    ↑
              CyclicBarrier
           (chờ đủ 3 thread)
```

> **Mục đích**: Đảm bảo tất cả thread gọi `transfer()` **đúng cùng 1 thời điểm**, tối đa hóa xác suất xảy ra Race Condition.

### Test Case — Giả lập 1000 concurrent requests

```java
@SpringBootTest
class TransferConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void testConcurrentTransfer_ShouldDetectLostUpdate() throws Exception {
        // ═══ SETUP ═══
        // Tạo 2 tài khoản
        Account accountA = Account.builder()
                .accountNumber("ACC_A")
                .ownerName("Người gửi")
                .balance(new BigDecimal("10000000"))  // 10 triệu
                .build();
        Account accountB = Account.builder()
                .accountNumber("ACC_B")
                .ownerName("Người nhận")
                .balance(BigDecimal.ZERO)
                .build();
        accountRepository.saveAll(List.of(accountA, accountB));

        // ═══ CONFIG ═══
        int threadCount = 1000;
        BigDecimal transferAmount = new BigDecimal("1000");  // Mỗi lần chuyển 1,000đ

        // ═══ CYCLICBARRIER ═══
        // Tất cả 1000 thread phải đợi nhau → release ĐỒNG THỜI
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // ═══ FIRE! ═══
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Tất cả thread chờ ở đây...
                    barrier.await();   // ← ĐỒNG THỜI bắt đầu khi đủ 1000 thread!

                    // Gọi transfer
                    transferService.transfer("ACC_A", "ACC_B", transferAmount);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Chờ tất cả hoàn thành
        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // ═══ VERIFY ═══
        Account finalA = accountRepository.findByAccountNumber("ACC_A").orElseThrow();
        Account finalB = accountRepository.findByAccountNumber("ACC_B").orElseThrow();

        BigDecimal expectedA = new BigDecimal("10000000")
                .subtract(transferAmount.multiply(new BigDecimal(successCount.get())));
        BigDecimal expectedB = transferAmount.multiply(new BigDecimal(successCount.get()));

        System.out.println("══════════════════════════════════════════");
        System.out.println("  KẾT QUẢ CONCURRENT TRANSFER TEST");
        System.out.println("══════════════════════════════════════════");
        System.out.println("  Threads:        " + threadCount);
        System.out.println("  Thành công:     " + successCount.get());
        System.out.println("  Thất bại:       " + failCount.get());
        System.out.println("──────────────────────────────────────────");
        System.out.println("  Account A:");
        System.out.println("    Kỳ vọng:      " + expectedA);
        System.out.println("    Thực tế:      " + finalA.getBalance());
        System.out.println("    Match:        " + (expectedA.compareTo(finalA.getBalance()) == 0 ? "✅" : "❌ LOST UPDATE!"));
        System.out.println("──────────────────────────────────────────");
        System.out.println("  Account B:");
        System.out.println("    Kỳ vọng:      " + expectedB);
        System.out.println("    Thực tế:      " + finalB.getBalance());
        System.out.println("    Match:        " + (expectedB.compareTo(finalB.getBalance()) == 0 ? "✅" : "❌ LOST UPDATE!"));
        System.out.println("──────────────────────────────────────────");

        BigDecimal totalMoney = finalA.getBalance().add(finalB.getBalance());
        System.out.println("  Tổng tiền hệ thống:");
        System.out.println("    Ban đầu:      10,000,000");
        System.out.println("    Hiện tại:     " + totalMoney);
        System.out.println("    Tiền mất:     " + new BigDecimal("10000000").subtract(totalMoney));
        System.out.println("══════════════════════════════════════════");

        // ASSERT — Test sẽ FAIL nếu có Lost Update
        assertThat(finalA.getBalance()).isEqualByComparingTo(expectedA);
        assertThat(finalB.getBalance()).isEqualByComparingTo(expectedB);
    }
}
```

### Kết quả kỳ vọng (BUG — Test FAIL)

```
══════════════════════════════════════════
  KẾT QUẢ CONCURRENT TRANSFER TEST
══════════════════════════════════════════
  Threads:        1000
  Thành công:     1000
  Thất bại:       0
──────────────────────────────────────────
  Account A:
    Kỳ vọng:      9000000
    Thực tế:      9500000           ← ❌ Chỉ bị trừ 500 lần thay vì 1000!
    Match:        ❌ LOST UPDATE!
──────────────────────────────────────────
  Account B:
    Kỳ vọng:      1000000
    Thực tế:      300000            ← ❌ Chỉ được cộng 300 lần thay vì 1000!
    Match:        ❌ LOST UPDATE!
──────────────────────────────────────────
  Tổng tiền hệ thống:
    Ban đầu:      10,000,000
    Hiện tại:     9,800,000
    Tiền mất:     200,000           ← 💀 200K MẤT TÍCH!
══════════════════════════════════════════
```

---

## 5. Bước 3: Đọc Hibernate Log để "bắt bệnh"

> 🎓 **Kỹ năng từ Mentor**: Nhìn log Hibernate để phát hiện Race Condition — Chỉ những người làm dự án thực tế nhiều mới đúc kết được trick này.

### 5.1 Bật Hibernate SQL Log

Đảm bảo `show-sql: true` trong `application.yml`. Khi chạy test với CyclicBarrier, quan sát console:

### 5.2 Log khi BỊ BUG (TRƯỚC fix)

```
// ⚠️ DẤU HIỆU NHẬN BIẾT: 2 câu SELECT liên tục → rồi mới UPDATE → DB sai!

Hibernate: SELECT * FROM accounts WHERE account_number = 'ACC_A'   ← Thread 1 đọc
Hibernate: SELECT * FROM accounts WHERE account_number = 'ACC_A'   ← Thread 2 đọc CÙNG LÚC!
Hibernate: SELECT * FROM accounts WHERE account_number = 'ACC_B'   ← Thread 1 đọc
Hibernate: SELECT * FROM accounts WHERE account_number = 'ACC_B'   ← Thread 2 đọc CÙNG LÚC!

Hibernate: UPDATE accounts SET balance = 9999000 WHERE id = 1      ← Thread 1 ghi: 10M - 1K = 9,999K
Hibernate: UPDATE accounts SET balance = 9999000 WHERE id = 1      ← Thread 2 ĐÈ: cũng ghi 9,999K!
```

**Cách "bắt bệnh" từ log:**

| Dấu hiệu trong Log | Ý nghĩa |
|---------------------|----------|
| 2+ câu SELECT **liên tục** cùng 1 bảng | Nhiều thread đọc cùng lúc |
| Các câu UPDATE có **giá trị giống nhau** | Threads đè lên nhau (Lost Update) |
| Kiểm tra DB → Số dư **SAI** | Xác nhận vi phạm ACID! |

> **Mentor's trick**: Thấy Hibernate log hiện **SELECT liên tục 2 lần** + kiểm tra DB sai → **100% đang bị Race Condition ở chỗ đó!**

---

## 6. Bước 4: Root Cause — Vi phạm ACID (Isolation)

Đây là lỗi **Read-Modify-Write** kinh điển, vi phạm chữ **I (Isolation — Tính cô lập)** trong nguyên tắc ACID.

### Tại sao xảy ra?

Vấn đề nằm ở pattern **Read-Modify-Write** (Đọc → Tính trong Java → Ghi lại DB):

```
Thread 1:                           Thread 2:
────────────                        ────────────

READ  balance = 10,000,000          
                                    READ  balance = 10,000,000  ← Đọc CÙNG giá trị!

MODIFY (trong Java RAM):
  10,000,000 - 1,000 = 9,999,000    MODIFY (trong Java RAM):
                                      10,000,000 - 1,000 = 9,999,000  ← Tính trên CÙNG giá trị!

WRITE balance = 9,999,000           
                                    WRITE balance = 9,999,000   ← ĐÈ lên update của Thread 1!

KẾT QUẢ: balance = 9,999,000
ĐÚNG RA: balance = 9,998,000       ← MẤT 1 lần trừ!
```

### Minh họa trực quan (Slide quan trọng)

```
TIME ───────────────────────────────────────────────────────►

Thread 1:  ──[READ: 10M]──[MODIFY: 10M-1K=9,999K]──[WRITE: 9,999K]──
Thread 2:  ────[READ: 10M]──[MODIFY: 10M-1K=9,999K]────[WRITE: 9,999K]──
                 ↑                                          ↑
           Đọc cùng giá trị                          Đè lên Thread 1!

Oracle DB: 10,000,000 ──────────────[9,999,000]──[9,999,000]──
                                        ↑              ↑
                                   Thread 1 ghi   Thread 2 đè
                                                  (Lost Update!)
```

### Code gây lỗi (3 bước TÁCH RỜI)

```java
// BƯỚC 1: READ — JPA SELECT từ Oracle
Account from = accountRepository.findByAccountNumber(fromAccountNumber);
// → SELECT * FROM accounts WHERE account_number = 'ACC_A'
// → balance = 10,000,000

// BƯỚC 2: MODIFY — Tính toán TRONG JAVA RAM (không liên quan DB)
BigDecimal newBalance = from.getBalance().subtract(amount);
// → newBalance = 9,999,000

// BƯỚC 3: WRITE — JPA UPDATE toàn bộ entity
from.setBalance(newBalance);
accountRepository.save(from);
// → UPDATE accounts SET balance = 9999000 WHERE id = 1
//                       ↑ GIÁ TRỊ CỐ ĐỊNH, không quan tâm balance hiện tại!
```

### SQL sinh ra (vấn đề)

```sql
-- Thread 1 ghi:
UPDATE accounts SET balance = 9999000 WHERE id = 1;

-- Thread 2 ghi (đè lên Thread 1):
UPDATE accounts SET balance = 9999000 WHERE id = 1;

-- KẾT QUẢ: balance = 9,999,000 (mất 1 lần trừ!)
```

> **Root Cause**: JPA `save()` sinh ra UPDATE với **giá trị cố định** — nó KHÔNG biết balance đã bị thread khác thay đổi.

---

## 7. Bước 5: Giải pháp — Atomic Update bằng @Query

### Fix: Dùng `@Modifying @Query` — Atomic trên Database

```java
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    // ✅ ATOMIC UPDATE — Oracle thực hiện phép tính TRỰC TIẾP trên DB
    @Modifying(clearAutomatically = true)  // ← QUAN TRỌNG: clear L1 Cache sau khi update!
    @Query("UPDATE Account a SET a.balance = a.balance - :amount WHERE a.accountNumber = :accountNumber")
    int debit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Account a SET a.balance = a.balance + :amount WHERE a.accountNumber = :accountNumber")
    int credit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);
}
```

### SQL sinh ra (atomic)

```sql
-- Thread 1:
UPDATE accounts SET balance = balance - 1000 WHERE account_number = 'ACC_A';
--                  ↑ DÙNG GIÁ TRỊ HIỆN TẠI, không phải giá trị cố định!

-- Thread 2 (ngay sau):
UPDATE accounts SET balance = balance - 1000 WHERE account_number = 'ACC_A';
--                  ↑ Oracle tự lấy balance MỚI NHẤT → KHÔNG mất update!
```

> **Key Insight**: `SET balance = balance - 1000` là **atomic trên database** — Oracle đảm bảo chỉ có 1 thread thực hiện UPDATE tại 1 thời điểm trên cùng 1 row (row-level lock).

### Service sau khi fix

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final AccountRepository accountRepository;

    @Transactional(rollbackFor = Exception.class)
    public void transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {

        // Kiểm tra tài khoản tồn tại
        if (!accountRepository.findByAccountNumber(fromAccountNumber).isPresent()) {
            throw new RuntimeException("Tài khoản gửi không tồn tại");
        }

        // ✅ ATOMIC: Trừ tiền — Oracle xử lý race condition
        int debitResult = accountRepository.debit(fromAccountNumber, amount);
        if (debitResult == 0) {
            throw new RuntimeException("Trừ tiền thất bại");
        }

        // ✅ ATOMIC: Cộng tiền — Oracle xử lý race condition
        int creditResult = accountRepository.credit(toAccountNumber, amount);
        if (creditResult == 0) {
            throw new RuntimeException("Cộng tiền thất bại");
        }

        log.info("✅ Chuyển {} từ {} → {}", amount, fromAccountNumber, toAccountNumber);
    }
}
```

---

## 8. Bước 6: Tại sao Log vẫn "ảo" nhưng DB lại đúng?

> 🎓 **Điểm ăn tiền trong buổi Present** — Nếu bạn giải thích được phần này, Team Lead sẽ đánh giá bạn ở mức **Senior** về độ hiểu biết sâu giữa Java và Database.

### 8.1 Log SAU khi fix (vẫn trông "lạ")

```
// Hibernate log SAU khi dùng Atomic Update:

Hibernate: SELECT * FROM accounts WHERE account_number = 'ACC_A'   ← Thread 1 đọc (validate)
Hibernate: SELECT * FROM accounts WHERE account_number = 'ACC_A'   ← Thread 2 đọc (validate)

Hibernate: UPDATE accounts SET balance = balance - 1000 WHERE account_number = 'ACC_A'
Hibernate: UPDATE accounts SET balance = balance - 1000 WHERE account_number = 'ACC_A'
```

> **Câu hỏi từ Team**: "Anh ơi, log vẫn hiện 2 câu SELECT liên tục, vẫn giống lúc bị bug mà? Sao anh nói fix xong rồi?"

### 8.2 Giải thích (Đây là phần gây ấn tượng mạnh)

**Lý do log vẫn hiện SELECT liên tục:**

Trong Service, trước khi gọi `debit()`, bạn vẫn gọi `findByAccountNumber()` để validate tài khoản tồn tại. Khi 2 threads lao vào cùng lúc, Hibernate vẫn in ra 2 câu SELECT liên tục — **đây là bình thường**.

**Nhưng tại sao DB lại ĐÚNG?**

Bởi vì bạn **không ghi đè một con số tĩnh** (như `9999000`) xuống DB nữa. Khi lệnh `UPDATE ... SET balance = balance - 1000` chạy xuống Oracle:

```
// Oracle Database Engine xử lý:

Thread 1 gửi: UPDATE ... SET balance = balance - 1000
  → Oracle khóa row (Row-Level Lock)
  → Đọc balance hiện tại = 10,000,000
  → Tính: 10,000,000 - 1,000 = 9,999,000
  → Ghi: 9,999,000
  → Nhả khóa ✅

Thread 2 gửi: UPDATE ... SET balance = balance - 1000
  → Oracle thấy row đang bị khóa → CHỜ...
  → Thread 1 nhả khóa → Thread 2 được vào
  → Đọc balance hiện tại = 9,999,000  ← GIÁ TRỊ MỚI NHẤT!
  → Tính: 9,999,000 - 1,000 = 9,998,000
  → Ghi: 9,998,000
  → Nhả khóa ✅

KẾT QUẢ: 9,998,000 — CHÍNH XÁC! ✅
```

> **Kết luận**: Oracle đã làm giúp bạn việc **synchronization ở mức thấp nhất** (Row-Level Lock). Log trông "ảo" vì SELECT không ảnh hưởng, chỉ có UPDATE mới quantrọng!

### 8.3 Tuyệt chiêu khi Present

Khi demo, bạn hãy nói:

> *"Mọi người có thể thấy trên console, Hibernate vẫn bắn ra 2 câu SELECT đồng thời, chứng tỏ 2 Threads vẫn đang lao vào cùng một lúc, đọc lên cùng một trạng thái cũ. Tuy nhiên, với giải pháp Atomic Update đẩy logic xuống DB, dữ liệu trong Oracle vẫn chính xác tuyệt đối. Log trông có vẻ sai, nhưng DB đúng — đó mới là điều quan trọng."*

---

## 9. Bước 7: Cảnh báo — Persistence Context (L1 Cache)

> ⚠️ **Điểm cộng tuyệt đối** — Nếu bạn nhắc thêm Persistence Context, bạn sẽ thể hiện rằng bạn hiểu Hibernate ở mức thực chiến chứ không chỉ lý thuyết.

### Vấn đề

Khi dùng `@Modifying @Query` (Atomic Update), lệnh UPDATE được gửi **trực tiếp xuống DB**, bypass hoàn toàn Hibernate Persistence Context (L1 Cache).

Điều này có nghĩa: **Entity đang nằm trên RAM Java sẽ KHÔNG tự biết dữ liệu dưới DB đã thay đổi!**

```java
// ❌ SAU atomic update, entity trên Java BỊ STALE (cũ)

Account account = accountRepository.findByAccountNumber("ACC_A");
// account.getBalance() = 10,000,000  (từ L1 Cache/RAM)

accountRepository.debit("ACC_A", new BigDecimal("1000"));
// DB đã cập nhật: balance = 9,999,000

System.out.println(account.getBalance());
// VẪN IN RA: 10,000,000 ← 💀 DỮ LIỆU CŨ! (L1 Cache chưa update)
```

### Giải pháp: `clearAutomatically = true`

```java
// ✅ Thêm clearAutomatically = true → Clear L1 Cache sau mỗi lần update
@Modifying(clearAutomatically = true)
@Query("UPDATE Account a SET a.balance = a.balance - :amount WHERE a.accountNumber = :accountNumber")
int debit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);
```

Hoặc nếu cần refresh 1 entity cụ thể:

```java
// ✅ Dùng EntityManager.refresh() — ép Hibernate query lại từ DB
@PersistenceContext
private EntityManager entityManager;

public void afterAtomicUpdate(Account account) {
    entityManager.refresh(account);  // ← Query lại dữ liệu mới nhất từ DB
}
```

### Tóm tắt L1 Cache Issue

| Thời điểm | Entity trên Java (L1 Cache) | Dữ liệu trên Oracle DB | Khớp? |
|-----------|----------------------------|------------------------|-------|
| Trước `debit()` | 10,000,000 | 10,000,000 | ✅ |
| Sau `debit()` **KHÔNG có** `clearAutomatically` | 10,000,000 ← CŨ | 9,999,000 | ❌ |
| Sau `debit()` **CÓ** `clearAutomatically = true` | *(cleared)* | 9,999,000 | ✅ (sẽ query lại khi cần) |

---

## 10. Bước 8: Verify lại bằng CyclicBarrier

Chạy lại **CÙNG test case** ở Bước 2 sau khi fix:

```
══════════════════════════════════════════
  KẾT QUẢ CONCURRENT TRANSFER TEST
══════════════════════════════════════════
  Threads:        1000
  Thành công:     1000
  Thất bại:       0
──────────────────────────────────────────
  Account A:
    Kỳ vọng:      9000000
    Thực tế:      9000000           ← ✅ CHÍNH XÁC!
    Match:        ✅
──────────────────────────────────────────
  Account B:
    Kỳ vọng:      1000000
    Thực tế:      1000000           ← ✅ CHÍNH XÁC!
    Match:        ✅
──────────────────────────────────────────
  Tổng tiền hệ thống:
    Ban đầu:      10,000,000
    Hiện tại:     10,000,000
    Tiền mất:     0                 ← ✅ KHÔNG MẤT ĐỒNG NÀO!
══════════════════════════════════════════
```

---

## So sánh TRƯỚC và SAU fix

| Tiêu chí | ❌ Read-Modify-Write (JPA save) | ✅ Atomic Update (@Query) |
|----------|-------------------------------|--------------------------|
| **SQL sinh ra** | `SET balance = 9999000` (cố định) | `SET balance = balance - 1000` (tương đối) |
| **Thread-safe** | ❌ KHÔNG | ✅ CÓ (row-level lock bởi Oracle) |
| **1000 concurrent requests** | Mất tiền | Chính xác 100% |
| **Performance** | 2 queries (SELECT + UPDATE) | 1 query (UPDATE only) |

---

## 11. Key Takeaways

### Quy tắc vàng:

> **Không bao giờ Read-Modify-Write trong Java khi có concurrency. Hãy để Database xử lý bằng Atomic Update.**

### Cho mọi Developer:

1. **`CyclicBarrier`** là công cụ mạnh để test concurrency — Buộc tất cả thread bắt đầu **đúng cùng 1 thời điểm**.
2. **JPA `save()` KHÔNG an toàn** cho cập nhật số lượng (balance, stock, quantity...) khi có concurrent access.
3. **`@Modifying @Query("SET x = x - :amount")`** là cách đúng — Để database engine xử lý atomicity.
4. **Đọc Hibernate log**: Thấy SELECT liên tục 2 lần + DB sai = Race Condition.
5. **Log trông "ảo" sau fix là bình thường** — Quan trọng là DB đúng, không phải log đúng.
6. **Luôn dùng `clearAutomatically = true`** với `@Modifying` để tránh Persistence Context bị stale.

### Cho Banking Developer:

4. **Mọi thao tác cập nhật số dư** PHẢI dùng Atomic Update hoặc Pessimistic Lock.
5. **Luôn viết Concurrency Test** với `CyclicBarrier` cho mọi API liên quan đến tiền.
6. **Thêm constraint** `CHECK (balance >= 0)` trong DB để tránh số dư âm.

### Cho Team Lead/Architect:

7. **Code Review checklist**: Có `findById()` → `setBalance()` → `save()` pattern không? → ❌ Race Condition!
8. **Pattern an toàn**: Dùng `@Query` atomic update hoặc `SELECT ... FOR UPDATE` (Pessimistic Lock).

---

## Các giải pháp thay thế

### Option B: Pessimistic Lock (`SELECT ... FOR UPDATE`)

```java
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}
```

### Option C: Optimistic Lock (`@Version`)

```java
@Entity
public class Account {
    // ...
    @Version
    private Long version;  // JPA tự kiểm tra version khi UPDATE
}
```

### So sánh 3 giải pháp

| Giải pháp | Performance | Complexity | Khi nào dùng |
|-----------|------------|-----------|-------------|
| **Atomic @Query** | ⭐⭐⭐⭐⭐ | Thấp | **Cập nhật số lượng đơn giản** |
| Pessimistic Lock | ⭐⭐⭐ | Trung bình | Cần đọc rồi mới quyết định update |
| Optimistic Lock | ⭐⭐⭐⭐ | Trung bình | Conflict hiếm, cho phép retry |

---

## 📚 Tài liệu tham khảo

- [Java CyclicBarrier (Oracle Docs)](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CyclicBarrier.html)
- [Spring Data JPA @Modifying](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html)
- [Oracle Row-Level Locking](https://docs.oracle.com/en/database/oracle/oracle-database/21/cncpt/data-concurrency-and-consistency.html)

---

> **Nguồn gốc**: Bài giảng của Mentor — Concurrency trong Core Banking
> **Tác giả**: Team Backend
> **Ngày tạo**: 25/04/2026
