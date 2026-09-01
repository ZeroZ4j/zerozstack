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

!!! note "Steps 1 and 2 are read but nothing writes them yet"
    The server reads the parameter and the cookie today. Nothing in the framework puts them there
    yet — a language picker in the browser, and the live switching that goes with it, are the second
    half of this work. What works now is step 3 onward: a person's browser preference, your
    deployment's own setting, and English.

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

## Translating the framework's own refusals

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

## What this does not do yet

The server speaks the caller's language. The browser does not yet — see
[limitations](../reference/limitations.md#language) for the full list. The short version: there is
no language picker, no live switching, and the words in the browser are still the ones compiled
into it.

Right-to-left layouts, plural rules, and translating content stored in your database are all
deliberately out of scope and are not planned. The reasoning is in the
[language support design](../design/language-support.md).
