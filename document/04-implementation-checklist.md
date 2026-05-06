# Implementation Checklist — Triển khai Security cho `backend-debug`

## Tổng quan

Tài liệu này là checklist từng bước để triển khai OAuth2 Resource Server Security vào project `backend-debug` (Spring Boot 4.0.6 / Spring Framework 7 / Oracle).

---

## Phase 1: Dependencies & Configuration

### 1.1. Thêm dependencies vào `pom.xml`

- [ ] Thêm `spring-boot-starter-oauth2-resource-server`
- [ ] Thêm `spring-boot-starter-data-redis` (cho token blacklisting)
- [ ] Kiểm tra `nimbus-jose-jwt` tương thích Jackson 3 (transitive dependency)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 1.2. Cấu hình `application-dev.yaml`

- [ ] Thêm cấu hình Redis
- [ ] Thêm JWT secret key
- [ ] Cấu hình Virtual Threads (tuỳ mục đích debug)

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
  threads:
    virtual:
      enabled: false # Tắt khi debug transaction scenarios

jwt:
  secret-key: ${JWT_SECRET_KEY} # Sử dụng environment variable
```

### 1.3. Docker (Redis)

- [ ] Thêm Redis service vào `docker-compose.yaml`

```yaml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

---

## Phase 2: Security Core (Package `core/security`)

### 2.1. Package Structure (theo cấu trúc hiện tại)

```
src/main/java/smartosc/conghung/core/security/
├── config/
│   ├── SecurityConfiguration.java
│   ├── JwtDecoderConfiguration.java
│   └── RedisConfiguration.java
├── dto/
│   ├── JwtInfo.java
│   ├── TokenPayload.java
│   ├── request/
│   │   └── LoginRequest.java
│   └── response/
│       └── LoginResponse.java
└── service/
    └── JwtService.java
```

### 2.2. Checklist

- [ ] **`SecurityConfiguration.java`** — SecurityFilterChain (Strict Lambda DSL)
  - [ ] Disable CSRF
  - [ ] Whitelist `/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`
  - [ ] Configure `oauth2ResourceServer` with custom decoder
  - [ ] Configure `AuthenticationManager` with `DaoAuthenticationProvider`
  - [ ] Configure `BCryptPasswordEncoder(10)`

- [ ] **`JwtDecoderConfiguration.java`** — Custom JwtDecoder
  - [ ] Implement `JwtDecoder` interface
  - [ ] Inject `JwtService` for `verifyToken()` check
  - [ ] Lazy init `NimbusJwtDecoder` with HS512

- [ ] **`RedisConfiguration.java`** — Lettuce connection factory
  - [ ] Configure `LettuceConnectionFactory` with host/port

- [ ] **`JwtService.java`** — Token management
  - [ ] `generateAccessToken(User)` — 30 phút
  - [ ] `generateRefreshToken(User)` — 14 ngày
  - [ ] `verifyToken(String)` — Check expiration + blacklist + signature
  - [ ] `parseToken(String)` — Extract JwtInfo

- [ ] **DTOs**: `JwtInfo`, `TokenPayload`, `LoginRequest`, `LoginResponse`

---

## Phase 3: Auth Module (Package `modules/auth`)

### 3.1. Package Structure

```
src/main/java/smartosc/conghung/modules/auth/
├── controller/
│   └── AuthenticationController.java
└── service/
    ├── AuthenticationService.java
    └── impl/
        └── UserDetailServiceCustomizer.java
```

### 3.2. Checklist

- [ ] **`AuthenticationController.java`**
  - [ ] `POST /auth/login` → Trả về `LoginResponse`
  - [ ] `POST /auth/logout` → Nhận `Authorization` header, gọi `logout()`

- [ ] **`AuthenticationService.java`**
  - [ ] `login(LoginRequest)` → Authenticate + Generate tokens + Save refresh `jti` to Redis
  - [ ] `logout(String token)` → Parse + Check + Blacklist `jti` in Redis

- [ ] **`UserDetailServiceCustomizer.java`**
  - [ ] Implement `UserDetailsService`
  - [ ] Query User by email from `UserRepository`

---

## Phase 4: User Entity Integration

### 4.1. Checklist

- [ ] **`User.java`** — Entity implements `UserDetails`
  - [ ] `getAuthorities()` → Return roles/permissions
  - [ ] `getUsername()` → Return email
  - [ ] Ensure `@Column(unique = true)` on email

- [ ] **`RedisToken.java`** — Redis Hash model
  - [ ] `@RedisHash` với key prefix
  - [ ] `@Id` trên `jwtID`
  - [ ] `@TimeToLive(unit = TimeUnit.SECONDS)` trên `expiredTime`

- [ ] **`RedisTokenRepository.java`** — `CrudRepository<RedisToken, String>`

---

## Phase 5: Verification

### 5.1. Functional Testing

- [ ] **Login**: `POST /auth/login` → Nhận `accessToken` + `refreshToken`
- [ ] **Access Protected**: `GET /api/*` với `Authorization: Bearer <accessToken>` → 200 OK
- [ ] **Without Token**: `GET /api/*` không có header → 401 Unauthorized
- [ ] **Expired Token**: Dùng token đã hết hạn → 401 Unauthorized
- [ ] **Logout**: `POST /auth/logout` → OK, sau đó dùng lại token → 401 (blacklisted)
- [ ] **Redis Check**: Kiểm tra `jti` xuất hiện trong Redis sau logout

### 5.2. Integration with Debug Scenarios

- [ ] **Ghost Transaction Bug**: Logout trong `@Transactional` → Token có bị blacklist không?
- [ ] **Virtual Threads**: Bật/tắt virtual threads → So sánh `SecurityContext` propagation
- [ ] **Connection Pool**: "Nhiều request đồng thời + Virtual Threads → Connection pool exhaustion?"

---

## Lưu ý quan trọng (Important Notes)

> [!CAUTION]
> **Khác biệt với project tham khảo (Spring Boot 3.x):**
>
> 1. Phải dùng **Strict Lambda DSL** — không có `.and()`
> 2. Kiểm tra **Jackson 3** compatibility cho Nimbus JOSE
> 3. **Virtual Threads** ảnh hưởng đến `ThreadLocal` và connection pool
> 4. Starter names đã đổi: `spring-boot-starter-webmvc` (không phải `web`)

> [!TIP]
> **Khi triển khai, nên làm theo thứ tự Phase:**
> Phase 1 (Config) → Phase 2 (Core Security) → Phase 3 (Auth Module) → Phase 4 (Entity) → Phase 5 (Test)
> Mỗi phase phải compile thành công trước khi chuyển sang phase tiếp theo.
