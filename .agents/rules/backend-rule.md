---
trigger: always_on
---

# CURSOR INSTRUCTIONS — BACKEND RULES (JAVA + SPRING BOOT)

## 3. Back-End Rules (Java / Spring Boot)

### Technology Stack

- Java 17+ (Consider Java 21 LTS)
- Spring Boot 4.0.6
- JPA/Hibernate
- Oracle

### Layered Architecture

```
controller → service → repository/mapper → entity → dto → mapper → database
```

---

## 3.1 DTO Rules (IMPORTANT)

### Always use **class** for all DTOs

- Request DTO
- Response DTO
- All data transfer objects
- Use Lombok: @Getter, @Setter
- Add @Builder only for complex DTOs with many fields

---

## 3.2 Additional Backend Conventions

- Use **constructor injection only**.
- Use Lombok (@Builder, @Getter, @Setter) for entities or mutable DTOs.
- No business logic inside controllers or repositories.
- Apply validation:
  - @Valid, @NotNull, @NotEmpty, @Size, @Pattern
- Error handling:
  - Use @ControllerAdvice + custom @ExceptionHandler.
- Database rules:
  - Prefer JPA for CRUD
  - Use MyBatis for explicit SQL
  - Avoid N+1 queries using fetch join or EntityGraph

---

## 3.3 Security Rules

- Always use Spring Security for authentication/authorization
- Use JWT with refresh token pattern
- Implement rate limiting for sensitive endpoints
- Validate all inputs with @Valid + custom validators
- Never log sensitive data (passwords, tokens, PII)
- Use @PreAuthorize for method-level security
- Enable CORS policy explicitly
- Use PasswordEncoder (BCrypt) for passwords
- Implement CSRF protection for stateful apps
- Sanitize all user inputs to prevent injection attacks

---

## 3.4 Transaction Management

- Use @Transactional only in service layer
- Default: @Transactional(readOnly = true) for queries
- Specify isolation level for critical operations:
  - READ_COMMITTED (default)
  - REPEATABLE_READ (for consistency)
  - SERIALIZABLE (for critical financial operations)
- Avoid long-running transactions
- Never use @Transactional at controller layer
- Use appropriate propagation levels:
  - REQUIRED (default)
  - REQUIRES_NEW (for independent transactions)
  - NESTED (for savepoints)

---

## 3.5 Database Best Practices

- Always use database migrations (Flyway or Liquibase)
- Index foreign keys and frequently queried columns
- Use database constraints (UNIQUE, CHECK, FK)
- Soft delete pattern: use `deleted_at` timestamp column
- Audit fields in all tables:
  - `created_at` (timestamp)
  - `created_by` (user reference)
  - `updated_at` (timestamp)
  - `updated_by` (user reference)
- Pagination: always use `Pageable` with proper sorting
- Connection pool configuration (HikariCP):
  - `maximum-pool-size: 10`
  - `connection-timeout: 30000`
  - `idle-timeout: 600000`
- Never use `SELECT *` in production queries
- Use database views for complex queries
- Optimize queries with EXPLAIN ANALYZE

---

## 8. Code Quality

- Descriptive & consistent naming conventions:
  - camelCase for variables/functions
  - PascalCase for classes/components
  - UPPER_SNAKE_CASE for constants
- Avoid magic numbers (use named constants)
- Avoid deep nesting (max 3 levels) → use early returns
- Comments only for complex domain logic or "why" explanations
- Follow Prettier/ESLint formatting (auto-format on save)
- Maximum function length: 50 lines
- Maximum file length: 500 lines
- Cyclomatic complexity: max 10 per function

---

## 11. Testing Rules (CRITICAL)

### Backend Testing (Java/Spring Boot)

- **Unit Tests:**
  - Service layer must have 80%+ code coverage
  - Use Mockito for mocking dependencies
  - Test naming convention: `shouldReturnXxx_WhenYyy_GivenZzz`
  - Example: `shouldReturnUser_WhenValidId_GivenUserExists()`
- **Integration Tests:**
  - Use `@SpringBootTest` with Testcontainers for real database
  - Test full request-response flow
  - Use `@DataJpaTest` for repository tests
- **Test database migrations:**
  - Validate Flyway/Liquibase scripts in CI/CD
- **Coverage target:** 80% minimum for services, 60% overall
- **Assertions:** Use AssertJ for fluent assertions

### Testing Principles

- AAA pattern: Arrange, Act, Assert
- One assertion per test (when possible)
- Test edge cases and error scenarios
- Don't test library code (Spring Boot internals)

---

## 12. API Design Rules

### REST Conventions

- Use proper HTTP methods:
  - `GET` - Retrieve resources
  - `POST` - Create resources
  - `PUT` - Full update
  - `PATCH` - Partial update
  - `DELETE` - Remove resources

### Standard Response Format

**Success Response:**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "John Doe"
  },
  "message": "Operation successful",
  "timestamp": "2025-11-26T10:00:00Z"
}
```

**Error Response:**

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input data",
    "details": [
      {
        "field": "email",
        "message": "Email format is invalid"
      }
    ]
  },
  "timestamp": "2025-11-26T10:00:00Z"
}
```

### Pagination

- Use query parameters: `page`, `size`, `sort`
- Example: `/api/v1/users?page=0&size=20&sort=createdAt,desc`
- Response includes metadata:
  ```json
  {
    "content": [...],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "totalPages": 5,
      "totalElements": 100
    }
  }
  ```

### API Versioning

- URL versioning: `/api/v1/resource`
- Never break backward compatibility within a version
- Deprecation warnings in headers: `X-API-Deprecation`

### Rate Limiting

- Include headers:
  - `X-RateLimit-Limit: 100`
  - `X-RateLimit-Remaining: 95`
  - `X-RateLimit-Reset: 1640000000`
- Return `429 Too Many Requests` when exceeded

### Documentation

- Use OpenAPI 3.0 (Swagger)
- Document all endpoints, parameters, responses
- Include example requests/responses
- Keep documentation in sync with code

---

## 14. Logging Rules

### Backend Logging (Java/Spring Boot)

- Use **SLF4J** + **Logback**
- **Log Levels:**
  - `ERROR` - Exceptions affecting users, system errors
  - `WARN` - Recoverable issues, deprecated usage
  - `INFO` - Important business events (login, order placed)
  - `DEBUG` - Detailed flow for debugging (dev/staging only)
  - `TRACE` - Very detailed debugging (rarely used)

**Best Practices:**

- Never log sensitive data:
  - Passwords, tokens, API keys
  - Credit card numbers, SSN
  - Personally Identifiable Information (PII)
- Include correlation/request ID for distributed tracing
- Use parameterized logging (not string concatenation):

  ```java
  // GOOD
  logger.info("User created with id={} and email={}", userId, email);

  // BAD
  logger.info("User created with id=" + userId + " and email=" + email);
  ```

- Structure logs for parsing (JSON format in production):
  ```json
  {
    "timestamp": "2025-11-26T10:00:00Z",
    "level": "INFO",
    "logger": "com.example.UserService",
    "message": "User created",
    "userId": 123,
    "requestId": "abc-def-123"
  }
  ```
- Log exceptions with full stack trace:
  ```java
  logger.error("Failed to create user", exception);
  ```

### Monitoring & Alerting

- Set up alerts for:
  - Error rate > 1% (within 5 minutes)
  - Response time > 2 seconds (P95)
  - High memory/CPU usage
- Use application monitoring tools:
  - **Backend:** Spring Boot Actuator + Prometheus + Grafana
- Track business metrics:
  - User registrations
  - Order completions
  - Payment failures

---

## 15. Environment Configuration

### Environment Management

- Use separate configurations for:
  - `development` (local machine)
  - `staging` (pre-production)
  - `production` (live system)
  - `test` (automated testing)

### Environment Variables

- Store all secrets in environment variables
- **Never hardcode:**
  - API URLs
  - Database credentials
  - Third-party API keys
  - Email service credentials
  - Feature flags
  - OAuth client secrets

**Example `.env` file:**

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=myapp
DB_USER=postgres
DB_PASSWORD=secret

# JWT
JWT_SECRET=super-secret-key-change-in-production
JWT_EXPIRATION=3600

# External APIs
STRIPE_API_KEY=sk_test_xxx
SENDGRID_API_KEY=SG.xxx

# Feature Flags
FEATURE_NEW_DASHBOARD=true
```

**Backend (application.yml):**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

### Docker Best Practices

- **Multi-stage builds** for smaller images:

  ```dockerfile
  # Build stage
  FROM maven:3.9-eclipse-temurin-21 AS build
  WORKDIR /app
  COPY pom.xml .
  RUN mvn dependency:go-offline
  COPY src ./src
  RUN mvn package -DskipTests

  # Runtime stage
  FROM eclipse-temurin:21-jre-alpine
  WORKDIR /app
  COPY --from=build /app/target/*.jar app.jar
  EXPOSE 8080
  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```

- Use **Alpine** base images (smaller size)
- Run as **non-root user**:
  ```dockerfile
  RUN addgroup -S appgroup && adduser -S appuser -G appgroup
  USER appuser
  ```
- Implement **health check endpoints**:
  ```dockerfile
  HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
  ```
- Use `.dockerignore` to exclude unnecessary files
- Pin specific image versions (not `latest`)

### CI/CD Pipeline Requirements

Pipeline must include:

1. **Build stage:**
   - Compile code
   - Install dependencies
2. **Test stage:**
   - Run unit tests
   - Run integration tests
   - Generate coverage reports
3. **Security stage:**
   - Dependency vulnerability scanning (OWASP Dependency-Check)
   - Static code analysis (SonarQube)
   - Secret scanning (GitGuardian)
4. **Quality stage:**
   - Code quality checks (SonarQube)
   - Code coverage threshold (80% minimum)
   - Linting and formatting checks
5. **Build & Push:**
   - Build Docker image
   - Push to container registry
   - Tag with version and commit SHA
6. **Deploy stage:**
   - Deploy to staging (automatic)
   - Deploy to production (manual approval required)
   - Run smoke tests post-deployment
7. **Notification:**
   - Slack/Email on failure
   - Deployment summary

### Secrets Management

- Use secrets management tools:
  - **AWS Secrets Manager**
  - **HashiCorp Vault**
  - **Azure Key Vault**
  - **Google Secret Manager**
- Rotate secrets regularly (90 days)
- Never commit secrets to git (use git-secrets hook)
- Encrypt secrets at rest and in transit