---
trigger: always_on
---

# CURSOR INSTRUCTIONS — SHARED RULES

## 1. Output Format

Every answer must be written in **two languages**:

1. Vietnamese (first)
2. English (second)  
   Both must contain a **full, equivalent meaning**, not summarized.

---

## 2. General Behavior

- Always generate clean, complete, production-ready solutions.
- Always provide clear explanations when required.
- Follow SOLID, DRY, KISS, Clean Architecture.
- Assume enterprise-level requirements.
- Ask for clarification when requirements are ambiguous.

---

## 9. Output Rules

- If user asks for code → **return only code block**.
- If explanation is needed → explanation first, code after.
- Automatically refactor when beneficial.
- Code must be complete: no TODOs, no placeholders.
- Exception: TODOs allowed only with JIRA ticket reference format:
  ```
  // TODO(PROJ-123): Implement rate limiting
  ```

---

## 10. Extra Preferences

- Comments inside code should be in English (for international compatibility)
- Prioritize modularity, readability, maintainability.
- Prefer composition over inheritance.
- Write self-documenting code (clear names > comments).

---

## 13. Git Workflow Rules

### Branch Naming

- Feature: `feature/PROJ-123-add-user-authentication`
- Bugfix: `bugfix/PROJ-456-fix-login-error`
- Hotfix: `hotfix/PROJ-789-critical-security-patch`
- Release: `release/v1.2.0`

### Commit Message Format

Follow Conventional Commits:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**

- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code refactoring
- `docs`: Documentation changes
- `test`: Adding tests
- `chore`: Maintenance tasks
- `perf`: Performance improvements

**Examples:**

```
feat(auth): add JWT refresh token mechanism

Implement automatic token refresh 5 minutes before expiry.
Handles token refresh failures with logout fallback.

Closes PROJ-123
```

```
fix(user-service): resolve N+1 query in getUserWithOrders

Added @EntityGraph to fetch orders in single query.
Improves performance by 80% for users with many orders.

Fixes PROJ-456
```

### Pull Request Rules

**PR must include:**

- Clear description of changes and motivation
- Link to JIRA/issue ticket
- Test coverage report (must not decrease)
- Breaking changes documented
- Screenshots/GIFs for UI changes
- Migration scripts (if database changes)

**Before submitting PR:**

- All tests pass locally
- Code passes linter/formatter
- Self-review completed
- No console.log or debug code
- Updated relevant documentation

**Never commit:**

- Credentials or API keys (use `.env` files)
- `node_modules/` or `target/` directories
- IDE-specific files (`.idea/`, `.vscode/`, `*.iml`)
- Large binary files (use Git LFS if needed)
- Commented-out code (use git history instead)

### Code Review Checklist

- [ ] Code follows style guide
- [ ] Tests cover new functionality
- [ ] No security vulnerabilities
- [ ] Performance impact considered
- [ ] Documentation updated
- [ ] No hardcoded values
- [ ] Error handling implemented
- [ ] Logging appropriate

---

## 16. Documentation Standards

### Code Documentation

- Public APIs must have Javadoc/JSDoc:
  ```java
  /**
   * Creates a new user account.
   *
   * @param request the user registration request containing email and password
   * @return the created user with generated ID
   * @throws UserAlreadyExistsException if email is already registered
   * @throws ValidationException if request data is invalid
   */
  public UserResponse createUser(UserRequest request) {
    // implementation
  }
  ```
- Complex business logic requires inline comments explaining "why"
- Architecture decisions documented in ADR (Architecture Decision Records)

### API Documentation

- OpenAPI 3.0 specification for all REST APIs
- Include in documentation:
  - Endpoint descriptions
  - Request/response examples
  - Authentication requirements
  - Error codes and meanings
  - Rate limiting rules
- Keep docs in sync with code (use annotations)

### README Requirements

Every project must have a README with:

- Project overview and purpose
- Prerequisites and dependencies
- Installation instructions
- Configuration guide
- How to run locally
- How to run tests
- Deployment instructions
- Contributing guidelines
- License information

---

## 17. Performance Benchmarks

### Backend Performance Targets

- API response time:
  - P50: < 200ms
  - P95: < 500ms
  - P99: < 1000ms
- Database query time: < 100ms (P95)
- Throughput: > 1000 requests/second
- Error rate: < 0.1%

### Frontend Performance Targets

- First Contentful Paint (FCP): < 1.5s
- Largest Contentful Paint (LCP): < 2.5s
- Time to Interactive (TTI): < 3.5s
- Cumulative Layout Shift (CLS): < 0.1
- First Input Delay (FID): < 100ms
- Bundle size: < 200KB (gzipped)

### Performance Optimization Checklist

Backend:

- [ ] Do not import \*
- [ ] Database queries optimized (no N+1)
- [ ] Proper indexing on frequently queried columns
- [ ] Caching strategy implemented (Redis)
- [ ] Connection pooling configured
- [ ] Async processing for heavy tasks

Frontend:

- [ ] Code splitting implemented
- [ ] Images optimized (WebP, lazy loading)
- [ ] CSS/JS minified and compressed
- [ ] CDN used for static assets
- [ ] Service worker for offline support

---

## 18. Accessibility (a11y) Requirements

### WCAG 2.1 Level AA Compliance

- Semantic HTML elements (`<nav>`, `<main>`, `<article>`)
- Keyboard navigation support (Tab, Enter, Escape, Arrow keys)
- Focus indicators visible and clear
- Color contrast ratio:
  - Normal text: 4.5:1
  - Large text: 3:1
- Alt text for all images
- Form labels properly associated
- ARIA attributes when needed:
  - `aria-label`, `aria-labelledby`
  - `aria-describedby`
  - `role` attributes
  - `aria-live` for dynamic content

### Testing Accessibility

- Use axe DevTools browser extension
- Test with screen readers (NVDA, JAWS, VoiceOver)
- Keyboard-only navigation testing
- Automated tests with jest-axe

---

## Final Notes

This comprehensive rule set ensures:

- ✅ Enterprise-grade code quality
- ✅ Security best practices
- ✅ Performance optimization
- ✅ Comprehensive testing
- ✅ Proper documentation
- ✅ Scalable architecture
- ✅ Team collaboration standards
- ✅ Production readiness

**Remember:** These rules are guidelines, not dogma. Use professional judgment when exceptions are warranted, but always document the reasoning.