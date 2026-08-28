# ZeroZ Stack documentation

ZeroZ Stack is a pure-Java full-stack framework. Your UI is Java compiled by TeaVM to run in the browser,
your network layer is a binary RPC protocol over a persistent WebSocket, and your database is the JVM
object graph on disk. You write no JavaScript, no JSON, no REST routes and no SQL.

!!! warning "Experimental"
    ZeroZ Stack is an experimental proof-of-concept at version **0.8.0**. It is a working demonstration,
    not an industrialized production framework. Known gaps are listed in
    [Limitations](reference/limitations.md), and every page states its own limits where the feature is
    taught.

!!! note "Compilation target"
    The client is written entirely in Java and compiled ahead-of-time by TeaVM. It currently targets
    TeaVM's **JavaScript** backend, deliberately: WasmGC does not yet provide functionality ZeroZ Stack
    depends on. **WasmGC is the intended destination** and the project will move to it once TeaVM's
    support is complete. Nothing in the code you write changes either way.

## Three ways in

<div class="grid cards" markdown>

- **Learn it**

    Start from nothing and get a running application.

    [Prerequisites and quickstart →](start/quickstart.md)

- **Decide**

    The framework's hardest question: which construct carries this piece of state?

    [Choosing how state moves →](decide/index.md)

- **Look it up**

    Annotations, APIs, the wire protocol, supported types.

    [Reference →](reference/glossary.md)

</div>

## The shape of an application

Three modules. The shared one compiles into both tiers, which is why a contract violation is a
compile error rather than a runtime surprise.

```java
// shared — the contract, compiled into client and server alike
@DataModel
public class ChatMessage {
    private String author;
    private String text;

    public ChatMessage() { }                        // required: public no-arg constructor
    public ChatMessage(String author, String text) {
        this.author = author;
        this.text = text;
    }
    // getters and setters for every serialized field
}

@RmiService
public interface ChatService {
    void sendMessage(ChatMessage msg);
    List<ChatMessage> getHistory();
}
```

```java
// server — an ordinary CDI bean
@ApplicationScoped
public class ChatServiceImpl implements ChatService {
    @Inject private EventPublisher events;

    @Override
    public void sendMessage(ChatMessage msg) {
        events.publish(ChatEvents.MESSAGE_POSTED, msg);
    }
}
```

```java
// client — Java, compiled by TeaVM. The call suspends; it does not block.
ChatService chat = new ChatService_Stub();
sendButton.addClickListener(e -> chat.sendMessage(new ChatMessage(author, text)));
```

## New in 0.8.0

<div class="grid cards" markdown>

- **Every control works without a mouse**

    Tabs, menus, links, copy buttons, splitters and drop boxes are all reachable with Tab and
    pressed with Enter, and every one of them says what it is. A build check fails when a new
    component forgets.

    [What a control owes a person with no mouse →](guides/ui-keyboard-and-naming.md)

- **A field can say what it is, and why it refused**

    `new TextField().withLabel("Email address")` puts a real caption on the field. A failed check
    now writes its sentence under the field instead of turning the box red and saying nothing.

    [Forms and binding →](guides/ui-forms-and-validation.md)

- **Overlays that behave like overlays**

    A dialog takes over the page and Escape closes it. A drawer holds the keyboard while it is
    open. What sits above what is a named layer — `PAGE` to `TOOLTIP` — instead of a number
    somebody guessed.

    [Stacking overlays →](guides/ui-layering.md)

- **A record can cross the wire**

    `public record Money(long amount, String currency) { }` replaces ten lines of constructor,
    getters and setters. A `sealed` interface travels as itself, so what arrives is the real type
    and not a cast. Fields inherited from a base class stop vanishing.

    [Declaring the types that cross the wire →](guides/data-models.md)

- **A server inside a test, in a tenth of a second**

    Start one in the same process, open connections to it, and count what the browser was sent.
    Two servers can run side by side without sharing anything.

    [Testing an application →](guides/testing.md)

- **Leaving a screen shuts it down**

    `replaceContents` swaps what is inside a container and tells everything on its way out. Before
    this, `onDetach` almost never ran, so the screen you had just left kept its timers going.

    [Swapping what is inside something →](UI_COMPONENTS.md#swapping-what-is-inside-something)

</div>

## New in 0.7.0

<div class="grid cards" markdown>

- **Files from the person using the app**

    A drop-or-pick box with a progress bar and a cancel button for each file, and one Java class on
    the server that is handed each finished file. 25 MB per file by default.

    [Accepting file uploads →](guides/file-uploads.md)

- **Numbers instead of "whatever the container allows"**

    The biggest message the server accepts is 4 MB. One connection may have 32 messages being
    handled at once, and 256 messages or 8 MB waiting to go out. All six are settings, and the one
    in force is written to the log at startup.

    [Every setting, in one table →](guides/packaging.md)

- **Editing one thing at a time**

    `LiveMutex` makes the second person wait instead of overwriting the first. A caller waits 30
    seconds, callers are served in the order they arrived, and a dropped connection tells the holder
    its lock is gone.

    [Locking an object while you edit it →](LIVESYNC.md#locking-an-object-while-you-edit-it)

- **What a client may ask for**

    The server keeps a record of the objects it sent to each browser, and answers a re-read or a
    lock request only from that record. It survives a reconnect, holds 10,000 objects per browser,
    and is dropped after 24 hours idle.

    [What re-sync will and will not send back →](LIVESYNC.md#what-re-sync-will-and-will-not-send-back)

- **Errors you can trace**

    An unexpected failure reaches the caller as one sentence and a short code, with the real message
    in the server log under the same code. Throw `ClientVisibleException` for text the caller should
    read.

    [What an error tells the caller →](guides/security-auth.md#what-an-error-tells-the-caller)

</div>

## New in 0.6.0

<div class="grid cards" markdown>

- **State that belongs to somebody**

    `Signals.shared` is one value for the whole server. `Signals.scoped` holds one per tenant, user
    or browser — and a client only ever sees its own. `Scope.CLIENT` needs no login at all.

    [Scoped signals →](SIGNALS.md#scoped-signals-one-value-per-tenant-user-or-browser)

- **URLs mapped to views**

    Real paths, nested layouts, typed parameters — and each route declares the data it needs, loaded
    before anything renders. The route table is generated at compile time.

    [Routing →](ROUTING.md)

- **Logging in for real**

    Authorization-code flow with PKCE against Keycloak in the browser, token verification at the
    handshake, and its claims becoming roles and a tenant.

    [OpenID Connect →](guides/oidc-auth.md)

- **Deployment into an application server**

    Take `zerozstack-server-jakarta` instead of the Helidon binding and a WAR runs on WildFly,
    Payara, Open Liberty or TomEE — with RMI calls on container threads, so `java:comp` lookups
    work inside a service.

    [Packaging and running →](guides/packaging.md)

- **Installable, with push**

    One call and three tags make an application installable and push-capable. It does not make it
    work offline — nothing here can, and the page says so plainly rather than pretending.

    [PWA →](PWA.md)

</div>

Alongside those: a server-issued, `HttpOnly` **client identity** so an application with no login can
still keep one browser's state to itself, and a check on every handshake that the page opening it is
one of yours. Both are described in
[Authentication and authorization](guides/security-auth.md).

## If you read one page

[Choosing how state moves](decide/index.md). ZeroZ Stack gives you five ways to move state — local
signals, RMI calls, server events, shared signals and LiveSync — and picking the wrong one is the most
common source of trouble in ZeroZ Stack applications. The symptoms are rarely obvious: a feature works on
your machine and fails for the second user, or works until someone reloads the page.

## Working with an AI agent

The stack is designed so a coding agent holds one language and one model of your data instead of four.
To get the benefit, point your agent at [AGENTS.md](https://github.com/ZeroZ4j/zerozstack/blob/main/AGENTS.md)
in the repository root; it carries the build commands, the decision procedure and the silent-failure
list in a form an agent can act on. The repository also ships a `context7.json`, so that guidance
reaches agents working through Context7 once the library is indexed.
