# Forms and binding

ZeroZ Stack gives you two ways to connect a UI field to state. They answer different questions, and a
typical application uses both.

## When to use this

- **`Binder<BEAN>`** — you are editing a **domain object**: a form over a POJO, with per-field
  validation, and often a Save/Cancel pair. Vaadin's `Binder` is the model.
- **`field.bindValue(signal)`** — you are wiring a field to a piece of **reactive state** that other
  parts of the UI derive from: a search box that filters a list, a theme toggle, a slider whose value
  three other components display.

Rule of thumb: **binding a POJO's fields → `Binder`. Binding a value other components react to →
`bindValue`.** A form can use `Binder` for the entity and `bindValue` for its own view state at the
same time.

## Name the fields first

Before any of this, give each field a caption. `new TextField("Email address")` sets the
*placeholder*, not a caption — it is gray text inside the empty box that disappears as soon as
somebody types, and no screen reader announces it. Use `withLabel`:

```java
import com.zeroz4j.ui.component.TextField;

TextField name  = new TextField().withLabel("Your name");
TextField email = new TextField("you@example.com").withLabel("Email address");
```

The caption is a real label tied to the control, so clicking the words focuses the field, and a
screen reader reads the caption as the field's name. It works in any container, not only a
`FormLayout`. Full detail in [Naming a field](../UI_COMPONENTS.md#naming-a-field).

`withLabel` is on the class every input extends, so all of these take one: text field, text area,
select, checkbox, toggle, range, rating, radio group, file picker, swap and theme switch.

A rating and a radio group are several controls rather than one, so there is no single control for
a caption to point at. Those two are captioned as a named group instead: a screen reader reads
"Delivery speed, group" and then each choice inside it. Nothing is different in your code.

You can also give the caption at any time, including after the field is already on the page:

```java
TextField port = new TextField();
layout.add(port);                      // no caption yet
port.setLabel("Port number");          // the caption appears where the field already is
```

## Binder: editing a domain object

`Binder` connects fields to a bean's getters and setters, validates on the way in, and gives you
control over *when* the bean is written.

```java
import com.zeroz4j.ui.binding.Binder;
import com.zeroz4j.ui.binding.ValidationException;

Binder<Registration> binder = new Binder<>();

binder.forField(nameField)
      .asRequired("Name is required")
      .withRule(Registration_Rules.fullName())        // generated from the model's annotations
      .bind(Registration::getFullName, Registration::setFullName);

binder.forField(emailField)
      .withRule(Registration_Rules.email())
      .bind(Registration::getEmail, Registration::setEmail);
```

### Reusing the model's own constraints

`withRule` takes a rule from the generated `<Model>_Rules` class, so constraints declared once on the
`@DataModel` are reused here instead of restated:

```java
@DataModel
public class Registration {
    @NotBlank @Size(min = 2, max = 60) private String fullName;
    @NotBlank @Size(min = 5, max = 120) private String email;
    // constructor, getters, setters
}
```

The annotation processor emits `Registration_Rules`, the client shows the messages, and the **server
enforces the same rules independently** on every RMI argument. Client-side validation is user
feedback; the server's answer is the one that counts.

Use `withValidator` for logic that isn't expressible as a model annotation:

```java
binder.forField(confirmField)
      .withValidator((value, ctx) -> value.equals(passwordField.getValue())
              ? ValidationResult.ok()
              : ValidationResult.error("Passwords do not match"))
      .bind(Registration::getConfirm, Registration::setConfirm);
```

### Where the messages appear

You do not place them. When a check fails, the binder shows the sentence under the field that
failed, colors that control and marks it invalid for assistive technology; when the value is fixed,
all three are cleared. `asRequired` also puts an asterisk after the field's caption, so the form
says which fields are needed before anyone presses Save.

```java
binder.forField(nameField)
      .asRequired("Name is required")                 // asterisk on the caption
      .bind(Registration::getFullName, Registration::setFullName);
```

Set a message yourself — for something the server said, say — with
`field.setErrorMessage("That email address is already registered.")`, and clear it by passing
`null`.

!!! note "This changed in 0.8.0"
    Before 0.8.0 the message went into a stylesheet variable on the field and nowhere else, so the
    field turned red and said nothing. If you wrote a stylesheet rule to display that variable,
    delete it now, or the message appears twice.

### What the reader gets

When a check fails, four things happen at once and you write none of them:

- the sentence appears under the field, in the error color;
- the control is colored to match;
- the field is marked invalid, so a screen reader says "invalid" when the reader reaches it;
- the sentence becomes the field's description, so the same screen reader reads it out.

Correcting the value undoes all four.

This is checked on every build, in a real browser, for every field type: a page is built, a value
is typed in that breaks a rule, and the test asserts that the sentence is part of the text a person
can read on the screen — not merely that the field is holding it somewhere. That distinction is the
whole of the fault that shipped in 0.7.0, and it is why the check is written that way.

### Two modes: write-through and buffered

This is the distinction that matters most, and choosing the wrong one is the usual source of
surprises.

**`setBean(bean)` — write-through.** The binder holds the bean. Every edit is validated and written to
it immediately. Best for "live" editing where there is nothing to cancel.

```java
binder.setBean(registration);
// user types → registration.setFullName(...) happens as they type

saveButton.addClickListener(e -> {
    if (binder.validate().isOk()) {
        registrationService.save(registration);      // RMI
    }
});
```

**`readBean(bean)` — buffered.** The binder populates the fields and then forgets the bean. Edits stay
in the fields until you commit them. Best for a dialog or a form with Save and Cancel.

```java
binder.readBean(registration);                       // load, buffered

saveButton.addClickListener(e -> {
    try {
        binder.writeBean(registration);              // validates, then writes
        registrationService.save(registration);
    } catch (ValidationException ex) {
        // per-field messages are already shown
    }
});

cancelButton.addClickListener(e -> binder.refreshFields());   // discard edits
```

Use `writeBeanIfValid(bean)` if you prefer a boolean to an exception.

!!! warning "`readBean` switches modes"
    Calling `readBean` releases any bean previously passed to `setBean`. That is deliberate: otherwise
    subsequent edits would keep writing into the old instance. Do not mix the two calls on one binder
    unless you intend to switch modes.

### Other useful methods

| Method | Purpose |
|---|---|
| `validate()` | Validates every binding, returns a `BinderValidationStatus` with `isOk()` |
| `hasChanges()` | True when a field differs from the value last read — enable a Save button, or warn before discarding |
| `refreshFields()` | Reload the fields from what was last loaded, discarding uncommitted edits |
| `getBean()` | The bean bound by `setBean`, or `null` in buffered mode |
| `removeBinding(binding)` / `removeAllBindings()` | Unbind, detaching the write-through listener |

### Custom fields and Binder

`Binder` writes edits back by registering a value-change listener. Any field extending
`AbstractField` supports this. If you implement `HasValue` directly, you **must** implement
`addValueChangeListener` and `removeValueChangeListener` — otherwise they throw
`UnsupportedOperationException`, which is deliberate: a silent no-op would make `setBean` appear to
work while never writing anything.

## bindValue: wiring a field to reactive state

`bindValue` connects a field to a `ValueSignal`, two-way, so anything deriving from that signal
updates automatically.

```java
ValueSignal<String> filter = new ValueSignal<>("");
searchField.bindValue(filter);                       // typing updates the signal

Computed<List<Product>> visible = new Computed<>(() ->
        products.get().stream().filter(p -> matches(p, filter.get())).toList());

Effect.create(() -> renderList(visible.get()));      // re-renders as they type
```

Two things to know:

- **Two-way binding requires a `ValueSignal`.** Passing a `Computed` silently degrades to a read-only
  binding, because there is nothing to write back to.
- **`bindValue` discards its `Disposable`.** The effect it creates cannot be released and lives as long
  as the upstream signal — see [Limitations](../reference/limitations.md).

For per-field validation feedback without a `Binder`, attach a generated rule directly:

```java
emailField.withRule(Registration_Rules.email());
if (!emailField.isValid()) { /* emailField.getViolations() */ }
```

Once the field has been typed in, a failing rule shows its message under the field in the same place
a binder would put it.

## Using both together

A realistic edit form:

```java
Binder<Product> binder = new Binder<>();            // the entity being edited
ValueSignal<Boolean> dirty = new ValueSignal<>(false);   // view state others react to

binder.forField(nameField)
      .withRule(Product_Rules.name())
      .bind(Product::getName, Product::setName);

binder.readBean(selected);                          // buffered

nameField.addValueChangeListener(e -> dirty.set(binder.hasChanges()));

Effect.create(() -> saveButton.setEnabled(dirty.get()));
```

`Binder` owns the POJO. The signal owns "is this form dirty", which the Save button and anything else
can derive from.

## See also

- [Choosing how state moves](../decide/index.md) — for state that crosses the wire
- [Validation](../VALIDATION.md) — the annotation set and server-side enforcement
- [Limitations](../reference/limitations.md)
