# Answering in the reader's language

How the server decides what language somebody reads, and how to write a sentence that comes out
right for each of them.

## When to use this

Read this when your application has, or might have, people who do not all read English. If everyone
does, you can stop after the first box: nothing has changed for you and nothing here is required.

!!! note "A project that adds no language changes nothing"
    English is the fallback and it is compiled in. Every sentence the framework produces is exactly
    the sentence it produced before any of this existed, character for character, including the
    ones your tests assert on. Adding a language is a thing you choose to do.

## The shape of it

A sentence stops being words and becomes a **name plus values**. The words are chosen at the last
possible moment, on the edge of the server, where the connection says who is being answered.

```
a service method              the edge of the server            the person
picks a message  ────────▶    turns it into words       ────────▶  reads it
(no language yet)             (in the caller's language)          in their own language
                                      │
                                      └────▶ the server log, always in English
```

That split is the whole idea. A refusal chosen deep inside a service knows nothing about
connections and should not have to. The one place that already decides what a caller is told is
also the one place that knows who the caller is, so that is where the words are picked.

## Declaring a catalog

Put the text in the **shared** module — the one module compiled into both the browser and the
server — as ordinary `.properties` files:

```
myapp-shared/src/main/resources/i18n/app.properties      the fallback language
myapp-shared/src/main/resources/i18n/app_de.properties   German
myapp-shared/src/main/resources/i18n/app_fr.properties   French
```

```properties
# app.properties
task.add = Add task
task.remaining = {0} of {1} tasks left
invoice.alreadyApproved = Invoice {0} was already approved.
```

Then one empty marker class, in the same module, saying where they are:

```java
package com.example.shared;

import com.zeroz4j.api.i18n.MessageCatalog;

@MessageCatalog(baseName = "i18n/app", fallback = "en")
public final class AppText {
    private AppText() { }
}
```

`baseName` is a **classpath path**, not a package name: `i18n/app`, not `i18n.app`. It reads the
same on both tiers, because both tiers reach the file by the route each of them has.

`fallback` names the language written in the file with no suffix — the one every other language
falls back to, and the one compiled into the browser.

### Files are read as UTF-8

Type `Zugriff verweigert` and it works. Java's own rule for `.properties` files is Latin-1 with
`\uXXXX` escapes for everything else, which is a rule from 1998 that nobody remembers; these files
do not follow it.

## Using the words

The compiler writes one method per key beside your marker class, in a class called `AppText_Text`.
The method is named by camel-casing the key, and it takes one value per blank:

```java
AppText_Text.taskAdd()                    // task.add
AppText_Text.taskRemaining(done, total)   // task.remaining = {0} of {1} tasks left
```

**A method gives you a message, not words.** Turning it into words is a separate, deliberate act:

```java
String words = AppText_Text.taskAdd().text();
```

Four kinds of mistake stop being possible, because the method is real code:

- A misspelled key does not compile.
- Passing one value to a two-blank sentence does not compile.
- Deleting a key from the fallback file breaks every place that used it, so dead text gets noticed.
- A message can be carried around without having chosen a language yet, which is what makes the
  whole thing work.

### Blanks are `{0}`, `{1}` and nothing else

There is no `{0,number,currency}`, no plural form and no date format. That is a decision, not an
oversight: one call into Java's own message formatting makes the browser download **43 percent
bigger** — more than twenty languages of translated text put together. Positional replacement costs
nothing, and it means the browser and the server produce the same sentence character for character,
because they run the same forty lines of code.

If you want a number written the local way, format the number yourself and pass the words in:

```java
AppText_Text.invoiceTotal(myFormatter.format(amount))
```

## Refusing something in the caller's language

`ClientVisibleException` is how you say "this sentence is for the caller". Give it a message
instead of a sentence and the caller reads it in their own language, while the server log keeps
English:

```java
@Override
public void approve(String invoiceId) {
    Invoice invoice = invoices.byId(invoiceId);
    if (invoice.isApproved()) {
        throw new ClientVisibleException(AppText_Text.invoiceAlreadyApproved(invoiceId));
    }
    ...
}
```

**Both forms are correct and both are staying.** They are not two ways to do one thing — one is the
translated case and one is the untranslated case:

```java
throw new ClientVisibleException("That invoice was already approved.");   // still right
```

An application that sells in one language should keep writing the sentence.

## How the server picks a language

Decided **once, when the connection opens**, and then kept on the connection. In order, first
answer wins:

| | Where it comes from | What it is for |
|---|---|---|
| 1 | The `lang` handshake parameter | The browser already knew, from a choice made earlier |
| 2 | The `zeroz-lang` cookie | Making that choice survive a restart and a new connection |
| 3 | The `Accept-Language` header | The person's own browser setting — a first visit, free |
| 4 | `zeroz.i18n.defaultLocale` | What this deployment answers in when nobody has said |
| 5 | English | |

Whatever is asked for is then **narrowed to what you actually translated**. Somebody asking for
Austrian German gets German when you have German and no Austrian German, and gets your own language
when you have neither. A half-translated screen is worse than a screen in one language, so it never
happens.

There is one more step, and it exists only if you ask for it — see
[Remembering it per person](#remembering-it-per-person).

Read it in a service the same way you read who is calling:

```java
Locale language = RmiRequestContext.getLocale();   // never null
```

Never take it from a method argument. That is the same rule identity follows, for the same reason:
an argument is something the caller can lie about and the framework cannot fill in.

## What happens when a key is missing

Looked for in this order, and the first answer wins:

1. The file for the language asked for — `app_de.properties`.
2. The file with no language suffix — `app.properties`. So a German file missing one key shows that
   one sentence in the fallback language, and everything else in German.
3. For the framework's own words, the English compiled into the jar. That cannot fail to load,
   which is why a deployment that configures nothing behaves exactly as it always did.
4. Nothing found: **the key itself is shown**. `task.add` on a screen is ugly and obvious, which is
   the point — an empty space would be a bug nobody reports.

## Keep the languages honest — one test

Only the fallback language is read when your code is compiled. That is on purpose: adding French
has to be dropping a file in and restarting, with nothing regenerated and no browser download
growing. The price is that nothing about your other languages is checked by the compiler at all,
and the two ways a translation goes wrong are both silent:

- **A missing key** leaves the fallback language showing in the middle of a translated screen.
- **A key whose blanks disagree** is worse. A German sentence written with one blank where English
  has two either drops a value the reader needed or leaves a blank showing.

So write this one test in the shared module, and the build fails when a translation drifts:

```java
import com.zeroz4j.server.test.CatalogParity;

@Test
void everyLanguageSaysTheSameThings() {
    CatalogParity.assertConsistent(Paths.get("src/main/resources/i18n"), "app");
}
```

It names the file, the key and what is wrong with it:

```
  app_de.properties has no task.remove, so that sentence comes out in the fallback language.
  app_de.properties gives task.remaining the blanks [0] where the fallback language gives it [0, 1].
```

`CatalogParity` is in `zerozstack-server-test`, which you already take at test scope.

## The framework's own words

This project ships its own forty-odd sentences - access denied, not signed in, the name on a
language picker - in **English and German**, and nothing else.

German because it is the language this project's author writes, so the translation can be reviewed
rather than guessed at. That is the whole criterion, and it is why there is not a third: a
translation nobody here can check is worse than one language, because a wrong sentence in a language
you cannot read looks exactly like a right one.

It costs your browser **nothing**. Translated words travel over the connection, not in the bundle,
so a build is byte-identical whether or not this file exists. What it costs is about 1.4 KB in one
jar.

!!! note "The framework's languages are not your deployment's languages"
    `i18n/zeroz4j_de.properties` is on every application's classpath, translated or not - so if it
    counted, every deployment would offer German, and a German browser would be answered with German
    refusals over an English screen. It does not count. **What your deployment can answer in is
    decided by your own catalogs**, and the framework's words ride along with whichever of them you
    have: ship `app_de.properties` and your readers get German refusals for free.

    A server with no interface at all, that wants German refusals and has no catalog of its own,
    says so with `zeroz.i18n.defaultLocale=de`. That setting is for exactly this.

## Changing the framework's own wording

The framework ships its own words in English only. To have "Access denied" and the rest read in
another language, put a file with the same keys on the server's classpath — the application's own
resources folder is the usual place:

```
myapp-server/src/main/resources/i18n/zeroz4j_de.properties
```

```properties
error.accessDenied = Zugriff verweigert: erfordert die Rolle {0}, vorhanden ist {1}
error.authenticationRequired = Anmeldung erforderlich für: {0}#{1}
```

There is nothing to register. The keys are the constants in `com.zeroz4j.api.i18n.FrameworkKeys`,
and the English behind each of them is in `i18n/zeroz4j.properties` inside
`zerozstack-shared-api` — copy that file and translate it.

## Testing it

`TestServer` opens a connection that reads a given language, so one test can prove the same call is
answered two ways:

```java
try (TestServer server = TestServer.builder().beans(InvoicesImpl.class).start();
     TestConnection english = server.connectSpeaking("en", "alice");
     TestConnection german = server.connectSpeaking("de", "bernd")) {
    ...
}
```

And when a test used to assert on the exact English of a refusal, assert on **which refusal it was**
instead. The wording is a translation now and depends on who asked; the name does not:

```java
import com.zeroz4j.api.i18n.FrameworkKeys;
import com.zeroz4j.server.test.Refusals;

Refusals.assertRefusedWith(FrameworkKeys.ACCESS_DENIED, thrown);
```

Nothing forces this. English is unchanged, so a test asserting on the English sentence still passes.

## Showing the words in the browser

The browser cannot read a `.properties` file, so the server sends it one. When a connection opens,
the words for that connection's language ride on the same frame that tells your application it may
build its first screen. There is nothing to configure and nothing to fetch: by the time your
`onResolved` callback runs, the words are already in the browser.

That means **no screen is ever drawn in English and corrected a moment later.**

### The one rule, and it is the one everybody gets wrong

```java
// WRONG. The words are read once, when the button is built, and never again.
Button add = new Button(AppText_Text.taskAdd().text());

// RIGHT. The words are read inside an effect, so the button follows the language.
Button add = new Button();
Effect.create(() -> add.setText(AppText_Text.taskAdd().text()));
```

It is the same shape as the LiveSync mistake — a value read outside an effect, so nothing
subscribed — and it will be far more common, because a screen has many more labels on it than it
has live objects.

**It is also nearly invisible in testing.** Screens are rebuilt when you navigate, so if you switch
language and then move around your application, everything looks right. The label that stayed
behind is on the screen that was open at the moment of the switch, which is exactly the screen
nobody thinks to check.

### The build fails when you get it wrong

`MessageReadContractTest` reads every file in the checkout that can run in a browser and fails the
build when `.text()` is called outside an effect. It has failed builds since the day this feature
landed, because there was no old code to grandfather in.

Where a read genuinely happens once — a sentence sent to the server, a line written to a log — say
so on the method:

```java
@ReadsMessagesOnce("assembled for one call and sent; never shown")
private String describeForTheServer() {
    return AppText_Text.taskAdd().text();
}
```

**Read what it cannot catch before you trust it.** All of these are real:

- **A read one call deep.** It reads one file's text and cannot follow a call, so a helper called
  from inside an effect is reported and the same helper called from a constructor is not. The one
  exception is a method handed straight to `Effect.create(this::redraw)`, which it follows one hop.
- **Words put in a variable inside an effect and used outside it.** Correct at the moment it runs,
  and indistinguishable from correct code afterwards.
- **English left hard-coded.** `new Button("Add task")` is not translated at all and nothing
  notices. No check can reliably tell a sentence a person reads from a CSS class or a log line.
- **A bad translation.** A German `taskAdd` that says "subtract task" passes everything.

### Carry the message, not the words

A `Message` has no language in it, so it is safe to hold on to and turn into words later. That is
what lets a failure that happened in one language be shown in another:

```java
// The failure is remembered as a message ...
private final ValueSignal<Message> problem = new ValueSignal<>(null);
...
catch (Exception ex) {
    problem.set(AppText_Text.sendFailed(ex.getMessage()));
}

// ... and turned into words where it is drawn, so it follows a switch like everything else.
Effect.create(() -> {
    Message shown = problem.get();
    errorBox.setText(shown == null ? "" : shown.text());
});
```

### Before the connection is up

A screen built before there is a connection — a sign-in card, most often — has no words yet and
would show key names. Register the English your build compiled in, once, at start-up:

```java
ClientMessages.useFallback(AppText_Catalog.BASE_NAME, AppText_Catalog::lookup);
```

Optional, and cheap: `AppText_Catalog` is generated for you and is the fallback language written out
as a `switch`.

## Letting somebody choose

```java
sidebar.add(new LanguageSelector());
```

That is the whole of it. It offers exactly the languages your deployment has words for — the list
arrives with the words when the connection opens — so it can never offer one that would be refused,
and it binds itself to the language, because there is nothing else a language selector could be
bound to.

It is a real `<select>`, so the browser supplies the keyboard: Tab reaches it, the arrow keys move
through the choices, typing jumps by first letters, Enter takes one and Escape leaves it alone.

It announces itself as "Language" in English and "Sprache" in German out of the box, because those
are the two languages this framework ships its own words in — and `setLabel(...)` replaces that
built-in name with words of your own.

**Each language is named in itself** — `Deutsch`, `Français`, `日本語` — never in the language
currently on screen. Somebody who has landed on a page in a language they cannot read is exactly the
person who needs this control. To change a name, or add one for a language this library has never
heard of, put `language.<tag>` in your own `i18n/zeroz4j_*.properties`.

### What happens when somebody uses it

1. The picker shows the new language at once, and the browser writes a `zeroz-lang` cookie so the
   choice survives a reload.
2. The server narrows the choice to a language it really has words for, remembers it on the
   connection so every later call is answered in it, and tells your `LocalePreferenceStore` if you
   registered one.
3. The words go down the wire, and **then** the value that redraws the screen. In that order, so
   nothing redraws twice.
4. Every effect that has read a message re-runs and calls `setText` on the label it already owns.

**Nothing else is rebuilt.** A half-filled form keeps its values, the scroll position does not move,
and the keyboard stays where it was. Only the words change.

Two things worth knowing:

- **Every tab of that browser changes together.** The language belongs to the browser, not to one
  tab.
- **While the connection is down, the words do not change.** The choice is written to the cookie and
  queued, and lands when the connection comes back. The words come from the server, so there is
  nothing to show until there is a server.

## Remembering it per person

Out of the box the choice is remembered in a cookie. That is right on the machine somebody chose it
on, and wrong on their second one.

The framework cannot do better on its own, because it has nowhere of its own to write user data.
If your application already has somewhere, say so in one small class:

```java
public final class UserLanguages implements LocalePreferenceStore {
    public Locale forUser(String userName) {
        Account account = accounts.byName(userName);
        return account == null ? null : account.language();
    }
    public void remember(String userName, Locale locale) {
        accounts.byName(userName).setLanguage(locale);
    }
}
```

Name it in `META-INF/services/com.zeroz4j.server.LocalePreferenceStore`. Found by `ServiceLoader`,
not by CDI, because a handshake runs before any bean exists — the same as `AuthenticationProvider`.

It is asked once, at the handshake, only for a connection that signed in, and it sits **after** the
language the browser asked for outright and **before** the cookie. So somebody who has just picked a
language on this machine gets what they picked, and a fresh browser gets what they chose last time
somewhere else.

## Numbers, dates and money

A translated screen that prints `1,234.56` to a German reader is not translated.

```java
AppText_Text.invoiceTotal(Formats.currency().format(amount))
```

`Formats.number()`, `integer()`, `percent()`, `currency()`, `date()` and `dateTime()` all read the
reader's language — the language on screen in the browser, the caller's language on the server — so
one call site is right on both tiers.

!!! warning "This is the most expensive line in the framework. Read the number before you use it."
    Nothing else in ZeroZ Stack touches `java.text`, and that is deliberate. **The first call from a
    client module into any method on `Formats` adds 233 KB to the bundle and 43 KB to what every
    visitor downloads, gzipped.** Every locale you then name in `java.util.Locale.available` costs
    about 36 KB more, 6 KB gzipped.

    For proportion: translating your interface into twenty languages costs the browser **nothing at
    all**, because the words travel over the connection. Formatting one number the German way costs
    more than every language of text you will ever ship, several times over.

Calling it is not enough. TeaVM compiles in locale data only for the locales your build names, and
the default is one. In your client module's `teavm-maven-plugin` configuration:

```xml
<properties>
  <java.util.Locale.available>en_US,de_DE,fr_FR</java.util.Locale.available>
  <java.util.Locale.default>en_US</java.util.Locale.default>
</properties>
```

**`java.util.Locale.default` must contain an underscore.** TeaVM splits it on one, and a value
without it fails at class initialization with nothing useful to read.

Four things it does not do:

- **A language with no locale data formats as English.** No error and no warning — the numbers are
  simply grouped the English way. The two lists (languages you translated, locales whose data you
  compiled in) live in different files and nothing compares them.
- **The locale decides the currency,** which is almost never what you want: a German reader looking
  at a dollar invoice must see dollars. Set the currency on the returned format explicitly.
- **Time zones are off unless your build asks.** TeaVM's `java.util.TimeZone.autodetect` defaults to
  false. A timestamp translated into German and shown in UTC is still the wrong time.
- **`ZonedDateTime` and `OffsetDateTime` do not cross the wire at all.** Send an `Instant` and
  format it against a zone the browser knows.

## What this does not do yet

- **Plural rules of any kind.** `"{0} tasks left"` says "1 tasks left". Write two keys and an `if`;
  that costs nothing, and the alternative costs 57 KB of download and is still wrong for Polish,
  Russian, Arabic and Welsh.
- **The framework's own words inside the browser** — the reconnect banner, `Close` on a drawer,
  `Copied` after a copy button, the offline page — are English literals and are not in a catalog.
  The words on a `LanguageSelector` are the exception; those are.
- **The framework ships two languages, English and German.** A third is a `.properties` file
  somebody contributes, and it costs the browser nothing to add.
- **Validation messages** are compiled into `<Model>_Rules` as written and are not translatable.
- **There is no check for English left hard-coded** in a screen.

Right-to-left layouts, locale-aware sorting, translation tooling, per-tenant catalogs, translated
route paths, and translating content stored in your database are all deliberately out of scope and
are not planned. The reasoning is in the
[language support design](../design/language-support.md).

## Seeing all of it working

`zerozstack-examples/chat-livesync` is translated into German end to end: a catalog in its shared
module and a `LanguageSelector` in the side panel. It translates none of the framework's own words -
it does not have to, because the framework ships German itself, which is why the picker announces
itself as `Sprache` with nothing in the example doing that. Run it, type something into the topic
box, and switch language while you look at it.
