# AutoRepo — Agent Instructions & Workflow Routing

You are an expert full-stack engineer working on the AutoRepo project. Before generating or modifying any code, consult the project documentation in `docs/` according to the task type.

---

## 1. Documentation Index

- **`docs/architecture-and-decisions.md`**: Core engineering baseline. Defines the Java 21 / Spring Boot 3 / Postgres stack, strict Package-by-Feature backend rules, repository boundary constraints, and DTO validation rules.
- **`docs/ui-specification.md`**: Presentation design system. Defines the DaisyUI `dim` theme, layout inheritance patterns (`base.html`), atomic components (`templates/components/`), and form/table structures.
- **`docs/intent-and-change.md`**: Domain specifications, business rules (e.g., booking interval overlap, 2-hour advance notice, 14-day maximum window), and state machine flows (`DRAFT` → `CONFIRMED` → `CANCELLED`).
- **`docs/evidence-and-evolution.md`**: Architectural decisions, database verification history, and spike test results.

---

## 2. Operating Rules & Constraints

1. **Architecture Enforcement:**
    - Place all domain code inside `cz.vsb.autarepo.autorepo.feature.<feature_name>`.
    - Enforce strict downward dependency flow: `ViewController` → `Service` → `Repository`. Controllers must never inject repositories.
    - Cross-feature access must occur strictly via public `Service` interfaces.

2. **Form & Data Binding:**
    - Never bind HTTP form requests directly to JPA entities. Use Java 21 `record` DTOs with Jakarta Bean Validation (`@Valid`, `@NotNull`, etc.).

3. **Presentation Standards:**
    - Render views via Thymeleaf with CDN-based Tailwind CSS and DaisyUI (`data-theme="dim"`). Do not introduce Node.js or build-time asset pipelines.
    - Return template paths matching the feature directory: `return "feature/<feature_name>/<view_name>";`.
    - Inherit from `templates/layout/base.html` and reuse partials from `templates/components/`.

4. **Domain Integrity:**
    - Always enforce booking invariants and business constraints defined in `docs/intent-and-change.md` inside the respective feature `Service`, not in controllers or UI templates.
