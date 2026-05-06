# 💣 Debug Presentation: "The Time Bomb"

> **Blocking I/O inside @Transactional = Connection Pool Exhaustion — Hệ thống Banking sập khi đối tác chậm**
> Áp dụng thực tế: API chuyển tiền bị nghẽn bởi KYC/AML check bên thứ 3

---

## 📋 Mục lục

1. [Bối cảnh nghiệp vụ Banking](#1-bối-cảnh-nghiệp-vụ-banking)
2. [Chuẩn bị Project Demo](#2-chuẩn-bị-project-demo)
3. [Bước 1: Xây dựng hệ thống chuyển tiền + KYC Check](#3-bước-1-xây-dựng-hệ-thống-chuyển-tiền--kyc-check)
4. [Bước 2: Tái hiện Bug — Hệ thống sập](#4-bước-2-tái-hiện-bug--hệ-thống-sập)
5. [Bước 3: Quá trình điều tra](#5-bước-3-quá-trình-điều-tra)
6. [Bước 4: Root Cause — Connection Pool + Blocking I/O](#6-bước-4-root-cause--connection-pool--blocking-io)
7. [Bước 5: Giải pháp & Best Practice](#7-bước-5-giải-pháp--best-practice)
8. [Key Takeaways](#8-key-takeaways)

---

## 1. Bối cảnh nghiệp vụ Banking

### Quy trình chuyển tiền có KYC/AML Check

```
Khách hàng → Yêu cầu chuyển 50 triệu VND

Bước 1: Mở Transaction
Bước 2: Kiểm tra số dư → OK
Bước 3: Trừ tiền người gửi (UPDATE Oracle)
Bước 4: ⚡ Gọi API KYC/AML bên thứ 3 (kiểm tra rửa tiền) → BLOCKING 10 GIÂY
Bước 5: Cộng tiền người nhận (UPDATE Oracle)
Bước 6: Commit Transaction
```

### Tại sao có Bước 4?

- **KYC** (Know Your Customer): Xác minh danh tính khách hàng.
- **AML** (Anti-Money Laundering): Kiểm tra giao dịch có dấu hiệu rửa tiền không.
- Đây là **yêu cầu pháp luật** — Ngân hàng Nhà nước bắt buộc.
- API này do **bên thứ 3 cung cấp** (NAPAS, Worldcheck...) — Ngân hàng KHÔNG kiểm soát được tốc độ.

### Vấn đề

```
Ngày bình thường: API KYC trả về trong 200ms  → OK ✅
Ngày cao điểm:    API KYC chậm 10-30 giây     → 💣 BOM NỔ
```

---

## 2. Chuẩn bị Project Demo

### Tech Stack

- Java 21
- Spring Boot 4.0.6 (Spring Framework 7)
- Spring Data JPA + Oracle Database
- HikariCP (Connection Pool mặc định của Spring Boot)
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
    <!-- Spring Actuator — để monitor connection pool -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

### application.yml — Cấu hình quan trọng

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:bankdb
    username: bank_user
    password: bank_password
    driver-class-name: oracle.jdbc.OracleDriver
    hikari:
      maximum-pool-size: 10          # ← MẶC ĐỊNH! Chỉ có 10 connection
      minimum-idle: 5
      connection-timeout: 30000      # 30 giây chờ connection
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: BankHikariPool
  jpa:
    database-platform: org.hibernate.dialect.OracleDialect
    hibernate:
      ddl-auto: update
    show-sql: true

# Actuator — expose metrics
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  endpoint:
    health:
      show-details: always
```

---

## 3. Bước 1: Xây dựng hệ thống chuyển tiền + KYC Check

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

### 3.2 KYC/AML Service — Giả lập API chậm

```java
@Service
@Slf4j
public class KycAmlService {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Giả lập gọi API KYC/AML bên thứ 3.
     * Bình thường: 200ms. Ngày cao điểm: 10-30 giây.
     *
     * ⚠️ Đây là BLOCKING call — thread bị giữ cho đến khi có response.
     */
    public boolean checkTransaction(String fromAccount, String toAccount, BigDecimal amount) {
        log.info("🔍 Đang kiểm tra KYC/AML cho giao dịch {} → {} ({}đ)...",
                fromAccount, toAccount, amount);

        try {
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Giả lập API chậm: Sleep 10 giây
            // Trong thực tế: restTemplate.postForObject("https://kyc-api.partner.com/check", ...)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Thread.sleep(10_000);  // 10 giây — giả lập partner API chậm
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("✅ KYC/AML check PASSED");
        return true;  // Giả sử luôn pass
    }
}
```

### 3.3 Transfer Service — ❌ BUG Ở ĐÂY!

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final AccountRepository accountRepository;
    private final KycAmlService kycAmlService;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ❌ BUG: Blocking I/O (KYC check) BÊN TRONG @Transactional
    //    → Database Connection bị GIỮA suốt thời gian KYC check
    //    → 10 giây/request × 10 connection = SẬP sau 10 request!
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Transactional(rollbackFor = Exception.class)
    public void transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {

        // Bước 1: Lấy tài khoản
        Account from = accountRepository.findByAccountNumber(fromAccountNumber)
                .orElseThrow(() -> new RuntimeException("Tài khoản gửi không tồn tại"));
        Account to = accountRepository.findByAccountNumber(toAccountNumber)
                .orElseThrow(() -> new RuntimeException("Tài khoản nhận không tồn tại"));

        // Bước 2: Kiểm tra số dư
        if (from.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Số dư không đủ");
        }

        // Bước 3: Trừ tiền
        from.setBalance(from.getBalance().subtract(amount));
        accountRepository.save(from);
        log.info("💰 Đã trừ {} từ {}", amount, fromAccountNumber);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Bước 4: ⚡ GỌI API KYC — BLOCKING 10 GIÂY!
        // → Database connection VẪN bị giữ trong suốt 10 giây này
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        boolean passKyc = kycAmlService.checkTransaction(
                fromAccountNumber, toAccountNumber, amount);

        if (!passKyc) {
            throw new RuntimeException("Giao dịch bị từ chối bởi AML");
        }

        // Bước 5: Cộng tiền
        to.setBalance(to.getBalance().add(amount));
        accountRepository.save(to);
        log.info("💰 Đã cộng {} vào {}", amount, toAccountNumber);
    }
}
```

### 3.4 Controller

```java
@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<?> transfer(@RequestParam String from,
                                       @RequestParam String to,
                                       @RequestParam BigDecimal amount) {
        transferService.transfer(from, to, amount);
        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "message", "Chuyển tiền thành công"
        ));
    }
}
```

---

## 4. Bước 2: Tái hiện Bug — Hệ thống sập

### 4.1 Gửi 15 request đồng thời (Pool chỉ có 10)

```bash
# Gửi 15 request cùng lúc bằng curl
for i in $(seq 1 15); do
  curl -X POST "http://localhost:8080/api/transfer?from=ACC001&to=ACC002&amount=1000" &
done
wait
```

Hoặc dùng script Java:

```java
public class ConnectionPoolStressTest {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        int requestCount = 15;  // > pool size (10)
        CountDownLatch latch = new CountDownLatch(requestCount);

        System.out.println("🚀 Gửi " + requestCount + " requests đồng thời...");
        System.out.println("📊 Pool size: 10 | Request count: " + requestCount);
        System.out.println("⏳ Mỗi request giữ connection 10 giây (KYC check)...\n");

        for (int i = 0; i < requestCount; i++) {
            final int reqId = i + 1;
            Thread.startVirtualThread(() -> {
                try {
                    long start = System.currentTimeMillis();
                    System.out.println("→ Request #" + reqId + " starting...");

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/api/transfer?from=ACC001&to=ACC002&amount=1000"))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();

                    HttpResponse<String> response = client.send(request,
                            HttpResponse.BodyHandlers.ofString());

                    long elapsed = (System.currentTimeMillis() - start) / 1000;
                    System.out.println("← Request #" + reqId + " [" + response.statusCode() + "] " + elapsed + "s");
                } catch (Exception e) {
                    System.out.println("❌ Request #" + reqId + " FAILED: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
    }
}
```

### 4.2 Kết quả kỳ vọng

```
🚀 Gửi 15 requests đồng thời...
📊 Pool size: 10 | Request count: 15
⏳ Mỗi request giữ connection 10 giây (KYC check)...

→ Request #1 starting...
→ Request #2 starting...
→ Request #3 starting...
...
→ Request #10 starting...        ← 10 connection đã HẾT!
→ Request #11 starting...        ← PHẢI CHỜ...
→ Request #12 starting...        ← PHẢI CHỜ...
...

← Request #1 [200] 10s
← Request #2 [200] 10s
...
← Request #10 [200] 10s
← Request #11 [200] 20s          ← Chờ 10s connection + 10s KYC = 20s!
...
❌ Request #14 FAILED: Connection is not available, request timed out after 30000ms
❌ Request #15 FAILED: Connection is not available, request timed out after 30000ms
```

### 4.3 Slide tóm tắt (Visual)

```
╔══════════════════════════════════════════════════════╗
║              TIMELINE CỦA THẢM HỌA                   ║
║                                                       ║
║  t=0s    10 requests chiếm hết 10 connections         ║
║          ┌──────────────── KYC check (10s) ──────┐    ║
║          │  Connection #1: ĐANG GIỮ              │    ║
║          │  Connection #2: ĐANG GIỮ              │    ║
║          │  ...                                  │    ║
║          │  Connection #10: ĐANG GIỮ             │    ║
║          └───────────────────────────────────────┘    ║
║                                                       ║
║  t=0s    Request #11-15: ĐỢI connection...            ║
║          "Connection is not available..."             ║
║                                                       ║
║  t=10s   Request #1-10 xong → trả connection          ║
║          Request #11-13 lấy được connection            ║
║                                                       ║
║  t=30s   Request #14-15: TIMEOUT! 💀                  ║
║          "request timed out after 30000ms"            ║
║                                                       ║
║  ➜ Trong thực tế: 1000 request cùng lúc               ║
║  ➜ 990 request TIMEOUT = Hệ thống Banking SẬP!       ║
╚══════════════════════════════════════════════════════╝
```

---

## 5. Bước 3: Quá trình điều tra

### 5.1 Kiểm tra Actuator Metrics

```bash
# Kiểm tra trạng thái connection pool
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

```json
{
  "name": "hikaricp.connections.active",
  "measurements": [{ "value": 10.0 }]     ← 10/10 connections đang bị chiếm!
}
```

```bash
curl http://localhost:8080/actuator/metrics/hikaricp.connections.pending
```

```json
{
  "name": "hikaricp.connections.pending",
  "measurements": [{ "value": 5.0 }]      ← 5 threads đang ĐỢI connection!
}
```

### 5.2 Phân tích Log — Tìm pattern

```
INFO  - 💰 Đã trừ 1000 từ ACC001          ← Trừ tiền XONG (1ms)
INFO  - 🔍 Đang kiểm tra KYC/AML...        ← Bắt đầu KYC
                                            ← ⏳ 10 GIÂY IM LẶNG...
                                            ← Connection vẫn bị giữ!
INFO  - ✅ KYC/AML check PASSED             ← KYC xong
INFO  - 💰 Đã cộng 1000 vào ACC002         ← Cộng tiền (1ms)
```

> **Insight**: 99% thời gian giữ connection là **CHỜ KYC**, không phải thao tác DB!

---

## 6. Bước 4: Root Cause — Connection Pool + Blocking I/O

### Flow chi tiết

```
@Transactional BẮT ĐẦU
    ↓
[HikariCP lấy 1 Connection từ Pool]    ← Connection bị "mượn"
    ↓
[accountRepository.save()]              ← Dùng connection: 5ms
    ↓
[kycAmlService.checkTransaction()]      ← KHÔNG dùng connection, nhưng VẪN GIỮ: 10,000ms!
    ↓
[accountRepository.save()]              ← Dùng connection: 5ms
    ↓
@Transactional KẾT THÚC → COMMIT
    ↓
[HikariCP trả Connection về Pool]      ← Connection được "trả"

Tổng thời gian giữ connection: 10,010ms
Thời gian THỰC SỰ dùng DB:        10ms
Lãng phí:                       10,000ms (99.9%)! 💀
```

### Công thức tính "sập"

```
Connection Pool Size:        10
Thời gian mỗi request giữ:  10 giây
Throughput tối đa:           10 / 10 = 1 request/giây 💀

Với 100 request/giây (bình thường): 99 request TIMEOUT!
```

---

## 7. Bước 5: Giải pháp & Best Practice

### Fix 1: Tách KYC ra khỏi Transaction (Khuyến nghị)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final AccountRepository accountRepository;
    private final KycAmlService kycAmlService;

    // ✅ FIX: Chia thành 2 bước — KYC TRƯỚC, Transaction SAU
    public void transfer(String from, String to, BigDecimal amount) {

        // Bước 1: KYC check TRƯỚC — KHÔNG giữ DB connection!
        boolean passKyc = kycAmlService.checkTransaction(from, to, amount);
        if (!passKyc) {
            throw new RuntimeException("Giao dịch bị từ chối bởi AML");
        }

        // Bước 2: Transaction chỉ chứa DB operations — nhanh gọn!
        executeTransfer(from, to, amount);
    }

    @Transactional(rollbackFor = Exception.class)
    public void executeTransfer(String from, String to, BigDecimal amount) {
        Account fromAcc = accountRepository.findByAccountNumber(from)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy"));
        Account toAcc = accountRepository.findByAccountNumber(to)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy"));

        fromAcc.setBalance(fromAcc.getBalance().subtract(amount));
        toAcc.setBalance(toAcc.getBalance().add(amount));

        accountRepository.save(fromAcc);
        accountRepository.save(toAcc);
        // Transaction chỉ giữ connection ~50ms thay vì 10,010ms!
    }
}
```

**Kết quả sau fix:**
```
Thời gian giữ connection: 50ms (thay vì 10,010ms)
Throughput tối đa: 10 / 0.05 = 200 request/giây ← Tăng 200x!
```

### Fix 2: Thêm Timeout cho API call

```java
@Bean
public RestTemplate restTemplate() {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(5));     // ← Tối đa 5 giây
    return new RestTemplate(factory);
}
```

### Fix 3: Dùng Circuit Breaker (Resilience4j)

```java
@Service
public class KycAmlService {

    @CircuitBreaker(name = "kyc", fallbackMethod = "kycFallback")
    @TimeLimiter(name = "kyc")
    public CompletableFuture<Boolean> checkTransactionAsync(...) {
        // Gọi API KYC
    }

    // Fallback khi circuit mở
    public CompletableFuture<Boolean> kycFallback(..., Throwable t) {
        log.warn("KYC service unavailable, queuing for manual review");
        return CompletableFuture.completedFuture(true);  // Approve tạm, review sau
    }
}
```

### Fix 4: Tune Connection Pool

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30        # ← Tăng pool size (nhưng KHÔNG phải solution chính!)
      connection-timeout: 5000     # ← Fail fast thay vì chờ 30s
      leak-detection-threshold: 5000  # ← Cảnh báo nếu connection bị giữ > 5s
```

### So sánh giải pháp

| Fix | Hiệu quả | Độ phức tạp | Khi nào dùng |
|-----|----------|------------|-------------|
| **Tách KYC ra ngoài TX** | ⭐⭐⭐⭐⭐ | Thấp | **Luôn luôn — Fix gốc** |
| Timeout API call | ⭐⭐⭐ | Thấp | Bổ sung cùng Fix 1 |
| Circuit Breaker | ⭐⭐⭐⭐ | Trung bình | Production Banking |
| Tăng Pool Size | ⭐⭐ | Thấp | Tạm thời — KHÔNG phải solution |

> ⚠️ **Tăng pool size KHÔNG giải quyết gốc vấn đề** — Nếu KYC chậm 30s, dù pool size 100 vẫn sập khi traffic đủ lớn!

---

## 8. Key Takeaways

### Quy tắc vàng:

> **KHÔNG BAO GIỜ đặt Blocking I/O (HTTP call, file read, message queue) bên trong @Transactional.**

### Cho mọi Developer:

1. **Transaction chỉ nên chứa DB operations** — Mọi external call phải ở NGOÀI transaction boundary.
2. **Luôn đặt timeout** cho mọi external API call — Không bao giờ tin tưởng service bên thứ 3.
3. **Monitor connection pool** bằng Actuator/Prometheus — `hikaricp.connections.active` là metric sống còn.

### Cho Banking Developer:

4. **Pattern chuẩn**: KYC/AML check → TRƯỚC Transaction. Nếu pass → Mở Transaction → Xử lý tiền.
5. **Async processing**: Cho giao dịch lớn, queue vào Kafka/RabbitMQ → xử lý background.
6. **Circuit Breaker là BẮT BUỘC** khi gọi API bên thứ 3 trong flow tài chính.

### Cho Team Lead/Architect:

7. **Code Review checklist**: Có external call bên trong `@Transactional` không?
8. **Load test với external API delay** (inject latency 5-30s) trước khi lên production.
9. **Đặt `leak-detection-threshold`** trong HikariCP — Tự động cảnh báo khi connection bị giữ quá lâu.

---

## 📚 Tài liệu tham khảo

- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)
- [Spring Boot Actuator — HikariCP Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Resilience4j Circuit Breaker](https://resilience4j.readme.io/docs/circuitbreaker)
- [Oracle JDBC Pool Tuning](https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/)

---

> **Tác giả**: Team Backend
> **Ngày tạo**: 25/04/2026
