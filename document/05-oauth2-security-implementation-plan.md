# OAuth2 Resource Server Implementation Plan — Step-by-Step

## Mục tiêu (Goal)

Triển khai **Spring Security OAuth2 Resource Server** với JWT + Redis Token Revocation từ project tham khảo (`springboot` - Spring Boot 3.5.6) sang project `backend-debug` (Spring Boot 4.0.6 / Spring Framework 7 / Oracle 23c).

---

## Tổng quan Source vs Target

| Đặc điểm | Source (springboot) | Target (backend-debug) |
|:--|:--|:--|
| **Spring Boot** | 3.5.6 | **4.0.6** |
| **Spring Security** | 6.x | **7.0** |
| **Database** | MariaDB | **Oracle 23c** |
| **JSON** | Jackson 2.x | **Jackson 3.x** |
| **Package** | `org.example.springboot.security` | `smartosc.conghung` |
| **Architecture** | Flat `security/` package | Modular `core/security/` + `modules/auth/` |

---

## Phase 1: Dependencies & Infrastructure

### Step 1.1 — Cập nhật `pom.xml`

**Uncomment** `spring-boot-starter-security` (line 43-46) và thêm dependencies mới:

```xml
<!-- 1. Uncomment Security starter -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- 2. Thêm OAuth2 Resource Server -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<!-- 3. Thêm Redis cho Token Blacklisting -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- 4. Uncomment Security Test (line 78-82) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

> [!IMPORTANT]
> Nimbus JOSE+JWT được pull tự động bởi `spring-boot-starter-oauth2-resource-server`. Kiểm tra tương thích Jackson 3 bằng `mvn dependency:tree`.

### Step 1.2 — Cấu hình `application-dev.yaml`

Thêm config Redis và cập nhật JWT config:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
  threads:
    virtual:
      enabled: false  # Tắt khi debug transaction

jwt:
  secret-key: ${JWT_SECRET_KEY:a8XYXwRNUdTGPZMLsTj4gV7UfM9vQzcgRaMrMeo1PJTu2p9aDMSARuxAD/xEiIgfHQOsNEgRGHMugHTugicoJJOLQTp5SEemclXz8pb8LrE=}
  issuer: smartosc.conghung
  audience: backend-debug-api
```

> [!NOTE]
> Giữ lại `jwt.access-token` và `jwt.refresh-token` hiện tại hoặc chuyển sang dùng `jwt.secret-key` duy nhất (khuyến nghị dùng 1 key cho đơn giản theo mô hình source).

### Step 1.3 — Docker Compose (Redis)

Tạo hoặc cập nhật `docker-compose.yaml` tại root:

```yaml
services:
  redis:
    image: redis:7-alpine
    container_name: backend-debug-redis
    ports:
      - "6379:6379"
```

### Step 1.4 — Database Migration

Tạo `V5__create_users_table.sql`:

```sql
CREATE TABLE users (
    id          NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email       VARCHAR2(255) NOT NULL UNIQUE,
    password    VARCHAR2(255) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Sample user (password = BCrypt("password123"))
INSERT INTO users (email, password) VALUES (
    'admin@smartosc.com',
    '$2a$10$...'  -- Generate with BCryptPasswordEncoder(10)
);
```

---

## Phase 2: Core Security Layer (`core/security/`)

### Package Structure

```
smartosc/conghung/core/security/
├── config/
│   ├── SecurityConfiguration.java
│   ├── JwtDecoderConfiguration.java
│   └── RedisConfiguration.java
├── dto/
│   ├── JwtInfo.java
│   └── TokenPayload.java
└── service/
    └── JwtService.java
```

### Step 2.1 — `RedisConfiguration.java`

**File:** `core/security/config/RedisConfiguration.java`

```java
package smartosc.conghung.common.security.config;

@Configuration
public class RedisConfiguration {
    @Value("${spring.data.redis.host}")
    private String host;
    @Value("${spring.data.redis.port}")
    private int port;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(host, port)
        );
    }
}
```

**Mapping từ Source:**
- Source: `security/configuration/RedisConfiguration.java` → Giữ nguyên logic
- Thay đổi: Chỉ đổi package name

### Step 2.2 — `JwtService.java`

**File:** `core/security/service/JwtService.java`

Dựa trên `JwtService2.java` (version cải tiến) từ Source project:

```java
package smartosc.conghung.common.security.service;

@Service
@RequiredArgsConstructor
public class JwtService implements InitializingBean {
    private static final JWSAlgorithm SIGN_ALG = JWSAlgorithm.HS512;
    private static final long ACCESS_TOKEN_TTL = 30;
    private static final ChronoUnit ACCESS_TOKEN_UNIT = ChronoUnit.MINUTES;
    private static final long REFRESH_TOKEN_TTL = 14;
    private static final ChronoUnit REFRESH_TOKEN_UNIT = ChronoUnit.DAYS;

    @Value("${jwt.issuer:smartosc.conghung}")
    private String issuer;
    @Value("${jwt.audience:backend-debug-api}")
    private String audience;
    @Value("${jwt.secret-key}")
    private String secretKeyBase64;

    private SecretKey secretKey;
    private final RedisTokenRepository redisTokenRepository;

    @Override
    public void afterPropertiesSet() {
        // Decode base64 → bytes → SecretKey with validation
        // HS512 requires >= 64 bytes
    }

    public TokenPayload generateAccessToken(User user) { ...}

    public TokenPayload generateRefreshToken(User user) { ...}

    public boolean verifyToken(String token) throws ParseException, JOSEException { ...}

    public JwtInfo parseToken(String token) throws ParseException { ...}

    private TokenPayload generateToken(User user, long duration, ChronoUnit unit) { ...}
}
```

**Mapping từ Source:**
- Source: `JwtService.java` + `JwtService2.java` → Merge lấy best practices từ `JwtService2`
- Khác biệt chính:
  - Dùng `InitializingBean` để validate secret key length
  - Dùng `Clock.systemUTC()` thay vì `new Date()`
  - Thêm `issuer` và `audience` claims
  - Verify signature TRƯỚC check expiration (secure order)

### Step 2.3 — `JwtDecoderConfiguration.java`

**File:** `core/security/config/JwtDecoderConfiguration.java`

```java
package smartosc.conghung.common.security.config;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtDecoderConfiguration implements JwtDecoder {
    @Value("${jwt.secret-key}")
    private String secretKey;
    private final JwtService jwtService;
    private NimbusJwtDecoder nimbusJwtDecoder = null;

    @Override
    public Jwt decode(String token) throws JwtException {
        // 1. Verify token (expiration + blacklist + signature)
        // 2. Lazy init NimbusJwtDecoder with HS512
        // 3. Decode and return Jwt
    }
}
```

**Mapping từ Source:**
- Source: `security/configuration/JwtDecoderConfiguration.java` → Giữ nguyên logic
- Thay đổi: Package name, logging format

### Step 2.4 — `SecurityConfiguration.java`

**File:** `core/security/config/SecurityConfiguration.java`

```java
package smartosc.conghung.common.security.config;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private static final String[] AUTH_WHITELIST = {
            "/auth/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/**"
    };

    private final UserDetailServiceCustomizer userDetailsService;
    private final JwtDecoderConfiguration jwtDecoderConfiguration;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Spring Security 7.0: Strict Lambda DSL (NO .and())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(AUTH_WHITELIST).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoderConfiguration))
                );
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
```

**Mapping từ Source:**
- Source: `security/configuration/SecurityConfiguration.java`
- Khác biệt Spring Boot 4.0.6:
  - **Strict Lambda DSL** — `.and()` đã bị xóa hoàn toàn
  - `authorizeRequests()` → `authorizeHttpRequests()`
  - `antMatchers()` → `requestMatchers()`
  - Whitelist mở rộng: thêm Swagger, Actuator endpoints

### Step 2.5 — DTOs

**`core/security/dto/TokenPayload.java`:**

```java
@Getter @Setter @Builder
public class TokenPayload {
    private String token;
    private String jwtID;
    private Date expiredTime;
}
```

**`core/security/dto/JwtInfo.java`:**

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JwtInfo implements Serializable {
    private String jwtID;
    private Date issuedAt;
    private Date expirationTime;
}
```

**Mapping từ Source:** Giữ nguyên, chỉ đổi package.

---

## Phase 3: Auth Module (`modules/auth/`)

### Package Structure

```
smartosc/conghung/modules/auth/
├── controller/
│   └── AuthenticationController.java
├── dto/
│   ├── request/
│   │   ├── LoginRequestDto.java
│   │   └── UserCreateRequestDto.java
│   └── response/
│       ├── LoginResponseDto.java
│       └── UserCreateResponseDto.java
├── entity/
│   ├── User.java
│   └── RedisToken.java
├── mapper/
│   └── UserMapper.java
├── repository/
│   ├── UserRepository.java
│   └── RedisTokenRepository.java
└── service/
    ├── AuthenticationService.java
    ├── UserService.java
    └── impl/
        ├── AuthenticationServiceImpl.java
        ├── UserDetailServiceCustomizer.java
        └── UserServiceImpl.java
```

### Step 3.1 — `User.java` Entity

**File:** `modules/auth/entity/User.java`

```java
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getUsername() { return email; }
    // Other UserDetails methods...
}
```

**Mapping từ Source:**
- Source: `security/model/User.java`
- Khác biệt: `getUsername()` trả về `email` (Source trả về `""` — bug tiềm ẩn)
- Oracle: `GenerationType.IDENTITY` tương thích Oracle 23c

### Step 3.2 — `RedisToken.java`

**File:** `modules/auth/entity/RedisToken.java`

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@RedisHash("token-blacklist")
@Builder
public class RedisToken {
    @Id
    private String jwtID;

    @TimeToLive(unit = TimeUnit.SECONDS)
    private Long expiredTime;
}
```

**Mapping từ Source:**
- Source: `security/model/RedisToken.java`
- Khác biệt: Đổi `@RedisHash("RedisHas")` → `@RedisHash("token-blacklist")` (naming rõ ràng hơn)

### Step 3.3 — Repositories

**`UserRepository.java`:**

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
}
```

**`RedisTokenRepository.java`:**

```java
@Repository
public interface RedisTokenRepository extends CrudRepository<RedisToken, String> {
}
```

### Step 3.4 — DTOs (theo convention `backend-debug`)

```java
// LoginRequestDto.java
@Getter @Setter
public class LoginRequestDto {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}

// LoginResponseDto.java
@Getter @Setter @Builder
public class LoginResponseDto {
    private String accessToken;
    private String refreshToken;
}

// UserCreateRequestDto.java
@Getter @Setter
public class UserCreateRequestDto {
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 6)
    private String password;
}

// UserCreateResponseDto.java
@Getter @Setter @Builder
public class UserCreateResponseDto {
    private String email;
}
```

**Mapping từ Source:**
- Source: Không có validation → Target thêm `@Valid`, `@NotBlank`, `@Email`
- Naming convention: Thêm suffix `Dto` theo project pattern

### Step 3.5 — Services

**`UserDetailServiceCustomizer.java`:**

```java
@Service
@RequiredArgsConstructor
public class UserDetailServiceCustomizer implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) 
            throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
```

**`AuthenticationService.java` (Interface):**

```java
public interface AuthenticationService {
    LoginResponseDto login(LoginRequestDto request);
    void logout(String token);
}
```

**`AuthenticationServiceImpl.java`:**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RedisTokenRepository redisTokenRepository;

    @Override
    public LoginResponseDto login(LoginRequestDto request) {
        // 1. Authenticate via AuthenticationManager
        // 2. Generate access + refresh tokens
        // 3. Save refresh JTI to Redis
        // 4. Return LoginResponseDto
    }

    @Override
    public void logout(String token) {
        // 1. Parse token → JwtInfo
        // 2. Check expiration, check already blacklisted
        // 3. Calculate remaining TTL
        // 4. Save to Redis blacklist
    }
}
```

**`UserService.java` + `UserServiceImpl.java`:**

```java
public interface UserService {
    UserCreateResponseDto createUser(UserCreateRequestDto request);
}

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserCreateResponseDto createUser(UserCreateRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = User.builder()
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .build();
        User saved = userRepository.save(user);
        return UserCreateResponseDto.builder().email(saved.getEmail()).build();
    }
}
```

**Mapping từ Source:**
- Source: Không dùng interface → Target dùng `Service` + `impl/` pattern (theo convention hiện tại)
- Source: `throw new RuntimeException` → Target: `throw new AppException(ErrorCode.xxx)`

### Step 3.6 — `AuthenticationController.java`

```java
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Login / Logout endpoints")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "User login")
    public ApiResult<LoginResponseDto> login(
            @Valid @RequestBody LoginRequestDto request) {
        return ApiResult.success(authenticationService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout")
    public ApiResult<Void> logout(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        authenticationService.logout(token);
        return ApiResult.success("Logout successful", null);
    }

    @PostMapping("/register")
    @Operation(summary = "Create new user")
    public ApiResult<UserCreateResponseDto> register(
            @Valid @RequestBody UserCreateRequestDto request) {
        return ApiResult.success(userService.createUser(request));
    }
}
```

**Mapping từ Source:**
- Source: `AuthenticationController` + `UserController` riêng biệt
- Target: Gộp vào 1 controller `/auth/**`
- Thêm: `ApiResult` wrapper, `@Valid`, Swagger annotations

---

## Phase 4: Exception Handling Updates

### Step 4.1 — Cập nhật `GlobalExceptionHandler.java`

Thêm handler cho Security exceptions:

```java
@ExceptionHandler(AuthenticationException.class)
public ResponseEntity<ApiResult<Void>> handleAuthException(AuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiResult.error(ErrorCode.UNAUTHENTICATED.getMessage(),
            Map.of("code", ErrorCode.UNAUTHENTICATED.getCode())));
}

@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ApiResult<Void>> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResult.error(ErrorCode.UNAUTHORIZED.getMessage(),
            Map.of("code", ErrorCode.UNAUTHORIZED.getCode())));
}
```

---

## Phase 5: Swagger/OpenAPI Update

### Step 5.1 — Cập nhật `OpenApiConfig.java`

Đã có `bearerAuth` scheme → Chỉ cần verify scan đúng package `modules.auth`.

---

## Phase 6: Verification

### Step 6.1 — Compile & Start

```bash
mvn clean compile
mvn spring-boot:run -Pdev
```

### Step 6.2 — Functional Tests

| # | Test Case | Method | Endpoint | Expected |
|:--|:--|:--|:--|:--|
| 1 | Register user | POST | `/auth/register` | 200 + email |
| 2 | Login | POST | `/auth/login` | 200 + tokens |
| 3 | Access protected | GET | `/api/v1/products` | 200 with token |
| 4 | No token | GET | `/api/v1/products` | 401 |
| 5 | Expired token | GET | `/api/v1/products` | 401 |
| 6 | Logout | POST | `/auth/logout` | 200 |
| 7 | Reuse token | GET | `/api/v1/products` | 401 (blacklisted) |
| 8 | Redis check | CLI | `redis-cli KEYS *` | jti present |

### Step 6.3 — Debug Scenarios

- [ ] Ghost Transaction + Logout: Token blacklist trong `@Transactional`?
- [ ] Virtual Threads: Bật `spring.threads.virtual.enabled=true` → Connection pool exhaustion?

---

## File Creation Order (Thứ tự tạo file)

> [!TIP]
> Mỗi step phải compile thành công trước khi sang step tiếp theo.

| Order | File | Package |
|:--|:--|:--|
| 1 | `pom.xml` | (root) |
| 2 | `application-dev.yaml` | resources |
| 3 | `docker-compose.yaml` | (root) |
| 4 | `V5__create_users_table.sql` | resources/database |
| 5 | `TokenPayload.java` | `core/security/dto` |
| 6 | `JwtInfo.java` | `core/security/dto` |
| 7 | `RedisToken.java` | `modules/auth/entity` |
| 8 | `User.java` | `modules/auth/entity` |
| 9 | `UserRepository.java` | `modules/auth/repository` |
| 10 | `RedisTokenRepository.java` | `modules/auth/repository` |
| 11 | `RedisConfiguration.java` | `core/security/config` |
| 12 | `JwtService.java` | `core/security/service` |
| 13 | `UserDetailServiceCustomizer.java` | `modules/auth/service/impl` |
| 14 | `JwtDecoderConfiguration.java` | `core/security/config` |
| 15 | `SecurityConfiguration.java` | `core/security/config` |
| 16 | `LoginRequestDto.java` | `modules/auth/dto/request` |
| 17 | `LoginResponseDto.java` | `modules/auth/dto/response` |
| 18 | `UserCreateRequestDto.java` | `modules/auth/dto/request` |
| 19 | `UserCreateResponseDto.java` | `modules/auth/dto/response` |
| 20 | `AuthenticationService.java` | `modules/auth/service` |
| 21 | `AuthenticationServiceImpl.java` | `modules/auth/service/impl` |
| 22 | `UserService.java` (auth) | `modules/auth/service` |
| 23 | `UserServiceImpl.java` (auth) | `modules/auth/service/impl` |
| 24 | `AuthenticationController.java` | `modules/auth/controller` |
| 25 | `GlobalExceptionHandler.java` | `core/exception` (update) |

---

## Lưu ý Spring Boot 4.0.6 (Critical Notes)

> [!CAUTION]
> 1. **Strict Lambda DSL** — KHÔNG dùng `.and()` trong Security config
> 2. **Jackson 3** — Nimbus JOSE không serialize/deserialize qua Jackson → OK
> 3. **`spring-boot-starter-webmvc`** — Đã đúng tên (không phải `web`)
> 4. **Virtual Threads** — Ảnh hưởng ThreadLocal, connection pool
> 5. **Oracle IDENTITY** — `GenerationType.IDENTITY` hoạt động với Oracle 23c
