# Spring Boot 4.x Migration Guide — Hướng dẫn chuyển đổi từ Spring Boot 3.x

## 1. Bối cảnh (Context)

| Đặc điểm             | Project tham khảo | Project `backend-debug` |
| :------------------- | :---------------- | :---------------------- |
| **Spring Boot**      | 3.5.6             | **4.0.6**               |
| **Spring Framework** | 6.x               | **7.x**                 |
| **Spring Security**  | 6.x               | **7.0**                 |
| **Jakarta EE**       | 10                | **11**                  |
| **Java**             | 21                | 21                      |
| **JSON Library**     | Jackson 2.x       | **Jackson 3.x**         |

---

## 2. Breaking Changes Chi Tiết

### 2.1. Spring Security 7.0 — Strict Lambda DSL

**Thay đổi quan trọng nhất:** Phương thức `.and()` đã bị **xóa hoàn toàn** (không chỉ deprecated).

```diff
  // ❌ KHÔNG HOẠT ĐỘNG trong Spring Security 7.0
  http
      .csrf().disable()
-     .and()
      .authorizeRequests()
      .anyRequest().authenticated()
-     .and()
      .oauth2ResourceServer().jwt();

  // ✅ CÁCH VIẾT ĐÚNG cho Spring Security 7.0
  http
      .csrf(AbstractHttpConfigurer::disable)
      .authorizeHttpRequests(authorize -> authorize
          .requestMatchers(AUTH_WHITELIST).permitAll()
          .anyRequest().authenticated()
      )
      .oauth2ResourceServer(oauth2 -> oauth2
          .jwt(jwt -> jwt.decoder(jwtDecoderConfiguration))
      );
```

**API bị xóa:**

| API cũ (6.x)          | API mới (7.0)             | Ghi chú       |
| :-------------------- | :------------------------ | :------------ |
| `.and()`              | Lambda blocks             | Xóa hoàn toàn |
| `authorizeRequests()` | `authorizeHttpRequests()` | Xóa hoàn toàn |
| `antMatchers()`       | `requestMatchers()`       | Đã xóa từ 6.x |

### 2.2. Jackson 3.x (Thay đổi Default)

Spring Boot 4 chuyển sang **Jackson 3** làm thư viện JSON mặc định.

**Ảnh hưởng đến Security:**

- `SecurityJacksonModules` cần cập nhật nếu bạn dùng custom serialization.
- Nimbus JOSE+JWT (`nimbus-jose-jwt`) cần tương thích Jackson 3.

```diff
  // Jackson 2.x (cũ)
- import com.fasterxml.jackson.databind.ObjectMapper;
+ // Jackson 3.x (mới)
+ import tools.jackson.databind.ObjectMapper;
+ import tools.jackson.databind.json.JsonMapper;
```

> [!WARNING]
> Nếu bạn serialize/deserialize Token hoặc `Authentication` object vào Redis bằng Jackson,
> bạn **phải** kiểm tra tính tương thích với Jackson 3.
> Trong project tham khảo, `RedisToken` chỉ lưu `jti` (String) và `TTL` (Long),
> nên **không bị ảnh hưởng** bởi thay đổi Jackson.

### 2.3. Jakarta EE 11 Baseline

Spring Framework 7 yêu cầu **Jakarta EE 11**.

| Spec            | Phiên bản cũ | Phiên bản mới | Ảnh hưởng             |
| :-------------- | :----------- | :------------ | :-------------------- |
| Servlet         | 6.0          | **6.1**       | Filter chain behavior |
| JPA             | 3.1          | **3.2**       | Entity mapping        |
| Bean Validation | 3.0          | **3.1**       | `@Valid`, `@NotNull`  |
| JSON Binding    | 3.0          | **3.1**       | REST serialization    |

**Kiểm tra import:**

```java
// ✅ Đúng — Jakarta namespace (đã dùng từ Spring Boot 3.x)
import jakarta.persistence.Entity;
import jakarta.validation.Valid;

// ❌ Sai — javax namespace (xóa hoàn toàn trong Spring Framework 7)
import javax.persistence.Entity;     // REMOVED
import javax.annotation.PostConstruct; // REMOVED
```

> [!NOTE]
> Project `backend-debug` đã dùng `jakarta.*` từ Spring Boot 4.0.6, nên phần này **không cần thay đổi**.

### 2.4. JSpecify Null-Safety (Tính năng mới)

Spring Framework 7 hỗ trợ JSpecify annotations cho null-safety tại thời điểm compile.

```java
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public class JwtService {
    // Compiler sẽ cảnh báo nếu truyền null
    public TokenPayload generateAccessToken(@NonNull User user) { ... }

    // Cho phép null
    public @Nullable JwtInfo parseToken(@Nullable String token) { ... }
}
```

### 2.5. Built-in Resilience (Tính năng mới)

Spring Framework 7 có native `@Retryable`:

```java
import org.springframework.retry.annotation.Retryable;

@Service
public class JwtService {
    // Retry tự động 3 lần nếu Redis connection thất bại
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public boolean verifyToken(String token) { ... }
}
```

> [!IMPORTANT]
> Trong Spring Boot 3.x, bạn cần dependency `spring-retry` riêng.
> Trong Spring Boot 4.x, `@Retryable` là native trong `spring-core`.

---

## 3. Virtual Threads (Ảnh hưởng đến Debugging)

### 3.1. Virtual Threads là gì?

Spring Boot 4 hỗ trợ Java 21 Virtual Threads (Project Loom) — mặc định có thể được bật.

```yaml
# application.yaml
spring:
  threads:
    virtual:
      enabled: true # true = Virtual Threads | false = Platform Threads
```

### 3.2. Ảnh hưởng đến Security và Transaction

| Khía cạnh           | Platform Threads                        | Virtual Threads                                                |
| :------------------ | :-------------------------------------- | :------------------------------------------------------------- |
| **SecurityContext** | Lưu trong `ThreadLocal`                 | Lưu trong `ThreadLocal` (vẫn hoạt động)                        |
| **@Async**          | Cần `DelegatingSecurityContextRunnable` | Tự động qua `ScopedValue` (tương lai)                          |
| **Transaction**     | `@Transactional` binding to thread      | Cùng cơ chế, nhưng thread pool behavior khác                   |
| **Connection Pool** | Bị bottleneck khi hết thread            | Virtual Threads **nhiều hơn** → có thể exhaust connection pool |
| **Debug Scenario**  | Quen thuộc                              | `ThreadLocal` có thể leak giữa các virtual thread              |

> [!CAUTION]
> **Quan trọng cho dự án Debug Banking Transactions:**
> Virtual Threads tạo ra hàng nghìn thread nhẹ. Nếu mỗi thread mở 1 DB connection,
> bạn có thể **exhaust connection pool** (HikariCP mặc định 10 connections).
> Đây chính là một bug đáng debug trong Scenario mới!

### 3.3. Khuyến nghị cho `backend-debug`

```yaml
# Khi debug transaction scenarios: Tắt Virtual Threads để so sánh hành vi
spring:
  threads:
    virtual:
      enabled: false

# Khi debug connection pool exhaustion: Bật Virtual Threads
spring:
  threads:
    virtual:
      enabled: true
```

---

## 4. Dependency và Starter Rename

Spring Boot 4 rename một số starters:

| Spring Boot 3.x            | Spring Boot 4.x                     | Ghi chú         |
| :------------------------- | :---------------------------------- | :-------------- |
| `spring-boot-starter-web`  | `spring-boot-starter-webmvc`        | Project đã đúng |
| `spring-boot-starter-test` | `spring-boot-starter-webmvc-test`   | Project đã đúng |
| N/A                        | `spring-boot-starter-actuator-test` | Mới             |
| N/A                        | `spring-boot-starter-data-jpa-test` | Mới             |

> [!NOTE]
> Project `backend-debug` **đã sử dụng đúng tên** starter mới của Spring Boot 4.

---

## 5. Tóm tắt hành động (Action Summary)

| Hạng mục            | Cần thay đổi?       | Chi tiết                                |
| :------------------ | :------------------ | :-------------------------------------- |
| **Security DSL**    | ✅ Phải dùng Lambda | Không được dùng `.and()`                |
| **Jackson**         | ⚠️ Kiểm tra         | Nếu dùng custom serialization cho Redis |
| **Jakarta imports** | ❌ Không cần        | Đã dùng `jakarta.*`                     |
| **JSpecify**        | 🆕 Khuyến nghị      | Thêm null-safety annotations            |
| **@Retryable**      | 🆕 Có thể dùng      | Native, không cần dependency ngoài      |
| **Virtual Threads** | ⚠️ Cần kiểm soát    | Ảnh hưởng connection pool & debugging   |
| **Nimbus JOSE**     | ⚠️ Kiểm tra         | Tương thích Jackson 3                   |
