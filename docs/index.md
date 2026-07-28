# ZeroZ4j documentation

ZeroZ4j is a pure-Java full-stack framework. Your UI is Java compiled by TeaVM to run in the browser,
your network layer is a binary RPC protocol over a persistent WebSocket, and your database is the JVM
object graph on disk. You write no JavaScript, no JSON, no REST routes and no SQL.

!!! warning "Experimental"
    ZeroZ4j is an experimental proof-of-concept at version **0.4.0**. It is a working demonstration,
    not an industrialised production framework. Known gaps are listed in
    [Limitations](reference/limitations.md), and every page states its own limits where the feature is
    taught.

!!! note "Compilation target"
    The client is written entirely in Java and compiled ahead-of-time by TeaVM. It currently targets
    TeaVM's **JavaScript** backend, deliberately: WasmGC does not yet provide functionality ZeroZ4j
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

## If you read one page

[Choosing how state moves](decide/index.md). ZeroZ4j gives you five ways to move state — local
signals, RMI calls, server events, shared signals and LiveSync — and picking the wrong one is the most
common source of trouble in ZeroZ4j applications. The symptoms are rarely obvious: a feature works on
your machine and fails for the second user, or works until someone reloads the page.

## Working with an AI agent

The stack is designed so a coding agent holds one language and one model of your data instead of four.
To get the benefit, point your agent at [AGENTS.md](https://github.com/fschoning/zeroz4j/blob/main/AGENTS.md)
in the repository root; it carries the build commands, the decision procedure and the silent-failure
list in a form an agent can act on. The repository also ships a `context7.json`, so that guidance
reaches agents working through Context7 once the library is indexed.
