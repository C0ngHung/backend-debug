# 🐛 Debug Guide: Batch Import — Conditional Breakpoint

## Bug Overview

**Bug Name:** Batch Import Data Poisoning  
**Module:** `modules/product`  
**Endpoint:** `POST /api/v1/products/batch-import`  
**Difficulty:** ⭐⭐⭐ (Intermediate)  
**Skill Taught:** IntelliJ IDEA Conditional Breakpoint

## What Happens

When you call the batch import endpoint, it generates **30 products** and processes each one.
The response shows **27/30 SUCCESS** and **3/30 FAILED** (or partially corrupted data).

Your mission: **Find the 3 problematic items and explain WHY each one fails.**

---

## Step 1: Reproduce the Bug

```bash
# Call the batch import endpoint
curl -X POST http://localhost:8080/api/v1/products/batch-import \
  -H "Authorization: Bearer <your-token>"
```

**Expected response (HTTP 207 Multi-Status):**
```json
{
  "success": true,
  "message": "Batch import completed",
  "data": {
    "totalItems": 30,
    "successCount": 27,
    "failedCount": 3,
    "details": [
      { "index": 0, "productName": "...", "status": "SUCCESS" },
      ...
      { "index": 7, "productName": "...", "status": "FAILED", "errorMessage": "ArithmeticException: ..." },
      ...
    ]
  }
}
```

**Question:** Which 3 items failed, and why?

---

## Step 2: The Wrong Way — Normal Breakpoint

1. Open `ProductBatchServiceImpl.java`
2. Set a breakpoint at line: `BigDecimal finalPrice = calculateDiscountPrice(...)`
3. Debug the endpoint
4. **Problem:** The breakpoint stops **30 times**. You have to press F9 (Resume) through all 30 iterations to find the 3 bad ones.

> ❌ This wastes time. In production batch jobs with 10,000+ items, this approach is **impossible**.

---

## Step 3: The Right Way — Conditional Breakpoint

### Finding Bug #1: Division-by-Zero

1. Set a breakpoint at `calculateDiscountPrice(item.getPrice(), item.getDiscountPercent())`
2. **Right-click** the breakpoint → **Edit Breakpoint** (or press `Ctrl+Shift+F8`)
3. In the **Condition** field, enter:

```java
item.getDiscountPercent() == 100
```

4. Debug → The breakpoint now stops **only once** → at index **#7**
5. Step into `calculateDiscountPrice()`:
   - `discountPercent = 100`
   - `remainRate = 100 - 100 = 0`
   - `price.divide(remainRate, ...)` → 💥 **ArithmeticException: Division by zero**

**Root Cause:** The formula `price.divide(remainRate)` divides by `(100 - discountPercent)`. When `discountPercent = 100`, `remainRate = 0`.

**Fix:** Add a guard:
```java
if (discountPercent >= 100) {
    return BigDecimal.ZERO;
}
```

---

### Finding Bug #2: Silent Null Price

1. Set a breakpoint at the same line
2. Change the condition to:

```java
item.getPrice() == null
```

3. Debug → Stops at index **#19**
4. Step into `calculateDiscountPrice()`:
   - `price = null`
   - `price.divide(...)` → 💥 **NullPointerException**

**Root Cause:** The batch generator produced a product with `price = null`. The service did not validate input before calculation.

**Fix:** Add null check before calculation:
```java
if (price == null) {
    throw new IllegalArgumentException("Price cannot be null for item at index " + item.getIndex());
}
```

---

### Finding Bug #3: Precision Trap (The Silent Killer)

This is the **hardest bug** because it does NOT crash. The data saves successfully but with **wrong values**.

1. After fixing bugs #1 and #2, run the batch import again
2. Now all 30 items show "SUCCESS" — but check the saved data:

```sql
SELECT id, u_product_name, u_price FROM tbl_product WHERE u_product_name LIKE '%Batch Import%' ORDER BY id;
```

3. Item at index **#23** ("Tennis Racket - Batch Import") has `u_price = 19.99` instead of the expected `~20.00`

4. Set a conditional breakpoint:

```java
item.getPrice() != null && item.getPrice().scale() > 2
```

5. Debug → Stops at index **#23**
6. Inspect: `item.getPrice() = 19.999999999999` (scale = 12)
7. After `setScale(2, HALF_UP)` → `20.00` (correct rounding)
8. But the calculation `price.divide(remainRate, 2, HALF_UP)` introduces intermediate rounding errors → final result `19.99`

**Root Cause:** Input data has excessive precision. The intermediate division introduces rounding errors that accumulate.

**Fix:** Normalize price scale at the start:
```java
BigDecimal normalizedPrice = price.setScale(2, RoundingMode.HALF_UP);
```

---

## Architecture Overview

```
POST /api/v1/products/batch-import
       │
       ▼
ProductController.batchImport()
       │
       ▼
ProductBatchService.importBatch()
       │
       ├── ProductBatchGenerator.generateBatch()
       │       │
       │       ├── 30 items generated
       │       ├── Item #7:  discountPercent = 100     ← 💣 Division-by-Zero
       │       ├── Item #19: price = null              ← 💣 Silent Null
       │       └── Item #23: price = 19.999999999999   ← 💣 Precision Trap
       │
       └── for each item:
               │
               ├── calculateDiscountPrice(price, discountPercent)
               │       │
               │       ├── remainRate = 100 - discountPercent
               │       └── price.divide(remainRate, ...)  ← Bug location
               │
               ├── buildEntity(item, finalPrice)
               ├── productRepository.save(entity)
               └── catch Exception → record as FAILED
```

---

## Conditional Breakpoint Cheat Sheet

| Bug | Condition Expression | Stops At |
|-----|---------------------|----------|
| Division-by-Zero | `item.getDiscountPercent() == 100` | Index #7 |
| Silent Null | `item.getPrice() == null` | Index #19 |
| Precision Trap | `item.getPrice() != null && item.getPrice().scale() > 2` | Index #23 |

### IntelliJ IDEA Tips

- **Set Condition:** Right-click breakpoint → Edit → enter Java expression
- **Log without stopping:** Check "Evaluate and log" + uncheck "Suspend" → prints expression value without pausing
- **Hit count:** Use "Pass count" to stop only on the Nth hit
- **Instance filter:** Stop only for specific object instances

---

## Key Takeaway

> **Normal breakpoints** are for single-item debugging.  
> **Conditional breakpoints** are for batch/loop debugging.  
> In production, batch jobs process thousands of items. You CANNOT step through each one.  
> Learn to set precise conditions that catch **only the outlier**.
