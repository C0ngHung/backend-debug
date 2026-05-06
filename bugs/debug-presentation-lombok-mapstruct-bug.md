# 🐛 Debug Presentation: "The 'u' Mystery"

> **Khi Naming Convention phá hỏng cả hệ thống — Xung đột giữa Lombok, MapStruct và JavaBeans Specification**

---

## 📋 Mục lục

1. [Bối cảnh & Mục tiêu](#1-bối-cảnh--mục-tiêu)
2. [Chuẩn bị Project Demo](#2-chuẩn-bị-project-demo)
3. [Bước 1: Tạo code "Bình thường"](#3-bước-1-tạo-code-bình-thường)
4. [Bước 2: Tái hiện Bug](#4-bước-2-tái-hiện-bug)
5. [Bước 3: Quá trình điều tra (Investigation)](#5-bước-3-quá-trình-điều-tra)
6. [Bước 4: Chạm mốc "Eureka!" — JavaBeans Specification](#6-bước-4-chạm-mốc-eureka--javabeans-specification)
7. [Bước 5: Giải pháp](#7-bước-5-giải-pháp)
8. [Key Takeaways](#8-key-takeaways)

---

## 1. Bối cảnh & Mục tiêu

### Bối cảnh thực tế (Pomina Project)

Trong module `pricing_policy_management`, Entity `ChinhSachParent` có các field bắt đầu bằng prefix `u` (ví dụ: `uChinhSachParentId`, `uDayBegin`, `uStatus`, `uDescription`).

Khi sử dụng **MapStruct** để convert giữa DTO và Entity, MapStruct **không tự mapping đúng** được các field này, dẫn đến dữ liệu bị `null` hoặc compile lỗi.

### Mục tiêu bài Present

- Tái hiện bug trên một project mới (Spring Boot đơn giản).
- Hiểu **root cause**: Xung đột giữa Lombok, MapStruct và JavaBeans Spec.
- Rút ra bài học về Naming Convention trong Java.

---

## 2. Chuẩn bị Project Demo

### Dependencies cần có (pom.xml)

```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>4.0.6</spring-boot.version>
    <mapstruct.version>1.6.3</mapstruct.version>
</properties>

<dependencies>
    <!-- Spring Boot Starter -->
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

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- MapStruct -->
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>${mapstruct.version}</version>
    </dependency>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct-processor</artifactId>
        <version>${mapstruct.version}</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Plugin cần có (để Lombok + MapStruct cùng hoạt động)

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <source>21</source>
                <target>21</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${lombok.version}</version>
                    </path>
                    <!-- Lombok-MapStruct binding (BẮT BUỘC) -->
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok-mapstruct-binding</artifactId>
                        <version>0.2.0</version>
                    </path>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>${mapstruct.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## 3. Bước 1: Tạo code "Bình thường"

### Entity (có prefix `u`)

```java
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Product {

    private Integer uProductId;    // ⚠️ prefix "u" + viết hoa chữ tiếp theo
    private String uProductName;   // ⚠️ prefix "u"
    private String uDescription;   // ⚠️ prefix "u"
    private Double uPrice;         // ⚠️ prefix "u"
    private String category;       // ✅ bình thường
}
```

### DTO (không có prefix `u`)

```java
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductRequestDto {

    private Integer productId;
    private String productName;
    private String description;
    private Double price;
    private String category;
}
```

### MapStruct Converter (KHÔNG có @Mapping thủ công)

```java
@Mapper(componentModel = "spring")
public interface ProductConverter {

    // ❌ KHÔNG viết @Mapping thủ công — để MapStruct tự mapping
    Product toEntity(ProductRequestDto dto);

    ProductRequestDto toDto(Product entity);
}
```

---

## 4. Bước 2: Tái hiện Bug

### Chạy `mvn compile` và kiểm tra kết quả

Mở file được MapStruct gen ra tại:
```
target/generated-sources/annotations/.../ProductConverterImpl.java
```

### Kết quả THỰC TẾ (Bug xuất hiện):

```java
// File: ProductConverterImpl.java (do MapStruct tự generate)
@Override
public Product toEntity(ProductRequestDto dto) {
    if (dto == null) {
        return null;
    }

    Product product = new Product();

    // ✅ Field bình thường: mapping ĐÚNG
    product.setCategory(dto.getCategory());

    // ❌ Các field prefix "u": KHÔNG ĐƯỢC MAPPING!
    // product.setUProductId(...)   → KHÔNG CÓ
    // product.setUProductName(...) → KHÔNG CÓ
    // product.setUDescription(...) → KHÔNG CÓ
    // product.setUPrice(...)       → KHÔNG CÓ

    return product;
}
```

### Kết quả: Tất cả field có prefix `u` đều bị **NULL**! 🔥

---

## 5. Bước 3: Quá trình điều tra

### Bước 3.1: Kiểm tra Lombok gen ra cái gì?

Mở IntelliJ → **View > Tool Windows > Structure** (hoặc `Alt+7`) để xem các method mà Lombok tự generate:

```java
// Lombok gen ra cho field: private Integer uProductId
public Integer getUProductId() { ... }  // ← Chữ "U" viết HOA
public void setUProductId(Integer uProductId) { ... }
```

### Bước 3.2: Kiểm tra MapStruct đang tìm gì?

MapStruct tuân thủ **JavaBeans Specification**, nó kỳ vọng:

```java
// MapStruct kỳ vọng cho field: uProductId
public Integer getuProductId() { ... }  // ← Chữ "u" viết THƯỜNG
public void setuProductId(Integer uProductId) { ... }
```

### Bước 3.3: Phát hiện xung đột! ⚡

| Thành phần  | Getter kỳ vọng       | Thực tế (Lombok)      | Khớp? |
|-------------|----------------------|----------------------|-------|
| **Lombok**  | —                    | `getUProductId()`    | —     |
| **MapStruct** | `getuProductId()`  | `getUProductId()`    | ❌ **KHÔNG** |
| **JPA**     | `getuProductId()`    | `getUProductId()`    | ❌ **KHÔNG** |
| **Jackson** | Tùy cấu hình         | `getUProductId()`    | ⚠️ **Có thể sai** |

---

## 6. Bước 4: Chạm mốc "Eureka!" — JavaBeans Specification

### Quy tắc chính (JavaBeans Spec - Section 8.8)

> **Quy tắc đặc biệt**: Nếu ký tự đầu tiên của tên field là **lowercase** và ký tự thứ hai là **UPPERCASE**, thì **KHÔNG viết hoa** ký tự đầu khi tạo getter/setter.

### Ví dụ minh họa:

| Field name     | Getter đúng chuẩn JavaBeans | Getter do Lombok gen    | Match? |
|----------------|-----------------------------|------------------------|--------|
| `name`         | `getName()`                 | `getName()`            | ✅     |
| `firstName`    | `getFirstName()`            | `getFirstName()`       | ✅     |
| `URL`          | `getURL()`                  | `getURL()`             | ✅     |
| **`uProductId`** | **`getuProductId()`**     | **`getUProductId()`**  | ❌     |
| **`uStatus`**  | **`getuStatus()`**          | **`getUStatus()`**     | ❌     |
| **`xCoord`**   | **`getxCoord()`**           | **`getXCoord()`**      | ❌     |
| **`iOS`**      | **`getiOS()`**              | **`getIOS()`**         | ❌     |

### Kết luận Root Cause:

```
Lombok gen KHÔNG ĐÚNG chuẩn JavaBeans Spec
            +
MapStruct đọc ĐÚNG chuẩn JavaBeans Spec
            =
💥 Hai thư viện "nói hai ngôn ngữ khác nhau" → Bug!
```

---

## 7. Bước 5: Giải pháp

### Cách 1: Dùng `@Mapping` thủ công (Workaround — Đang áp dụng)

```java
@Mapper(componentModel = "spring")
public interface ProductConverter {

    @Mapping(target = "uProductId", source = "productId")
    @Mapping(target = "uProductName", source = "productName")
    @Mapping(target = "uDescription", source = "description")
    @Mapping(target = "uPrice", source = "price")
    Product toEntity(ProductRequestDto dto);
}
```

**Nhược điểm**: Mỗi khi thêm field mới phải nhớ thêm `@Mapping`, dễ quên.

---

### Cách 2: Dùng `@Accessors(fluent = true)` hoặc cấu hình `lombok.config`

Tạo file `lombok.config` ở root project:

```properties
# lombok.config
lombok.accessors.capitalization = beanspec
```

> ⚠️ **Lưu ý**: Option `beanspec` chỉ có từ **Lombok 1.18.30+**. Kiểm tra version trước khi dùng.

**Ưu điểm**: Fix một lần, ảnh hưởng toàn bộ project.
**Nhược điểm**: Có thể phá vỡ code cũ đang dựa vào getter kiểu `getU...`.

---

### Cách 3: Đổi Naming Convention (Best Practice — Khuyến nghị)

```java
// ❌ TRÁNH: prefix 1 ký tự + viết hoa
private Integer uProductId;
private String uStatus;
private String mValue;

// ✅ NÊN: Tên rõ ràng, không gây nhập nhằng
private Integer productId;
private String status;
private String value;

// ✅ HOẶC: Nếu cần prefix, dùng từ đầy đủ
private Integer uniqueProductId;
private String userStatus;
```

---

### So sánh 3 giải pháp:

| Tiêu chí          | Cách 1: `@Mapping` | Cách 2: `lombok.config` | Cách 3: Đổi tên |
|--------------------|---------------------|--------------------------|------------------|
| **Độ phức tạp**    | Thấp                | Trung bình               | Cao (refactor)   |
| **Rủi ro**         | Quên thêm mapping   | Phá code cũ              | Cần migration    |
| **Bền vững**       | ❌ Tạm thời          | ✅ Tốt                    | ✅ Tốt nhất       |
| **Áp dụng khi**    | Fix nhanh           | Project mới/nhỏ          | Greenfield       |

---

## 8. Key Takeaways

### Cho Developer:

1. **Naming Convention không chỉ để cho đẹp** — Nó ảnh hưởng trực tiếp đến cách các thư viện giao tiếp với nhau.
2. **Khi gặp lỗi lạ với auto-generate**, hãy kiểm tra file `.class` hoặc generated source thực tế.
3. **Tránh đặt tên field bắt đầu bằng 1 ký tự prefix** rồi viết hoa chữ tiếp theo (`uName`, `mValue`, `xCoord`).

### Cho Team:

4. **Hãy thống nhất Naming Convention** ngay từ đầu project và document rõ ràng.
5. **Hiểu rõ các quy chuẩn nền tảng** (JavaBeans Spec) giúp debug nhanh hơn 10x.
6. **Không có thư viện nào "thông minh" 100%** — Luôn verify output của auto-generated code.

---

## 📚 Tài liệu tham khảo

- [JavaBeans Specification (Oracle)](https://www.oracle.com/java/technologies/javase/javabeans-spec.html) — Section 8.8: Capitalization of inferred names
- [MapStruct Documentation](https://mapstruct.org/documentation/stable/reference/html/)
- [Lombok Changelog - `beanspec` option](https://projectlombok.org/changelog)
- [Stack Overflow: Lombok getter/setter naming](https://stackoverflow.com/questions/tagged/lombok+naming-conventions)

---

> **Nguồn gốc Bug**: Module `pricing_policy_management` — Pomina Backend Project
> **Tác giả**: Team Backend
> **Ngày tạo**: 24/04/2026
