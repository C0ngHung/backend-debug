# Progress

## What Works

- Project structure and core infrastructure setup.
- Oracle 23c Free database configuration in `application-dev.yaml`.
- Schema (9 tables) in `V1__init_database.sql`, sample data in `V2__insert_sample_data.sql`.
- Oracle setup guide in `document/oracle-23c-free-setup-guide.md`.
- **Phase 1 Core Infrastructure (Complete):**
  - `ApiResult.java` — Standard response wrapper (renamed from `ApiResponse` to avoid Swagger conflict).
  - `ErrorCode.java` — Centralized error codes (incl. Product codes 6001, 6002).
  - `AppException.java` — Custom RuntimeException.
  - `GlobalExceptionHandler.java` — Centralized exception handler (`@Order(2)`, handles AppException, validation, and generic exceptions).
  - `OpenApiConfig.java` — Fixed package scan path.
  - **Database Migration:** Refactored `TBL_PRODUCT` to use named sequence `TBL_PRODUCT_SEQ`.
- Bug presentation scripts: 6 total (3 to demo: Lombok-MapStruct, Ghost Transaction, Phantom Fee).
- **Phase 2 — Transfer Module (Complete, 19 files):**
  - **Controller:** `TransferController` — glue only, repeatable `@ApiResponse`, typed `ApiResult<T>`.
  - **Service Interfaces:** `TransferService`, `AccountService`, `TransactionService`, `PartnerBankApiService`.
  - **Service Impls:** `TransferServiceImpl` (pure orchestration, zero repo imports), `AccountServiceImpl`, `TransactionServiceImpl`, `PartnerBankApiServiceImpl` (mock).
  - **DTOs:** `TransferRequestDto` (Bean Validation), `TransferResponseDto`, `AccountBalanceResponseDto`.
  - **Mappers:** `AccountMapper`, `TransactionMapper` (MapStruct, compile-time).
  - **Entities:** `Account`, `Transaction` (`@CreationTimestamp`).
  - **Repositories:** `AccountRepository`, `TransactionRepository`.
  - **Exception:** `BankTransferException` (Checked — core of Ghost Transaction bug).
  - **Debug Guide:** `bugs/debug-guide-ghost-transaction.md` with Architecture Overview diagram.
- **Phase 2 — Fee Module (Complete, 7 files):**
  - **Controller:** `FeeController` — 3 endpoints (generate-batch, batch, calculate).
  - **Service Interfaces:** `FeeCalculationService`, `FeeBatchGeneratorService`.
  - **Service Impls:** `FeeCalculationServiceImpl` (BigDecimal.equals() bug), `FeeBatchGeneratorServiceImpl` (1000 records + 5 hidden bombs).
  - **DTOs:** `FeeRequestDto` (Bean Validation), `FeeResponseDto` (batch summary).
  - **No DB required** — fully in-memory batch processing.
  - **Debug Guide:** Updated `bugs/debug-presentation-phantom-fee.md`.
- **Phase 2 — Product Module (Complete, 12 new files + 1 modified):**
  - **Database:** `V3__create_product_table.sql` (TBL_PRODUCT + TBL_PRODUCT_SEQ), `V4__insert_product_sample_data.sql` (5 records).
  - **Controller:** `ProductController` — 4 endpoints (create, getById, getAll with Pageable, getByCategory), Swagger `@ApiResponses` wrapper, `ApiResult<T>`, no `@Slf4j`.
  - **Service Interface:** `ProductService` (4 methods, `getAllProducts` uses `Page<T>` + `Pageable`).
  - **Service Impl:** `ProductServiceImpl` — constructor injection, `@Transactional`, Zero-Data Logging, `ProductStatus` enum.
  - **DTOs:** `ProductRequestDto` (Bean Validation), `ProductResponseDto`.
  - **Mapper:** `ProductMapper` (MapStruct — INTENTIONALLY BROKEN, no `@Mapping` for u* fields).
  - **Entity:** `Product` — u-prefix fields (`uProductName`, `uDescription`, `uPrice`) + normal `category`.
  - **Repository:** `ProductRepository` — `findByCategory()`.
  - **Enum:** `ProductStatus` — eliminates magic strings `"ACTIVE"` / `"INACTIVE"`.
  - **Bug Verified:** `ProductMapperImpl.java` only maps `category`, `id`, `status`. All u* fields → null.
- **Phase 2 — Ghost Double Debit (Complete):**
  - **Debug Presentation:** `bugs/debug-presentation-ghost-double-debit.md` (Boss level: Timeout + Retry + Idempotency).
  - **Patterns Covered:** Value Object, Independent Transaction Propagation, UNKNOWN state handling.
  - **Flow Comments (2026-05-05):** Vietnamese comments added to `IdempotencyServiceImpl`, `CoreBankingServiceImpl`, `TransferServiceImpl` — explaining the full orchestration flow, BUG root cause (strip() mismatch), and timeout behavior.

## Left to Build

- Phase 3: Security layer — refresh token endpoint, RBAC roles.
- Nice-to-have: Convert `JwtInfo`/`TokenPayload` to Java `record`. Create `JwtProperties` with `@ConfigurationProperties`.
- Integration tests.

## Completed (Phase 3 — Security Restructuring)

- **Security audit & refactoring** — Eliminated circular dependency between `common/security` ↔ `modules/auth`.
- JWT infrastructure (JwtService, CustomJwtDecoder, DTOs) moved to `modules/auth/security/`.
- `SecurityConfiguration` refactored to inject Spring interfaces only.
- `RedisConfiguration` moved to `common/config/`.
- `UserDetailServiceCustomizer` → `CustomUserDetailsService` (naming convention).
- Fixed thread-safety bug in JWT decoder (race condition on lazy init).
- Fixed typo `HmacSHE512` → `HmacSHA512`.
- Fixed login logic: removed incorrect refresh token blacklisting on creation.

## Completed (Phase 3.2 — Handler Pattern Adoption)

- **CustomAuthenticationEntryPoint** — 401 handler at SecurityFilterChain level.
- **CustomAccessDeniedHandler** — 403 handler at SecurityFilterChain level.
- **@EnableMethodSecurity** — enables @PreAuthorize for RBAC.
- **SessionCreationPolicy.STATELESS** — explicit stateless session for JWT API.
- SecurityConfiguration moved to `common/security/config/` sub-package.
- Removed security handlers from GlobalExceptionHandler (wrong layer).
## Completed (Phase 3.3 — Code Audit & Clean Code Fixes)

- **DIP fully enforced**: `BankTransferException` handler moved to `TransferExceptionHandler` (`@Order(1)`) inside `modules/transfer/exception/`. `GlobalExceptionHandler` (`@Order(2)`) now has ZERO imports from `modules/`.
- **Transfer enums**: Created `AccountStatus` + `TransactionStatus` in `modules/transfer/enums/`. Eliminated `"SUCCESS"` magic string.
- **MapStruct warning fixed**: Added `@Mapping(source = "createdAt", target = "timestamp")` in `TransactionMapper`. Removed `LocalDateTime.now()` default from `TransferResponseDto`.
- **RedisConfiguration**: Refactored to `@Bean` method param injection (no field injection).
- **ProductServiceImpl**: Class-level `@Transactional(readOnly = true)` default.
- **PartnerBankApiServiceImpl**: Added `@Override`.
- **Controllers standardized**: All have `@Slf4j(topic)`, full `@ApiResponse` annotations, `ApiConstant` paths.
- **Deleted**: Empty `common/enums/` directory.
- **Build**: 0 errors, TransactionMapper warning eliminated. Only intentional ProductMapper warnings remain.

## Known Issues

- `PartnerBankApiServiceImpl` condition `startsWith("PARTNER")` blocks all PARTNER accounts — toggle to `"PARTNER_NONE"` for happy path testing.
- `ProductMapper` intentionally broken — u* fields are null due to Lombok/JavaBeans naming conflict. Fix with `@Mapping` annotations or `lombok.config`.
