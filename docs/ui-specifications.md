# AutoRepo — UI Design System & Component Styleguide

## 1. Theme Configuration & Typography

- **Theme:** DaisyUI `dim` theme applied on the root: `<html lang="en" data-theme="dim">`.
- **Surface Layers:**
    - Background / Base Surface: `bg-base-100` (application background)
    - Container / Elevated Surface: `bg-base-200` (cards, sidebars, headers, table headers)
    - Elevated Interactive / Accent Surface: `bg-base-300` (active items, dropdown menus, table stripe)
    - Borders / Dividers: `border-base-300`
- **Brand Accents:**
    - Primary Action: `btn-primary`
    - Secondary / Neutral Action: `btn-secondary` or `btn-neutral`
    - Destructive Action: `btn-error` (or `text-error btn-ghost`)
- **Typography & Numbers:** Standard sans-serif stack. Monospace (`font-mono`) and tabular numbers (`tabular-nums`) are mandatory for technical identifiers (IDs, VINs, plates, currency, dates).

---

## 2. Global Layout Template (`templates/layout/base.html`)

All feature views inject into this layout via standard Thymeleaf replacement (`th:replace="~{layout/base :: layout(~{::main})}"`).

```html
<!DOCTYPE html>
<html lang="en" data-theme="dim">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title th:text="${pageTitle} ?: 'AutoRepo Fleet Portal'">AutoRepo</title>

    <!-- External Styles (Loaded once here) -->
    <script src="https://cdn.tailwindcss.com"></script>
    <link
      href="https://cdn.jsdelivr.net/npm/daisyui@latest/dist/full.min.css"
      rel="stylesheet"
      type="text/css"
    />
  </head>
  <body class="min-h-screen bg-base-100 flex flex-col text-base-content antialiased">
    <!-- Header / Navigation Slot -->
    <header th:replace="~{components/navbar :: navbar}"></header>

    <!-- Global Flash Banners -->
    <aside class="container mx-auto max-w-7xl px-4 mt-4">
      <div th:replace="~{components/alert :: alerts}"></div>
    </aside>

    <!-- Feature Content Slot -->
    <main
      class="container mx-auto max-w-7xl px-4 py-6 flex-1"
      th:replace="${content}"
    >
      <!-- Content replaced by feature view -->
    </main>

    <!-- Global Footer -->
    <footer
      class="footer footer-center p-4 bg-base-200 text-base-content border-t border-base-300 text-xs"
    >
      <p>&copy; AutoRepo Fleet Management Portal</p>
    </footer>
  </body>
</html>
```

---

## 3. Atomic Shared Partials (`templates/components/`)

### 3.1 Flash Alerts (`alert.html`)

- **Standard wrapper:** `<div class="alert shadow-lg mb-4">`
- **Success:** `alert-success` with checkmark icon
- **Error:** `alert-error` with warning icon
- **Informational:** `alert-info` with info bubble icon

### 3.2 Navigation Bar (`navbar.html`)

- **Container:** `<div class="navbar bg-base-200 border-b border-base-300 shadow-sm px-4">`
- **Brand:** `<a class="text-xl font-bold tracking-wide text-primary">AutoRepo</a>`
- **Navigation List:** `<ul class="menu menu-horizontal gap-2">`
- **Active State:** Controller sets attribute to conditionally apply `active bg-base-300` to the current item.

### 3.3 Form Control Wrappers (`forms.html`)

Standard field template for text inputs and dropdowns:

```html
<!-- Input Fragment Pattern -->
<div class="form-control w-full mb-4">
  <label class="label">
    <span class="label-text font-medium" th:text="${label}">Label Title</span>
  </label>
  <input
    class="input input-bordered w-full focus:input-primary"
    th:classappend="${#fields.hasErrors(field)} ? 'input-error' : ''"
  />
  <label class="label" th:if="${#fields.hasErrors(field)}">
    <span class="label-text-alt text-error text-xs" th:errors="*{__${field}__}"
      >Validation Error</span
    >
  </label>
</div>
```

---

## 4. UI Patterns for Feature Pages

### 4.1 Dashboard / List Screen Pattern (`list.html`)

Every feature collection overview must use this page structure:

- **Header Action Bar:**
    - Flex container: `<div class="flex justify-between items-center mb-6">`
    - Left: Feature Title (`<h1 class="text-2xl font-bold tracking-tight">`) + total counter badge.
    - Right: Filter group (`join`) and primary action CTA (`<a class="btn btn-primary btn-sm">+ Add Resource</a>`).
- **Data Table Container:**
    - Wrapped in `<div class="overflow-x-auto rounded-box border border-base-300 bg-base-200/50">`.
    - Table class: `<table class="table table-zebra w-full">`.
    - Thead: Upper-case muted labels (`<thead class="bg-base-200 text-xs text-base-content/70">`).
    - Row Actions: Clustered in `<div class="join">` using compact action buttons (`btn-xs btn-ghost join-item`).

### 4.2 Form Screen Pattern (`form.html`)

Every resource creation and update screen must use this card structure:

- **Card Layout:**
    - Centered card wrapper: `<div class="card bg-base-200 border border-base-300 shadow-md max-w-2xl mx-auto">`.
    - Card Body: `<div class="card-body">`.
    - Header: `<h2 class="card-title text-xl font-bold mb-4">`.
- **Form Action Footer:**
    - Bottom button group: `<div class="card-actions justify-end mt-6 gap-2">`.
    - Cancel / Back Button: `<a class="btn btn-ghost">Cancel</a>`.
    - Submit Button: `<button type="submit" class="btn btn-primary">Save Changes</button>`.

### 4.3 Semantic State Badges

Render status indicators across tables and cards consistently using DaisyUI badges:

- **Active / Confirmed / Available:** `<span class="badge badge-success badge-sm">...</span>`
- **Draft / Pending / Maintenance:** `<span class="badge badge-warning badge-sm">...</span>`
- **Cancelled / Inactive / Danger:** `<span class="badge badge-error badge-sm">...</span>`
- **Read-only Metadata:** `<span class="badge badge-neutral badge-sm">...</span>`
