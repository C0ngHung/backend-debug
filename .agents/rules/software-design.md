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
- **Every technical choice must include trade-off awareness** (see Section 3).
- **Never recommend a technology without explaining what you lose by choosing it.**

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
- **Before using any pattern/tool, understand WHY it exists, not just HOW to use it.**

---

## 3. Decision-Making Protocol (Engineering Mindset)

> **There is no "best" solution. Only the "most suitable" one.**
> If you cannot name the weakness of your choice, you do not understand it.

### MANDATORY: For Every Technical Choice

Before writing code that introduces a new technology, pattern, or architecture decision:

1. **State the problem** you are solving (not the solution)
2. **List at least 2 options** with concrete pros/cons
3. **Explain why you chose this one** for THIS specific context
4. **Name what you sacrifice** (performance, simplicity, flexibility, etc.)
5. **Define when to revisit** (at what scale/requirement change does this break?)

### Examples

**BAD (Parrot learning):**

```
"I use Redis because it is fast."
"I use microservices because it scales."
"I use JWT because it is stateless."
```

**GOOD (Engineer thinking):**

```
"I chose Redis for this ranking feature because sorted set gives O(log N) insertion.
 It costs extra RAM and requires cache invalidation strategy.
 At <1K users, PostgreSQL ORDER BY would be simpler and sufficient."
```

```
"JWT was chosen for auth because this is a stateless REST API with no session store.
 Trade-off: cannot revoke individual tokens without a blacklist (Redis/DB).
 If we need instant revoke later, we should migrate to opaque tokens + token store."
```

### Anti-Patterns to Avoid

| Anti-Pattern       | What It Looks Like                       | Fix                                  |
| ------------------ | ---------------------------------------- | ------------------------------------ |
| **Tutorial Slave** | Copy-paste without understanding         | Read how it works internally first   |
| **Hype-Driven**    | Using latest tech because it is trending | Evaluate against actual requirements |
| **Resume-Driven**  | Choosing tech to add to resume           | Choose what fits the problem         |
| **Comfort Zone**   | Same tool for every problem              | Consider alternatives honestly       |

---

## 4. Learning Protocol (Root-First)

> **Tutorials are leaves, not roots.**
> Understanding how data is stored and processed underneath
> lets you handle any tool a company throws at you.

### Three Levels of Understanding

| Level     | Focus                              | Example                                                                 |
| --------- | ---------------------------------- | ----------------------------------------------------------------------- |
| **Leaf**  | How to use it                      | Spring Security annotations                                             |
| **Trunk** | How it works internally            | Filter chain lifecycle, SecurityContext propagation                     |
| **Root**  | What fundamental problem it solves | Authentication = proving identity, Authorization = checking permissions |

**Always aim for Trunk. Strive for Root.**

### Self-Check Before Using Any Technology

- [ ] Can I explain what this does in 2 sentences without jargon?
- [ ] Do I know how it works at least 1 level below the API?
- [ ] Can I name a scenario where this is the WRONG choice?
- [ ] Can I implement a simplified version from scratch if needed?

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
- [ ] **Technical choices are justified (WHY, not just WHAT)**
- [ ] **Trade-offs are documented for non-obvious decisions**
- [ ] **Failure scenarios considered (what if DB/service/network fails?)**
- [ ] **Could a new team member understand this without verbal explanation?**

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