---
trigger: always_on
---

Level‑by‑Level Software Design Rules

> A practical, battle‑tested rule set distilled from building software **incrementally (Level 1 → Level 8)** without breaking existing code, while keeping the design clean, scalable, and testable.

This document is **not theory-first**. It encodes _engineering behavior_ you can apply to any real system.

---

## 0. Core Philosophy (Read This First)

**Software grows, requirements change, code must survive.**

Your primary job as a software engineer is **not to implement features**, but to:

- Preserve **invariants**
- Isolate **change**
- Prevent **regressions**
- Enable **future work at low cost**
- **Know WHY you chose this path and WHAT you lose** by choosing it

Everything below exists to serve those goals.

> "Engineers are not judged by knowing HOW to do something,
> but by knowing WHY to do it that way, and WHAT risks come with it."

---

## 0.1 Trade-off Analysis Protocol (MANDATORY)

> **No solution is perfect. Only solutions that fit the context.**
> If you cannot point out the weakness of your choice, you do not understand it yet.

**Before adopting ANY technology, pattern, or architecture, answer these:**

| Question                     | Purpose                                               |
| ---------------------------- | ----------------------------------------------------- |
| **Why this?**                | What specific problem does it solve in THIS context?  |
| **Why not the alternative?** | What other options exist and why are they worse HERE? |
| **What do I gain?**          | Concrete, measurable benefits                         |
| **What do I lose?**          | Cost, complexity, operational burden, coupling        |
| **When does it break?**      | At what scale/load/scenario does this choice fail?    |

### Anti-Pattern: Parrot Learning

- BAD: "I use Redis because it is fast."
- GOOD: "I chose Redis here because sorted set is optimal for ranking. It costs extra RAM and cache management, but removes the DB bottleneck at 10K+ concurrent reads."

### Trade-off Documentation Template

Whenever making a significant technical choice, document:

1. **Decision**: What you chose
2. **Context**: What problem you are solving
3. **Options Considered**: At least 2 alternatives with pros/cons
4. **Rationale**: Why this fits THIS context
5. **Accepted Trade-offs**: What you knowingly sacrifice
6. **Revisit When**: Conditions that would invalidate this choice

---

## 0.2 Risk-First Thinking

> **Think about failure BEFORE thinking about success.**
> Code that works on your machine is the easy part.
> Engineers manage risk. They do not code in ideal conditions.

**Before shipping any feature, ask:**

- What happens when **load increases 10x**?
- What happens when **this dependency is down**?
- What happens when **requirements change** next quarter?
- What happens when **a new team member** reads this code?
- What happens when **data grows** from 1K to 1M rows?

### The Failure Checklist

Before every commit:

- [ ] If DB goes down, does the app crash or degrade gracefully?
- [ ] If traffic spikes 10x, which component breaks first?
- [ ] If I need to change the data model, how many files are affected?
- [ ] If this service is compromised, what is the blast radius?
- [ ] If I leave the project, can someone else maintain this?

---

## 0.3 Root Understanding Protocol

> **Do not be a slave to tutorials. They are the leaves, not the root.**
> When you understand how data is stored and processed underneath,
> you can handle any tool the company throws at you.

### The Three Levels of Understanding

| Level     | Focus                               | Example                                                                 |
| --------- | ----------------------------------- | ----------------------------------------------------------------------- |
| **Leaf**  | How to use X                        | Spring Security config                                                  |
| **Trunk** | How X works internally              | Filter chain and SecurityContext lifecycle                              |
| **Root**  | What problem X solves fundamentally | Authentication = proving identity. Authorization = checking permissions |

**Rule: Always go at least to Trunk level. Aim for Root.**

### How to Apply

1. **Before using a framework feature** - Read how it works internally (at least the high-level flow)
2. **Before choosing a database** - Understand storage engines, indexing strategies, ACID vs BASE
3. **Before adding a dependency** - Ask: Can I explain what this library does in 2 sentences?
4. **Before following a tutorial** - Ask: What PRINCIPLE is this tutorial applying? Can I apply it differently?

### Self-Test: Code Monkey vs Engineer

- Code Monkey: "I use @Transactional because the tutorial said so."
- Engineer: "@Transactional here because this operation needs atomicity. For read-only queries I use readOnly=true to skip Hibernate dirty checking, reducing overhead. For financial operations, I would use SERIALIZABLE isolation but accept lower throughput."

---

## 1. Development Model: Level‑by‑Level Growth

### Rule 1.1 — Treat Every Feature as a “Next Level”

Design as if _another level is guaranteed to come_.

Ask before coding:

- “What will Level N+1 probably change?”
- “Where should that change live?”

If you cannot answer → **do not code yet**.

---

### Rule 1.2 — One Level = One Commit

- Each level is a **cohesive increment**
- Never mix multiple conceptual changes in one commit

**Good commit**:

```
feat(level-4): support open-ended ranges with infinity
```

**Bad commit**:

```
update range logic
```

Commits are part of the design.

---

## 2. Public API Stability Rules

### Rule 2.1 — Public API Is a Contract

Once something is public:

- Tests rely on it
- Users rely on it
- Future levels depend on it

**Breaking changes must be avoided or explicitly layered.**

---

### Rule 2.2 — Evolve APIs, Don’t Replace Them

**Wrong**:

```java
T lowerbound();  // removed
```

**Right**:

```java
T lowerbound();              // legacy
Optional<T> lower();         // new, safer API
```

This pattern allows:

- Backward compatibility
- Gradual migration
- No test breakage

---

## 3. Object Modeling Rules (OOP Done Right)

### Rule 3.1 — Model Concepts, Not Flags

If you see:

```java
boolean isOpen;
boolean isClosed;
```

You missed an abstraction.

Replace with:

```java
Bound
Range
Interval
State
```

Concepts survive change. Flags don’t.

---

### Rule 3.2 — Invariants Live in Constructors

All invalid states must be **impossible to create**.

```java
private Range(Bound lower, Bound upper) {
    if (lower.isFinite() && upper.isFinite()
        && lower.value().compareTo(upper.value()) > 0) {
        throw new IllegalArgumentException();
    }
}
```

Never rely on callers to “use it correctly”.

---

### Rule 3.3 — Prefer Value Objects Over Primitives

If a value has rules → wrap it.

**Bad**:

```java
int lower;
int upper;
```

**Good**:

```java
Bound<T> lower;
Bound<T> upper;
```

Value Objects:

- Encapsulate logic
- Eliminate duplication
- Localize change

---

## 4. SOLID Principles (Applied, Not Academic)

### S — Single Responsibility

A class should change for **one reason only**.

**Example**:

- `Range` → orchestration
- `Bound` → comparison + infinity semantics
- `Controller` → HTTP only

If a method contains `if` for multiple reasons → split.

---

### O — Open / Closed

New behavior should be added by:

- New types
- New strategies

Not by editing large `if/else` blocks.

**Pattern**:

```java
enum BoundInclusion {
    INCLUDE { ... },
    EXCLUDE { ... }
}
```

---

### L — Liskov Substitution

If `B extends A`, then **B must be usable wherever A is expected**.

This is why:

```java
T extends Comparable<? super T>
```

is preferred over:

```java
T extends Comparable<T>
```

---

### I — Interface Segregation

Expose **only what callers need**.

- Domain objects ≠ HTTP DTOs
- Parsing ≠ Formatting

Avoid “god interfaces”.

---

### D — Dependency Inversion

High-level logic must not depend on:

- Parsing details
- Frameworks
- Transport protocols

**Example**:

```java
Range.parse(String, Function<String, T>)
```

Caller supplies dependency, domain stays pure.

---

## 5. Pattern Selection Rules

### Rule 5.1 — Prefer Small, Local Patterns

Choose:

- Enum strategy
- Value Object
- Factory method

Before:

- Visitor
- Abstract Factory
- Reflection

Patterns should **reduce code**, not increase it.

---

### Rule 5.2 — Nested Types When Scope Is Local

If a type exists only to support one class:

```java
class Range {
    private record Bound { ... }
}
```

Do **not** extract prematurely.

---

## 6. Testing Rules (Non‑Negotiable)

### Rule 6.1 — Tests Describe Behavior, Not Implementation

**Good test**:

```java
assertThat(range.contains(5)).isTrue();
```

**Bad test**:

```java
assertThat(range.lower.kind()).isEqualTo(...);
```

Test what the user cares about.

---

### Rule 6.2 — Preserve Old Tests Forever

When adding a level:

- All previous tests **must still pass**

If they don’t:

- You broke the contract
- You broke trust

---

### Rule 6.3 — Round‑Trip Tests for Serialization

Whenever you add:

- `toString()`
- `parse()`

Add:

```java
assertThat(parse(r.toString()).toString())
    .isEqualTo(r.toString());
```

This locks correctness.

---

## 7. Error Handling Rules

### Rule 7.1 — Fail Fast at the Boundary

- Validate inputs early
- Throw meaningful exceptions
- Do not let invalid state propagate

---

### Rule 7.2 — Domain Errors ≠ Transport Errors

- Domain → `IllegalArgumentException`
- HTTP → `400`, `422`

Never mix the two.

---

## 8. Framework Isolation Rules

### Rule 8.1 — Domain Must Not Depend on Frameworks

**Never**:

```java
import org.springframework.*;
```

in domain code.

Frameworks change. Domain must not.

---

### Rule 8.2 — Controllers Are Glue Only

Controller responsibilities:

- Deserialize input
- Call domain
- Serialize output

Nothing else.

---

## 9. Naming Rules

- Class names → concepts (`Range`, `Bound`)
- Method names → intent (`contains`, `parse`)
- Avoid technical names (`doCheck`, `processData`)

If naming is hard → design is unclear.

---

## 10. Final Engineer’s Checklist

Before committing any level, ask:

- [ ] Did I preserve all existing behavior?
- [ ] Is the new logic isolated?
- [ ] Did I introduce any flags that hide concepts?
- [ ] Would Level N+1 be easy to add?
- [ ] Are tests describing behavior, not structure?

If any answer is “no” → stop and refactor.

---

## Closing Principle

> **Great software is not written. It is grown — carefully, deliberately, and respectfully.**

This rule set is your guardrail.