---
trigger: always_on
---

# Format Lesson Rule - Java Core Course

This rule defines the standard formatting and structure for all Java course lesson documents (Markdown files) to ensure consistency, readability, and professional presentation.

## 1. File Structure & Headers

- **Main Title**: Every file must start with exactly one `h1` header.
  - Format: `# [Lesson Title] - [English Title]`
  - Example: `# LinkedList trong Java - Java LinkedList`
- **Main Sections**: Use `h2` headers with numerical prefixes.
  - Format: `## N. Section Name`
  - Example: `## 1. LinkedList là gì?`
- **Subsections**: Use `h3` headers. Numbered prefixes are optional but recommended if they help structure the content.
  - Format: `### Sub-section Name` or `### N.M Sub-section Name`
- **Spacing**: Ensure one empty line between headers and the following content.

## 2. Text Formatting & Terminology

- **Key Terms**: Highlight critical technical terms using bold text (`**...**`).
- **English Translations**: Always include the English equivalent for technical terms in bold parentheses.
  - Format: `**Vietnamese Term** (**English Term**)`
  - Example: `**cấu trúc dữ liệu** (**data structure**)`
- **In-line Code**: Use backticks (`` `...` ``) for class names, method names, variables, and keywords in sentences.
  - Example: `ArrayList`, `add()`, `null`.

## 3. Lists & Bullet Points

- **Style**: Use hyphen `-` for bullet points. Avoid using `.` or `*`.
- **Indentation**: Use 2 or 4 spaces consistently for nested lists.
- **Bold Start**: If a list item defines a term, start with the term in bold.
  - Example: `- **Thao tác**: Mô tả thao tác...`

## 4. Interview Questions & Answers

This section is critical for learner preparation. Follow this exact pattern:

- **Section Header**: Use `## N. Câu hỏi phỏng vấn [Topic]` followed by `### N.1 Câu hỏi lý thuyết` etc.
- **Question Format**:
  - Format: `**Câu X:** [Question Content]`
  - Example: `**Câu 1:** Java LinkedList là gì?`
- **Answer Format**:
  - Format: `**Trả lời:**` on its own line, followed by the answer content.
- **Answer Content**: Use lists, bolding, and code blocks within answers for clarity.

## 5. Code Blocks

- **Language Identifier**: Always specify the language (usually `java`).
- **Best Practices**:
  - Use meaningful variable names.
  - Include brief comments for complex logic.
  - Ensure correct indentation.
  - Wrap the block with single newlines before and after.
- **Results**: When showing output, use a bolded **Kết quả:** or **Output:** followed by a code block.

## 6. Alerts & Callouts

Use GitHub-style alerts for critical notes or specific rules:

- `> [!NOTE]` for helpful info.
- `> [!IMPORTANT]` for mandatory rules or critical concepts.
- `> [!WARNING]` for common pitfalls.

---

_Follow these rules strictly to maintain a unified "Java Core Tiếng Việt" course style._