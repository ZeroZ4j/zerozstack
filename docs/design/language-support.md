# Language support

Status: **PROPOSED DESIGN — none of this is implemented.**
Date: 2026-09-01.

ZeroZ Stack speaks English and nothing else.
There is no message catalog, no locale, no way for a person to pick a language, and no way for the
server to answer in one.
The only `java.util.Locale` in the whole checkout is `Locale.ROOT`, used twice in test code to
lower-case a word before comparing it.

This document is the design for adding language support to both tiers.
It is written before any code because one finding changes the shape of the whole thing, and because
one consequence — the framework's own error messages stop being strings and become keys — breaks
every application and every test that reads them.
Both are cheaper to decide now than to unpick later.

Everything under "What exists today" and "What TeaVM actually does" is verified against the code and
cited.
Everything under "Design" is a proposal.

---

## 1. What exists today

### Nothing, and that is the whole of it

No `ResourceBundle`, no `.properties` catalog, no `MessageFormat`, no locale on any context object.
Searching the checkout for `Locale` returns two test files doing `toLowerCase(Locale.ROOT)`.
So this is greenfield: there is no existing mechanism to keep working, and no migration to write.

### The English that is already written down

There is not much of it, and knowing exactly how much is what makes the breaking section below
affordable.
It falls into four groups.

**The server's own refusals**, in `WasmRmiServerEngine.java`.
These reach the client word for word, by an explicit decision at `:1735-1742`:

```java
static String clientSafeMessage(Throwable failure, String reference) {
    if (failure instanceof ClientVisibleException
            || failure instanceof SecurityException
            || failure instanceof NoSuchMethodException) {
        String message = failure.getMessage();
        if (message != null && !message.isEmpty()) {
            return message;
        }
    }
    return "The server could not complete this request. Reference: " + reference;
}
```

The sentences behind that are at `:1618` (`"Rejected RMI call to unregistered service: "`), `:1634`
(`"Authentication required for: "`), `:1651` (`"Access denied: requires role "`), and the generic
sentence at `:1742`.
The live-mutation refusal reasons are the same shape: `:1143`, `:1208`, `:1212`, `:1216`.
Around a dozen sentences in total.

**The validation messages the annotation processor writes into generated code.**
`RmiAnnotationProcessor.java:674-718` builds four default messages by string concatenation:

```java
String msg = notBlank.message().isEmpty() ? fName + " must not be blank" : notBlank.message();
```

These are generated into `<Model>_Rules`, which is compiled into **both** tiers, so the same English
is used for a browser form hint and for a server-side rejection.

**The component library's own words.**
`"Close"` on a drawer's dismiss overlay (`Drawer.java:129`), `"Copied"` after a copy button
(`CodeBlock.java:78`), `"never"` for an age that has none (`RefreshControl.java:174`),
`"Sending..."` during an upload (`FileUpload.java:445`), `" star"` / `" stars"` on a rating
(`Rating.java:57`), and `"Dark theme"` as a theme toggle's spoken name
(`ThemeController.java:33`).
Roughly a dozen again.

**The client runtime's words.** `"Connection lost — reconnecting…"` (`Zeroz4jClient.java:134`) and
the static offline page at
`zerozstack-server-core/src/main/resources/META-INF/resources/zeroz4j-offline.html`.

Call it forty strings altogether.
That number matters: it is small enough that the framework's own catalog is a weekend, and the work
is all in the mechanism, not the translating.

### Where an identity already lives

`RmiRequestContext` carries five thread-locals — principal, roles, session id, tenant id and
client id — set once per frame by the engine and cleared in a `finally`.
It is the place a locale belongs, and §4.6 puts one there.

`Scope.CLIENT` already solves the hardest part of "remember this browser's choice".
Every connection carries a server-issued, HMAC-signed 256-bit id in an `HttpOnly` cookie, minted
when the page is served and again at the handshake if the browser presents none, valid for a year
by default (`zeroz.clientId.ttlDays`).
It survives reconnects, page reloads, restarts and a cluster.
See [Client identity without a login](../guides/security-auth.md#client-identity-without-a-login).

---

## 2. What TeaVM actually does, and how that was established

The client is Java compiled to JavaScript by TeaVM 0.15.0.
That a class exists in `teavm-classlib-0.15.0.jar` proves nothing about whether it works in a
browser, so every claim here was checked against TeaVM's own source and against generated
JavaScript.

**This section is the reason the design looks the way it does.**
Read it before §4 or §4 will look arbitrary.

### `ResourceBundle` works, but it is a compile-time mechanism

`java.util.ResourceBundle`, `PropertyResourceBundle` and `ListResourceBundle` are all present.
They are not what a Java developer expects.

`TResourceBundle` looks a bundle up in a map built once, at compile time, by
`org.teavm.classlib.impl.ResourceBundleImpl.createBundleMap` — a TeaVM metaprogramming method
annotated `@Meta`.
That method scans the compile classpath for every `META-INF/services/java.util.ResourceBundle`
resource.
Each line is a fully qualified bundle name **including its locale suffix**:

```
com.example.app.Messages
com.example.app.Messages_de
```

For each line it looks for a class with a no-argument constructor; failing that it looks for
`com/example/app/Messages_de.properties` on the classpath, reads it **at compile time**, and emits a
synthetic `ListResourceBundle` whose `getContents()` returns a hard-coded `Object[][]` of the
key/value pairs.

Three consequences, and all three are load-bearing:

1. **The translations are baked into `classes.js` as string literals.** They are not fetched, not
   read from a file, and not loadable from anywhere at runtime.
2. **A language that is not on the TeaVM compile classpath does not exist.** Adding Portuguese means
   recompiling the client, not dropping a file on a server.
3. **A bundle that is not listed in the services file is invisible**, even if the `.properties` file
   is right there. There is no directory scan.

**So the browser cannot load a `.properties` file at runtime.** That is the finding that shapes
everything. A design that assumed otherwise would have to be thrown away.

### `Locale.getDefault()` is a build constant, and nothing reads the browser's language

`TLocale`'s static initializer reads `CLDRHelper.getDefaultLocale()`, which is generated by
`DefaultLocaleMetadataGenerator`:

```java
result.value = context.getProperties().getProperty("java.util.Locale.default", "en_GB");
```

That is a TeaVM build property, resolved when the compiler runs.
`Locale.getDefault()` in a browser therefore returns `en_GB` unless the build says otherwise, on
every machine in the world, regardless of the person's own settings.
Nothing in TeaVM ever reads `navigator.language`.

The value must contain an underscore: the initializer does
`localeName.substring(0, localeName.indexOf('_'))` with no guard, so `java.util.Locale.default=de`
throws at class-init time and takes the whole application down.

`Locale.setDefault(...)` does exist and does work at runtime, so the default can be corrected once
the application knows better.
Two cautions: `TResourceBundle`'s cache is static and caches "no such bundle" results, and
`getBundle(name, locale)` falls back to `Locale.getDefault()` when the requested locale has no
bundle. Changing the default after lookups have happened gives results that depend on call order.

### The rest of the toolkit, checked one class at a time

| Class | In TeaVM 0.15? |
|---|---|
| `java.text.MessageFormat` | yes |
| `java.text.ChoiceFormat` | yes |
| `java.text.NumberFormat`, `DecimalFormat`, `DecimalFormatSymbols` | yes |
| `java.text.SimpleDateFormat`, `DateFormatSymbols` | yes |
| `java.util.Currency` | yes |
| `java.text.Collator` | **no** |
| `java.text.BreakIterator` | **no** |
| `java.text.Normalizer` | **no** |
| `java.text.Bidi` | **no** |

The four missing ones are why §6 rules out locale-aware sorting and right-to-left layout rather than
leaving them as future work: there is nothing to build them on.

The five that are present are all expensive, which the measurements below quantify and which §4.3
and §4.12 are built around.

### How much CLDR data is compiled in is a build property

`teavm-classlib-0.15.0.jar` carries `cldr-json.zip`, 15.8 MB of locale data.
`CLDRReader` reads the build property `java.util.Locale.available`, default `"en_EN"`, splits it on
commas, and keeps only the locales it names:

```java
String availableLocalesString = properties.getProperty("java.util.Locale.available", "en_EN").trim();
```

So the CLDR weight in a bundle is chosen, not inherited, and it is chosen in the
`teavm-maven-plugin` configuration — the same `<properties>` block the example poms already use for
`java.util.ServiceLoader.parseServiceConfiguration`.

Dead-code elimination removes all of it when nothing calls `NumberFormat` or `SimpleDateFormat`,
which is why today's client bundles carry none.

### The measurements

Every number below is the emitted `target/js/classes.js` of the `todo-signals` example client,
compiled with that module's own settings — `targetType=JAVASCRIPT`, `optimizationLevel=SIMPLE`,
`minifying=false` — plus its gzip size, which is what a browser actually downloads.
Each row is one real build.

| What was compiled | bytes | gzip |
|---|---|---|
| The example, unchanged | 1,261,677 | 208,530 |
| + a `ResourceBundle` with **2** languages of 40 keys | 1,289,814 | 213,746 |
| + the same with **6** languages | 1,310,537 | 217,120 |
| + the same with **20** languages | 1,383,397 | 228,198 |
| **20 locales declared** in `java.util.Locale.available`, no formatting call | 1,289,814 | 213,746 |
| One `NumberFormat.getInstance(locale).format(n)` call, nothing else | 1,495,041 | 251,904 |
| One `ChoiceFormat.format(n)` call, nothing else | 1,526,590 | 265,975 |
| One `MessageFormat.format(pattern, arg)` call, 2 languages | 1,779,525 | 302,648 |
| + `NumberFormat` and `SimpleDateFormat` on top, 1 locale's CLDR data | 1,780,823 | 302,917 |
| + the same with **6** locales' CLDR data | 1,958,200 | 331,008 |
| + the same with **20** locales' CLDR data | 2,460,982 | 418,468 |

Five things come out of that, and the second one changes the design.

**1. Translated text is cheap.** About **5.2 KB raw and 0.8 KB gzip per extra language** of 40
strings, measured consistently across the 2-, 6- and 20-language builds. Twenty languages of
interface text cost under 20 KB of download in total. Text is not the problem.

**2. Touching `java.text` at all is expensive, and `MessageFormat` is the worst of it.**
One `MessageFormat.format` call adds **490 KB raw and 89 KB gzip** — a 43% increase in what the
browser downloads, from one call. `NumberFormat` alone costs 233 KB raw and 43 KB gzip;
`ChoiceFormat` alone, 265 KB and 57 KB. There is no cheap corner of that package.
**This is more expensive than twenty languages of translated text, several times over**, and §4.3
and §4.12 are shaped around avoiding it.

**3. CLDR locale data costs about 36 KB raw and 6 KB gzip per locale** listed in
`java.util.Locale.available`, and it is charged only once `java.text` formatting is reachable at
all.

**4. Declaring locales you never format with is free.** Twenty locales declared, with no formatting
call anywhere, produced a file **byte-identical** to the two-language build. The data is
demand-driven, not fixed.
That is worth knowing because it means a wrong `java.util.Locale.available` costs nothing until
somebody formats a number, and then costs a great deal all at once.

**5. `Locale.setDefault(...)` compiles and links.** A build calling it and then requesting a German
bundle succeeded. It was **not executed in a browser** — no claim is made here about which bundle it
selects at run time.

Two caveats on the figures. They were taken on the examples' unminified `SIMPLE` build; the
archetype generates `ADVANCED` and minified, so a real application's absolute numbers are smaller
and the ratios are what transfer. And every test string was ASCII, so accented and CJK text will
cost more per string than the 5.2 KB per language above.

---

## 3. The decisions already taken

These three are settled and this document builds on them rather than arguing with them.

1. **Both tiers are translated.** The server formats its own messages — validation failures,
   live-mutation refusals, `ClientVisibleException`, its own refusals — in the caller's language,
   using the identity the connection already carries.
2. **The language is remembered per signed-in user, with the browser's own setting as the first
   visit's default.** An anonymous visitor falls back to the browser.
3. **Switching updates the screen live.** The current language is a signal: text re-reads and the
   page changes with nothing lost.

§4.6 keeps decision 2 honestly rather than completely, and §8 says so out loud.

---

## 4. Design

### 4.1 Two catalogs, two problems, two mechanisms

An application's catalog and the framework's own catalog look alike and behave differently, and
conflating them is the mistake to avoid.

| | The application's catalog | The framework's catalog |
|---|---|---|
| Size | hundreds of strings, grows forever | about forty, barely grows |
| Who writes it | the application author | this project |
| When it is needed | after the UI mounts | **before the socket is up** — "Connection lost" has to work when there is no connection |
| Which languages | whatever the project sells in | whatever this project ships |
| How it reaches the browser | over the wire, per connection (§4.4) | compiled in, every shipped language (§4.5) |
| How it is built | by the annotation processor (§4.3) | by hand, with a build-failing parity check (§4.5) |

The split is not tidiness. It is forced: the framework's client-side words are the ones a person
reads when nothing is working, so they cannot arrive over the thing that is not working.

### 4.2 Where an application's catalog lives

In the **shared** module, as ordinary `.properties` files:

```
myapp-shared/src/main/resources/i18n/app.properties        # the fallback language
myapp-shared/src/main/resources/i18n/app_de.properties
myapp-shared/src/main/resources/i18n/app_fr.properties
```

The shared module is the only one compiled into both tiers, which is what makes one file the source
for both a browser label and a server-side validation message.
Its resources are on the server's runtime classpath and on the client's TeaVM compile classpath, so
both tiers can reach them by the route each one needs.

An application declares the catalog with one marker class, in the same module:

```java
package com.example.shared;

import com.zeroz4j.api.i18n.MessageCatalog;

@MessageCatalog(baseName = "i18n/app", fallback = "en")
public final class AppText {
    private AppText() { }
}
```

`baseName` is a classpath path, not a package name, so it reads the same on both tiers.
`fallback` names the language whose file has no suffix and whose text is compiled into the browser.

### 4.3 The annotation processor's part: keys become methods

The processor already generates `_Serializer`, `_Rules`, `_Live`, `_Stub` and the SPI registrar.
It gains one more output, and it is the piece that keeps message keys from being free strings.

From `AppText` above the processor reads `i18n/app.properties` through
`Filer.getResource(StandardLocation.CLASS_PATH, ...)` and generates **`AppText_Text`**: one method
per key, named by camel-casing the key, with one parameter per placeholder.

Given:

```properties
task.add = Add task
task.remaining = {0} of {1} tasks left
```

it generates:

```java
public final class AppText_Text {
    public static Message taskAdd() {
        return new Message("i18n/app", "task.add");
    }
    public static Message taskRemaining(Object arg0, Object arg1) {
        return new Message("i18n/app", "task.remaining", arg0, arg1);
    }
}
```

**A method returns a `Message`, not a `String`, and that is the whole trick.**
`Message` is a plain immutable value — catalog, key, arguments — that compiles on both tiers and
carries no locale of its own. Turning it into words is a separate, explicit act:

```java
String words = AppText_Text.taskAdd().text();
```

`text()` resolves against the current language — the signal on the client, the caller's locale from
`RmiRequestContext` on the server — so the same call site is right on both.
That mirrors `Signals.scoped`, whose `mine()` and `forTarget(...)` split already teaches this shape.

**Placeholders are `{0}`, `{1}` and nothing else, substituted by about forty lines of the
framework's own code on both tiers.** Not `java.text.MessageFormat`, and the reason is measured:
one `MessageFormat.format` call adds 89 KB gzip to the client bundle (§2), which is a 43% increase
in what every visitor downloads, for a feature that is almost always plain substitution.

Positional substitution and nothing more also buys something the fancier syntax cannot: the client
and the server render a message **byte-identically**, because they run the same forty lines. A
message formatted one way in a browser hint and another way in the server's rejection of the same
value would be a defect, and this design cannot produce one.

An application that wants `{0,number,currency}` formats the value itself and passes the words:

```java
AppText_Text.invoiceTotal(Formats.currency().format(amount))
```

which keeps the 43 KB `java.text` cost (§4.10) on the one application that asked for it.

Five things fall out of it, and each one is a class of bug that never happens:

- **A misspelled key is a compile error**, not a `???task.add???` on a screen.
- **A wrong argument count is a compile error.** Passing one value to a two-placeholder message
  cannot be written.
- **A key deleted from the fallback file breaks every call site**, so dead keys get noticed.
- **A message can be carried without being turned into words yet**, which is what lets an exception
  travel to the edge of the server and be rendered in the caller's language there while the log line
  keeps its English (§4.8).
- **The build check in §4.9 has one method name to look for.** `.text()` is a single greppable
  token across every catalog in the checkout; free-string lookups would be neither.

The processor also emits **`AppText_Catalog`**, the fallback language baked into Java as a `switch`
on the key returning a string literal.
That is what the browser falls back to when the server's catalog has no answer, and it deliberately
does *not* go through TeaVM's `ResourceBundle` machinery — a generated `switch` is smaller,
predictable, and free of the static-cache and `Locale.getDefault()` coupling described in §2.

Two errors the processor reports rather than emitting broken code: two keys that camel-case to the
same method name (`task.add` and `taskAdd`), and a key whose placeholders are not a contiguous run
from `{0}`. A third is worth a warning: a key containing a comma inside its braces, such as
`{0,number}`, which reads like `MessageFormat` and is not.

**Every other language file is never seen by the processor and never reaches the client build.**
Adding French is dropping `app_fr.properties` into the shared module.
Nothing is regenerated, nothing is recompiled for the browser, and the bundle does not grow.

A separate build-failing test — the project's established answer — reads every `app_*.properties`
beside the fallback and fails when one is missing keys the fallback has, or carries keys it does
not, or gives a key a different number of placeholders.
A translation that is merely absent is a blank screen area; a translation with the wrong placeholder
count leaves a literal `{1}` on somebody's screen, or drops a value they needed to read.

### 4.4 The client's catalog arrives on the frame that already says "you can start"

The browser cannot load a catalog from a file (§2) and should not carry every language (§4.10).
So the server sends it.

The AUTH frame (`0x03`) is already sent on **every** connection, authenticated or not, and it
already begins with a protocol version byte, currently 2.
Bump it to 3 and append the catalog for the resolved locale: a count, then key/value pairs.
The client stores them in a map and `Messages.lookup` reads from it.

This costs no extra round trip.
The framework already tells applications to mount the UI from `onResolved(...)` — the callback that
fires when the AUTH frame has arrived — so the catalog is in hand at the exact moment the first
screen is built.
There is no window in which English is shown and then replaced.

What it costs is bytes on one frame.
A three-hundred-string catalog at forty characters a string is about 12 KB, once per connection.
That is inside the 4 MB default message ceiling (`zeroz.ws.maxBinaryMessageBytes`) by three orders
of magnitude, and it is smaller than what a single language would add to the bundle if compiled in.

Two refinements, both worth having and neither required for a first version:

- **Send a hash first.** The client keeps the last catalog it received in `localStorage`, keyed by
  locale, and sends the hash it holds on the handshake. A match means the server sends the hash
  back and no strings. This turns 12 KB per connection into 12 KB per deployment per browser.
- **Only for the fallback locale, send nothing at all.** The fallback is already compiled in.

The compiled-in fallback catalog (§4.3) covers three moments: before the AUTH frame arrives, when
the connection is down and a screen is rebuilt, and when the server's catalog lacks a key the
client asks for.

### 4.5 The framework's own catalog is compiled in, in every language it ships

"Connection lost — reconnecting…" has to be readable while the connection is lost.
So the framework's own client-side words are compiled into `classes.js` for **every** language this
project ships, and there is no wire path for them at all.

They live in `zerozstack-shared-api`, which every other module already depends on — including
`zerozstack-ui-components`, which is where most of them are used:

```
zerozstack-shared-api/src/main/resources/i18n/zeroz4j.properties
zerozstack-shared-api/src/main/resources/i18n/zeroz4j_de.properties
```

They cannot be generated by the annotation processor.
`zerozstack-apt` depends on `zerozstack-shared-api`, so shared-api is built first and cannot run a
processor that does not exist yet.

So `FrameworkText` is written by hand — a class with a `switch` per language — and kept honest by
`FrameworkCatalogParityTest`, a build-failing test that reads both the class and the `.properties`
files and fails when they disagree in either direction.
That is not a workaround; it is the pattern this project already uses six times over, and it costs
no new build machinery.

Forty strings times the number of shipped languages is a few kilobytes.
§4.10 has the measured figure.

The offline page is a static HTML file with no Java in it.
It carries every shipped language as hidden blocks and a three-line inline script that shows the one
matching `navigator.language`, falling back to English.
It is the one place in the design where `navigator.language` is read directly, because it is the one
place with no framework running.

### 4.6 How the server learns the caller's language

`RmiRequestContext` gains a sixth thread-local and a getter:

```java
public static Locale getLocale();
```

Never null. It returns the deployment default when nothing better is known, so no caller ever has to
null-check, and a service that formats a message never accidentally formats it in the JVM's own
locale — which on a server in Frankfurt is German and has nothing to do with the person calling.

The engine sets it per frame from the session, exactly as it already sets principal, roles, tenant
and client id, and clears it in the same `finally`.

**The session's locale is resolved once, at the handshake**, in this order:

1. The `lang` handshake parameter, when the client sends one. The client sends it because it may
   already know the answer from its own stored choice.
2. A `zeroz-lang` cookie, read from the handshake headers. This is what makes the choice survive a
   restart and a new connection, and it is what the client reads to fill in step 1.
3. The `Accept-Language` header. Browsers send it on a WebSocket upgrade like any other request, and
   `HandshakeCredentials.header("Accept-Language")` already exposes it.
4. `zeroz.i18n.defaultLocale`, the deployment's own setting.
5. `en`.

Whatever is resolved is then narrowed to the languages the deployment actually has a catalog for:
`de-AT` becomes `de` when only `de` exists, and falls to the default when neither does.
A person's browser asking for a language nobody translated must never produce a half-translated
screen.

**A connection that never chose one gets step 3, then 4, then 5** — which is to say it gets the
browser's own preference, which is decision 2's "first-visit default" and needs nothing stored
anywhere.

**Where the remembering happens is the honest gap.**
Decision 2 says the language is remembered per signed-in user.
The framework has no user store and cannot acquire one without answering the much larger question
the [transactions design](persistence-transactions.md#7-open-questions) leaves open — whether the
framework writes to the application's database at all.

So the framework remembers **per browser**, in a cookie, and offers a one-method seam for an
application that wants it per person.

**The client writes that cookie, not the server**, and this is deliberate. The client-id cookie is
`HttpOnly` and written by the server at the handshake, and the code that does it carries a caution
worth reading (`RmiEndpointConfigurator.java:350-354`):

```java
} catch (RuntimeException ex) {
    // Some containers expose an immutable response header map. The id still works for
    // this connection; it just will not persist past it.
```

A language is not a secret and nothing is protected by it, so it needs none of that. The client sets
an ordinary `document.cookie` — `SameSite=Lax`, a year, no `HttpOnly` — the moment the language
changes, and the server only ever reads it. That works in every container, has no failure mode to
log, and needs nothing on the handshake response.

The seam for per-person memory:

```java
package com.zeroz4j.server;

public interface LocalePreferenceStore {
    Locale forUser(String userName);            // null when unknown
    void remember(String userName, Locale locale);
}
```

Discovered by `ServiceLoader`, like `AuthenticationProvider`, because the handshake runs before CDI
beans exist.
Registered, it is consulted between steps 1 and 2 for an authenticated connection, and written
whenever the language changes.
Not registered, the cookie is the whole story.

This covers the common case completely and the second-device case not at all, and §8 puts that in
front of the owner rather than burying it.

### 4.7 Switching, live

The current language is a scoped writable signal, declared once by the framework in the shared
module:

```java
public final class Zeroz4jSignals {
    public static final ScopedSignal<String> LOCALE =
            Signals.scopedWritable("zeroz.locale", "en", Scope.CLIENT);
}
```

`Scope.CLIENT` because a language must work with no login, and because the client id already
survives reconnects and reloads.

The whole switch is then:

```java
// client — the selector writes it
Zeroz4jSignals.LOCALE.mine().set("de");
```

and everything else falls out of machinery that already exists:

- The client applies the write optimistically, writes its own `zeroz-lang` cookie (§4.6) and sends
  the value up.
- The server accepts it, stores it on the session, calls `LocalePreferenceStore.remember(...)` if
  one is registered, fetches the catalog for the new language and sends it down, then broadcasts the
  accepted value.
- `Messages.lookup` on the client reads `Zeroz4jSignals.LOCALE.mine()`. Every `Effect` and
  `Computed` that has read a message is therefore subscribed to the language, and re-runs.
  **Text updates for the same reason every other value in this framework updates.** Nothing new is
  invented, which is the point.
- Nothing is lost, because nothing is rebuilt except the text: an effect re-runs and calls
  `setText` on the label it already owns. Field contents, scroll position and focus are untouched.

**Server-side ordering is already guaranteed.** Since 0.8.0 one connection's messages are handled in
the order the browser wrote them, so a service call sent after a language write is answered in the
new language. There is no race to design around and no flush to write.

Two things to get right in the implementation:

- The catalog must be applied **before** the signal's new value is published, or the first effect to
  re-run reads the old catalog under the new language name.
- A locale the server has no catalog for is refused the way any invalid signal write is refused: the
  writer is snapped back to the accepted value and told why. A selector that only offers what the
  server has never triggers this, but a client can send anything.

### 4.8 The framework's own messages become keys — the breaking part

This is the largest consequence in the document and the reason it is written now.

**What is true today.** The framework's own refusals reach the client word for word
(`WasmRmiServerEngine.java:1735-1742`), and this is documented as a feature: the
[security guide](../guides/security-auth.md#what-an-error-tells-the-caller) says those messages
"exist to be read, and clients already act on them", and `ClientVisibleException`'s javadoc says the
message "travels to the client word for word".

Applications read `getMessage()`. Tests assert on exact English. **The framework's own tests do
too.** So do the examples.

**What has to change.** A message that is a `String` written on the server cannot be produced in the
caller's language unless the server knows the caller's language at the moment the string is
constructed — and the string is constructed deep inside a service method that knows nothing about
connections. The message must therefore stop being a string and become a key plus arguments,
formatted at the edge where the locale is known.

`ClientVisibleException` gains a second constructor and keeps the first:

```java
// unchanged, and still correct — one language, sent as written
throw new ClientVisibleException("That invoice was already approved.");

// translated — the exception carries the Message, not words
throw new ClientVisibleException(AppText_Text.invoiceAlreadyApproved(invoiceNumber));
```

Carrying the `Message` rather than calling `.text()` at the throw site is what buys the log line.
`sendError` — the one place that already decides what a caller is told, and the one place with the
caller's locale in hand — renders it in the caller's language for the wire and in English for the
log, from the same value.

The framework's own refusals become `Message` values from the framework catalog, rendered in the
same place.

**What breaks, precisely:**

| Breaks | Who feels it |
|---|---|
| A test asserting `"Access denied: requires role approver"` | this project's own tests, and any application that copied the pattern |
| A client comparing an error message to a literal to decide what to show | applications; already a bad idea, now a broken one |
| A validation message baked into `<Model>_Rules` | every application, silently — the generated English is simply no longer what appears |
| Log-scraping on the server's own message text | operators |

**What does not break:** an application that throws `ClientVisibleException` with a plain string.
That constructor stays, and it stays correct. A single-language application changes nothing at all.

**English stays the fallback, and stays exact.** Three reasons, and the third is the one that
settles it:

1. The fallback catalog is compiled in, so English is always reachable with no lookup that can fail.
2. A deployment that never configures a language sees byte-identical behavior to today.
3. **A test can then assert on the key rather than the sentence**, which is what it should have been
   asserting on all along. Provide `assertRefusedWith(FrameworkKeys.ACCESS_DENIED, thrown)` in
   `zerozstack-server-test` and the upgrade is mechanical.

**What an upgrader does**, in order:

1. Change nothing. Everything works, in English, exactly as before. This is the honest default and
   most projects stop here.
2. When a test breaks, replace the literal with the key. The release notes carry the table of old
   sentence to new key — around a dozen rows, listed in §1.
3. To translate their own refusals, add `.properties` files and swap
   `new ClientVisibleException("...")` for the key form, one call site at a time. The two forms
   coexist indefinitely.

**One thing that only half works.** The log keeps English, because an operator reading a log at
three in the morning should not have to know which language the caller had.
That is free for a refusal thrown as a `Message`: `sendError` renders English for the log and the
caller's language for the wire, from one value.

It is not free for a refusal thrown as a plain string in a language the server picked some other
way, and it is not free for a support ticket. Somebody quoting a German sentence off their screen
still has to be matched to an English log line. The reference code already solves this for unplanned
failures — `The server could not complete this request. Reference: 4f2a91cc` — and the same code
should be put on translated refusals, or support has a translation problem of its own.

### 4.9 The reactive-read hazard, and what a build check can and cannot catch

**The mistake, which everyone will make:**

```java
// WRONG — .text() read once, at construction. Switching the language leaves this label behind.
Button add = new Button(AppText_Text.taskAdd().text());
```

```java
// RIGHT — .text() read inside an effect, so the language signal is a dependency and it re-reads.
Button add = new Button();
Effect.create(() -> add.setText(AppText_Text.taskAdd().text()));
```

This is the same shape as the LiveSync hazard already in the troubleshooting table — *"the getter
was read outside an `Effect`, so nothing subscribed"* — and it will be more common, because a
screen has far more labels than live objects.

It is also invisible in testing. Route views are rebuilt on navigation, so a developer who switches
language and then navigates sees everything correct. The stale label only shows on the screen that
was open when the switch happened, which is exactly the screen nobody tests.

**The check.** A source-reading test in `zerozstack-ui-components/src/test`, in the established
style: it reads every Java file in the checkout, finds every `.text()` call on a `Message`, and
fails the build when that call is not lexically inside an `Effect.create(...)` or
`new Computed<>(...)` body.

`.text()` on a `Message` is the only way words are ever produced (§4.3), so one method name is the
whole surface the check has to watch. That is the practical reason the generated methods return a
value rather than a `String`: a design where every catalog method returned words would give the
check hundreds of names to know about, and it would go stale the first time somebody added a
catalog.

Brace matching over source text, the same technique `DetachContractTest` uses for
`setInnerHTML("")` and `JsBodyNamingContractTest` uses for `@JSBody` scripts. Those already work and
already fail builds, so the mechanism is proven.

The escape hatch is explicit, because a hidden one grows until it is the rule.
A method that legitimately reads a message outside an effect — a validation message assembled for a
single call, a string handed straight to the server — is annotated `@ReadsMessagesOnce`, and the
check skips it and says in its failure message that the annotation exists.

**What it cannot catch. All of these are real and none has an automated answer:**

- **A read one call deep.** `Effect.create(() -> add.setText(buildLabel()))` where `buildLabel()`
  calls `.text()` is correct and the check calls it wrong; a constructor calling the same
  `buildLabel()` is wrong and the check calls it right. The check sees one file's text and cannot
  follow a call. The `@ReadsMessagesOnce` hatch is what makes the false positive survivable, and
  nothing makes the false negative visible.
- **A `Message` stored in a field and rendered later.** The `Message` itself is fine to hold — it
  has no language in it — so passing one around is correct and encouraged. Where somebody calls
  `.text()` on it is the only thing that matters, and if that happens in a file the check has
  already cleared, it is cleared.
- **The opposite mistake — English left hard-coded.** A screen with `new Button("Add task")` in it
  is not translated at all, and no check reliably tells a user-visible literal from a CSS class
  name, a DOM attribute or a log line. A narrow version is possible and worth having: fail the build
  when a component **in this repository** passes a string literal to `setText`, `setLabel` or an
  `aria-label`. That covers the framework's own words and covers nothing in anybody's application —
  the same honest limitation the accessibility checks already carry.
- **Whether a translation is any good.** "Button" and "Delete this invoice permanently" both pass
  the naming check today, and `taskAdd` returning the German for "subtract task" passes everything.
- **Words read into a field and used later.** `String label = AppText_Text.taskAdd().text();`
  inside an effect passes, and every later use of `label` is stale. Correct at the moment it runs
  and indistinguishable from correct code afterwards.

### 4.10 Bundle size, and which languages ship

The measurements in §2 make this section short, because they settle it.

**The application's languages cost the browser nothing.** They travel on the wire (§4.4), so a
deployment that offers twelve languages compiles exactly the same bundle as one that offers one.
Adding a language is dropping `app_pt.properties` into the shared module and restarting the server.

**The framework's own languages cost about 0.8 KB gzip each**, because it has about forty strings
and they are compiled in (§4.5). Six languages is under 5 KB of download on a 209 KB baseline.
That figure was measured through TeaVM's `ResourceBundle`, which also charges a one-time 3.6 KB of
machinery; the generated `switch` of §4.3 skips that machinery, so it should land at or under the
measured per-language figure. It has not been measured separately.

**Recommendation: ship the framework's own words in English and one other language, and let the
per-language cost be a non-decision.** It is small enough that the argument is about maintaining
translations, not about bytes. §8 puts the choice of which languages in front of the owner.

**The real budget item is `java.text`, and the design spends none of it by default.**

| Choice | gzip cost | Who pays |
|---|---|---|
| Any number of translated languages | 0 | nobody — they come over the wire |
| The framework's own words, per language | ~0.8 KB | every application |
| Reaching `java.text` at all (`Formats`, a `{0,choice}` message) | 43–89 KB | only an application that calls it |
| Each locale in `java.util.Locale.available`, once `java.text` is reachable | ~6 KB | only that application |

So the default is: `java.util.Locale.available` stays at the deployment's own single locale, nothing
in the framework calls `java.text`, and an application that never formats a number pays nothing for
localization beyond the framework's own words.

**A project that wants locale-correct numbers, money or dates opts in explicitly**, in its own
client module's `teavm-maven-plugin` configuration — the same `<properties>` block that already
carries `java.util.ServiceLoader.parseServiceConfiguration`:

```xml
<properties>
  <java.util.ServiceLoader.parseServiceConfiguration>true</java.util.ServiceLoader.parseServiceConfiguration>
  <java.util.Locale.available>en_US,de_DE,fr_FR</java.util.Locale.available>
  <java.util.Locale.default>en_US</java.util.Locale.default>
</properties>
```

The first call to `Formats.number()` costs 43 KB gzip; each locale in that list then costs 6 KB
more.
Both numbers belong in the guide beside the API, not in a caveats file, so nobody discovers
them from a bug report about a slow first load.

**`java.util.Locale.default` must contain an underscore** (§2) or the application dies at class-init
with no useful message. The archetype should set it, and a startup check should refuse a malformed
value rather than letting TeaVM's `substring(0, -1)` explain it.

### 4.11 The selector

`LanguageSelector`, in `zerozstack-ui-components`, beside `ThemeController` — which is the exact
precedent: a small framework-provided control that binds to one piece of global state and gets
dropped into an application's shell.

It extends the existing `Select`, which is a real `<select>` element, so the keyboard contract is
met by the browser and not by hand: Tab reaches it, arrows and typing move through the options,
Enter and Escape close it.
The [keyboard and naming guide](../guides/ui-keyboard-and-naming.md) is satisfied the easy way, and
the easy way is the one it asks for.

The accessible name is the part to get right, and `ThemeController` shows how.
It carries a built-in name, `"Language"`, translated in the framework's own catalog, and
`setLabel(...)` replaces it and removes the built-in one so the two cannot disagree.

Two rules about the option text, which is where most language pickers go wrong:

- **Each language is named in itself** — `Deutsch`, `Français`, `日本語` — never in the current
  language. Somebody who has landed on a page in a language they cannot read is exactly the person
  who needs this control, and "German" is no help to them.
- **The names come from the framework catalog, not from `Locale.getDisplayLanguage()`.** That method
  works in TeaVM but resolves against CLDR data, so it only answers for languages in
  `java.util.Locale.available` — and §4.10's recommendation is that most deployments carry CLDR data
  for one. Fixed strings always work and cost about twenty bytes each.

It binds to the signal, and that is the whole component:

```java
LanguageSelector picker = new LanguageSelector();          // options come from the server's list
picker.bindValue(Zeroz4jSignals.LOCALE.mine());
```

The list of offered languages arrives with the catalog on the AUTH frame, so the control offers
exactly what the deployment can actually serve. It never offers a language that would be refused.

### 4.12 Numbers, dates and money

A translated interface that prints `1,234.56` to a German reader is not translated.

`NumberFormat.getInstance(locale)`, `getCurrencyInstance(locale)` and `SimpleDateFormat` with a
locale all work in TeaVM, and all read CLDR data.

**They are also the expensive part of this whole design, and they are therefore opt-in.**
The first call into `java.text` costs 43 KB gzip, and each locale of CLDR data costs 6 KB more
(§2). Nothing in the framework calls it, so an application that never formats a number never pays.

`Formats.number()`, `Formats.currency()` and `Formats.date()` are how an application opts in. They
read the language signal on the client and `RmiRequestContext.getLocale()` on the server, so the
same call site works on both tiers — the `mine()` / `forTarget(...)` shape `Signals.scoped` already
teaches. They live in their own class for a reason: the moment anything in an application reaches
one, its bundle grows by 43 KB, and a class nobody imports by accident is a cost nobody pays by
accident.

Four limits to state where the feature is taught rather than in a distant file:

- **The bill arrives with the first call.** One `Formats.number()` anywhere in a client module adds
  43 KB gzip to what every visitor downloads, and it is invisible in code review. A build that
  reported the emitted size would catch it; nothing reports it today.
- **A locale with no CLDR data formats as the fallback language.** No error, no warning: the numbers
  are simply grouped the English way. The build cannot catch it, because the two lists — languages
  you translated, locales whose CLDR data you compiled in — are set in different files for different
  reasons.
- **Time zones are a separate axis and are off by default.** TeaVM's `java.util.TimeZone.autodetect`
  defaults to `false`, so the browser's own zone is not detected unless the build asks for it. A
  timestamp translated into German and shown in UTC is still wrong.
- **`ZonedDateTime` and `OffsetDateTime` do not cross the wire at all** — they are on the
  serialization exclusion list in
  [limitations](../reference/limitations.md#serialization). Send an `Instant` and format it against
  a zone the client knows.

---

## 5. Not adopted

**TeaVM's `ResourceBundle` as the client's runtime mechanism.** It works, and using it would mean
every language compiled into the browser bundle (§2), a services file listing every locale by hand,
and a static cache whose results depend on the order lookups happened in. The generated `switch` of
§4.3 gives the fallback with none of that, and the wire gives the rest.

**`java.text.MessageFormat` as the placeholder mechanism.** It is the obvious choice, it is the one
every Java developer would reach for, and it costs 89 KB gzip in the browser — 43% more download,
from one call (§2). Forty lines of positional substitution cost nothing, render identically on both
tiers, and cover every message this framework's own catalog contains. The format types it gives up
(`{0,number,currency}`) are reachable by formatting the value first (§4.3).

**Free-string keys — `t("task.add")`.** Familiar from every JavaScript framework and wrong for this
one. A misspelled key would be a runtime blank in a framework whose entire thesis is that routes,
serializers, validation rules and stubs are all resolved at compile time. Generated methods (§4.3)
cost one build step and remove a whole class of defect.

**A locale argument on every service method.** Explicit, and wrong for the same reason the security
guide gives for identity: *"Never take the caller's identity from a method argument."* A locale from
an argument is a locale the caller can lie about and the framework cannot default. It belongs on the
context, with everything else the connection carries.

**Reconnecting to change language.** It would make the server's locale a handshake-only value and
save a small amount of session state. It also throws away every live object, every subscription and
anything typed and not sent, which contradicts decision 3 and is worse than the problem.

**Translating the framework's log output.** English, always. An operator reading a log should not
have to know what language the caller had, and a searchable log is worth more than a polite one.

---

## 6. Out of scope, said plainly

Named here so nobody assumes they are included.

**Right-to-left layout.** Arabic and Hebrew need mirrored layouts, not just translated words:
`dir="rtl"` on the document, and every physical direction in the stylesheet — `margin-left`,
`text-align: right`, an icon that points forward — replaced by a logical one. The component library
uses physical directions throughout and has never been looked at in a mirror. Translating the words
without doing the layout produces something worse than English. TeaVM has no `java.text.Bidi`
either, so mixed-direction text within one string has nothing to sort it out. **Not designed, not
tested, not planned.**

**Plural rules of any kind.** The framework's own substitution (§4.3) replaces `{0}` with a value
and does nothing else, so `"{0} tasks left"` says "1 tasks left" and the application has to pick
between two keys itself. `java.text` would give you
`{0,choice,0#no tasks|1#one task|1<{0} tasks}`, which is correct for English, German, Dutch and the
other two-form languages. It is **not** ICU's `{count, plural, one{…} few{…} many{…} other{…}}`, and
TeaVM has no ICU. Polish, Russian, Arabic and Welsh need three to six forms chosen by rules
`ChoiceFormat` cannot express, and writing the boundaries by hand gets them wrong.

There is a second reason not to reach for `ChoiceFormat` even where two forms are enough: it is part
of `java.text`, and one call costs 57 KB gzip in the browser (§2) — more than every language of
translated text this design will ever ship. Two forms are usually better written as two keys and an
`if`, which costs nothing. A project needing genuine plural rules must supply its own selection, and
should know what it is spending.

**Translating content stored in the database.** This design translates the *interface* — the words
the framework and the application authors wrote. A product name, a coach's biography, a support
article typed by a person: those are data, they belong to the application's model, and translating
them is a schema question (which field, which language, what happens when only one is filled in)
that no framework can answer.
The framework translates its own words and the application's own words, and stops there.

**Also out, and less likely to be assumed:** locale-aware sorting and searching (no `Collator`, no
`BreakIterator`, no `Normalizer` in TeaVM — §2); any translation workflow, format conversion or
translator tooling, `.po` files included; machine translation of anything; per-tenant catalogs, as
opposed to per-language; and translated route paths, so `/tasks` stays `/tasks` in every language.

---

## 7. Sequence

| # | Item | Breaking? |
|---|---|---|
| 1 | `RmiRequestContext.getLocale()`, handshake resolution, `zeroz.i18n.defaultLocale` | no |
| 2 | The framework's own catalog: `.properties`, `FrameworkText`, the parity test | no — English is byte-identical |
| 3 | The framework's refusals become `Message` values; `ClientVisibleException(Message)`; `assertRefusedWith` | **yes** — §4.8 |
| 4 | `@MessageCatalog`, the generated `_Text` and `_Catalog`, the substitutor, the key-parity test | no |
| 5 | Catalog on the AUTH frame at protocol version 3 | protocol change |
| 6 | The `zeroz.locale` signal, live switching, the cookie, `LocalePreferenceStore` | no |
| 7 | `LanguageSelector` and `Formats` | no |

3 is placed third because it is the breaking one and it gets more expensive with every application
that ships. 5 is the protocol change, and it wants the version byte bumped once rather than twice,
so anything else wanting a protocol change should ride with it.

Items 1, 2 and 3 together are a complete and useful release on their own: the server speaks the
caller's language and the client does not change at all.

---

## 8. Decisions

Settled on 2026-09-01. Section 9 keeps the reasoning that led to each.

1. **Remembered per browser, with a hook for applications.** Option B. The cookie is the behavior
   nobody has to ask for; an application that wants the choice to follow a person to a second
   computer implements `LocalePreferenceStore`. The framework does not learn to write application
   data, which keeps the question the transactions design left open still closed.
2. **The framework ships its own forty strings in English only, for now.** English is the fallback,
   so a project that adds no language sees no change at all, and every string the framework shows
   is one an application can override in its own catalog. Shipping a second language costs every
   application that download forever, and we have no evidence yet which second language is wanted.
   The example application demonstrates a second language, which is where it proves the mechanism
   without charging everybody for it.
3. **The catalog rides on the AUTH frame.** As designed. It is already sent, it already carries a
   version byte, and a separate fetch would be a second round trip before the first screen.
4. **Numbers, dates and money stay off unless a project asks.** One `MessageFormat` call makes the
   download 43% bigger -- more than twenty languages of text. The price is written next to the
   instructions, not buried.
5. **English stays the fallback and today's behavior is unchanged.** A project that adds no
   language keeps working exactly as it does now, including anything reading a message's wording.
6. **The stale-label build check fails from the first day.** Nothing uses language support yet, so
   there is no violation to grandfather in and no cost to being strict. Six checks added in 0.8.0
   fail the build rather than warning, and every one of them earned its keep; a warning nobody
   fails on is a warning nobody reads.

## 9. How each decision was reached

**1. Remembering the language per person needs somewhere to write it. Which?**

Decision 2 says the language is remembered per signed-in user. The framework has no user store, so
by itself it can only remember per browser.

| Option | What it gives | What it costs |
|---|---|---|
| A. Cookie only | Right on the browser they chose it on. Survives restarts, reloads, a cluster. | A second computer, or a private window, starts from the browser's setting again. |
| B. Cookie + a `LocalePreferenceStore` an application implements | Truly per person, for applications that want it | One interface, one `ServiceLoader` file, and every application has to write it |
| C. The framework writes to the application's store | Per person, free for the application | Opens the question the transactions design deliberately left open: does the framework own any data at all |

**Recommendation: B.** It is one small interface, it makes the framework's own limit explicit rather
than pretending, and it does not commit the project to owning application data. A is the behavior
when nobody implements it, which is most projects, and it is good enough for them.

**2. Which languages does this project ship its own forty strings in?**

Every one of them is compiled into every application's bundle, in every language shipped, forever.
It is the only place per-language cost is unavoidable.

It is also small: about **0.8 KB of download per language** (§2), against a 209 KB baseline. So the
question is not a budget one. It is a maintenance one: is this a framework that ships English and a
documented way to add a language, or one that ships six and keeps them all correct forever?

**Recommendation: English plus German**, because German is the project's own second language and one
real translation proves the mechanism in a way zero do not. Everything else is contributed, and the
contribution is one `.properties` file.

**3. Does the AUTH frame carry the catalog, or does the client ask for it?**

§4.4 proposes the AUTH frame, because it removes a round trip and the catalog is then in hand at
`onResolved`. The alternative already has a pattern to copy: `SyncFrameTypes.java:55-56` reserves
the name `zeroz4j.signals` for framework-internal calls shaped like ordinary RMI, which the engine
intercepts before it dispatches to any service. A `zeroz4j.i18n` alongside it would keep the
protocol unchanged and cost one round trip before any text can be drawn.

**Recommendation: the AUTH frame.** The protocol already has a version byte for exactly this, the
frame is already sent to everybody, and a screen that draws in the wrong language and then corrects
itself is the thing this whole design is meant to avoid.

**4. Does `ClientVisibleException` keep its string constructor forever, or is it deprecated?**

Keeping both means two ways to do one thing, and this project's documentation is unusually strict
about that. Deprecating the string form means a single-language application gets warnings for
writing the correct code for its situation.

**Recommendation: keep both, permanently, and say why in the javadoc.** A one-language application
is a legitimate application, and the string constructor is exactly right for it. The two forms are
not two ways to do one thing — they are the translated case and the untranslated case, and both
exist.

**5. Locale-correct numbers, money and dates cost 43 KB of download. Ship the door, or not?**

`Formats.number()` and friends are the only way to print `1.234,56` to a German reader, and the
first call to any of them adds 43 KB gzip to every visitor's download, with 6 KB more for each
country's data (§2). A translated interface that still prints numbers the English way is visibly
half-done; a 43 KB tax on every application that touches one is real money on a slow connection.

| Option | What it gives |
|---|---|
| A. Ship `Formats`, documented with the price, nothing calls it by default | Applications choose, knowingly |
| B. Ship nothing; document that number formatting is the application's own problem | Nobody pays, nobody is helped |
| C. Ship a hand-written formatter for grouping and decimal separators only, no CLDR | Covers the common case for a few hundred bytes; wrong for currency, dates and anything unusual |

**Recommendation: A, and write the number into the guide beside the API.** The cost is real and the
feature is real, and the framework's job is to make the trade visible rather than to make it for
somebody. C is tempting and is how locale bugs get shipped — a formatter that is right for German
and quietly wrong for Swiss French is worse than none.

**6. Should the reactive-read check ship failing, or warning, for one release?**

§4.9's check will fire on real, working code the first time it runs — the false positive it cannot
avoid. The project's habit is that a check fails the build, and that habit is why the checks work.

**Recommendation: fail from the start.** The `@ReadsMessagesOnce` annotation is the escape hatch,
the failure message names it, and a warning nobody has to act on is a warning nobody reads. This
project has six failing checks and no warning ones, and that is not an accident.
