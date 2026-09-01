# Validation: Annotate Once, Enforce Everywhere

ZeroZ Stack validation lives as annotations on your `@DataModel` POJOs — the single place your domain rules belong — and is enforced on **both tiers** from that one declaration:

* the **Wasm client** uses the rules for live form feedback (the binder),
* the **server** applies them automatically to every incoming RMI argument and every client-written shared signal value — so the rules hold even against a client that bypassed your UI entirely.

No reflection is involved: the annotation processor generates a `<Model>_Rules` class next to each constrained model at compile time, and the same generated code runs in the browser and on the JVM.

## Declaring rules

```java
@DataModel
public class Registration {
    @NotBlank @Size(min = 2, max = 40)
    private String fullName;

    @Min(0) @Max(50)
    private int experience;
    ...
}
```

Available constraints (in `com.zeroz4j.api.validation`):

| Annotation | Applies to | Meaning |
|---|---|---|
| `@NotBlank` | String | non-null and at least one non-whitespace character |
| `@Size(min, max)` | String | length within bounds (null not checked — combine with `@NotBlank`) |
| `@Min(value)` | numeric | ≥ value |
| `@Max(value)` | numeric | ≤ value |

Every annotation takes an optional `message` to override the generated default.

!!! warning "Validation messages are one language, whichever you write them in"
    The sentence a broken rule produces — the generated `fullName must not be blank`, or whatever
    you put in `message` — is baked into the generated `<Model>_Rules` class at compile time and is
    the same for every reader. It is used for a browser hint and for the server's rejection, and
    neither is translated.

    The refusal that carries them **is** translated: a caller reading German is told
    `Prüfung fehlgeschlagen für Registration: ...` with the untranslated rule text after the colon.
    Translating the rule text itself needs the annotations to name a catalog key rather than carry
    a sentence, and that has not been built. Where it matters today, check the value inside your
    service method and throw `ClientVisibleException` with a message from your own catalog. See
    [Answering in the reader's language](guides/language.md).

## Client: binder integration

Attach the generated rule to a field — the field then validates on every change, carries the `input-error` style class once the user has touched it, and reports validity:

```java
TextField nameField = new TextField("Full name");
nameField.bindValue(fullName);                       // two-way signal binding
nameField.withRule(Registration_Rules.fullName());   // annotation-driven validation

Computed<Boolean> formValid = new Computed<>(() -> ...); // combine per-field isValid()
```

`isValid()` is accurate from the start (for form-level validity), while the error styling appears only after the first user interaction — untouched empty forms don't bleed red. `getViolations()` returns the messages for display next to the field.

`<Model>_Rules.validate(obj)` validates a whole object client-side (e.g. before enabling Submit).

## Server: automatic enforcement

Nothing to write. The RMI engine validates every incoming argument (including elements of `List` arguments) against the registered rules and rejects the call with a validation error before your service method runs. Client-written shared signal values pass through the same check. Client-side validation is UX; **the server-side check is the one that decides**, and it comes from the same annotations.

For rules that span fields or need server data ("username already taken"), validate inside your service method — annotations cover per-field constraints; business logic stays business logic.

The refusal the caller gets is `Validation failed for <Model>: <the broken rules, joined>`. That
sentence is the framework's own and is translated into the caller's language; the broken rules
inside it are the annotation messages and are not.

## Limits (current release)

* Constraints apply to String and numeric fields; nested objects are validated only when they arrive as RMI arguments themselves (no deep graph walking).
* The constraint set is deliberately small; it will grow as real usage demands (`@Pattern` is the likely next addition).
* Constraint messages are not translatable. They are compiled into `<Model>_Rules` as written and read the same to every person.
