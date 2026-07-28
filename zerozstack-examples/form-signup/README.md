# form-signup

A signup form for a fictional developer conference demonstrating **zeroz4j's single-declaration model validation** and **reactive Signals** (`ValueSignal`, `Computed`, `Effect`).

## What it demonstrates

* **Annotate Once, Enforce Everywhere** — validation rules (`@NotBlank`, `@Size`, `@Min`, `@Max`) are declared directly on the `@DataModel` POJO (`Registration`). The `zerozstack-apt` annotation processor generates `Registration_Rules` at compile time, enforcing constraints on both tiers.
* **Live Client Feedback** — UI input fields are bound to `ValueSignal`s via `bindValue(...)` and attached to APT-generated rules via `withRule(Registration_Rules.<field>())`. Each field displays real-time violation messages in a small error container driven by an `Effect`.
* **Computed Form Validity** — a `Computed<Boolean> formValid` evaluates per-field `isValid()` states. The Submit button is enabled or disabled reactively via an `Effect` with no manual recheck code path.
* **Server-Side Enforcement** — the RMI engine validates incoming `Registration` arguments against `ValidationRegistry` / `Registration_Rules` before `RegistrationService.register(...)` is invoked, rejecting invalid calls automatically.
* **EclipseStore Persistence** — submitted registrations are appended to the EclipseStore `DataRoot` and stored to disk, surviving server restarts.
* **Refetched Data Table** — after registration, the client clears form signals and re-queries `RegistrationService.listRegistrations()`, rendering attendees in a styled DaisyUI `Table`.

## Component Design Choices

Where the spec offered design options, the following components were chosen:
* **Years of Java Experience**: `IntegerSelect` (DaisyUI `select select-bordered` mapped to `AbstractField<IntegerSelect, Integer>`) to seamlessly bind `ValueSignal<Integer>` and `Registration_Rules.experienceYears()`.
* **T-Shirt Size**: `Select` (`S`, `M`, `L`, `XL`).
* **Newsletter Subscription**: `Toggle`.
* **Short Bio**: `TextArea`.

## Server-Side Validation Verification

Server-side validation enforcement was verified by running `RegistrationValidationTest` (which validates that an invalid `Registration` instance containing out-of-bounds fields triggers field violation messages from `Registration_Rules` and `ValidationRegistry` before service execution).

## Running

Build the reactor once from the repository root (`mvn install -DskipTests`), then double-click `run.bat` in this folder (or run it from a terminal) to launch the server:

```bash
mvn install -DskipTests
cd zerozstack-examples/form-signup/form-signup-server
mvn exec:java -Dexec.mainClass="com.zeroz4j.example.server.ExampleServer"
```

Open `http://localhost:8080` in your browser to test live signup validation and registration.
