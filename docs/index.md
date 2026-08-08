# ZeroZ Stack documentation

ZeroZ Stack is a pure-Java full-stack framework. Your UI is Java compiled by TeaVM to run in the browser,
your network layer is a binary RPC protocol over a persistent WebSocket, and your database is the JVM
object graph on disk. You write no JavaScript, no JSON, no REST routes and no SQL.

!!! warning "Experimental"
    ZeroZ Stack is an experimental proof-of-concept at version **0.6.0**. It is a working demonstration,
    not an industrialised production framework. Known gaps are listed in
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
still keep one browser's state to itself, and an **origin check** on every handshake. Both are
described in [Authentication and authorization](guides/security-auth.md).

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
