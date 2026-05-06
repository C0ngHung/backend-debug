---
trigger: always_on
---

# Git Commit Message Convention

> Based on [Karma Runner Git Commit Msg](https://karma-runner.github.io/6.4/dev/git-commit-msg.html) and [Angular Commit Message Format](https://github.com/angular/angular/blob/master/CONTRIBUTING.md#commit).

---

## Why This Convention?

- **Automatic changelog generation** from git history
- **Simple navigation** through git history (e.g. ignoring style changes)
- **Semantic versioning** — commit type determines version bump (MAJOR / MINOR / PATCH)

---

## Commit Message Format

Every commit message consists of **3 parts**: header (required), body (optional), footer (optional).
Each part is separated by a **blank line**.

```
<type>(<scope>): <subject>

<body>

<footer>
```

> [!IMPORTANT]
> - The **header** (first line) is **mandatory** and cannot exceed **72 characters**.
> - The **body** and **footer** are optional but recommended for non-trivial changes.

---

## 1. Header — `<type>(<scope>): <subject>`

### 1.1 `<type>` (Required)

Must be one of the following **lowercase** values:

| Type       | Description                                                      | Version Bump |
| ---------- | ---------------------------------------------------------------- | ------------ |
| `feat`     | A new feature for the **user** (not build scripts)               | **MINOR**    |
| `fix`      | A bug fix for the **user** (not build scripts)                   | **PATCH**    |
| `perf`     | Performance improvements                                         | **PATCH**    |
| `docs`     | Documentation only changes                                       | None         |
| `style`    | Formatting, missing semicolons, etc. (no production code change) | None         |
| `refactor` | Refactoring production code (e.g. renaming a variable)           | None         |
| `test`     | Adding or refactoring tests (no production code change)          | None         |
| `build`    | Build configuration, dev tools, or changes irrelevant to user    | None         |

### 1.2 `<scope>` (Optional)

The scope provides **additional context** about what part of the codebase is affected.

**Project-specific scopes for this backend-service:**

| Scope            | When to Use                                               |
| ---------------- | --------------------------------------------------------- |
| `user`           | User module (controller, service, repository, entity)     |
| `auth`           | Authentication & authorization (JWT, SecurityConfig)      |
| `email`          | Email integration (SendGrid, verification)                |
| `config`         | Configuration files (application.yaml, profiles)          |
| `db`             | Database migrations, entities, repositories               |
| `security`       | Spring Security, CORS, CSRF                               |
| `api`            | API response format, error handling, global exception      |
| `test`           | Test infrastructure, test utilities                       |
| `docker`         | Docker, docker-compose                                    |
| `deps`           | Dependency updates, CVE fixes                             |

**Rules:**
- Scope can be **empty** if the change is global or hard to assign to a single component — in this case, **omit the parentheses**.
- Always use **lowercase**.

### 1.3 `<subject>` (Required)

- Use **imperative, present tense**: "change" not "changed" nor "changes"
- **Do not capitalize** the first letter
- **No period (.)** at the end
- Max **72 characters** for the entire header line

**Good:**
```
feat(email): add async verification email with event listener
```

**Bad:**
```
feat(email): Added async verification email with event listener.
```

---

## 2. Body (Optional)

- Use **imperative, present tense** (same as subject)
- Include **motivation** for the change and **contrast with previous behavior**
- Wrap lines at **72 characters**

**Example:**
```
feat(user): add password encoding in save flow

Use BCryptPasswordEncoder to hash passwords before persisting.
Previously passwords were stored as plain text, which is a
critical security vulnerability.
```

---

## 3. Footer (Optional)

The footer is used for two purposes:

### 3.1 Referencing Issues

Closed issues should be listed on a **separate line**, prefixed with `Closes`:

```
Closes #234
```

For multiple issues:

```
Closes #123, #245, #992
```

### 3.2 Breaking Changes

All breaking changes **must** be mentioned in the footer with:
- Description of the change
- Justification
- Migration notes

```
BREAKING CHANGE: `getUserById` now returns Optional<User> instead of User.
To migrate, update all callers to handle the Optional wrapper.
```

> [!CAUTION]
> Any commit with a `BREAKING CHANGE` footer triggers a **MAJOR** version bump and appears on the changelog independently of the commit type.

---

## Full Examples

### Simple fix (header only)
```
fix(user): handle null email in validation check
```

### Feature with body
```
feat(email): add SendGrid dynamic template for verification

Implement email verification using SendGrid Dynamic Templates.
Includes responsive HTML template with gradient header, CTA button,
and mobile breakpoints at 620px.
```

### Feature with body and footer
```
feat(auth): implement JWT refresh token mechanism

Add automatic token refresh 5 minutes before expiry.
Handle token refresh failures with logout fallback.
Use SecureRandom for token generation (512-bit entropy).

Closes #42
```

### Breaking change
```
refactor(api): unify error response format with ApiResponse wrapper

Replace all Map<String, Object> responses with ApiResponse<T>.
Remove ResourceNotFoundException and InvalidDataException in favor
of AppException with ErrorCode enum.

BREAKING CHANGE: all API endpoints now return `ApiResponse<T>` instead
of raw `Map<String, Object>`. Clients must update their response parsers.

Closes #38, #39
```

### Build / dependency update
```
build(deps): upgrade sendgrid-java to 4.9.3 and fix 5 CVEs

Exclude vulnerable bcprov-jdk15on transitive dependency.
Add bcprov-jdk18on:1.83 as direct dependency.
```

### Documentation
```
docs(email): add email verification V2 complete guide
```

### Test
```
test(user): add 24 unit tests for UserServiceImpl

Cover CRUD operations, validation edge cases, and error scenarios.
Use Mockito + AssertJ with AAA pattern.
```

---

## Quick Reference Checklist

Before committing, verify:

- [ ] Type is one of: `feat`, `fix`, `perf`, `docs`, `style`, `refactor`, `test`, `build`
- [ ] Type and scope are **lowercase**
- [ ] Subject uses **imperative mood** ("add" not "added")
- [ ] Subject does **not** start with uppercase
- [ ] Subject has **no trailing period**
- [ ] Header line ≤ **72 characters**
- [ ] Body explains **why**, not just what
- [ ] Breaking changes documented in footer with `BREAKING CHANGE:`
- [ ] Related issues referenced with `Closes #xxx`


Commit message Convention
Cấu trúc chuẩn:
<type> [<id-ticket>]:[<scope>]:<message>

Giải thích các thành phần:

<type>: include: feature, bugfix and hotfix

[<id-ticket>]: Ticket code from the management system

[<scope>]: Short descriptive sentences, connected by -

<message>: A detailed description of the changes we made in the commit.

Example:
hofix[HF-2102]:login:fix login error when password is empty
bugfix[HF-1022]:api:add validation for email input
feature[HF-1983]:response:fix API response format

Checkout:
hotfix/AR-2026-token-leak-issue

Commit:
hotfix[AR-2026]:token-leak-issue:Fix token leak when get API response
