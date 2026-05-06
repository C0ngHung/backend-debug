# 💀 Debug Presentation: "The Invisible Killer"

> **HashMap Infinite Loop — CPU 100%, không có Error Log, hệ thống treo cứng**
> Áp dụng thực tế trong Core Banking: Cache tài khoản bị treo ngày cao điểm

---

## 📋 Mục lục

1. [Bối cảnh nghiệp vụ Banking](#1-bối-cảnh-nghiệp-vụ-banking)
2. [Chuẩn bị Project Demo](#2-chuẩn-bị-project-demo)
3. [Bước 1: Code "trông bình thường"](#3-bước-1-code-trông-bình-thường)
4. [Bước 2: Tái hiện Bug — CPU 100%](#4-bước-2-tái-hiện-bug--cpu-100)
5. [Bước 3: Quá trình điều tra — Thread Dump Analysis](#5-bước-3-quá-trình-điều-tra--thread-dump-analysis)
6. [Bước 4: Root Cause — HashMap Internal Structure](#6-bước-4-root-cause--hashmap-internal-structure)
7. [Bước 5: Giải pháp & Best Practice](#7-bước-5-giải-pháp--best-practice)
8. [Key Takeaways](#8-key-takeaways)

---

## 1. Bối cảnh nghiệp vụ Banking

### Scenario thực tế

Hệ thống Core Banking có một **Account Cache** lưu thông tin tài khoản để tránh query Oracle liên tục. Developer dùng `HashMap` để cache vì "đơn giản, nhanh".

```
Ngày bình thường: 100 request/giây → Hoạt động tốt ✅
Ngày lương (25 hàng tháng): 5,000 request/giây → CPU 100%, hệ thống treo 💀
```

### Triệu chứng

- ❌ **Không có Exception** trong log
- ❌ **Không có Error** nào được throw
- ❌ **Health check vẫn OK** (endpoint `/actuator/health` vẫn trả về `UP`)
- ✅ Chỉ thấy **CPU 100%** trên monitoring dashboard
- ✅ Tất cả API request **bị timeout** — không trả về response

> **Câu hỏi**: Không có lỗi, không có log, không crash... Vậy chuyện gì đang xảy ra?

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

---

## 3. Bước 1: Code "trông bình thường"

### 3.1 Entity — Tài khoản ngân hàng

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

### 3.2 Account Cache Service — ❌ BUG Ở ĐÂY!

```java
@Service
@Slf4j
public class AccountCacheService {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // ❌ BUG: Dùng HashMap trong môi trường multi-thread
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private final Map<String, Account> cache = new HashMap<>();

    private final AccountRepository accountRepository;

    public AccountCacheService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Lấy thông tin tài khoản — Cache first, DB fallback.
     * Mỗi request từ API sẽ gọi method này.
     * Trong Spring MVC, mỗi request = 1 thread riêng biệt.
     */
    public Account getAccount(String accountNumber) {
        // Đọc từ cache
        Account cached = cache.get(accountNumber);   // ← Thread A đọc
        if (cached != null) {
            return cached;
        }

        // Cache miss → Query DB
        Account fromDb = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // Ghi vào cache
        cache.put(accountNumber, fromDb);             // ← Thread B ghi ĐỒNG THỜI
        log.info("Cached account: {}", accountNumber);

        return fromDb;
    }

    public void updateCache(String accountNumber, Account account) {
        cache.put(accountNumber, account);             // ← Thread C ghi ĐỒNG THỜI
    }

    public void evict(String accountNumber) {
        cache.remove(accountNumber);
    }
}
```

### 3.3 Controller

```java
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountCacheService accountCacheService;

    @GetMapping("/{accountNumber}")
    public ResponseEntity<Account> getAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountCacheService.getAccount(accountNumber));
    }
}
```

---

## 4. Bước 2: Tái hiện Bug — CPU 100%

### 4.1 Tạo Load Test giả lập ngày lương

```java
/**
 * Chạy class này để giả lập 200 thread cùng truy cập cache đồng thời.
 * Đây là tình huống thực tế vào "ngày lương" — hàng nghìn nhân viên
 * cùng check lương, chuyển khoản cùng lúc.
 */
public class ConcurrentCacheStressTest {

    private static final Map<String, String> cache = new HashMap<>();
    private static final AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        int threadCount = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        System.out.println("🚀 Bắt đầu stress test với " + threadCount + " threads...");
        System.out.println("📊 Theo dõi CPU usage trong Task Manager / htop");
        System.out.println("⏳ Nếu chương trình TREO và CPU lên 100% → Bug đã tái hiện!\n");

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100_000; j++) {
                        String key = "ACC" + (j % 50);   // 50 keys, force resize nhiều lần
                        String value = "Thread-" + threadId + "-" + j;

                        cache.put(key, value);             // ← Nhiều thread cùng PUT
                        cache.get(key);                    // ← Nhiều thread cùng GET
                        counter.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.out.println("❌ Exception: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Chờ tối đa 30 giây
        boolean completed = latch.await(30, TimeUnit.SECONDS);

        if (!completed) {
            System.out.println("\n💀 TIMEOUT! Chương trình bị TREO → HashMap Infinite Loop!");
            System.out.println("   Threads hoàn thành: " + (threadCount - latch.getCount()) + "/" + threadCount);
            System.out.println("   Operations hoàn thành: " + counter.get());
            System.out.println("\n👉 Mở Task Manager → Xác nhận CPU 100%");
            System.out.println("👉 Chạy: jstack <PID> để lấy Thread Dump");
        } else {
            System.out.println("\n✅ Hoàn thành (có thể cần chạy lại nhiều lần để trigger bug)");
            System.out.println("   Total operations: " + counter.get());
        }

        executor.shutdownNow();
    }
}
```

### 4.2 Kết quả kỳ vọng khi demo

```
🚀 Bắt đầu stress test với 200 threads...
📊 Theo dõi CPU usage trong Task Manager / htop
⏳ Nếu chương trình TREO và CPU lên 100% → Bug đã tái hiện!

💀 TIMEOUT! Chương trình bị TREO → HashMap Infinite Loop!
   Threads hoàn thành: 47/200
   Operations hoàn thành: 1,847,293

👉 Mở Task Manager → Xác nhận CPU 100%
👉 Chạy: jstack <PID> để lấy Thread Dump
```

---

## 5. Bước 3: Quá trình điều tra — Thread Dump Analysis

### 5.1 Lấy Thread Dump

```bash
# Tìm PID của Java process
jps

# Lấy Thread Dump
jstack <PID> > thread_dump.txt
```

### 5.2 Phân tích Thread Dump

Trong file `thread_dump.txt`, bạn sẽ thấy pattern này:

```
"pool-1-thread-42" #42 prio=5 os_prio=0 tid=0x00007f... nid=0x... RUNNABLE
   java.lang.Thread.State: RUNNABLE
        at java.util.HashMap.get(HashMap.java:557)     ← 🔥 Stuck ở đây!
        at com.example.AccountCacheService.getAccount(AccountCacheService.java:25)
        ...

"pool-1-thread-78" #78 prio=5 os_prio=0 tid=0x00007f... nid=0x... RUNNABLE
   java.lang.Thread.State: RUNNABLE
        at java.util.HashMap.put(HashMap.java:611)     ← 🔥 Stuck ở đây!
        at com.example.AccountCacheService.getAccount(AccountCacheService.java:32)
        ...
```

### 5.3 Dấu hiệu nhận biết

| Dấu hiệu | Ý nghĩa |
|-----------|---------|
| Trạng thái `RUNNABLE` (không phải `WAITING` hay `BLOCKED`) | Thread đang **chạy liên tục**, không chờ lock |
| Stuck ở `HashMap.get()` hoặc `HashMap.put()` | Thread đang **lặp vô hạn** trong internal structure |
| Nhiều thread cùng pattern | Nhiều thread cùng rơi vào vòng lặp |
| **Không có `BLOCKED`** | Đây KHÔNG phải deadlock — đây là infinite loop |

> **Key Insight**: `RUNNABLE` + stuck ở `HashMap` = **Infinite Loop**, không phải Deadlock!

---

## 6. Bước 4: Root Cause — HashMap Internal Structure

### 6.1 HashMap hoạt động như thế nào?

```
HashMap (capacity=4, load_factor=0.75)

Bucket Array:
[0] → null
[1] → Entry("ACC001", account1) → null
[2] → Entry("ACC005", account5) → Entry("ACC009", account9) → null
[3] → Entry("ACC003", account3) → null

Khi số entry > capacity * load_factor → RESIZE (tạo array mới, rehash tất cả)
```

### 6.2 Bug xảy ra khi: 2 Threads cùng RESIZE

```
Thread A (resize):                    Thread B (resize):
────────────────────                  ────────────────────

Đọc linked list:                      Đọc linked list:
  Entry1 → Entry2 → null               Entry1 → Entry2 → null

Rehash Entry1 vào bucket mới:
  newBucket[X] = Entry1

                                      Rehash Entry2 vào bucket mới:
                                        newBucket[X] = Entry2 → Entry1

Rehash Entry2 (nhưng Entry2.next
  đã bị Thread B đổi thành Entry1):
  newBucket[X] = Entry1 → Entry2 → Entry1 → Entry2 → ...

💀 CIRCULAR REFERENCE! → Infinite Loop!
```

### 6.3 Minh hoạ trực quan (Slide quan trọng nhất)

```
TRƯỚC resize (bình thường):
  Entry1 → Entry2 → Entry3 → null     ✅ Kết thúc bằng null

SAU resize bị corrupt:
  Entry1 → Entry2 → Entry1 → Entry2 → Entry1 → ...   💀 KHÔNG BAO GIỜ KẾT THÚC!

Khi HashMap.get() duyệt linked list:
  → Entry1 → Entry2 → Entry1 → Entry2 → Entry1 → ...
  → CHẠY MÃI MÃI
  → CPU 100%
  → KHÔNG có exception vì code logic vẫn "đúng"
```

### 6.4 Tại sao không có Exception?

```java
// HashMap.get() internal — KHÔNG CÓ check infinite loop!
final Node<K,V> getNode(int hash, Object key) {
    Node<K,V> e;
    // Duyệt linked list — nếu list bị circular, vòng lặp này KHÔNG BAO GIỜ dừng
    if ((e = first) != null) {
        do {
            if (e.hash == hash && (e.key == key || key.equals(e.key)))
                return e;
        } while ((e = e.next) != null);   // ← e.next KHÔNG BAO GIỜ = null!
    }
    return null;
}
```

---

## 7. Bước 5: Giải pháp & Best Practice

### Fix 1: Dùng `ConcurrentHashMap` (Khuyến nghị)

```diff
- private final Map<String, Account> cache = new HashMap<>();
+ private final Map<String, Account> cache = new ConcurrentHashMap<>();
```

> **1 dòng code** — khác biệt giữa hệ thống chạy ổn và hệ thống sập ngày lương!

**Tại sao `ConcurrentHashMap` không bị?**
- Dùng lock ở mức **segment/bucket** (không lock toàn bộ map).
- Resize operation có **synchronization** nội bộ.
- Đọc (`get`) hầu như **không cần lock** (volatile read).

### Fix 2: Dùng `Collections.synchronizedMap`

```java
private final Map<String, Account> cache =
    Collections.synchronizedMap(new HashMap<>());
```

> **Nhược điểm**: Lock toàn bộ map mỗi operation → Performance kém hơn `ConcurrentHashMap`.

### Fix 3: Dùng Cache Library chuyên dụng (Best Practice cho Banking)

```java
// Dùng Caffeine Cache — production-grade, có TTL, eviction, metrics
@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, Account> accountCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()        // ← Metrics cho monitoring
                .build();
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class AccountCacheService {

    private final Cache<String, Account> accountCache;  // ✅ Thread-safe by design
    private final AccountRepository accountRepository;

    public Account getAccount(String accountNumber) {
        return accountCache.get(accountNumber, key ->
            accountRepository.findByAccountNumber(key)
                .orElseThrow(() -> new RuntimeException("Account not found"))
        );
    }
}
```

### So sánh giải pháp

| Fix | Thread-safe | Performance | Tính năng | Khi nào dùng |
|-----|------------|-------------|-----------|-------------|
| `ConcurrentHashMap` | ✅ | ⭐⭐⭐⭐ | Không có TTL, eviction | Simple cache |
| `synchronizedMap` | ✅ | ⭐⭐ | Không có TTL, eviction | Legacy code |
| **Caffeine** | ✅ | ⭐⭐⭐⭐⭐ | TTL, eviction, metrics | **Production Banking** |
| **Redis** | ✅ | ⭐⭐⭐⭐ | Distributed, persistent | Microservices |

---

## 8. Key Takeaways

### Cho mọi Developer:

1. **`HashMap` KHÔNG thread-safe** — Tuyệt đối KHÔNG dùng trong môi trường multi-thread (Spring MVC = multi-thread).
2. **"Không có exception" KHÔNG có nghĩa là "không có lỗi"** — Infinite loop là bug im lặng nguy hiểm nhất.
3. **Thread Dump là kỹ năng BẮT BUỘC** — `jstack <PID>` là vũ khí mạnh nhất khi CPU 100%.

### Cho Banking Developer:

4. **Dùng Caffeine hoặc Redis cho cache** — Không bao giờ dùng raw `HashMap`.
5. **Monitoring CPU, Thread count, GC** là bắt buộc trong production.
6. **Load test trước ngày cao điểm** (ngày lương, Black Friday, Tết) — Đừng để production là nơi "test" đầu tiên.

### Cho Team Lead/Architect:

7. **Code Review PHẢI kiểm tra**: Có ai dùng `HashMap` hay `ArrayList` trong shared state không?
8. **Quy tắc vàng**: Mọi shared mutable state trong Spring Bean phải dùng **concurrent collection** hoặc **immutable**.

---

## 📚 Tài liệu tham khảo

- [Java HashMap Source Code (OpenJDK)](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/HashMap.java)
- [ConcurrentHashMap vs HashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)
- [How to take Thread Dump](https://docs.oracle.com/en/java/javase/21/troubleshoot/diagnostic-tools.html)

---

> **Tác giả**: Team Backend
> **Ngày tạo**: 25/04/2026
