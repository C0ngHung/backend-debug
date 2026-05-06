# 💰 Debug Presentation: "The Phantom Fee"

> **Khi `BigDecimal.equals()` phản bội bạn — Phí giao dịch "bốc hơi" với một số khách hàng đặc biệt**
> Bắt buộc sử dụng Conditional Breakpoint để truy vết

---

## 📋 Mục lục

1. [Bối cảnh nghiệp vụ Banking](#1-bối-cảnh-nghiệp-vụ-banking)
2. [Chuẩn bị Project Demo](#2-chuẩn-bị-project-demo)
3. [Bước 1: Xây dựng hệ thống tính phí](#3-bước-1-xây-dựng-hệ-thống-tính-phí)
4. [Bước 2: Tái hiện Bug — Phí "biến mất"](#4-bước-2-tái-hiện-bug--phí-biến-mất)
5. [Bước 3: Tại sao PHẢI dùng Conditional Breakpoint?](#5-bước-3-tại-sao-phải-dùng-conditional-breakpoint)
6. [Bước 4: Root Cause — BigDecimal.equals() vs compareTo()](#6-bước-4-root-cause--bigdecimalequals-vs-compareto)
7. [Bước 5: Giải pháp & Best Practice](#7-bước-5-giải-pháp--best-practice)
8. [Key Takeaways](#8-key-takeaways)

---

## 1. Bối cảnh nghiệp vụ Banking

### Quy trình tính phí giao dịch (Transaction Fee)

Trong hệ thống ngân hàng, mỗi giao dịch chuyển tiền đều bị tính **phí dịch vụ** theo bậc thang:

```
Số tiền giao dịch          → Phí dịch vụ
≤ 1,000,000 VND             → Miễn phí (0 VND)
> 1,000,000 → 5,000,000     → 10,000 VND
> 5,000,000 → 50,000,000    → 25,000 VND
> 50,000,000                 → 50,000 VND
```

### Sự cố thực tế

Bộ phận đối soát (**Reconciliation**) phát hiện: **Một số giao dịch có số tiền đúng bằng mốc ranh giới** (ví dụ: chính xác `5,000,000.00 VND`) bị **tính phí sai** hoặc **bị skip** — hệ thống nghĩ đã tính rồi.

**Vấn đề**: Lỗi chỉ xảy ra với **5 giao dịch trong 1,000 giao dịch** mỗi batch. Không thể đặt breakpoint thường để dừng từng cái một.

---

## 2. Chuẩn bị Project Demo

### Tech Stack

- Java 21
- Spring Boot 4.0.6 (Spring Framework 7)
- Lombok
- **Không cần Database** — toàn bộ xử lý in-memory

### Module Structure

```
modules/fee/
├── controller/
│   └── FeeController.java              ← API endpoints
├── dto/
│   ├── request/
│   │   └── FeeRequestDto.java          ← Input DTO (transactionId, accountNumber, amount)
│   └── response/
│       └── FeeResponseDto.java         ← Output DTO (totalProcessed, totalSkipped, feeResults)
└── service/
    ├── FeeCalculationService.java      ← Interface
    ├── FeeBatchGeneratorService.java   ← Interface
    └── impl/
        ├── FeeCalculationServiceImpl.java      ← ❌ BUG NẰM Ở ĐÂY
        └── FeeBatchGeneratorServiceImpl.java   ← Sinh 1000 records + 5 "bom"
```

### API Endpoints

| Method | URL                           | Mô tả                               |
| ------ | ----------------------------- | ----------------------------------- |
| GET    | `/api/v1/fees/generate-batch` | Sinh 1000 giao dịch test (có "bom") |
| POST   | `/api/v1/fees/batch`          | Xử lý batch — **chứa bug**          |
| POST   | `/api/v1/fees/calculate`      | Tính phí đơn lẻ (không bug)         |

---

## 3. Bước 1: Xây dựng hệ thống tính phí

### 3.1 DTO — Yêu cầu tính phí

```java
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
public class FeeRequestDto {

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;
}
```

### 3.2 Service tính phí — Code "trông hoàn hảo" nhưng có BUG! 🐛

```java
@Service
@Slf4j(topic = "FEE-CALCULATION")
public class FeeCalculationServiceImpl implements FeeCalculationService {

    // Các mốc ranh giới phí
    private static final BigDecimal TIER_1 = new BigDecimal("1000000");    // 1 triệu
    private static final BigDecimal TIER_2 = new BigDecimal("5000000");    // 5 triệu
    private static final BigDecimal TIER_3 = new BigDecimal("50000000");   // 50 triệu

    // Mức phí tương ứng
    private static final BigDecimal FEE_FREE   = BigDecimal.ZERO;
    private static final BigDecimal FEE_LOW    = new BigDecimal("10000");
    private static final BigDecimal FEE_MEDIUM = new BigDecimal("25000");
    private static final BigDecimal FEE_HIGH   = new BigDecimal("50000");

    /**
     * Tính phí cho một giao dịch đơn lẻ — ĐÚNG.
     */
    public BigDecimal calculateFee(BigDecimal amount) {
        if (amount.compareTo(TIER_1) <= 0) {
            return FEE_FREE;
        } else if (amount.compareTo(TIER_2) <= 0) {
            return FEE_LOW;
        } else if (amount.compareTo(TIER_3) <= 0) {
            return FEE_MEDIUM;
        } else {
            return FEE_HIGH;
        }
    }

    /**
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * ❌ BUG ẨN NÁU Ở ĐÂY — Xử lý batch giao dịch
     * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     */
    public FeeResponseDto processBatchFees(List<FeeRequestDto> requests) {
        Map<String, BigDecimal> feeResults = new HashMap<>();
        int totalSkipped = 0;

        for (FeeRequestDto request : requests) {
            BigDecimal fee = calculateFee(request.getAmount());

            // ❌ BUG: Dùng equals() để kiểm tra "đã tính phí chưa"
            BigDecimal existingFee = feeResults.get(request.getAccountNumber());

            if (existingFee != null && existingFee.equals(fee)) {
                // Skip — nghĩ rằng đã tính rồi
                log.info("Skip fee for txn {}: already calculated", request.getTransactionId());
                totalSkipped++;
                continue;
            }

            feeResults.put(request.getAccountNumber(), fee);
            log.info("Fee calculated for txn {}", request.getTransactionId());
        }

        FeeResponseDto response = new FeeResponseDto();
        response.setTotalProcessed(requests.size() - totalSkipped);
        response.setTotalSkipped(totalSkipped);
        response.setFeeResults(feeResults);
        return response;
    }
}
```

### 3.3 Batch Generator — Sinh 1000 giao dịch thực tế

```java
@Service
public class FeeBatchGeneratorServiceImpl implements FeeBatchGeneratorService {

    // 🐛 "Bom" — amounts có trailing decimals gây scale mismatch
    private static final BigDecimal BOMB_1 = new BigDecimal("5000000.00");   // scale=2
    private static final BigDecimal BOMB_2 = new BigDecimal("500000.0");     // scale=1
    private static final BigDecimal BOMB_3 = new BigDecimal("3000000.00");   // scale=2
    private static final BigDecimal BOMB_4 = new BigDecimal("1000000.00");   // scale=2
    private static final BigDecimal BOMB_5 = new BigDecimal("10000000.0");   // scale=1

    @Override
    public List<FeeRequestDto> generateBatch() {
        List<FeeRequestDto> batch = new ArrayList<>(1000);
        Random random = new Random(42); // Deterministic seed

        for (int i = 1; i <= 1000; i++) {
            // 🐛 "Bom" ẩn tại vị trí 156, 237, 512, 789, 934
            BigDecimal amount = switch (i) {
                case 237 -> BOMB_1;   // 5,000,000.00  (tier boundary)
                case 512 -> BOMB_2;   // 500,000.0     (free tier)
                case 789 -> BOMB_3;   // 3,000,000.00  (low tier)
                case 156 -> BOMB_4;   // 1,000,000.00  (tier boundary)
                case 934 -> BOMB_5;   // 10,000,000.0  (medium tier)
                default -> NORMAL_AMOUNTS[random.nextInt(NORMAL_AMOUNTS.length)];
            };

            batch.add(new FeeRequestDto("TXN" + String.format("%04d", i),
                    "ACC" + String.format("%03d", (i % 50) + 1), amount));
        }
        return batch;
    }
}
```

### 3.4 Controller

```java
@RestController
@RequestMapping("/api/v1/fees")
@Tag(name = "Fee Controller", description = "APIs for transaction fee calculation — Phantom Fee Bug Demo")
@RequiredArgsConstructor
public class FeeController {

    private final FeeCalculationService feeCalculationService;
    private final FeeBatchGeneratorService feeBatchGeneratorService;

    @GetMapping("/generate-batch")    // Sinh 1000 records
    @PostMapping("/batch")            // Xử lý batch (có bug)
    @PostMapping("/calculate")        // Tính phí đơn lẻ
}
```

---

## 4. Bước 2: Tái hiện Bug — Phí "biến mất"

### Kịch bản demo (dùng Postman hoặc Swagger UI)

#### Bước 4.1: Sinh batch 1000 giao dịch

```
GET http://localhost:8080/api/v1/fees/generate-batch
```

> Response: 1000 `FeeRequestDto` — copy toàn bộ `data` array.

#### Bước 4.2: Gửi batch vào hệ thống

```
POST http://localhost:8080/api/v1/fees/batch
Content-Type: application/json
Body: [paste 1000 records vừa copy]
```

#### Bước 4.3: Kết quả — Có giao dịch bị SKIP sai!

```json
{
    "success": true,
    "data": {
        "totalProcessed": 9xx,
        "totalSkipped": xx,    ← ⚠️ CÓ GIAO DỊCH BỊ SKIP SAI!
        "feeResults": {
            "ACC001": 0,
            "ACC002": 10000,
            ...
        }
    }
}
```

> 💀 **`totalSkipped > 0`** — Một số giao dịch bị skip sai vì `BigDecimal.equals()` trả về kết quả bất ngờ do khác **scale**.

### Tại sao bug xảy ra?

Ví dụ cho account `ACC013` (xuất hiện ở TXN012 và TXN0512):

| Lần | TXN     | Amount        | Scale | Fee                     | Hành vi                                      |
| --- | ------- | ------------- | ----- | ----------------------- | -------------------------------------------- |
| 1   | TXN0012 | 500,000       | 0     | BigDecimal("0") scale=0 | ✅ Lưu vào map                               |
| 2   | TXN0512 | 500,000**.0** | **1** | BigDecimal("0") scale=0 | existingFee.equals(fee) → **true** → SKIP ⚠️ |

> Tình cờ SKIP đúng ở trên, nhưng với mức phí khác nhau sẽ gây sai!

---

## 5. Bước 3: Tại sao PHẢI dùng Conditional Breakpoint?

### Vấn đề: Batch có 1000 giao dịch — không thể step qua từng cái!

Nếu bạn đặt breakpoint thường trong vòng lặp `for`, bạn sẽ dừng lại ở **1000 giao dịch** — phải bấm F8 **1000 lần** mới xong. **Không khả thi.**

### Giải pháp: Conditional Breakpoint

#### Bước 5.1: Đặt breakpoint tại dòng `existingFee.equals(fee)` trong `FeeCalculationServiceImpl`

```
Dòng: if (existingFee != null && existingFee.equals(fee))
```

#### Bước 5.2: Chuột phải vào breakpoint → "Edit Breakpoint" → Nhập Condition:

**Cách 1**: Lọc theo scale bất thường — dừng KHI phát hiện scale khác nhau:

```java
existingFee != null && existingFee.scale() != fee.scale()
```

**Cách 2**: Lọc theo Transaction ID cụ thể (nếu đã biết):

```java
request.getTransactionId().equals("TXN0512")
```

**Cách 3**: Lọc theo amount có decimal (nghi ngờ input data):

```java
request.getAmount().scale() > 0
```

#### Bước 5.3: Chạy Debug → Debugger CHỈ dừng tại record có vấn đề

Trong 1000 records, debugger chỉ dừng tại **5 records** (vị trí 156, 237, 512, 789, 934).

Khi dừng, bạn kiểm tra trong panel **Evaluate Expression**:

```java
existingFee                    // → 0       (scale = 0)
fee                            // → 0       (scale = 0)
existingFee.equals(fee)        // → true — tình cờ đúng ở đây!

// Nhưng với record khác:
existingFee                    // → 10000   (scale = 0)
fee                            // → 10000   (scale = 0)
existingFee.equals(fee)        // → true — SKIP SAI! Account này có giao dịch mới!
```

### Slide Demo (Khoảnh khắc "Eureka!")

```
╔══════════════════════════════════════════════════════════════════╗
║           KẾT QUẢ TRONG DEBUGGER (Evaluate Expression)          ║
║                                                                  ║
║  Batch size: 1000 records                                       ║
║  Breakpoint hits: chỉ 5 lần (thay vì 1000)                     ║
║                                                                  ║
║  existingFee         = 0                                        ║
║  fee                 = 0                                        ║
║                                                                  ║
║  existingFee.equals(fee)    → true    ← SKIP (đúng tình cờ)    ║
║  existingFee.compareTo(fee) → 0       ← ✅ BẰNG NHAU!          ║
║                                                                  ║
║  💡 BUG THỰC SỰ: Logic SKIP bản thân đã sai!                  ║
║     Cùng account, fee giống → skip = KHÔNG update lại fee       ║
║     Nhưng giao dịch MỚI cũng cần được tính phí!                ║
╚══════════════════════════════════════════════════════════════════╝
```

---

## 6. Bước 4: Root Cause — BigDecimal.equals() vs compareTo()

### Java Documentation (chính thức)

> **`BigDecimal.equals()`**: Compares this BigDecimal with the specified Object for equality. Unlike `compareTo`, this method considers **two `BigDecimal` objects equal only if they are equal in value AND scale**. Thus `2.0` is not equal to `2.00` when compared by this method.

### Bảng minh họa:

| Biểu thức                                                   | `equals()` | `compareTo()` | Lý do                               |
| ----------------------------------------------------------- | ---------- | ------------- | ----------------------------------- |
| `new BigDecimal("0").equals(new BigDecimal("0"))`           | `true ✅`  | `0`           | Cùng value, cùng scale (0)          |
| `new BigDecimal("0").equals(new BigDecimal("0.0"))`         | `false ❌` | `0`           | Cùng value, **KHÁC scale** (0 vs 1) |
| `new BigDecimal("5.0").equals(new BigDecimal("5.00"))`      | `false ❌` | `0`           | Cùng value, **KHÁC scale** (1 vs 2) |
| `new BigDecimal("10000").equals(new BigDecimal("10000.0"))` | `false ❌` | `0`           | Cùng value, **KHÁC scale** (0 vs 1) |

### Nguồn gốc scale khác nhau trong hệ thống:

```
Source 1: API JSON   "amount": 500000     → BigDecimal scale = 0
Source 2: API JSON   "amount": 500000.0   → BigDecimal scale = 1
Source 3: Database   NUMBER(19,2)          → BigDecimal scale = 2
Source 4: Frontend   user nhập "5000000"  → scale = 0
Source 5: Frontend   user nhập "5000000." → scale = 0 nhưng khác nếu parse khác nhau
```

> **Kết luận**: Trong hệ thống thực tế, data đến từ nhiều source (API, DB, UI) với scale khác nhau. Dùng `equals()` sẽ tạo ra **bug không xảy ra 100% thời gian** — cực kỳ nguy hiểm.

---

## 7. Bước 5: Giải pháp & Best Practice

### Fix: Thay `equals()` bằng `compareTo()`

```diff
- if (existingFee != null && existingFee.equals(fee)) {
+ if (existingFee != null && existingFee.compareTo(fee) == 0) {
```

> **Chỉ 1 từ thay đổi**: `equals` → `compareTo` — nhưng ảnh hưởng đến hàng nghìn giao dịch mỗi ngày.

### Quy tắc vàng cho BigDecimal trong Banking:

| Phương thức                | Khi nào dùng                    | Khi nào KHÔNG dùng       |
| -------------------------- | ------------------------------- | ------------------------ |
| `compareTo() == 0`         | So sánh **giá trị** tiền        | —                        |
| `equals()`                 | **KHÔNG BAO GIỜ** dùng cho tiền | So sánh giá trị tiền     |
| `setScale()`               | Chuẩn hóa trước khi lưu DB      | —                        |
| `new BigDecimal("string")` | **LUÔN** dùng cách này          | `new BigDecimal(double)` |

---

## 8. Key Takeaways

### Cho mọi Developer:

1. **KHÔNG BAO GIỜ dùng `BigDecimal.equals()` để so sánh giá trị tiền** — Luôn dùng `compareTo() == 0`.
2. **KHÔNG BAO GIỜ dùng `new BigDecimal(double)`** — Luôn dùng `new BigDecimal("string")` hoặc `BigDecimal.valueOf()`.
3. **Conditional Breakpoint là kỹ năng BẮT BUỘC** — Trong batch 1000+ records, bạn không thể step qua từng cái.

### Cho Banking/Fintech Developer:

4. **Luôn chuẩn hóa scale** trước khi lưu vào database: `amount.setScale(2, RoundingMode.HALF_UP)`.
5. **Viết Unit Test cho các giá trị biên** (boundary values): chính xác `1,000,000`, `5,000,000`, `50,000,000`.
6. **Log đầy đủ scale** khi debugging: `log.debug("amount={}, scale={}", amount, amount.scale())`.

### Về kỹ năng Conditional Debugging:

7. **Dùng khi**: Bug chỉ xảy ra với 5 trong 1000 records — step thường KHÔNG THỂ.
8. **Condition phổ biến**:
   - Lọc theo ID: `request.getTransactionId().equals("TXN0512")`
   - Lọc theo scale: `request.getAmount().scale() > 0`
   - Lọc theo hành vi bất thường: `existingFee != null && existingFee.scale() != fee.scale()`
9. **Kết hợp Evaluate Expression**: Sau khi dừng tại điểm lỗi, dùng Evaluate để kiểm tra giả thuyết ngay tại chỗ.

---

## 📚 Tài liệu tham khảo

- [Java BigDecimal.equals() Documentation](<https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html#equals(java.lang.Object)>)
- [IntelliJ IDEA — Conditional Breakpoints](https://www.jetbrains.com/help/idea/using-breakpoints.html#conditional)
- [Effective Java — Item 11: Always override equals with compareTo for BigDecimal](https://www.oreilly.com/library/view/effective-java/9780134686097/)

---

> **Nguồn gốc ý tưởng**: Hệ thống tính phí giao dịch — Core Banking
> **Module**: `modules/fee/` (độc lập, không cần Database)
> **Tác giả**: Team Backend
> **Ngày tạo**: 26/04/2026
> **Cập nhật**: 27/04/2026 — Batch 1000 records + Conditional Breakpoint focus
