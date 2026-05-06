# System Patterns

## Architecture

DIP-enforced Layered Architecture:

```
Controller (Glue) → Service Interface → Service Impl → Repository/Mapper
```

**Key Rule:** Orchestrator services (e.g., `TransferServiceImpl`) depend ONLY on service interfaces — never on repositories, mappers, or entities from other services.

## Key Patterns

- **Stateless REST API:** Using JWT for authentication.
- **Transaction Management:** `@Transactional` at Service layer only. Default: rollback on `RuntimeException` only (Ghost Transaction bug demo).
- **DTO Mapping:** MapStruct `@Mapper(componentModel = "spring")` — zero manual mapping.
- **Global Exception Handling:** Centralized through `@RestControllerAdvice` (`GlobalExceptionHandler`).
- **Validation:** JSR-303 Bean Validation (`@NotBlank`, `@NotNull`, `@DecimalMin`).
- **Zero-Data Logging:** No PII (account numbers, balances, amounts, IDs) in log statements. No `log.debug()` / `log.trace()`.
- **Interface-Impl Pattern:** All services follow `XxxService` (interface) + `XxxServiceImpl` (implementation).
- **No Magic Strings:** Use enums for status values (e.g., `ProductStatus.ACTIVE`).
- **Pagination:** List endpoints use `Pageable` + `@PageableDefault` — never `findAll()` without pagination.
- **Controller = Glue Only:** No `@Slf4j` on controllers unless actively logging. No business logic.

## Component Relationships

- `modules/` contains domain-specific logic (e.g., `product`, `transfer`, `fee`, `auth`).
- `common/` contains cross-cutting infrastructure (OpenAPI, SecurityFilterChain, Redis, Global Enums, ApiResult).
- **Dependency Direction:** `modules/` → `common/` (one-way only). `common/` NEVER imports from `modules/` (except `GlobalExceptionHandler` for cross-cutting exception handling).

### Auth Module Dependency Graph

```
AuthenticationController (glue, no business logic)
  ├→ AuthenticationService (interface)
  └→ UserService (interface)

AuthenticationServiceImpl
  ├→ AuthenticationManager (Spring Security)
  ├→ JwtService (modules/auth/security/) — token generation + verification
  └→ RedisTokenRepository — token blacklist

CustomJwtDecoder (implements Spring's JwtDecoder)
  └→ JwtService — signature verify + blacklist check

SecurityConfiguration (common/security/config/) — cross-cutting orchestration
  ├→ UserDetailsService (Spring interface → CustomUserDetailsService)
  ├→ JwtDecoder (Spring interface → CustomJwtDecoder)
  ├→ CustomAuthenticationEntryPoint (401 handler)
  ├→ CustomAccessDeniedHandler (403 handler)
  └→ PasswordEncoder (BCrypt)
```

### Transfer Module Dependency Graph

```
TransferController
  ├→ TransferService (interface)
  └→ AccountService (interface)

TransferServiceImpl (orchestrator)
  ├→ AccountService → AccountRepository + AccountMapper
  ├→ PartnerBankApiService → PartnerBankApiServiceImpl (mock)
  └→ TransactionService → TransactionRepository + TransactionMapper
```

### Product Module Dependency Graph

```
ProductController (glue, no @Slf4j)
  └→ ProductService (interface)
       └→ ProductServiceImpl
            ├→ ProductRepository (Spring Data JPA)
            └→ ProductMapper (MapStruct — BROKEN for u* fields)
```

## Core Infrastructure Patterns

- **API Response**: All endpoints return `ApiResult<T>` via factory methods (`success()`, `error()`). Renamed from `ApiResponse` to avoid conflict with Swagger's `@ApiResponse`.
- **API Constants**: Centralized endpoint management in `smartosc.conghung.common.constant.ApiConstant`. Uses nested static classes for module-specific paths (e.g., `ApiAuth`, `ApiProduct`, `ApiTransfer`) with consistent versioning (`VERSION_V1`).
- **Clean Code**: Centralized all API endpoints in `ApiConstant` to eliminate magic strings in Controllers and Security configuration.
- **Security**: Auth module owns ALL JWT/token logic. `common/security/` organized in sub-packages (`config/`, `handler/`) with Spring interface injection. Security exceptions handled at filter chain level (EntryPoint + AccessDeniedHandler), not `@ExceptionHandler`.
  - `config/` — `SecurityConfiguration` (SecurityFilterChain, AuthenticationManager, PasswordEncoder, `@EnableMethodSecurity`, `SessionCreationPolicy.STATELESS`)
  - `handler/` — `CustomAuthenticationEntryPoint` (401) + `CustomAccessDeniedHandler` (403) — registered at filter chain level
  - All JWT logic lives in `modules/auth/security/` (JwtService, CustomJwtDecoder, JwtInfo, TokenPayload)
  - SecurityConfiguration injects Spring interfaces (`UserDetailsService`, `JwtDecoder`) — never concrete auth classes
- **Redis**: `RedisConfiguration` lives in `common/config/` — general infrastructure, not security-specific.
- **Error Handling**: `ErrorCode` enum → `AppException` (RuntimeException) → `GlobalExceptionHandler`.
- **Exception Hierarchy**:
  - `AppException extends RuntimeException` — for internal errors, auto-rollback by Spring.
  - `BankTransferException extends Exception` — Checked Exception for partner bank errors (Ghost Transaction bug).
- **Swagger Documentation**: `@Operation` + `@ApiResponse` (repeatable) on controller endpoints. Some controllers use `@ApiResponses` wrapper with `@SuppressWarnings("java:S1710")`.

## Data Model Patterns

- **RBAC Core:** Tables prefix `tbl_` (e.g., `tbl_user`, `tbl_role`).
- **Bug Demo Scenarios:** `tbl_account`, `tbl_transaction` (Ghost Transaction) and `tbl_product` (Lombok-MapStruct).
- **Audit Fields:** Every table includes `created_at` and `updated_at`.
- **ID Generation:** Prefer **Named Sequences** (e.g., `TBL_PRODUCT_SEQ`) over `IDENTITY` for granular control in Oracle 23c.
- **Timestamps:** Use `@CreationTimestamp` / `@UpdateTimestamp` instead of `@PrePersist`.
