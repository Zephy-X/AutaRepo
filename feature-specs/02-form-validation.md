I need to implement robust form validation, user input retention, and proper 404 error handling for my Spring Boot + Thymeleaf application (Vehicle and Reservation features) so that invalid input re-renders the form with DaisyUI/Tailwind error highlights, and missing entities return a clean 404 instead of throwing a 500 Server Error.

### Requirements:

1. **DTO / Form Objects with Jakarta Validation:**
    - Define or update `VehicleForm` with validation annotations:
        - `brand`, `model`: `@NotBlank`
        - `year`: `@NotNull`, `@Min(1900)`, `@Max(2027)`
        - `licensePlate`: `@NotBlank`
        - `vin`: `@NotBlank`, `@Size(min = 17, max = 17, message = "VIN must be exactly 17 characters")`
    - Define or update `ReservationForm`:
        - `vehicleId`, `userId`: `@NotNull(message = "Please select an option")`
        - `startTime`, `endTime`: `@NotNull`, ensure start is at least 2 hours in the future and end is after start (either via a custom validator or service check rejected via `bindingResult.rejectValue`).

2. **Controllers (`VehicleController`, `ReservationController`):**
    - In all `POST` handler methods, annotate form objects with `@Valid @ModelAttribute("vehicleForm")` or `@Valid @ModelAttribute("reservationForm")`.
    - Place `BindingResult bindingResult` **immediately following** the form parameter (before `Model` or `RedirectAttributes`).
    - If `bindingResult.hasErrors()` is true:
        - Do not redirect. Re-populate any required collections into the `Model` (e.g., `vehicles`, `users`).
        - Return the view path directly (`"feature/vehicle/form"` or `"feature/reservation/form"`) so input values are retained.

3. **404 Handling Instead of 500:**
    - Whenever an entity is looked up by ID (e.g., `/vehicles/{id}/edit`, `/vehicles/{id}`, `/reservations/{id}/confirm`) and is not present in the repository, throw a `ResourceNotFoundException` (annotated with `@ResponseStatus(HttpStatus.NOT_FOUND)`), or throw `ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found")` directly.
    - Do not let `NoSuchElementException` or `NullPointerException` bubble up uncaught to trigger the default 500 error page.
    - Ensure the app routes to the existing `404.html` error template when a 404 occurs.

4. **Thymeleaf Template Integration:**
    - Ensure inputs use `th:field="*{fieldName}"` to retain submitted data on validation failure.
    - Conditionally apply `input-error` / `select-error` classes via `th:classappend="${#fields.hasErrors('fieldName')} ? 'input-error' : ''"`.
    - Display the specific error message under each field using:
      `<span class="label-text-alt text-error text-xs" th:if="${#fields.hasErrors('fieldName')}" th:errors="*{fieldName}"></span>`.