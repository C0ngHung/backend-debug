# Active Context

## Current Status

- Phase 1 (Core Infrastructure) is **Complete** on branch `feature/BUG-002-core-infrastructure`.
- Phase 2 (Bug Demo Modules) is **Complete**.
  - **`transfer` module** — ✅ **Complete** on branch `feature/BUG-003-api-transfer`. Fully refactored to SOLID/DIP best practices.
  - **`fee` module** — ✅ **Complete** on branch `feature/BUG-004-api-fee-transaction`. 7 files, no DB, 1000-record batch + 5 hidden BigDecimal bombs.
  - **`product` module** — ✅ **Complete** on branch `feature/BUG-005-api-product`. 12 new files + 1 modified, Lombok-MapStruct bug confirmed.
  - **Ghost Double Debit** — ✅ **New presentation added** in `bugs/debug-presentation-ghost-double-debit.md`.
- Phase 3 (Security Layer) — **In Progress**.
  - **Security restructuring** — ✅ **Complete**. Full audit + refactoring of `common/security` and `modules/auth`.

## Recent Changes (Session 2026-05-05)

### Ghost Double Debit — Flow-Explaining Comments

Added detailed Vietnamese comments inside all methods of the 3 core service implementations for the Ghost Double Debit lab. This is educational documentation — comments explain the **why** and **flow**, not obvious code.

**Files commented:**
- `IdempotencyServiceImpl` — Explains idempotency key creation, REQUIRES_NEW transaction isolation, and PROCESSING → SUCCESS / UNKNOWN state machine.
- `CoreBankingServiceImpl` — Explains the intentional BUG (strip() mismatch between App and Core idempotency keys), sleep() simulating network latency causing timeout, and the Ghost Double Debit root cause.
- `TransferServiceImpl` — Explains the orchestration flow: normalize ref → reserve idempotency → call Core Banking → handle success/timeout.

**Key insight documented in comments:**
- `ExternalReference.from()` strips trailing spaces (Value Object normalization)
- `buildCoreIdempotencyKey()` also strips → but App layer key may retain CHAR(16) padding
- When `coreDelayMillis > readTimeout (2000ms)` → client gets `ResourceAccessException` but Core still commits the debit → Ghost Double Debit

## Previous Changes (Session 2026-05-01)

### Security Module Restructuring — Phase 2 (Handler Pattern Adoption)

Adopted 3 patterns from reference project (`backend-service`):
1. **`CustomAuthenticationEntryPoint`** — handles 401 at SecurityFilterChain level (replaces `@ExceptionHandler` which can't catch security exceptions outside DispatcherServlet)
2. **`CustomAccessDeniedHandler`** — handles 403 at SecurityFilterChain level
3. **`@EnableMethodSecurity`** + **`SessionCreationPolicy.STATELESS`** — enables `@PreAuthorize` for RBAC + explicit stateless session

Structural changes:
- `SecurityConfiguration` → moved to `common/security/config/` (sub-package pattern matching reference)
- New `common/security/handler/` package for security handlers
- Removed `AuthenticationException` + `AccessDeniedException` handlers from `GlobalExceptionHandler`
- Build verified: `mvn clean compile` ✅ SUCCESS

### API Constants Centralization

Refactored all magic strings in controller request mappings into a centralized constant class:
- `ApiConstant.java` — Core registry for all endpoints.
- Nested classes for `ApiAuth`, `ApiProduct`, and `ApiTransfer`.
- Versioning managed via `VERSION_V1 = "/api/v1"`.
- Updated `SecurityConfiguration` to use `ApiConstant` for the auth whitelist.

### Full Code Audit & Clean Code Fixes

Performed comprehensive audit of all 40+ source files. Fixed 7 issues:
- **P0**: Moved `BankTransferException` handler from `GlobalExceptionHandler` to `TransferExceptionHandler` (DIP fix — `common/` no longer imports `modules/`).
- **P0**: Deleted empty `common/enums/` directory.
- **P1**: Created `AccountStatus` + `TransactionStatus` enums in `modules/transfer/enums/`. Replaced `"SUCCESS"` magic string.
- **P1**: Fixed `TransferResponseDto.timestamp` mapping (`@Mapping(source = "createdAt", target = "timestamp")`) — eliminated MapStruct warning.
- **P1**: Refactored `RedisConfiguration` to inject `@Value` via `@Bean` method params (no more field injection).
- **P2**: Applied class-level `@Transactional(readOnly = true)` on `ProductServiceImpl`.
- **P3**: Added missing `@Override` on `PartnerBankApiServiceImpl`.
- **Controllers**: Added `@Slf4j` to all controllers, standardized Swagger `@ApiResponse` annotations.
- Build: 0 errors, 0 new warnings. TransactionMapper warning eliminated.

**Problem:** Circular dependency between `common/security/` and `modules/auth/`. `JwtService`, `JwtDecoderConfiguration`, and DTOs in `common/` were importing from `modules/auth/` (User entity, RedisTokenRepository) — violating DIP.

**Changes:**
- **Moved** `JwtService`, `JwtInfo`, `TokenPayload` → `modules/auth/security/` (auth domain owns JWT logic)
- **Created** `CustomJwtDecoder` replacing `JwtDecoderConfiguration`:
  - Fixed typo: `"HmacSHE512"` → `"HmacSHA512"` 
  - Fixed thread-safety: eager init via `@PostConstruct` instead of lazy init race condition
  - Renamed to accurately reflect it's an implementation, not a configuration
  - Single source of truth for secret key via `JwtService.getSecretKey()`
- **Refactored** `SecurityConfiguration`:
  - Package: `common.security.config` → `common.security` (flattened)
  - Injects Spring interfaces (`UserDetailsService`, `JwtDecoder`) instead of concrete auth classes
  - Zero imports from `modules/` — circular dependency eliminated
- **Moved** `RedisConfiguration` → `common/config/` (general infrastructure, not security-specific)
- **Renamed** `UserDetailServiceCustomizer` → `CustomUserDetailsService` (Spring naming convention)
- **Fixed** login logic: removed incorrect refresh token blacklisting on creation
- **Deleted** empty `modules/auth/mapper/` directory
- **Deleted** old `common/security/config/`, `common/security/dto/`, `common/security/service/` directories

**Verified:** `mvn clean compile` ✅ SUCCESS. Grep for `import smartosc.conghung.modules` in `common/` returns only `GlobalExceptionHandler` → `BankTransferException` (acceptable, out of scope).

## Active Decisions

- Branch Strategy: Each feature/bug gets its own branch with ticket ID.
- Code Style: No Javadoc blocks; self-documenting code. **Exception:** Flow-explaining Vietnamese comments allowed in educational/lab modules (e.g., Ghost Double Debit) to help learners understand complex patterns.
- Architecture: DIP enforced — `common/` NEVER imports from `modules/` (except GlobalExceptionHandler for cross-cutting exception handling).
- Mapping: MapStruct for all entity↔DTO conversions (no `@Builder` for DTOs).
- Response Wrapper: `ApiResult<T>` (renamed from `ApiResponse` to avoid Swagger `@ApiResponse` import conflict).
- Logging: Zero-Data Logging — no PII, no IDs, no debug/trace level.
- Exception: `BankTransferException` (Checked) kept separate from `AppException` (Runtime) for Ghost Transaction demo.
- Pagination: All list endpoints use `Pageable` — never `findAll()` without it.
- Swagger Annotations: `TransferController` uses repeatable `@ApiResponse` (modern). `ProductController` uses `@ApiResponses` wrapper + `@SuppressWarnings("java:S1710")` (legacy style).
- No Magic Strings: Status values use domain-specific enums (`ProductStatus`, `AccountStatus`, `TransactionStatus`). API endpoints use `ApiConstant`.
- Security: Auth module owns ALL JWT/token logic. `common/security/` organized in sub-packages (`config/`, `handler/`) with Spring interface injection. Security exceptions handled at filter chain level (EntryPoint + AccessDeniedHandler), not `@ExceptionHandler`.
- DIP Fully Enforced: `common/` has ZERO imports from `modules/`. `BankTransferException` handler lives in `modules/transfer/exception/TransferExceptionHandler`.
- Transaction: Class-level `@Transactional(readOnly = true)` default on service impls; explicit `@Transactional` override on write methods.

## Next Steps

- Demo Bug "Ghost Transaction" by toggling `PartnerBankApiServiceImpl` condition.
- Demo Bug "Lombok-MapStruct" by calling `POST /api/v1/products` and observing null fields.
- Implement refresh token endpoint in auth module.
- Nice-to-have: Convert `JwtInfo`/`TokenPayload` to Java `record`. Create `JwtProperties` with `@ConfigurationProperties`.
- Integration tests.
